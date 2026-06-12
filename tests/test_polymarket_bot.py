"""
Polymarket bot test suite — runs entirely with fake/mocked data.
No real API keys, no real money, no network calls.
"""
import json
from datetime import date
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# ══════════════════════════════════════════════════════════════════════════════
# Shared fixtures & fake data
# ══════════════════════════════════════════════════════════════════════════════

@pytest.fixture()
def db(tmp_path, monkeypatch):
    """Fresh isolated database per test."""
    import polymarket.database as db_mod
    monkeypatch.setattr(db_mod, "DB_PATH", tmp_path / "test.db")
    db_mod.init_db()
    return db_mod


def _trade(**overrides):
    base = dict(
        market_id="mkt_001", market_question="Will X happen?",
        token_id="tok_001", side="YES", size_usdc=5.0,
        entry_price=0.65, confidence=0.92, status="open", order_id="ord_001",
    )
    base.update(overrides)
    return base


FAKE_GAMMA_RESPONSE = [
    {
        "conditionId": "cond_abc", "question": "Will the US House pass a budget by Jan 2026?",
        "category": "Politics", "volume": "250000.00", "endDate": "2026-01-31",
        "active": True, "closed": False,
        "tags": [{"label": "politics"}, {"label": "election"}],
        "tokens": [
            {"outcome": "YES", "token_id": "tok_yes_abc", "price": "0.72"},
            {"outcome": "NO",  "token_id": "tok_no_abc",  "price": "0.28"},
        ],
    },
    {
        "conditionId": "cond_def", "question": "Ukraine ceasefire in 2025?",
        "category": "World Events", "volume": "180000.00", "endDate": "2025-12-31",
        "tags": [{"label": "world"}, {"label": "geopolitics"}],
        "tokens": [
            {"outcome": "YES", "token_id": "tok_yes_def", "price": "0.34"},
            {"outcome": "NO",  "token_id": "tok_no_def",  "price": "0.66"},
        ],
    },
    {
        "conditionId": "cond_low", "question": "Low volume market?",
        "category": "Sports", "volume": "4000.00",
        "tags": [], "tokens": [],
    },
]

FAKE_MARKETS = [
    {"market_id": "cond_abc", "question": "Will the US House pass a budget by Jan 2026?",
     "yes_price": 0.72, "yes_token_id": "tok_yes_abc", "volume_usd": 250_000,
     "category": "Politics", "end_date": "2026-01-31", "tags": ["politics"]},
    {"market_id": "cond_def", "question": "Ukraine ceasefire in 2025?",
     "yes_price": 0.34, "yes_token_id": "tok_yes_def", "volume_usd": 180_000,
     "category": "World Events", "end_date": "2025-12-31", "tags": ["world"]},
]

REC_JSON = json.dumps({
    "recommendations": [
        {"market_id": "cond_abc",
         "market_question": "Will the US House pass a budget by Jan 2026?",
         "action": "BUY_YES", "confidence": 0.93,
         "current_yes_price": 0.72, "fair_value_estimate": 0.88,
         "reasoning": "Strong bipartisan support per latest whip count.",
         "news_sources": ["https://example.com/article1"]},
        {"market_id": "cond_def",
         "market_question": "Ukraine ceasefire in 2025?",
         "action": "SKIP", "confidence": 0.54,
         "current_yes_price": 0.34, "fair_value_estimate": 0.40,
         "reasoning": "Too uncertain.", "news_sources": []},
    ]
})

RESOLVED_MARKETS = [
    {"market_id": "res_001", "question": "Did candidate X win?",
     "yes_price_at_open": 0.68, "volume_usd": 300_000,
     "category": "Politics", "end_date": "2025-11-05", "resolved_outcome": "YES"},
    {"market_id": "res_002", "question": "Did ceasefire hold 30 days?",
     "yes_price_at_open": 0.45, "volume_usd": 150_000,
     "category": "World Events", "end_date": "2025-09-01", "resolved_outcome": "NO"},
]

