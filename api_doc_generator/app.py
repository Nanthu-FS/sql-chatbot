import os
import pathlib
import sys

import streamlit as st

sys.path.insert(0, str(pathlib.Path(__file__).parent.parent))
from api_doc_generator.graph import DocState, build_graph

# ── Page config ───────────────────────────────────────────────────────────────
st.set_page_config(
    page_title="API Doc Generator",
    page_icon="📄",
    layout="wide",
    initial_sidebar_state="expanded",
)

# ── CSS ───────────────────────────────────────────────────────────────────────
st.markdown("""
<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap');

*, *::before, *::after { box-sizing: border-box; }

html, body, [data-testid="stAppViewContainer"] {
    background: #f5f5f3 !important;
    font-family: 'Inter', sans-serif !important;
    color: #1c1c1c !important;
}
#MainMenu, footer, [data-testid="stToolbar"], [data-testid="stDecoration"] { display: none !important; }

.main .block-container { padding: 2rem 2.5rem !important; max-width: 100% !important; }

/* Sidebar */
[data-testid="stSidebar"] {
    background: #ffffff !important;
    border-right: 1px solid #e8e8e6 !important;
    min-width: 270px !important; max-width: 270px !important;
}
[data-testid="stSidebar"] p, [data-testid="stSidebar"] span,
[data-testid="stSidebar"] div, [data-testid="stSidebar"] label { color: #1c1c1c !important; }
[data-testid="stSidebar"] .block-container { padding: 1.5rem 1.2rem !important; }

/* Brand */
.sb-brand { display:flex; align-items:center; gap:9px; padding-bottom:1.2rem;
    border-bottom:1px solid #f0f0ee; margin-bottom:1.4rem; }
.sb-brand-icon { width:28px; height:28px; background:#1c1c1c; border-radius:6px;
    display:flex; align-items:center; justify-content:center; font-size:13px; }
.sb-brand-name { font-size:0.9rem; font-weight:600; letter-spacing:-0.3px; }
.sb-brand-sub  { font-size:0.68rem; color:#9ca3af; margin-top:1px; }
.sb-label { font-size:0.62rem; font-weight:600; color:#b0b0a8;
    text-transform:uppercase; letter-spacing:.1em; margin-bottom:.5rem; }
.sb-section { margin-bottom:1.3rem; }

/* Input area */
.input-card {
    background:#ffffff; border:1px solid #e8e8e6; border-radius:12px;
    padding:1.5rem; margin-bottom:1.2rem;
}
.input-card-title { font-size:0.85rem; font-weight:600; margin-bottom:0.8rem; color:#1c1c1c; }

/* Step indicator */
.steps-row { display:flex; gap:0; margin-bottom:1.5rem; align-items:center; }
.step { display:flex; flex-direction:column; align-items:center; flex:1; }
.step-icon {
    width:32px; height:32px; border-radius:50%;
    display:flex; align-items:center; justify-content:center;
    font-size:0.75rem; font-weight:600; border:2px solid #e8e8e6;
    background:#ffffff; color:#9ca3af; transition:all .3s;
}
.step-icon.done   { background:#1c1c1c; border-color:#1c1c1c; color:#ffffff; }
.step-icon.active { background:#f0fdf4; border-color:#22c55e; color:#15803d; animation:pulse-step 1.5s ease-in-out infinite; }
.step-label { font-size:0.62rem; color:#9ca3af; margin-top:4px; text-align:center; }
.step-label.done   { color:#1c1c1c; font-weight:500; }
.step-label.active { color:#15803d; font-weight:500; }
.step-connector { height:2px; flex:1; background:#e8e8e6; margin:0; margin-bottom:18px; transition:background .3s; }
.step-connector.done { background:#1c1c1c; }
@keyframes pulse-step { 0%,100%{opacity:1} 50%{opacity:.5} }

/* Result tabs */
[data-testid="stTabs"] [data-baseweb="tab"] {
    font-size:0.82rem !important; font-weight:500 !important;
    padding:0.5rem 1rem !important; color:#6b7280 !important;
}
[data-testid="stTabs"] [aria-selected="true"] { color:#1c1c1c !important; }

/* Code blocks */
pre, code { font-family:'SF Mono','Fira Mono',monospace !important; font-size:0.78rem !important; }
[data-testid="stCode"] { border-radius:8px !important; border:1px solid #e8e8e6 !important; }

/* Buttons */
[data-testid="stButton"] > button {
    border-radius:8px !important; font-size:0.82rem !important;
    font-weight:500 !important; transition:all .15s !important;
}
[data-testid="stButton"] > button:hover { transform:translateY(-1px) !important; }
[data-testid="stDownloadButton"] > button {
    background:#f5f5f3 !important; border:1px solid #e8e8e6 !important;
    color:#374151 !important; font-size:0.78rem !important;
    border-radius:7px !important; font-weight:500 !important;
}
[data-testid="stDownloadButton"] > button:hover { background:#ebebea !important; }

/* Sidebar buttons */
[data-testid="stSidebar"] [data-testid="stButton"] > button {
    background:#f5f5f3 !important; border:1px solid #e8e8e6 !important;
    color:#374151 !important; width:100% !important; font-size:0.78rem !important;
}
[data-testid="stSidebar"] [data-testid="stButton"] > button:hover {
    background:#1c1c1c !important; color:#fff !important; border-color:#1c1c1c !important;
}

/* Text inputs */
[data-testid="stTextInput"] input, [data-testid="stSelectbox"] div {
    font-size:0.82rem !important; border-radius:7px !important;
}
[data-testid="stTextArea"] textarea {
    font-family:'SF Mono','Fira Mono',monospace !important;
    font-size:0.78rem !important; border-radius:8px !important;
    border:1px solid #d6d6d3 !important;
}

/* Alert */
[data-testid="stAlert"] {
    background:#fef2f2 !important; border:1px solid #fecaca !important;
    border-radius:8px !important; font-size:0.8rem !important;
}

/* Scrollbar */
::-webkit-scrollbar { width:4px; }
::-webkit-scrollbar-thumb { background:#d6d6d3; border-radius:10px; }

/* Page fade */
.main .block-container { animation:fadeIn .4s ease both; }
@keyframes fadeIn { from{opacity:0} to{opacity:1} }
</style>
""", unsafe_allow_html=True)

