from __future__ import annotations

import json
import operator
import re
from typing import Annotated, List, TypedDict

from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import PromptTemplate
from langchain_ollama import OllamaLLM
from langgraph.graph import END, StateGraph


# ── State ─────────────────────────────────────────────────────────────────────

class DocState(TypedDict):
    files: List[dict]               # [{"name": str, "content": str}]
    model: str
    api_title: str
    api_version: str
    base_url: str
    framework: str
    language: str
    endpoints: List[dict]
    docs_markdown: str
    openapi_yaml: str
    review_notes: str
    current_step: str
    error: str
    progress_messages: Annotated[List[str], operator.add]


# ── Helpers ───────────────────────────────────────────────────────────────────

def _llm(model: str) -> OllamaLLM:
    return OllamaLLM(model=model)


def _extract_json(text: str):
    text = text.strip()
    try:
        return json.loads(text)
    except Exception:
        pass

    code_block = re.search(r"```(?:json)?\s*([\s\S]*?)```", text)
    if code_block:
        try:
            return json.loads(code_block.group(1).strip())
        except Exception:
            pass

    for pattern in (r"\[[\s\S]*\]", r"\{[\s\S]*\}"):
        m = re.search(pattern, text)
        if m:
            try:
                return json.loads(m.group(0))
            except Exception:
                pass

    return None


def _keyword_detect(code: str) -> tuple[str, str]:
    low = code.lower()
    if "fastapi" in low:
        return "FastAPI", "python"
    if "flask" in low:
        return "Flask", "python"
    if "django" in low and "rest_framework" in low:
        return "Django REST", "python"
    if "express" in low and ("require(" in low or "import express" in low):
        return "Express", "javascript"
    if "koa" in low:
        return "Koa", "javascript"
    if "gin.default" in low or "gin.new" in low:
        return "Go-gin", "go"
    if "fiber.new" in low or "fiber.app" in low:
        return "Go-fiber", "go"
    return "Unknown", "unknown"


# ── Nodes ─────────────────────────────────────────────────────────────────────

def detect_framework(state: DocState) -> dict:
    files = state["files"]
    if not files:
        return {
            "framework": "Unknown", "language": "unknown",
            "current_step": "detect_framework",
            "error": "No files provided",
            "progress_messages": ["No files found"],
        }

    snippet = "\n\n".join(
        f"# {f['name']}\n{f['content'][:1500]}" for f in files[:3]
    )

    prompt = PromptTemplate.from_template(
        'Identify the web framework in this code.\n'
        'Reply with ONLY a JSON object, no other text:\n'
        '{{"framework": "FastAPI|Flask|Django REST|Express|Koa|Go-gin|Go-fiber|Spring Boot|Other", '
        '"language": "python|javascript|typescript|go|java|other"}}\n\n'
        'Code:\n{code}\n\nJSON:'
    )
    try:
        result = (prompt | _llm(state["model"]) | StrOutputParser()).invoke({"code": snippet})
        parsed = _extract_json(result)
        if parsed and isinstance(parsed, dict) and "framework" in parsed:
            fw, lang = parsed["framework"], parsed.get("language", "unknown")
        else:
            fw, lang = _keyword_detect(snippet)
    except Exception:
        fw, lang = _keyword_detect(snippet)

    return {
        "framework": fw, "language": lang,
        "current_step": "detect_framework",
        "progress_messages": [f"Detected: {fw} ({lang})"],
    }


def extract_endpoints(state: DocState) -> dict:
    files = state["files"]
    framework = state["framework"]
    llm = _llm(state["model"])
    all_endpoints: list[dict] = []
    seen: set[str] = set()

    prompt = PromptTemplate.from_template(
        "Extract ALL API endpoints from this {framework} code.\n"
        "Return ONLY a valid JSON array. Each object must have:\n"
        '- "method": HTTP method (GET/POST/PUT/DELETE/PATCH)\n'
        '- "path": URL path, path params as {{param}}\n'
        '- "function_name": handler function name\n'
        '- "summary": one-line description inferred from function name/docstring\n'
        '- "path_params": array of path param name strings\n'
        '- "query_params": array of {{"name": str, "type": str, "required": bool}} objects\n'
        '- "request_body": string description of body schema or null\n'
        '- "response_schema": string description of success response or null\n'
        '- "auth_required": true if auth dependency/decorator present\n'
        '- "tags": array of tag strings for grouping\n\n'
        "Return [] if no endpoints. No explanation.\n\n"
        "File: {filename}\n\nCode:\n{code}\n\nJSON array:"
    )
    chain = prompt | llm | StrOutputParser()

    for file_info in files:
        name = file_info["name"]
        content = file_info["content"]
        for chunk in [content[i : i + 4000] for i in range(0, len(content), 4000)][:3]:
            try:
                raw = chain.invoke({"code": chunk, "framework": framework, "filename": name})
                parsed = _extract_json(raw)
                if not isinstance(parsed, list):
                    continue
                for ep in parsed:
                    if not isinstance(ep, dict):
                        continue
                    method = str(ep.get("method", "")).upper()
                    path = str(ep.get("path", ""))
                    if not method or not path:
                        continue
                    key = f"{method}:{path}"
                    if key in seen:
                        continue
                    seen.add(key)
                    ep["method"] = method
                    ep["source_file"] = name
                    all_endpoints.append(ep)
            except Exception:
                continue

    return {
        "endpoints": all_endpoints,
        "current_step": "extract_endpoints",
        "progress_messages": [f"Found {len(all_endpoints)} endpoints"],
    }