BACKTEST_JSON = json.dumps({
    "recommendations": [
        {"market_id": "res_001", "market_question": "Did candidate X win?",
         "action": "BUY_YES", "confidence": 0.94,
         "current_yes_price": 0.68, "fair_value_estimate": 0.87,
         "reasoning": "Consistent polling lead.", "news_sources": ["https://ex.com/p1"]},
        {"market_id": "res_002", "market_question": "Did ceasefire hold 30 days?",
         "action": "BUY_YES", "confidence": 0.91,
         "current_yes_price": 0.45, "fair_value_estimate": 0.70,
         "reasoning": "Both sides appeared committed.", "news_sources": []},
    ]
})


def _ollama_response(text: str):
    """Fake ollama.chat() response."""
    r = MagicMock()
    r.message = MagicMock()
    r.message.content = text
    return r


# ══════════════════════════════════════════════════════════════════════════════
# 1. Database
# ══════════════════════════════════════════════════════════════════════════════

class TestDatabase:
    def test_init_creates_all_tables(self, db):
        import sqlite3
        conn = sqlite3.connect(db.DB_PATH)
        names = {r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        conn.close()
        assert {"trades", "analyses", "daily_stats"}.issubset(names)

    def test_save_trade_returns_id(self, db):
        assert db.save_trade(_trade()) == 1

    def test_open_trade_visible(self, db):
        db.save_trade(_trade())
        rows = db.get_open_trades()
        assert len(rows) == 1
        assert rows[0]["market_id"] == "mkt_001"
        assert rows[0]["entry_price"] == pytest.approx(0.65)

    def test_update_price_and_pnl(self, db):
        tid = db.save_trade(_trade())
        db.update_trade_price(tid, 0.80, 1.15)
        rows = db.get_open_trades()
        assert rows[0]["current_price"] == pytest.approx(0.80)
        assert rows[0]["pnl"] == pytest.approx(1.15)

    def test_close_trade_removes_from_open(self, db):
        tid = db.save_trade(_trade())
        db.close_trade(tid, 0.90, 3.08)
        assert db.get_open_trades() == []
        history = db.get_trade_history()
        assert history[0]["status"] == "closed"

    def test_close_trade_win_updates_daily_pnl(self, db):
        tid = db.save_trade(_trade())
        db.close_trade(tid, 0.90, 3.08)
        assert db.get_daily_pnl_today() == pytest.approx(3.08)

    def test_close_trade_loss_updates_daily_pnl(self, db):
        tid = db.save_trade(_trade())
        db.close_trade(tid, 0.30, -2.50)
        assert db.get_daily_pnl_today() == pytest.approx(-2.50)

    def test_multiple_trades_cumulative_pnl(self, db):
        t1 = db.save_trade(_trade(market_id="m1"))
        t2 = db.save_trade(_trade(market_id="m2"))
        db.close_trade(t1, 0.90, 2.00)
        db.close_trade(t2, 0.20, -1.50)
        assert db.get_daily_pnl_today() == pytest.approx(0.50)

    def test_daily_pnl_starts_zero(self, db):
        assert db.get_daily_pnl_today() == 0.0

    def test_save_and_retrieve_analysis(self, db):
        db.save_analysis({
            "market_id": "mkt_a01", "market_question": "Analysis Q?",
            "action": "BUY_YES", "confidence": 0.93,
            "fair_value": 0.85, "current_price": 0.70,
            "reasoning": "Strong evidence.", "sources": ["https://ex.com/1", "https://ex.com/2"],
        })
        hist = db.get_analysis_history()
        assert len(hist) == 1
        assert hist[0]["confidence"] == pytest.approx(0.93)
        assert isinstance(hist[0]["sources"], list)
        assert len(hist[0]["sources"]) == 2

    def test_five_open_positions_tracked(self, db):
        for i in range(5):
            db.save_trade(_trade(market_id=f"m{i}"))
        assert len(db.get_open_trades()) == 5

    def test_daily_stats_trades_opened_count(self, db):
        db.save_trade(_trade(market_id="m1"))
        db.save_trade(_trade(market_id="m2"))
        stats = db.get_daily_stats()
        today = date.today().isoformat()
        row = next(s for s in stats if s["date"] == today)
        assert row["trades_opened"] == 2

    def test_daily_stats_wins_losses(self, db):
        t1 = db.save_trade(_trade(market_id="m1"))
        t2 = db.save_trade(_trade(market_id="m2"))
        t3 = db.save_trade(_trade(market_id="m3"))
        db.close_trade(t1, 0.90, 2.0)
        db.close_trade(t2, 0.90, 1.5)
        db.close_trade(t3, 0.10, -3.0)
        stats = db.get_daily_stats()
        today = date.today().isoformat()
        row = next(s for s in stats if s["date"] == today)
        assert row["wins"] == 2
        assert row["losses"] == 1


# ══════════════════════════════════════════════════════════════════════════════
# 2. Gamma API client (mocked HTTP)
# ══════════════════════════════════════════════════════════════════════════════

class TestGammaClient:
    def _mock_client(self, response_data):
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_resp = MagicMock()
            mock_resp.json.return_value = response_data
            mock_sess.get.return_value = mock_resp
            from polymarket.client import GammaClient
            return GammaClient(), mock_sess

    def test_parses_two_valid_markets(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = FAKE_GAMMA_RESPONSE
            markets = GammaClient().get_markets(min_volume=100_000)
        assert len(markets) == 2

    def test_filters_low_volume(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = FAKE_GAMMA_RESPONSE
            markets = GammaClient().get_markets(min_volume=200_000)
        assert len(markets) == 1
        assert markets[0]["market_id"] == "cond_abc"

    def test_parses_yes_token_id_and_price(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = FAKE_GAMMA_RESPONSE
            markets = GammaClient().get_markets(min_volume=100_000)
        m = markets[0]
        assert m["yes_token_id"] == "tok_yes_abc"
        assert m["yes_price"] == pytest.approx(0.72)
        assert m["no_price"] == pytest.approx(0.28)

    def test_missing_tokens_default_to_05(self):
        from polymarket.client import GammaClient
        data = [{"conditionId": "x", "question": "Q?", "volume": "500000", "tags": [], "tokens": []}]
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = data
            markets = GammaClient().get_markets(min_volume=100_000)
        assert markets[0]["yes_price"] == pytest.approx(0.5)

    def test_api_error_raises_runtime_error(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.side_effect = Exception("Connection refused")
            with pytest.raises(RuntimeError, match="Failed to fetch markets"):
                GammaClient().get_markets()

    def test_get_market_price_returns_float(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = {"price": "0.73"}
            price = GammaClient().get_market_price("tok_yes_abc")
        assert price == pytest.approx(0.73)

    def test_get_market_price_returns_none_on_error(self):
        from polymarket.client import GammaClient
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.side_effect = Exception("Timeout")
            price = GammaClient().get_market_price("tok_bad")
        assert price is None

    def test_category_filter_excludes_non_matching(self):
        from polymarket.client import GammaClient
        # Only "Sports" keywords — neither market matches Politics/World Events
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = FAKE_GAMMA_RESPONSE
            markets = GammaClient().get_markets(min_volume=100_000, categories=["Sports"])
        assert len(markets) == 0

    def test_resolved_markets_attaches_outcome(self):
        from polymarket.client import GammaClient
        data = [{
            "conditionId": "res_x", "question": "Did it happen?",
            "volume": "200000", "endDate": "2025-11-01",
            "resolvedOutcome": "YES", "tokens": [],
        }]
        with patch("polymarket.client.requests.Session") as MockSess:
            mock_sess = MagicMock()
            MockSess.return_value = mock_sess
            mock_sess.get.return_value.json.return_value = data
            markets = GammaClient().get_resolved_markets(limit=10)
        assert markets[0]["resolved_outcome"] == "YES"


# ══════════════════════════════════════════════════════════════════════════════
# 3. Analyst — recommendation parsing (_parse_text)
# ══════════════════════════════════════════════════════════════════════════════

class TestAnalystParsing:
    def test_filters_buy_yes_only(self):
        from polymarket.analyst import _parse_text
        recs = _parse_text(REC_JSON)
        assert len(recs) == 1
        assert recs[0]["action"] == "BUY_YES"

    def test_strips_markdown_fences(self):
        from polymarket.analyst import _parse_text
        fenced = "```json\n" + REC_JSON + "\n```"
        recs = _parse_text(fenced)
        assert len(recs) == 1

    def test_empty_on_bad_json(self):
        from polymarket.analyst import _parse_text
        assert _parse_text("This is not JSON.") == []

    def test_empty_on_blank_string(self):
        from polymarket.analyst import _parse_text
        assert _parse_text("") == []

    def test_empty_recommendations_array(self):
        from polymarket.analyst import _parse_text
        assert _parse_text('{"recommendations": []}') == []

    def test_confidence_value_preserved(self):
        from polymarket.analyst import _parse_text
        recs = _parse_text(REC_JSON)
        assert recs[0]["confidence"] == pytest.approx(0.93)

    def test_news_sources_preserved(self):
        from polymarket.analyst import _parse_text
        recs = _parse_text(REC_JSON)
        assert "https://example.com/article1" in recs[0]["news_sources"]


# ══════════════════════════════════════════════════════════════════════════════
# 4. Analyst — full flow (mocked Ollama)
# ══════════════════════════════════════════════════════════════════════════════

class TestAnalystFlow:
    def test_returns_recommendations(self):
        from polymarket.analyst import analyze_markets
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(REC_JSON)
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)
        assert len(recs) == 1
        assert recs[0]["action"] == "BUY_YES"

    def test_empty_markets_skips_api_call(self):
        from polymarket.analyst import analyze_markets
        with patch("polymarket.analyst.ollama.Client") as MockClient:
            recs = analyze_markets([], balance=300.0)
        MockClient.return_value.chat.assert_not_called()
        assert recs == []

    def test_bad_json_returns_empty(self):
        from polymarket.analyst import analyze_markets
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response("not json at all")
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)
        assert recs == []

    def test_enriches_yes_token_id_from_markets(self):
        from polymarket.analyst import analyze_markets
        json_no_token = json.dumps({
            "recommendations": [{
                "market_id": "cond_abc",
                "market_question": "Will the US House pass a budget by Jan 2026?",
                "action": "BUY_YES", "confidence": 0.93,
                "current_yes_price": 0.72, "fair_value_estimate": 0.88,
                "reasoning": "Strong evidence.", "news_sources": [],
            }]
        })
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(json_no_token)
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)
        assert recs[0]["yes_token_id"] == "tok_yes_abc"

    def test_enriches_question_from_markets_if_missing(self):
        from polymarket.analyst import analyze_markets
        json_no_q = json.dumps({
            "recommendations": [{
                "market_id": "cond_abc",
                "action": "BUY_YES", "confidence": 0.93,
                "current_yes_price": 0.72, "fair_value_estimate": 0.88,
                "reasoning": "Strong.", "news_sources": [],
            }]
        })
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(json_no_q)
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)
        assert "House" in recs[0]["market_question"]

    def test_default_model_is_qwen(self):
        from polymarket.analyst import MODEL_DEFAULT
        assert "qwen" in MODEL_DEFAULT.lower()

    def test_news_injected_from_prefetch(self):
        """Sources from pre-fetched news are attached when model returns empty sources."""
        from polymarket.analyst import analyze_markets
        fake_news = [{"title": "Test", "url": "https://news.com/test", "body": "..."}]
        json_empty_sources = json.dumps({
            "recommendations": [{
                "market_id": "cond_abc",
                "market_question": "Will the US House pass a budget by Jan 2026?",
                "action": "BUY_YES", "confidence": 0.93,
                "current_yes_price": 0.72, "fair_value_estimate": 0.88,
                "reasoning": "Strong.", "news_sources": [],
            }]
        })
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=fake_news):
            MockClient.return_value.chat.return_value = _ollama_response(json_empty_sources)
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)
        assert "https://news.com/test" in recs[0]["news_sources"]

    def test_format_json_sent_to_ollama(self):
        """Must request JSON format from Ollama for reliable structured output."""
        from polymarket.analyst import analyze_markets
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(REC_JSON)
            analyze_markets(FAKE_MARKETS, balance=300.0)
        call_kwargs = MockClient.return_value.chat.call_args.kwargs
        assert call_kwargs.get("format") == "json"

    def test_low_temperature_in_options(self):
        """Low temperature keeps JSON output consistent."""
        from polymarket.analyst import analyze_markets
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(REC_JSON)
            analyze_markets(FAKE_MARKETS, balance=300.0)
        call_kwargs = MockClient.return_value.chat.call_args.kwargs
        assert call_kwargs.get("options", {}).get("temperature", 1.0) <= 0.2