# ── Constants ─────────────────────────────────────────────────────────────────

STEPS = [
    ("detect_framework",  "Detect\nFramework"),
    ("extract_endpoints", "Extract\nEndpoints"),
    ("generate_docs",     "Write\nDocs"),
    ("generate_openapi",  "OpenAPI\nSpec"),
    ("review_docs",       "Review"),
]

MODELS = [
    "qwen2.5-coder:32b",
    "qwen2.5:72b",
    "llama3.3:70b",
    "deepseek-r1:70b",
    "mixtral:8x22b",
    "phi4:14b",
    "gemma3:27b",
    "llama3.2:3b",
]

SAMPLE_PATH = pathlib.Path(__file__).parent / "sample_fastapi.py"


def load_sample() -> str:
    return SAMPLE_PATH.read_text()


# ── Sidebar ────────────────────────────────────────────────────────────────────
with st.sidebar:
    st.markdown("""
    <div class="sb-brand">
        <div class="sb-brand-icon">📄</div>
        <div>
            <div class="sb-brand-name">API Doc Generator</div>
            <div class="sb-brand-sub">LangGraph · Ollama · Local</div>
        </div>
    </div>
    """, unsafe_allow_html=True)

    st.markdown('<div class="sb-section"><div class="sb-label">Model</div></div>', unsafe_allow_html=True)
    model = st.selectbox(
        "Model",
        options=MODELS,
        index=0,
        label_visibility="collapsed",
        help="Higher-parameter models produce richer docs. qwen2.5-coder excels at code analysis.",
    )

    st.markdown('<div class="sb-section"><div class="sb-label">API Metadata</div></div>', unsafe_allow_html=True)
    api_title   = st.text_input("API Title",   value="My API",          placeholder="My API")
    api_version = st.text_input("API Version", value="1.0.0",           placeholder="1.0.0")
    base_url    = st.text_input("Base URL",    value="https://api.example.com", placeholder="https://api.example.com")

    st.markdown("<br>", unsafe_allow_html=True)
    if st.button("🗑 Clear results", use_container_width=True):
        for key in ("result_state", "pipeline_log"):
            st.session_state.pop(key, None)
        st.rerun()

    st.markdown("""
    <div style="margin-top:1.5rem;padding:0.75rem;background:#f5f5f3;border-radius:8px;font-size:0.7rem;color:#6b7280;line-height:1.6;">
    <strong style="color:#1c1c1c;">Pipeline</strong><br>
    1. Detect framework &amp; language<br>
    2. Extract endpoints (LLM)<br>
    3. Write Markdown docs<br>
    4. Generate OpenAPI 3.0 YAML<br>
    5. AI quality review
    </div>
    """, unsafe_allow_html=True)


