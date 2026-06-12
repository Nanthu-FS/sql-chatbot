import json
import ollama

MODEL_DEFAULT = "qwen2.5:14b"

_SYSTEM = """You are a quantitative prediction-market analyst for Polymarket specialising in Politics and World Events.

Your mission: identify YES contracts that are UNDERPRICED (market price < fair probability) with ≥90% confidence.

Rules:
- Only recommend BUY_YES — never NO positions, never sells.
- Confidence must be ≥0.90 to recommend; otherwise action = "SKIP".
- Be rigorous. Overconfidence loses money. When in doubt, SKIP.
- Edge must exist: fair_value_estimate > current_yes_price + 0.03.
- Each market includes recent news — use it to justify your probability estimate.

Output ONLY valid JSON (no markdown, no explanation outside the JSON):
{
  "recommendations": [
    {
      "market_id": "<string>",
      "market_question": "<full question text>",
      "action": "BUY_YES",
      "confidence": <0.0-1.0>,
      "current_yes_price": <0.0-1.0>,
      "fair_value_estimate": <0.0-1.0>,
      "reasoning": "<2-3 sentence explanation citing specific evidence from the news>",
      "news_sources": ["<url1>", "<url2>"]
    }
  ]
}

Only include markets where action = "BUY_YES". If no market meets the threshold, return {"recommendations": []}.
"""

_BACKTEST_SUFFIX = (
    "\n\nCRITICAL: These markets have already resolved. Analyse them AS IF still open. "
    "Do NOT use knowledge of the actual outcome — reason only from the news provided."
)


def _fetch_news(question: str, n: int = 4) -> list[dict]:
    """Fetch recent news for a market question via DuckDuckGo."""
    try:
        from duckduckgo_search import DDGS
        seen, results = set(), []
        with DDGS() as ddgs:
            for q in [question[:80], question[:50] + " 2025"]:
                for r in ddgs.news(q, max_results=3):
                    url = r.get("url", "")
                    if url and url not in seen:
                        seen.add(url)
                        results.append({
                            "title": r.get("title", ""),
                            "url":   url,
                            "body":  (r.get("body") or "")[:300],
                        })
                if len(results) >= n:
                    break
        return results[:n]
    except Exception:
        return []


def _build_market_block(m: dict, news: list[dict]) -> str:
    news_lines = "\n".join(
        f"  [{i+1}] {n['title']} — {n['body']} ({n['url']})"
        for i, n in enumerate(news)
    ) or "  No recent news found."
    return (
        f"Market ID: {m['market_id']}\n"
        f"Question:  {m['question']}\n"
        f"YES price: {m['yes_price']:.3f}  (implied {m['yes_price']*100:.1f}%)\n"
        f"Volume:    ${m['volume_usd']:,.0f}\n"
        f"Category:  {m.get('category','')}\n"
        f"End date:  {m.get('end_date','Unknown')}\n"
        f"Recent news:\n{news_lines}"
    )


def _parse_text(text: str) -> list[dict]:
    """Extract BUY_YES recommendations from raw model output."""
    if not text.strip():
        return []
    t = text.strip()
    if t.startswith("```"):
        parts = t.split("```")
        t = parts[1].lstrip("json").strip() if len(parts) > 1 else t
    try:
        data = json.loads(t.strip())
        return [r for r in data.get("recommendations", []) if r.get("action") == "BUY_YES"]
    except (json.JSONDecodeError, KeyError):
        return []


def _call_model(model: str, host: str, system: str, user: str) -> str:
    client = ollama.Client(host=host)
    resp = client.chat(
        model=model,
        messages=[
            {"role": "system", "content": system},
            {"role": "user",   "content": user},
        ],
        format="json",
        options={"temperature": 0.1, "num_predict": 4096},
    )
    return resp.message.content


def analyze_markets(
    markets: list[dict],
    balance: float,
    model: str = MODEL_DEFAULT,
    ollama_host: str = "http://localhost:11434",
) -> list[dict]:
    """Fetch news for each market, then ask the local model for BUY_YES recommendations."""
    if not markets:
        return []

    # Pre-fetch news per market and collect source URLs
    news_map: dict[str, list[dict]] = {}
    for m in markets:
        news_map[m["market_id"]] = _fetch_news(m["question"])

    sections = [_build_market_block(m, news_map[m["market_id"]]) for m in markets]
    max_trade = balance * 0.01

    user_msg = (
        f"Portfolio balance: ${balance:.2f}  |  Max per trade: ${max_trade:.2f} (1%)  |  Min confidence: 90%\n\n"
        f"Analyse each market and return your JSON recommendations.\n\n"
        + "\n\n---\n\n".join(sections)
    )

    raw  = _call_model(model, ollama_host, _SYSTEM, user_msg)
    recs = _parse_text(raw)

    market_map = {m["market_id"]: m for m in markets}
    for r in recs:
        m = market_map.get(r.get("market_id"), {})
        if not r.get("market_question"):
            r["market_question"] = m.get("question", r.get("market_id", ""))
        r["yes_token_id"] = m.get("yes_token_id", "")
        if not r.get("news_sources"):
            r["news_sources"] = [n["url"] for n in news_map.get(r.get("market_id"), [])]

    return recs


def run_backtest(
    markets: list[dict],
    model: str = MODEL_DEFAULT,
    ollama_host: str = "http://localhost:11434",
) -> list[dict]:
    """Analyse resolved markets without hindsight, then score against actual outcomes."""
    if not markets:
        return []

    news_map: dict[str, list[dict]] = {}
    for m in markets:
        news_map[m["market_id"]] = _fetch_news(m["question"])

    sections = []
    for m in markets:
        news = news_map[m["market_id"]]
        news_lines = "\n".join(
            f"  [{i+1}] {n['title']} — {n['body']} ({n['url']})"
            for i, n in enumerate(news)
        ) or "  No recent news found."
        sections.append(
            f"Market ID: {m['market_id']}\n"
            f"Question:  {m['question']}\n"
            f"YES price at open: {m['yes_price_at_open']:.3f}\n"
            f"Volume:    ${m['volume_usd']:,.0f}\n"
            f"Category:  {m.get('category','')}\n"
            f"End date:  {m.get('end_date','Unknown')}\n"
            f"Recent news:\n{news_lines}"
        )

    user_msg = (
        "Backtest — analyse these resolved markets AS IF still open (no hindsight).\n\n"
        + "\n\n---\n\n".join(sections)
    )

    raw  = _call_model(model, ollama_host, _SYSTEM + _BACKTEST_SUFFIX, user_msg)
    recs = _parse_text(raw)

    outcome_map = {m["market_id"]: m.get("resolved_outcome", "") for m in markets}
    for r in recs:
        actual = outcome_map.get(r.get("market_id"), "")
        r["actual_outcome"] = actual
        r["would_win"]      = actual.strip().upper() == "YES"
        if not r.get("market_question"):
            m = next((x for x in markets if x["market_id"] == r.get("market_id")), {})
            r["market_question"] = m.get("question", r.get("market_id", ""))
        if not r.get("news_sources"):
            r["news_sources"] = [n["url"] for n in news_map.get(r.get("market_id"), [])]

    return recs