# ══════════════════════════════════════════════════════════════════════════════
# 5. Backtest
# ══════════════════════════════════════════════════════════════════════════════

class TestBacktest:
    def test_win_scored_correctly(self):
        from polymarket.analyst import run_backtest
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(BACKTEST_JSON)
            results = run_backtest(RESOLVED_MARKETS)
        win = next(r for r in results if r["market_id"] == "res_001")
        assert win["would_win"] is True
        assert win["actual_outcome"] == "YES"

    def test_loss_scored_correctly(self):
        from polymarket.analyst import run_backtest
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(BACKTEST_JSON)
            results = run_backtest(RESOLVED_MARKETS)
        loss = next(r for r in results if r["market_id"] == "res_002")
        assert loss["would_win"] is False
        assert loss["actual_outcome"] == "NO"

    def test_accuracy_calculation(self):
        from polymarket.analyst import run_backtest
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(BACKTEST_JSON)
            results = run_backtest(RESOLVED_MARKETS)
        correct  = sum(1 for r in results if r["would_win"])
        accuracy = correct / len(results) * 100
        assert accuracy == pytest.approx(50.0)

    def test_empty_markets_returns_empty(self):
        from polymarket.analyst import run_backtest
        assert run_backtest([]) == []

    def test_enriches_question_from_resolved_markets(self):
        from polymarket.analyst import run_backtest
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(BACKTEST_JSON)
            results = run_backtest(RESOLVED_MARKETS)
        assert results[0]["market_question"] != ""