# ── Step indicator ─────────────────────────────────────────────────────────────

def render_steps(completed: list[str], active: str | None = None):
    html = '<div class="steps-row">'
    for i, (node_id, label) in enumerate(STEPS):
        if node_id in completed:
            icon_cls, label_cls = "done", "done"
            icon_html = "✓"
        elif node_id == active:
            icon_cls, label_cls = "active", "active"
            icon_html = "…"
        else:
            icon_cls, label_cls = "", ""
            icon_html = str(i + 1)

        html += (
            f'<div class="step">'
            f'<div class="step-icon {icon_cls}">{icon_html}</div>'
            f'<div class="step-label {label_cls}">{label.replace(chr(10), "<br>")}</div>'
            f'</div>'
        )
        if i < len(STEPS) - 1:
            conn_cls = "done" if node_id in completed else ""
            html += f'<div class="step-connector {conn_cls}"></div>'
    html += "</div>"
    st.markdown(html, unsafe_allow_html=True)


# ── Main layout ────────────────────────────────────────────────────────────────

st.markdown("## API Documentation Generator")
st.markdown(
    '<p style="color:#6b7280;font-size:0.88rem;margin-top:-0.5rem;margin-bottom:1.5rem;">'
    "Upload or paste API code → LangGraph pipeline → Markdown docs + OpenAPI YAML"
    "</p>",
    unsafe_allow_html=True,
)

# ── Input section ──────────────────────────────────────────────────────────────
st.markdown('<div class="input-card">', unsafe_allow_html=True)
st.markdown('<div class="input-card-title">Input — paste code or upload files</div>', unsafe_allow_html=True)

col_input, col_upload = st.columns([3, 2], gap="large")

with col_input:
    if "code_text" not in st.session_state:
        st.session_state.code_text = ""

    if st.button("Load sample FastAPI app", use_container_width=True):
        st.session_state.code_text = load_sample()
        st.rerun()

    code_input = st.text_area(
        "Paste code",
        value=st.session_state.code_text,
        height=280,
        placeholder="# Paste your FastAPI, Flask, Express, Django, Go-gin… code here",
        label_visibility="collapsed",
    )

with col_upload:
    st.markdown(
        '<p style="font-size:0.8rem;color:#6b7280;margin-bottom:0.5rem;">Or upload files</p>',
        unsafe_allow_html=True,
    )
    uploaded = st.file_uploader(
        "Upload files",
        accept_multiple_files=True,
        type=["py", "js", "ts", "go", "java", "rb", "php", "cs"],
        label_visibility="collapsed",
    )
    if uploaded:
        st.markdown(
            f'<p style="font-size:0.75rem;color:#15803d;margin-top:0.3rem;">✓ {len(uploaded)} file(s) ready</p>',
            unsafe_allow_html=True,
        )

    st.markdown(
        '<p style="font-size:0.72rem;color:#9ca3af;margin-top:1rem;line-height:1.6;">'
        "Supports: FastAPI, Flask, Django REST, Express, Koa, Go-gin, Go-fiber, Spring Boot<br>"
        "Output: Markdown docs + downloadable OpenAPI 3.0 YAML"
        "</p>",
        unsafe_allow_html=True,
    )

st.markdown("</div>", unsafe_allow_html=True)

# ── Build file list ────────────────────────────────────────────────────────────
files: list[dict] = []
if uploaded:
    for f in uploaded:
        files.append({"name": f.name, "content": f.read().decode("utf-8", errors="replace")})
if code_input.strip():
    name = "pasted_code.py" if not files else "pasted_code.txt"
    files.append({"name": name, "content": code_input.strip()})

# ── Run button ─────────────────────────────────────────────────────────────────
col_run, col_info = st.columns([2, 5], gap="small")
with col_run:
    run_clicked = st.button(
        "Generate Documentation",
        type="primary",
        use_container_width=True,
        disabled=not files,
    )
with col_info:
    if not files:
        st.markdown(
            '<p style="font-size:0.78rem;color:#9ca3af;margin-top:0.6rem;">← Add code to enable generation</p>',
            unsafe_allow_html=True,
        )

# ── Pipeline execution ─────────────────────────────────────────────────────────
steps_placeholder = st.empty()
log_placeholder   = st.empty()