def generate_docs(state: DocState) -> dict:
    endpoints = state["endpoints"]
    framework = state["framework"]
    base_url = state["base_url"]
    api_title = state["api_title"]
    llm = _llm(state["model"])

    if not endpoints:
        return {
            "docs_markdown": "# No endpoints found\n\nNo API endpoints were detected.",
            "current_step": "generate_docs",
            "progress_messages": ["No endpoints to document"],
        }

    prompt = PromptTemplate.from_template(
        "Write professional API documentation for this endpoint. Use Markdown.\n\n"
        "Endpoint JSON:\n{endpoint_json}\n\n"
        "Framework: {framework} | Base URL: {base_url}\n\n"
        "Use this exact format:\n"
        "### `{method} {path}`\n\n"
        "> {summary}\n\n"
        "**Description:** 2-3 sentences about behaviour, use cases, and notes.\n\n"
        "{params_section}"
        "{body_section}"
        "**Responses:**\n\n"
        "| Status | Description |\n"
        "|--------|-------------|\n"
        "| `200` | Success |\n"
        "| `400` | Bad Request |\n"
        "| `401` | Unauthorized |\n"
        "| `404` | Not Found |\n"
        "| `500` | Server Error |\n\n"
        "**Example:**\n"
        "```http\n"
        "{method} {base_url}{path}\n"
        "Authorization: Bearer <token>\n"
        "```\n\n"
        "---\n\n"
        "Output ONLY the documentation block above, nothing else."
    )
    chain = prompt | llm | StrOutputParser()

    sections = [f"# {api_title} — API Reference\n\n"]
    groups: dict[str, list] = {}
    for ep in endpoints:
        tag = ((ep.get("tags") or []) + ["General"])[0]
        groups.setdefault(tag, []).append(ep)

    for tag, eps in groups.items():
        sections.append(f"## {tag}\n\n")
        for ep in eps:
            method = ep.get("method", "GET")
            path = ep.get("path", "/")
            summary = ep.get("summary") or f"{method} {path}"

            path_params = ep.get("path_params") or []
            query_params = ep.get("query_params") or []
            params_section = ""
            if path_params or query_params:
                params_section = (
                    "**Parameters:**\n\n"
                    "| Name | In | Type | Required | Description |\n"
                    "|------|----|------|----------|-------------|\n"
                )
                for p in path_params:
                    params_section += f"| `{p}` | path | string | Yes | — |\n"
                for q in query_params:
                    if isinstance(q, dict):
                        req = "Yes" if q.get("required") else "No"
                        params_section += f"| `{q.get('name','?')}` | query | {q.get('type','string')} | {req} | — |\n"
                    elif isinstance(q, str):
                        params_section += f"| `{q}` | query | string | No | — |\n"
                params_section += "\n"

            body = ep.get("request_body")
            body_section = f"**Request Body:** {body}\n\n" if body else ""

            try:
                doc = chain.invoke({
                    "endpoint_json": json.dumps(ep, indent=2),
                    "framework": framework,
                    "base_url": base_url,
                    "method": method,
                    "path": path,
                    "summary": summary,
                    "params_section": params_section,
                    "body_section": body_section,
                })
                sections.append(doc + "\n\n")
            except Exception:
                sections.append(
                    f"### `{method} {path}`\n\n> {summary}\n\n"
                    f"{params_section}{body_section}---\n\n"
                )

    return {
        "docs_markdown": "".join(sections),
        "current_step": "generate_docs",
        "progress_messages": [f"Documented {len(endpoints)} endpoints"],
    }