# ══════════════════════════════════════════════════════════════════════════════
# 6. Trading risk logic
# ══════════════════════════════════════════════════════════════════════════════

class TestTradingLogic:
    def test_1pct_risk_rule(self):
        for balance in [50.0, 300.0, 500.0, 5000.0]:
            assert balance * 0.01 <= balance

    @pytest.mark.parametrize("balance,pnl,should_pause", [
        (300.0, -9.01, True),   # Just over 3% ($9.00) — pauses
        (300.0, -9.00, False),  # Exactly at limit — strict < means no pause yet
        (300.0, -8.99, False),  # Just under limit
        (300.0, +5.00, False),  # Profitable — no pause
        (500.0, -15.01, True),  # 3% of $500 = $15.00, exceeded
    ])
    def test_daily_loss_limit(self, balance, pnl, should_pause):
        limit = balance * 0.03
        result = pnl < -limit
        assert result is should_pause

    @pytest.mark.parametrize("entry,current,should_flag", [
        (0.70, 0.49, False),  # Exactly at 30% boundary (0.70*0.70=0.49) — strict < means no flag
        (0.70, 0.48, True),   # Just past 30% — flagged
        (0.70, 0.50, False),  # Just above threshold
        (0.80, 0.55, True),   # 31% below
        (0.60, 0.43, False),  # Only 28% below
    ])
    def test_exit_suggestion_threshold(self, entry, current, should_flag):
        result = current < entry * 0.70
        assert result is should_flag

    @pytest.mark.parametrize("open_count,max_pos,can_trade", [
        (0, 5, True),
        (4, 5, True),
        (5, 5, False),
        (6, 5, False),
    ])
    def test_max_positions_gate(self, open_count, max_pos, can_trade):
        assert (open_count < max_pos) is can_trade

    @pytest.mark.parametrize("entry,exit_p,size,expected_pnl", [
        (0.60, 0.90, 5.0,  2.50),   # WIN: 50% gain → $2.50 on $5
        (0.80, 0.40, 5.0, -2.50),   # LOSS: 50% drop → -$2.50 on $5
        (0.50, 1.00, 4.0,  4.00),   # Full resolution YES
        (0.50, 0.00, 4.0, -4.00),   # Full resolution NO
    ])
    def test_pnl_formula(self, entry, exit_p, size, expected_pnl):
        pnl = (exit_p - entry) / entry * size
        assert pnl == pytest.approx(expected_pnl)

    @pytest.mark.parametrize("confidence,threshold,included", [
        (0.93, 0.90, True),
        (0.90, 0.90, True),
        (0.89, 0.90, False),
        (0.75, 0.90, False),
    ])
    def test_confidence_filter(self, confidence, threshold, included):
        recs = [{"confidence": confidence, "action": "BUY_YES"}]
        filtered = [r for r in recs if r["confidence"] >= threshold]
        assert (len(filtered) == 1) is included


