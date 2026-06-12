import json
import anthropic

MODEL = "claude-fable-5"

_SEARCH_TOOL = {
    "name": "search_news",
    "description": (
        "Search for recent news and information about a prediction market topic. "
        "Use this before assessing probability — call it multiple times with focused queries "
        "to find polling data, recent events, expert forecasts, or official announcements."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Specific search query, e.g. 'Trump approval poll June 2025' or 'Fed interest rate decision July 2025'",
            }
        },
        "required": ["query"],
    },
}

_SYSTEM = """You are a quantitative prediction-market analyst for Polymarket specialising in Politics and World Events.

Your mission: identify YES contracts that are UNDERPRICED (market price < fair probability) with ≥90% confidence.

Rules:
- Only recommend BUY_YES — never NO positions, never sells.
- Confidence must be ≥0.90 to recommend; otherwise action = "SKIP".
- Use search_news liberally before scoring each market — at least one search per market.
- Be rigorous. Overconfidence loses money. When in doubt, SKIP.
- A market at 0.92 YES price is NOT an opportunity — edge must exist (fair_value > current_price + 0.03).

Output exactly this JSON and nothing else (no markdown fences):
{
  "recommendations": [
    {
      "market_id": "<string>",
      "market_question": "<full question text>",
      "action": "BUY_YES",
      "confidence": <0.0-1.0>,
      "current_yes_price": <0.0-1.0>,
      "fair_value_estimate": <0.0-1.0>,
      "reasoning": "<2-3 sentence explanation citing specific evidence>",
      "news_sources": ["<url>", "<url>"]
    }
  ]
}

Only include markets where action = "BUY_YES". If no markets meet the threshold, return {"recommendations": []}.
"""


def _ddg_search(query: str, max_results: int = 4) -> list[dict]:
    try:
        from duckduckgo_search import DDGS
        with DDGS() as ddgs:
            results = list(ddgs.news(query, max_results=max_results))
        return [
            {"title": r.get("title", ""), "url": r.get("url", ""), "body": r.get("body", "")}
            for r in results
        ]
    except Exception:
        return []


def _run_agent_loop(client: anthropic.Anthropic, system: str, messages: list) -> list:
    """Run tool-use loop and return final content blocks."""
    while True:
        resp = client.messages.create(
            model=MODEL,
            max_tokens=8192,
            system=system,
            tools=[_SEARCH_TOOL],
            messages=messages,
        )

        if resp.stop_reason == "refusal":
            return []

        if resp.stop_reason == "tool_use":
            tool_results = []
            for block in resp.content:
                if block.type == "tool_use" and block.name == "search_news":
                    results = _ddg_search(block.input.get("query", ""))
                    tool_results.append({
                        "type": "tool_use_id" if False else "tool_result",
                        "tool_use_id": block.id,
                        "content": json.dumps(results),
                    })
            messages.append({"role": "assistant", "content": resp.content})
            messages.append({"role": "user", "content": tool_results})
            continue

        # end_turn or max_tokens
        return resp.content


def _parse_recommendations(content_blocks: list) -> list[dict]:
    raw = "".join(b.text for b in content_blocks if b.type == "text")
    if not raw.strip():
        return []
    text = raw.strip()
    # Strip accidental markdown fences
    if text.startswith("```"):
        parts = text.split("```")
        text = parts[1].lstrip("json").strip() if len(parts) > 1 else text
    try:
        data = json.loads(text)
        return [r for r in data.get("recommendations", []) if r.get("action") == "BUY_YES"]
    except (json.JSONDecodeError, KeyError):
        return []


def analyze_markets(markets: list[dict], balance: float, api_key: str) -> list[dict]:
    """Analyze live markets and return BUY_YES recommendations from Claude Fable 5."""
    if not markets:
        return []

    client = anthropic.Anthropic(api_key=api_key)
    max_per_trade = balance * 0.01

    markets_text = "\n\n".join(
        f"Market ID: {m['market_id']}\n"
        f"Question:  {m['question']}\n"
        f"YES price: {m['yes_price']:.3f}  (implied prob {m['yes_price']*100:.1f}%)\n"
        f"Volume:    ${m['volume_usd']:,.0f}\n"
        f"Category:  {m.get('category','')}\n"
        f"End date:  {m.get('end_date','Unknown')}\n"
        f"Tags:      {', '.join(m.get('tags', []))}"
        for m in markets
    )

    user_msg = (
        f"Portfolio balance: ${balance:.2f}\n"
        f"Max risk per trade: ${max_per_trade:.2f} (1%)\n"
        f"Required confidence: ≥90%\n\n"
        f"Search for news on each promising market, then return your JSON recommendations.\n\n"
        f"Markets:\n{markets_text}"
    )

    messages = [{"role": "user", "content": user_msg}]
    content  = _run_agent_loop(client, _SYSTEM, messages)
    recs     = _parse_recommendations(content)

    # Enrich with token IDs from the markets list (Claude doesn't know these)
    market_map = {m["market_id"]: m for m in markets}
    for r in recs:
        m = market_map.get(r.get("market_id"), {})
        if not r.get("market_question"):
            r["market_question"] = m.get("question", r.get("market_id", ""))
        r["yes_token_id"] = m.get("yes_token_id", "")

    return recs


def run_backtest(markets: list[dict], api_key: str) -> list[dict]:
    """Analyze resolved markets without hindsight, then score against actual outcomes."""
    if not markets:
        return []

    client = anthropic.Anthropic(api_key=api_key)

    backtest_system = (
        _SYSTEM
        + "\n\nCRITICAL: These markets have ALREADY resolved, but you must analyse them "
        "as if they are still OPEN. Do NOT use any knowledge of the actual outcome. "
        "Reason purely from publicly available information as of the market open date."
    )

    markets_text = "\n\n".join(
        f"Market ID: {m['market_id']}\n"
        f"Question:  {m['question']}\n"
        f"YES price at open: {m['yes_price_at_open']:.3f}\n"
        f"Volume:    ${m['volume_usd']:,.0f}\n"
        f"Category:  {m.get('category','')}\n"
        f"End date:  {m.get('end_date','Unknown')}"
        for m in markets
    )

    user_msg = (
        "Backtest: analyse these resolved markets as if still open (no hindsight).\n\n"
        + markets_text
    )

    messages = [{"role": "user", "content": user_msg}]
    content  = _run_agent_loop(client, backtest_system, messages)
    recs     = _parse_recommendations(content)

    outcome_map = {m["market_id"]: m.get("resolved_outcome", "") for m in markets}
    for r in recs:
        actual = outcome_map.get(r.get("market_id"), "")
        r["actual_outcome"] = actual
        r["would_win"]      = actual.strip().upper() == "YES"
        if not r.get("market_question"):
            m = next((x for x in markets if x["market_id"] == r.get("market_id")), {})
            r["market_question"] = m.get("question", r.get("market_id", ""))

    return recs