if run_clicked and files:
    graph = build_graph()

    initial_state: DocState = {
        "files": files,
        "model": model,
        "api_title": api_title,
        "api_version": api_version,
        "base_url": base_url,
        "framework": "",
        "language": "",
        "endpoints": [],
        "docs_markdown": "",
        "openapi_yaml": "",
        "review_notes": "",
        "current_step": "",
        "error": "",
        "progress_messages": [],
    }

    completed_steps: list[str] = []
    active_step: str | None = None
    log_lines: list[str] = []
    final_state: dict = {}

    try:
        for event in graph.stream(initial_state):
            for node_name, node_output in event.items():
                active_step = node_name
                with steps_placeholder.container():
                    render_steps(completed_steps, active_step)

                if isinstance(node_output, dict):
                    final_state.update(node_output)
                    for msg in node_output.get("progress_messages", []):
                        log_lines.append(f"✓ [{node_name}] {msg}")

                completed_steps.append(node_name)
                active_step = None

                with steps_placeholder.container():
                    render_steps(completed_steps, None)
                with log_placeholder.container():
                    st.markdown(
                        "\n".join(
                            f'<span style="font-size:0.75rem;color:#6b7280;">{l}</span>'
                            for l in log_lines
                        ),
                        unsafe_allow_html=True,
                    )

        st.session_state.result_state = final_state
        st.session_state.pipeline_log = log_lines

    except Exception as e:
        st.error(f"Pipeline error: {e}")

# Restore step display from session state
elif "result_state" in st.session_state:
    completed = [s[0] for s in STEPS]
    with steps_placeholder.container():
        render_steps(completed)

# ── Results ────────────────────────────────────────────────────────────────────
if "result_state" in st.session_state:
    rs = st.session_state.result_state
    docs_md    = rs.get("docs_markdown", "")
    openapi    = rs.get("openapi_yaml", "")
    review     = rs.get("review_notes", "")
    framework  = rs.get("framework", "")
    language   = rs.get("language", "")
    n_endpoints = len(rs.get("endpoints", []))

    st.markdown("<br>", unsafe_allow_html=True)

    # Metrics row
    m1, m2, m3, m4 = st.columns(4)
    with m1:
        st.metric("Framework", framework or "—")
    with m2:
        st.metric("Language", language or "—")
    with m3:
        st.metric("Endpoints", n_endpoints)
    with m4:
        st.metric("Doc size", f"{len(docs_md):,} chars")

    st.markdown("<br>", unsafe_allow_html=True)

    # Download row
    dl1, dl2, _, _ = st.columns(4)
    with dl1:
        if docs_md:
            st.download_button(
                "⬇ Download Markdown",
                data=docs_md.encode("utf-8"),
                file_name=f"{api_title.replace(' ', '_').lower()}_docs.md",
                mime="text/markdown",
                use_container_width=True,
            )
    with dl2:
        if openapi:
            st.download_button(
                "⬇ Download OpenAPI YAML",
                data=openapi.encode("utf-8"),
                file_name=f"{api_title.replace(' ', '_').lower()}_openapi.yaml",
                mime="text/yaml",
                use_container_width=True,
            )

    st.markdown("<br>", unsafe_allow_html=True)

    # Tabbed results
    tab_docs, tab_openapi, tab_review, tab_endpoints = st.tabs(
        ["📖 Markdown Docs", "📋 OpenAPI YAML", "🔍 Review", "🗂 Raw Endpoints"]
    )

    with tab_docs:
        if docs_md:
            st.markdown(docs_md)
        else:
            st.info("No documentation generated.")

    with tab_openapi:
        if openapi:
            st.code(openapi, language="yaml")
        else:
            st.info("No OpenAPI spec generated.")

    with tab_review:
        if review:
            st.markdown(review)
        else:
            st.info("No review available.")

    with tab_endpoints:
        endpoints = rs.get("endpoints", [])
        if endpoints:
            import pandas as pd
            rows = []
            for ep in endpoints:
                rows.append({
                    "Method": ep.get("method", ""),
                    "Path": ep.get("path", ""),
                    "Summary": ep.get("summary", ""),
                    "Auth": "Yes" if ep.get("auth_required") else "No",
                    "Tags": ", ".join(ep.get("tags") or []),
                    "Source": ep.get("source_file", ""),
                })
            st.dataframe(
                pd.DataFrame(rows),
                use_container_width=True,
                hide_index=True,
            )
        else:
            st.info("No endpoints extracted.")

# ── Empty state ────────────────────────────────────────────────────────────────
elif not run_clicked:
    with steps_placeholder.container():
        render_steps([])
    st.markdown(
        '<div style="text-align:center;padding:3rem 0;color:#9ca3af;font-size:0.85rem;">'
        "Paste or upload API code, then click <strong>Generate Documentation</strong>"
        "</div>",
        unsafe_allow_html=True,
    )