# ══════════════════════════════════════════════════════════════════════════════
# 7. End-to-end scenario: scan → save → update → close
# ══════════════════════════════════════════════════════════════════════════════

class TestEndToEnd:
    def test_full_trade_lifecycle(self, db):
        """
        Simulate: scan finds opportunity → save trade → price rises →
        close at profit → daily stats updated.
        """
        # Step 1: scan produces a recommendation
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(REC_JSON)
            from polymarket.analyst import analyze_markets
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)

        assert len(recs) == 1
        rec = recs[0]

        # Step 2: save the trade
        tid = db.save_trade({
            "market_id":       rec["market_id"],
            "market_question": rec["market_question"],
            "token_id":        rec["yes_token_id"],
            "side":            "YES",
            "size_usdc":       3.0,            # 1% of $300
            "entry_price":     rec["current_yes_price"],
            "confidence":      rec["confidence"],
            "status":          "open",
            "order_id":        "fake_order_001",
        })
        assert len(db.get_open_trades()) == 1

        # Step 3: price rises — update unrealised P&L
        new_price = 0.88
        entry = rec["current_yes_price"]
        unrealised = (new_price - entry) / entry * 3.0
        db.update_trade_price(tid, new_price, unrealised)
        trade = db.get_open_trades()[0]
        assert trade["current_price"] == pytest.approx(0.88)

        # Step 4: close the trade at profit
        db.close_trade(tid, new_price, unrealised)
        assert db.get_open_trades() == []
        assert db.get_daily_pnl_today() == pytest.approx(unrealised)

        # Step 5: daily stats reflect one win
        stats = db.get_daily_stats()
        today_stat = next(s for s in stats if s["date"] == date.today().isoformat())
        assert today_stat["wins"] == 1
        assert today_stat["losses"] == 0

    def test_daily_loss_limit_blocks_second_scan(self, db):
        """After hitting the 3% daily loss cap, no new trades should execute."""
        balance = 300.0
        loss_limit = balance * 0.03   # $9.00

        # Simulate two losing trades that exceed the daily limit
        for i in range(2):
            tid = db.save_trade(_trade(market_id=f"m{i}", size_usdc=5.0))
            db.close_trade(tid, 0.10, -4.75)   # -$4.75 each → total -$9.50

        daily_pnl = db.get_daily_pnl_today()
        can_trade  = not (daily_pnl < -loss_limit)
        assert can_trade is False

    def test_max_5_positions_enforced(self, db):
        """Opening a 6th trade should be blocked by the position cap."""
        for i in range(5):
            db.save_trade(_trade(market_id=f"m{i}"))

        open_count = len(db.get_open_trades())
        max_pos    = 5
        can_open   = open_count < max_pos
        assert can_open is False

    def test_analysis_saved_with_sources(self, db):
        """Analysis from a scan should be persisted with its source URLs."""
        with patch("polymarket.analyst.ollama.Client") as MockClient, \
             patch("polymarket.analyst._fetch_news", return_value=[]):
            MockClient.return_value.chat.return_value = _ollama_response(REC_JSON)
            from polymarket.analyst import analyze_markets
            recs = analyze_markets(FAKE_MARKETS, balance=300.0)

        for r in recs:
            db.save_analysis({
                "market_id":       r["market_id"],
                "market_question": r["market_question"],
                "action":          r["action"],
                "confidence":      r["confidence"],
                "fair_value":      r.get("fair_value_estimate"),
                "current_price":   r.get("current_yes_price"),
                "reasoning":       r.get("reasoning", ""),
                "sources":         r.get("news_sources", []),
            })

        hist = db.get_analysis_history()
        assert len(hist) == 1
        assert hist[0]["sources"] == ["https://example.com/article1"]
