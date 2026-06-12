import sqlite3
import json
from datetime import date, datetime
from pathlib import Path

DB_PATH = Path(__file__).parent.parent / "polymarket_trades.db"


def _conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with _conn() as conn:
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS trades (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            market_id       TEXT    NOT NULL,
            market_question TEXT    NOT NULL,
            token_id        TEXT,
            side            TEXT    NOT NULL DEFAULT 'YES',
            size_usdc       REAL    NOT NULL,
            entry_price     REAL    NOT NULL,
            current_price   REAL,
            status          TEXT    NOT NULL DEFAULT 'open',
            order_id        TEXT,
            confidence      REAL,
            pnl             REAL    DEFAULT 0,
            created_at      TEXT    DEFAULT (datetime('now')),
            closed_at       TEXT
        );

        CREATE TABLE IF NOT EXISTS analyses (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            market_id       TEXT    NOT NULL,
            market_question TEXT    NOT NULL,
            action          TEXT    NOT NULL,
            confidence      REAL    NOT NULL,
            fair_value      REAL,
            current_price   REAL,
            reasoning       TEXT    NOT NULL,
            sources         TEXT    DEFAULT '[]',
            created_at      TEXT    DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS daily_stats (
            date            TEXT    PRIMARY KEY,
            starting_balance REAL   DEFAULT 0,
            realized_pnl    REAL    DEFAULT 0,
            trades_opened   INTEGER DEFAULT 0,
            trades_closed   INTEGER DEFAULT 0,
            wins            INTEGER DEFAULT 0,
            losses          INTEGER DEFAULT 0
        );
        """)


def save_trade(trade: dict) -> int:
    with _conn() as conn:
        cur = conn.execute(
            """INSERT INTO trades
               (market_id, market_question, token_id, side, size_usdc,
                entry_price, confidence, status, order_id)
               VALUES (:market_id, :market_question, :token_id, :side, :size_usdc,
                       :entry_price, :confidence, :status, :order_id)""",
            trade,
        )
        today = date.today().isoformat()
        conn.execute(
            """INSERT INTO daily_stats (date, trades_opened) VALUES (?, 1)
               ON CONFLICT(date) DO UPDATE SET trades_opened = trades_opened + 1""",
            (today,),
        )
        return cur.lastrowid


def get_open_trades() -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT * FROM trades WHERE status = 'open' ORDER BY created_at DESC"
        ).fetchall()
    return [dict(r) for r in rows]


def update_trade_price(trade_id: int, current_price: float, pnl: float):
    with _conn() as conn:
        conn.execute(
            "UPDATE trades SET current_price = ?, pnl = ? WHERE id = ?",
            (current_price, pnl, trade_id),
        )


def close_trade(trade_id: int, close_price: float, pnl: float):
    outcome = "win" if pnl >= 0 else "loss"
    now = datetime.now().isoformat()
    today = date.today().isoformat()
    with _conn() as conn:
        conn.execute(
            """UPDATE trades
               SET status = 'closed', current_price = ?, pnl = ?, closed_at = ?
               WHERE id = ?""",
            (close_price, pnl, now, trade_id),
        )
        wins   = 1 if outcome == "win" else 0
        losses = 1 if outcome == "loss" else 0
        conn.execute(
            """INSERT INTO daily_stats
               (date, trades_closed, wins, losses, realized_pnl)
               VALUES (?, 1, ?, ?, ?)
               ON CONFLICT(date) DO UPDATE SET
                   trades_closed  = trades_closed + 1,
                   wins           = wins + ?,
                   losses         = losses + ?,
                   realized_pnl   = realized_pnl + ?""",
            (today, wins, losses, pnl, wins, losses, pnl),
        )


def save_analysis(analysis: dict):
    with _conn() as conn:
        conn.execute(
            """INSERT INTO analyses
               (market_id, market_question, action, confidence,
                fair_value, current_price, reasoning, sources)
               VALUES (:market_id, :market_question, :action, :confidence,
                       :fair_value, :current_price, :reasoning, :sources)""",
            {**analysis, "sources": json.dumps(analysis.get("sources", []))},
        )


def get_analysis_history(limit: int = 100) -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT * FROM analyses ORDER BY created_at DESC LIMIT ?", (limit,)
        ).fetchall()
    result = []
    for r in rows:
        d = dict(r)
        try:
            d["sources"] = json.loads(d.get("sources") or "[]")
        except (json.JSONDecodeError, TypeError):
            d["sources"] = []
        result.append(d)
    return result


def get_daily_pnl_today() -> float:
    today = date.today().isoformat()
    with _conn() as conn:
        row = conn.execute(
            "SELECT realized_pnl FROM daily_stats WHERE date = ?", (today,)
        ).fetchone()
    return float(row["realized_pnl"]) if row and row["realized_pnl"] else 0.0


def get_trade_history(limit: int = 200) -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT * FROM trades ORDER BY created_at DESC LIMIT ?", (limit,)
        ).fetchall()
    return [dict(r) for r in rows]


def get_daily_stats(limit: int = 30) -> list[dict]:
    with _conn() as conn:
        rows = conn.execute(
            "SELECT * FROM daily_stats ORDER BY date DESC LIMIT ?", (limit,)
        ).fetchall()
    return [dict(r) for r in rows]