def generate_openapi(state: DocState) -> dict:
    endpoints = state["endpoints"]
    api_title = state["api_title"]
    api_version = state["api_version"]
    base_url = state["base_url"]
    llm = _llm(state["model"])

    if not endpoints:
        yaml_out = (
            f'openapi: "3.0.0"\n'
            f'info:\n  title: "{api_title}"\n  version: "{api_version}"\npaths: {{}}\n'
        )
        return {
            "openapi_yaml": yaml_out,
            "current_step": "generate_openapi",
            "progress_messages": ["Generated empty OpenAPI spec"],
        }

    prompt = PromptTemplate.from_template(
        "Generate a complete OpenAPI 3.0.0 YAML specification.\n\n"
        "API Title: {title}\nVersion: {version}\nBase URL: {base_url}\n\n"
        "Endpoints:\n{endpoints_json}\n\n"
        "Rules:\n"
        "1. Output ONLY valid YAML — no markdown fences, no explanation\n"
        "2. Use openapi: \"3.0.0\"\n"
        "3. Define path parameters with schema: {{type: string}}\n"
        "4. Add requestBody for POST/PUT/PATCH with application/json content\n"
        "5. Include 200, 400, 401, 404, 500 responses\n"
        "6. Add tags array for each operation\n"
        "7. Add a basic components/schemas section\n\n"
        "YAML:"
    )
    chain = prompt | llm | StrOutputParser()

    try:
        raw = chain.invoke({
            "title": api_title,
            "version": api_version,
            "base_url": base_url,
            "endpoints_json": json.dumps(endpoints[:20], indent=2),
        })
        # Strip markdown fences if the model added them
        if "```" in raw:
            m = re.search(r"```(?:yaml|yml)?\s*([\s\S]*?)```", raw)
            raw = m.group(1).strip() if m else raw
        yaml_out = raw.strip()
    except Exception:
        yaml_out = _minimal_openapi(api_title, api_version, base_url, endpoints)

    return {
        "openapi_yaml": yaml_out,
        "current_step": "generate_openapi",
        "progress_messages": [f"OpenAPI spec generated ({len(endpoints)} paths)"],
    }


def _minimal_openapi(title: str, version: str, base_url: str, endpoints: list) -> str:
    lines = [
        'openapi: "3.0.0"',
        "info:",
        f'  title: "{title}"',
        f'  version: "{version}"',
        "servers:",
        f'  - url: "{base_url}"',
        "paths:",
    ]
    path_groups: dict[str, list] = {}
    for ep in endpoints:
        path_groups.setdefault(ep.get("path", "/"), []).append(ep)

    for path, eps in path_groups.items():
        lines.append(f"  {path}:")
        for ep in eps:
            method = ep.get("method", "GET").lower()
            summary = ep.get("summary") or f"{ep.get('method','GET')} {path}"
            lines += [
                f"    {method}:",
                f'      summary: "{summary}"',
                "      responses:",
                '        "200":',
                '          description: "Success"',
            ]
    return "\n".join(lines)


def review_docs(state: DocState) -> dict:
    docs_markdown = state["docs_markdown"]
    llm = _llm(state["model"])

    prompt = PromptTemplate.from_template(
        "Review this API documentation and give concise, actionable feedback.\n\n"
        "Look for:\n"
        "1. Vague or missing endpoint descriptions\n"
        "2. Parameters missing type or requirement info\n"
        "3. Missing authentication notes\n"
        "4. Undocumented error scenarios\n"
        "5. Inconsistent naming conventions\n\n"
        "Docs (excerpt):\n{docs}\n\n"
        "Format your response as:\n"
        "## Review Summary\n"
        "(1-2 sentence overall quality assessment)\n\n"
        "## Issues Found\n"
        "- (specific issues referencing endpoints, or 'No issues found')\n\n"
        "## Suggestions\n"
        "- (2-3 concrete improvements)\n"
    )
    chain = prompt | llm | StrOutputParser()

    excerpt = docs_markdown[:3000] + ("…" if len(docs_markdown) > 3000 else "")
    try:
        review = chain.invoke({"docs": excerpt})
    except Exception as e:
        review = f"Review unavailable: {e}"

    return {
        "review_notes": review,
        "current_step": "review_docs",
        "progress_messages": ["Review complete"],
    }


# ── Graph ─────────────────────────────────────────────────────────────────────

def build_graph():
    wf = StateGraph(DocState)
    wf.add_node("detect_framework", detect_framework)
    wf.add_node("extract_endpoints", extract_endpoints)
    wf.add_node("generate_docs", generate_docs)
    wf.add_node("generate_openapi", generate_openapi)
    wf.add_node("review_docs", review_docs)

    wf.set_entry_point("detect_framework")
    wf.add_edge("detect_framework", "extract_endpoints")
    wf.add_edge("extract_endpoints", "generate_docs")
    wf.add_edge("generate_docs", "generate_openapi")
    wf.add_edge("generate_openapi", "review_docs")
    wf.add_edge("review_docs", END)

    return wf.compile()
