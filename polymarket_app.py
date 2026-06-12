import io
import logging
import os
import smtplib
import ssl
from datetime import date, datetime
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

import pandas as pd
import plotly.express as px
import streamlit as st

st.set_page_config(
    page_title="Polymarket Bot",
    page_icon="📈",
    layout="wide",
    initial_sidebar_state="expanded",
)

from polymarket.analyst import analyze_markets, run_backtest
from polymarket.client import ClobClient, GammaClient
from polymarket.config import (
    CATEGORIES,
    CONFIDENCE_THRESHOLD,
    DAILY_LOSS_LIMIT,
    EMAIL_PASSWORD,
    EMAIL_RECIPIENT,
    EMAIL_SMTP_HOST,
    EMAIL_SMTP_PORT,
    EMAIL_USER,
    MAX_OPEN_POSITIONS,
    MAX_RISK_PER_TRADE,
    MIN_VOLUME_USD,
    OLLAMA_HOST,
    POLY_API_KEY,
    POLY_API_PASSPHRASE,
    POLY_API_SECRET,
    POLY_PRIVATE_KEY,
    POLYMARKET_MODEL,
)
from polymarket.database import (
    close_trade,
    get_analysis_history,
    get_daily_pnl_today,
    get_daily_stats,
    get_open_trades,
    get_trade_history,
    init_db,
    save_analysis,
    save_trade,
    update_trade_price,
)
from polymarket.security import escape_for_markdown, safe_url, sanitize_email_field

logger = logging.getLogger(__name__)

# Minimum seconds between scans to prevent abuse (VULN-20)
MIN_SCAN_INTERVAL_S = 300

init_db()

# ── Password gate (VULN-02) ──────────────────────────────────────────────────────
_APP_PASSWORD = os.getenv("APP_PASSWORD", "")
if _APP_PASSWORD:
    if "authenticated" not in st.session_state:
        st.session_state.authenticated = False
    if not st.session_state.authenticated:
        st.title("🔒 Polymarket Bot")
        with st.form("login_form"):
            pw = st.text_input("Password", type="password")
            if st.form_submit_button("Login"):
                if pw == _APP_PASSWORD:
                    st.session_state.authenticated = True
                    st.rerun()
                else:
                    st.error("Incorrect password.")
        st.stop()

# ── Session state defaults ──────────────────────────────────────────────────────
_DEFAULTS = {
    "last_scan_time": None,
    "recommendations":  [],
    "markets_cache":    [],
    "scan_pending":     False,
    "auto_run":         False,
    "clob_client":      None,
}
for k, v in _DEFAULTS.items():
    if k not in st.session_state:
        st.session_state[k] = v


# ── Helpers ─────────────────────────────────────────────────────────────────────

def _creds_ok() -> bool:
    return bool(POLY_PRIVATE_KEY and POLY_API_KEY)


def _get_clob() -> ClobClient:
    if st.session_state.clob_client is None:
        st.session_state.clob_client = ClobClient(
            private_key=POLY_PRIVATE_KEY,
            api_key=POLY_API_KEY,
            api_secret=POLY_API_SECRET,
            api_passphrase=POLY_API_PASSPHRASE,
        )
    return st.session_state.clob_client


def _get_balance() -> float:
    if not _creds_ok():
        return 0.0
    try:
        bal = _get_clob().get_balance()
        st.session_state["_cached_balance"] = bal
        return bal
    except Exception:
        return st.session_state.get("_cached_balance", 0.0)


def _send_email(subject: str, body: str):
    if not (EMAIL_USER and EMAIL_PASSWORD and EMAIL_RECIPIENT):
        return
    try:
        msg = MIMEMultipart()
        msg["From"]    = EMAIL_USER
        msg["To"]      = EMAIL_RECIPIENT
        msg["Subject"] = sanitize_email_field(subject, max_len=200)
        msg.attach(MIMEText(sanitize_email_field(body, max_len=4000), "plain"))
        # Explicit TLS context prevents downgrade attacks (VULN-15)
        ctx = ssl.create_default_context()
        with smtplib.SMTP(EMAIL_SMTP_HOST, EMAIL_SMTP_PORT) as srv:
            srv.starttls(context=ctx)
            srv.login(EMAIL_USER, EMAIL_PASSWORD)
            srv.send_message(msg)
    except Exception as exc:
        logger.error("Email send failed: %s", exc, exc_info=True)


def _refresh_open_positions(gamma: GammaClient):
    """Update current price and unrealised P&L for every open trade."""
    for trade in get_open_trades():
        token_id = trade.get("token_id") or ""
        if not token_id:
            continue
        price = gamma.get_market_price(token_id)
        if price is None:
            continue
        entry = trade["entry_price"]
        # Guard against zero entry_price to avoid division by zero (VULN-09)
        pnl = (price - entry) / entry * trade["size_usdc"] if entry > 0 else 0.0
        update_trade_price(trade["id"], price, pnl)


# ── Sidebar ─────────────────────────────────────────────────────────────────────

with st.sidebar:
    st.title("📈 Polymarket Bot")
    st.caption(f"Model: {POLYMARKET_MODEL}")

    if _creds_ok():
        st.success("✅ Credentials configured")
    else:
        st.error("⚠️ Credentials missing — see **Setup** tab")

    st.divider()

    st.subheader("Bot Settings")
    sb_min_volume    = st.number_input("Min market volume ($)", value=int(MIN_VOLUME_USD), step=10_000, min_value=10_000)
    # Minimum confidence is 90% — lower values violate risk policy (VULN-13)
    sb_confidence    = st.slider("Min confidence (%)", 90, 99, max(90, int(CONFIDENCE_THRESHOLD * 100)))
    sb_daily_loss    = st.slider("Daily loss limit (%)", 1, 10, int(DAILY_LOSS_LIMIT * 100))
    sb_max_positions = st.number_input("Max open positions", value=MAX_OPEN_POSITIONS, min_value=1, max_value=20)

    st.divider()
    st.subheader("Auto-Scan")
    sb_auto_run = st.toggle("Enable auto-scan", value=st.session_state.auto_run)
    st.session_state.auto_run = sb_auto_run

    sb_interval = st.slider("Interval (minutes)", 5, 1440, 60, 5, disabled=not sb_auto_run)

    if st.session_state.last_scan_time:
        elapsed_min = int((datetime.now() - st.session_state.last_scan_time).total_seconds() // 60)
        st.caption(f"Last scan: {elapsed_min}m ago")
        if sb_auto_run:
            remaining = max(0, sb_interval - elapsed_min)
            st.caption(f"Next scan: {remaining}m")

    if st.button("🔍 Run Scan Now", type="primary", use_container_width=True):
        st.session_state.scan_pending = True

# Auto-refresh ticker (requires streamlit-autorefresh)
if st.session_state.auto_run:
    try:
        from streamlit_autorefresh import st_autorefresh
        st_autorefresh(interval=60_000, key="autorefresh_ticker")
    except ImportError:
        pass

# Trigger auto-scan only if enough time has elapsed (VULN-20)
if st.session_state.auto_run and st.session_state.last_scan_time:
    elapsed_s = (datetime.now() - st.session_state.last_scan_time).total_seconds()
    if elapsed_s >= max(sb_interval * 60, MIN_SCAN_INTERVAL_S):
        st.session_state.scan_pending = True


# ── Tabs ────────────────────────────────────────────────────────────────────────

(
    tab_dashboard,
    tab_scanner,
    tab_recs,
    tab_history,
    tab_backtest,
    tab_setup,
) = st.tabs(["Dashboard", "Market Scanner", "Recommendations", "Trade History", "Backtest", "Setup"])


# ── Dashboard ────────────────────────────────────────────────────────────────────

with tab_dashboard:
    st.header("Portfolio Dashboard")

    balance       = _get_balance()
    open_trades   = get_open_trades()
    trade_history = get_trade_history()
    daily_stats   = get_daily_stats()
    today_pnl     = get_daily_pnl_today()

    closed    = [t for t in trade_history if t["status"] == "closed"]
    wins      = sum(1 for t in closed if (t.get("pnl") or 0) > 0)
    total_pnl = sum((t.get("pnl") or 0) for t in closed)
    win_rate  = (wins / len(closed) * 100) if closed else 0.0

    c1, c2, c3, c4 = st.columns(4)
    c1.metric("Balance (USDC)", f"${balance:,.2f}")
    c2.metric("Total Realised P&L", f"${total_pnl:+,.2f}")
    c3.metric("Open Positions", f"{len(open_trades)} / {sb_max_positions}")
    c4.metric("Win Rate", f"{win_rate:.1f}%", f"{len(closed)} closed trades")

    loss_limit_usdc = balance * (sb_daily_loss / 100)
    if today_pnl < -loss_limit_usdc:
        st.error(
            f"🚫 Daily loss limit hit: ${today_pnl:.2f} loss today "
            f"(limit ${loss_limit_usdc:.2f}). New trades are paused."
        )

    st.divider()
    col_chart, col_scan = st.columns([2, 1])

    with col_chart:
        st.subheader("Cumulative P&L")
        if daily_stats:
            df_s = pd.DataFrame(daily_stats).sort_values("date")
            df_s["cum_pnl"] = df_s["realized_pnl"].cumsum()
            fig = px.area(
                df_s,
                x="date",
                y="cum_pnl",
                labels={"date": "", "cum_pnl": "P&L (USDC)"},
                color_discrete_sequence=["#0068c9"],
            )
            fig.update_layout(height=230, margin=dict(l=0, r=0, t=0, b=0), showlegend=False)
            st.plotly_chart(fig, use_container_width=True)
        else:
            st.info("No trade history yet — run a scan to get started.")

    with col_scan:
        st.subheader("Last Scan")
        if st.session_state.last_scan_time:
            st.caption(st.session_state.last_scan_time.strftime("Ran at %H:%M on %b %d"))
            recs = st.session_state.recommendations
            if recs:
                st.success(f"**{len(recs)} opportunity{'s' if len(recs)>1 else ''} found**")
                for r in recs[:4]:
                    q  = escape_for_markdown(r.get("market_question", ""))[:55]
                    cf = r.get("confidence", 0) * 100
                    st.write(f"• {q}… — **{cf:.0f}%**")
            else:
                st.info("No high-confidence trades found.")
        else:
            st.info("No scan run yet.")

    st.subheader("Active Positions")
    if open_trades:
        df_open = pd.DataFrame(open_trades)
        cols_show = [c for c in ["market_question", "size_usdc", "entry_price", "current_price", "pnl", "confidence", "created_at"] if c in df_open.columns]
        df_open = df_open[cols_show].rename(columns={
            "market_question": "Market",
            "size_usdc": "Size ($)",
            "entry_price": "Entry",
            "current_price": "Current",
            "pnl": "Unreal. P&L",
            "confidence": "Confidence",
            "created_at": "Opened",
        })
        if "Confidence" in df_open:
            df_open["Confidence"] = df_open["Confidence"].apply(lambda x: f"{x*100:.0f}%" if x else "—")
        if "Unreal. P&L" in df_open:
            df_open["Unreal. P&L"] = df_open["Unreal. P&L"].apply(lambda x: f"${x:+.2f}" if x else "—")
        st.dataframe(df_open, use_container_width=True, hide_index=True)
    else:
        st.info("No open positions.")

    if daily_stats:
        st.subheader("Daily Trade Activity")
        df_d = pd.DataFrame(daily_stats).sort_values("date")
        fig2 = px.bar(
            df_d,
            x="date",
            y=["wins", "losses"],
            labels={"value": "Trades", "date": ""},
            color_discrete_map={"wins": "#00b09b", "losses": "#e74c3c"},
            barmode="group",
        )
        fig2.update_layout(height=200, margin=dict(l=0, r=0, t=0, b=0))
        st.plotly_chart(fig2, use_container_width=True)


# ── Market Scanner ───────────────────────────────────────────────────────────────

with tab_scanner:
    st.header("Market Scanner")

    col_btn, col_info = st.columns([2, 3])
    with col_btn:
        if st.button("Fetch High-Volume Markets", use_container_width=True):
            with st.spinner("Loading Polymarket markets…"):
                try:
                    gamma = GammaClient()
                    st.session_state.markets_cache = gamma.get_markets(
                        min_volume=sb_min_volume,
                        categories=CATEGORIES,
                    )
                    _refresh_open_positions(gamma)
                    st.success(f"Found {len(st.session_state.markets_cache)} markets")
                except Exception:
                    logger.exception("Market fetch failed")
                    st.error("Failed to fetch markets — check logs for details.")

    with col_info:
        if st.session_state.markets_cache:
            st.caption(
                f"{len(st.session_state.markets_cache)} markets loaded · "
                f"Categories: {', '.join(CATEGORIES)} · "
                f"Min volume: ${sb_min_volume:,}"
            )

    markets = st.session_state.markets_cache
    if markets:
        df_m = pd.DataFrame(markets)
        view_cols = [c for c in ["question", "category", "volume_usd", "yes_price", "end_date"] if c in df_m.columns]
        df_m = df_m[view_cols].copy()
        df_m.columns = ["Market", "Category", "Volume ($)", "YES Price", "End Date"][:len(view_cols)]
        if "Volume ($)" in df_m:
            df_m["Volume ($)"] = df_m["Volume ($)"].apply(lambda x: f"${x:,.0f}")
        if "YES Price" in df_m:
            df_m["YES Price"] = df_m["YES Price"].apply(lambda x: f"{x:.3f}  ({x*100:.1f}%)")
        st.dataframe(df_m, use_container_width=True, hide_index=True)
    else:
        st.info("Click **Fetch High-Volume Markets** to load current markets.")


# ── Execute Scan ─────────────────────────────────────────────────────────────────

if st.session_state.scan_pending:
    st.session_state.scan_pending = False

    if not _creds_ok():
        st.warning("Configure credentials in the **Setup** tab before scanning.")
    else:
        balance   = _get_balance()
        today_pnl = get_daily_pnl_today()
        loss_limit = balance * (sb_daily_loss / 100)

        if today_pnl < -loss_limit:
            st.warning("Daily loss limit reached. Scan skipped.")
        else:
            with st.status("Analysing markets…", expanded=True) as status:
                st.write("Fetching high-volume markets…")
                try:
                    gamma = GammaClient()
                    if not st.session_state.markets_cache:
                        st.session_state.markets_cache = gamma.get_markets(
                            min_volume=sb_min_volume, categories=CATEGORIES
                        )

                    markets = st.session_state.markets_cache
                    st.write(f"Analysing {len(markets)} markets with {POLYMARKET_MODEL}…")

                    recs = analyze_markets(markets, balance, POLYMARKET_MODEL, OLLAMA_HOST)

                    # Enforce both UI slider and env-configured threshold (VULN-21)
                    effective_threshold = max(sb_confidence / 100, CONFIDENCE_THRESHOLD)
                    recs = [r for r in recs if r.get("confidence", 0) >= effective_threshold]

                    for r in recs:
                        save_analysis({
                            "market_id":       r.get("market_id", ""),
                            "market_question": r.get("market_question", ""),
                            "action":          r.get("action", ""),
                            "confidence":      r.get("confidence", 0),
                            "fair_value":      r.get("fair_value_estimate"),
                            "current_price":   r.get("current_yes_price"),
                            "reasoning":       r.get("reasoning", ""),
                            "sources":         r.get("news_sources", []),
                        })

                    st.session_state.recommendations = recs
                    st.session_state.last_scan_time  = datetime.now()

                    status.update(
                        label=f"Scan complete — {len(recs)} opportunity{'s' if len(recs)!=1 else ''} found",
                        state="complete",
                    )

                    if recs:
                        st.toast(f"🎯 {len(recs)} trade{'s' if len(recs)>1 else ''} found!", icon="🎯")
                        _send_email(
                            f"Polymarket Bot: {len(recs)} opportunities",
                            "\n\n".join(
                                f"Market: {sanitize_email_field(r.get('market_question',''))}\n"
                                f"Confidence: {r.get('confidence',0)*100:.0f}%\n"
                                f"Edge: {r.get('fair_value_estimate',0)-r.get('current_yes_price',0):.3f}\n"
                                f"Reasoning: {sanitize_email_field(r.get('reasoning',''))}"
                                for r in recs
                            ),
                        )
                    else:
                        st.toast("Scan complete — no high-confidence trades found.")

                except Exception:
                    logger.exception("Market scan failed")
                    status.update(label="Scan failed", state="error")
                    st.error("Scan failed — check logs for details.")


# ── Recommendations ───────────────────────────────────────────────────────────────

with tab_recs:
    st.header("Trade Recommendations")

    recs = st.session_state.recommendations
    if not recs:
        st.info("No recommendations yet. Use the **Run Scan Now** button in the sidebar.")
    else:
        balance       = _get_balance()
        open_trades   = get_open_trades()
        open_count    = len(open_trades)
        max_per_trade = balance * MAX_RISK_PER_TRADE

        st.caption(
            f"{len(recs)} recommendation{'s' if len(recs)!=1 else ''} · "
            f"Max per trade: ${max_per_trade:.2f} · "
            f"Open positions: {open_count}/{sb_max_positions}"
        )
        st.divider()

        for i, rec in enumerate(list(recs)):
            confidence    = rec.get("confidence", 0)
            current_price = rec.get("current_yes_price", 0.5)
            fair_value    = rec.get("fair_value_estimate", current_price)
            edge          = fair_value - current_price

            with st.container(border=True):
                left, right = st.columns([5, 2])

                with left:
                    # Escape user-facing text before rendering as markdown (VULN-16)
                    safe_q = escape_for_markdown(rec.get("market_question", "Unknown market"))
                    st.markdown(f"#### {safe_q}")
                    m1, m2, m3, m4 = st.columns(4)
                    m1.metric("Confidence",  f"{confidence*100:.0f}%")
                    m2.metric("Current YES", f"{current_price:.3f}")
                    m3.metric("Fair Value",  f"{fair_value:.3f}")
                    m4.metric("Edge",        f"{edge*100:+.1f}%")

                    with st.expander("Reasoning"):
                        st.write(rec.get("reasoning", "No reasoning provided."))
                        sources = rec.get("news_sources", [])
                        if sources:
                            # Only allow http/https URLs in rendered links (VULN-04)
                            links = "  ·  ".join(
                                f"[link]({safe_url(u)})" for u in sources
                            )
                            st.markdown(f"**Sources:** {links}")

                with right:
                    if open_count >= sb_max_positions:
                        st.warning("Max positions reached")
                    elif not _creds_ok():
                        st.warning("Add credentials\nin Setup tab")
                    else:
                        size = st.number_input(
                            "Size (USDC)",
                            min_value=1.0,
                            max_value=float(max(max_per_trade, 1.0)),
                            value=float(max(min(max_per_trade, balance), 1.0)),
                            key=f"size_{i}",
                            step=1.0,
                        )

                        if st.button("✅ Buy YES", key=f"buy_{i}", type="primary", use_container_width=True):
                            try:
                                # Re-query live count right before placing (VULN-05/VULN-10)
                                live_open = get_open_trades()
                                if len(live_open) >= sb_max_positions:
                                    st.error("Max positions reached — order not placed.")
                                    st.rerun()

                                # Look up token_id from canonical cache only — never trust LLM output (VULN-06)
                                market = next(
                                    (m for m in st.session_state.markets_cache
                                     if m["market_id"] == rec.get("market_id")), None
                                )
                                if not market:
                                    st.error("Market not found in cache — refresh the scanner first.")
                                    st.rerun()

                                token_id = market.get("yes_token_id", "")
                                if not token_id:
                                    st.error("YES token ID missing for this market — order rejected.")
                                    st.rerun()

                                clob     = _get_clob()
                                resp     = clob.place_order(token_id, size, current_price)
                                order_id = (resp or {}).get("orderID", "")

                                trade_id = save_trade({
                                    "market_id":       rec.get("market_id", ""),
                                    "market_question": rec.get("market_question", ""),
                                    "token_id":        token_id,
                                    "side":            "YES",
                                    "size_usdc":       size,
                                    "entry_price":     current_price,
                                    "confidence":      confidence,
                                    "status":          "open",
                                    "order_id":        order_id,
                                })

                                open_count += 1
                                st.toast(f"Trade #{trade_id} placed!", icon="✅")
                                _send_email(
                                    "Polymarket Bot — Trade Executed",
                                    f"Market:     {sanitize_email_field(rec.get('market_question',''))}\n"
                                    f"Size:       ${size:.2f}\n"
                                    f"Price:      {current_price:.3f}\n"
                                    f"Confidence: {confidence*100:.0f}%\n"
                                    f"Order ID:   {sanitize_email_field(order_id)}",
                                )
                                st.session_state.recommendations.pop(i)
                                st.rerun()

                            except Exception:
                                logger.exception("Order placement failed")
                                st.error("Order failed — check logs for details.")

                        if st.button("⏭ Skip", key=f"skip_{i}", use_container_width=True):
                            st.session_state.recommendations.pop(i)
                            st.rerun()


# ── Trade History ─────────────────────────────────────────────────────────────────

with tab_history:
    st.header("Trade History")

    sub_trades, sub_analyses, sub_exit = st.tabs(["Trades", "Analysis Archive", "Exit Suggestions"])

    with sub_trades:
        trades = get_trade_history()
        if trades:
            df_t = pd.DataFrame(trades)
            show = [c for c in ["id", "market_question", "side", "size_usdc", "entry_price", "current_price", "pnl", "confidence", "status", "created_at", "closed_at"] if c in df_t.columns]
            df_t = df_t[show].copy()
            df_t.columns = [
                {"id": "ID", "market_question": "Market", "side": "Side", "size_usdc": "Size ($)",
                 "entry_price": "Entry", "current_price": "Current", "pnl": "P&L ($)",
                 "confidence": "Conf", "status": "Status", "created_at": "Opened", "closed_at": "Closed"}.get(c, c)
                for c in show
            ]
            st.dataframe(df_t, use_container_width=True, hide_index=True)

            csv = io.StringIO()
            df_t.to_csv(csv, index=False)
            st.download_button(
                "📥 Export CSV",
                data=csv.getvalue(),
                file_name=f"polymarket_trades_{date.today()}.csv",
                mime="text/csv",
            )
        else:
            st.info("No trades recorded yet.")

    with sub_analyses:
        analyses = get_analysis_history()
        if analyses:
            for a in analyses:
                label = (
                    f"{a['created_at'][:16]}  ·  "
                    f"{escape_for_markdown(a['market_question'][:60])}  ·  "
                    f"{a['confidence']*100:.0f}% conf"
                )
                with st.expander(label):
                    st.write(a["reasoning"])
                    if a.get("sources"):
                        links = "  ·  ".join(f"[link]({safe_url(u)})" for u in a["sources"])
                        st.markdown(f"**Sources:** {links}")
        else:
            st.info("No analysis history yet.")

    with sub_exit:
        st.subheader("Positions to Consider Exiting")
        open_trades = get_open_trades()
        flagged = [
            t for t in open_trades
            if t.get("current_price") and t["entry_price"] > 0
            and t["current_price"] < t["entry_price"] * 0.70
        ]

        if not flagged:
            st.success("No positions have moved more than 30% against you.")
        else:
            st.warning(f"{len(flagged)} position(s) have deteriorated significantly.")
            for t in flagged:
                with st.container(border=True):
                    entry   = t["entry_price"]
                    current = t.get("current_price", entry)
                    # Guard against zero entry_price (VULN-09)
                    pnl = (current - entry) / entry * t["size_usdc"] if entry > 0 else 0.0

                    st.markdown(f"**{escape_for_markdown(t['market_question'][:80])}**")
                    c1, c2, c3 = st.columns(3)
                    c1.metric("Entry",   f"{entry:.3f}")
                    c2.metric("Current", f"{current:.3f}")
                    c3.metric("P&L",     f"${pnl:+.2f}")

                    if st.button(f"Close Position #{t['id']}", key=f"close_{t['id']}"):
                        if _creds_ok():
                            try:
                                clob = _get_clob()
                                if t.get("order_id"):
                                    clob.cancel_order(t["order_id"])
                            except Exception:
                                pass
                        close_trade(t["id"], current, pnl)
                        st.toast(f"Position #{t['id']} closed.", icon="🔴")
                        _send_email(
                            "Polymarket Bot — Position Closed",
                            f"Market: {sanitize_email_field(t['market_question'])}\n"
                            f"P&L: ${pnl:+.2f}\n"
                            f"Entry: {entry:.3f} → Exit: {current:.3f}",
                        )
                        st.rerun()


# ── Backtest ──────────────────────────────────────────────────────────────────────

with tab_backtest:
    st.header("Backtest")

    st.info(
        "**How it works:** The model analyses recently resolved Polymarket markets "
        "as if they were still open (instructed to ignore outcome knowledge). "
        "Results show how the strategy's recommendations would have performed. "
        "Note: LLMs have training-data knowledge, so treat these as directional only."
    )

    n_markets = st.slider("Number of resolved markets to analyse", 5, 50, 20)

    if st.button("▶ Run Backtest", type="primary"):
        with st.status(f"Running backtest with {POLYMARKET_MODEL}…", expanded=True) as status:
            try:
                st.write("Fetching resolved markets…")
                gamma    = GammaClient()
                resolved = gamma.get_resolved_markets(limit=n_markets)

                if not resolved:
                    status.update(label="No resolved markets found.", state="error")
                else:
                    st.write(f"Analysing {len(resolved)} resolved markets with {POLYMARKET_MODEL}…")
                    results = run_backtest(resolved, POLYMARKET_MODEL, OLLAMA_HOST)
                    status.update(label="Backtest complete", state="complete")

                    if not results:
                        st.info("Model found no trades meeting the 90% threshold in the backtest set.")
                    else:
                        correct  = sum(1 for r in results if r.get("would_win"))
                        accuracy = correct / len(results) * 100
                        hypo_pnl = sum(
                            (1 - r.get("current_yes_price", 0.5)) * 10 if r.get("would_win")
                            else -r.get("current_yes_price", 0.5) * 10
                            for r in results
                        )

                        c1, c2, c3, c4 = st.columns(4)
                        c1.metric("Markets Analysed",   len(resolved))
                        c2.metric("Trades Recommended", len(results))
                        c3.metric("Would-Win Rate",     f"{accuracy:.1f}%")
                        c4.metric("Hypothetical P&L*",  f"${hypo_pnl:+.1f}", help="Assumes $10/trade flat sizing")

                        st.divider()
                        for r in results:
                            icon  = "✅" if r.get("would_win") else "❌"
                            label = (
                                f"{icon} {escape_for_markdown(r.get('market_question','')[:70])}  "
                                f"({r.get('confidence',0)*100:.0f}% conf)"
                            )
                            with st.expander(label):
                                st.write(f"**Actual outcome:** {r.get('actual_outcome','Unknown')}")
                                st.write(f"**Reasoning:** {r.get('reasoning','')}")
                                if r.get("news_sources"):
                                    links = "  ·  ".join(
                                        f"[link]({safe_url(u)})" for u in r["news_sources"]
                                    )
                                    st.markdown(f"**Sources:** {links}")

            except Exception:
                logger.exception("Backtest failed")
                status.update(label="Backtest failed", state="error")
                st.error("Backtest failed — check logs for details.")


# ── Setup ─────────────────────────────────────────────────────────────────────────

with tab_setup:
    st.header("Setup & Credentials")

    st.subheader("Status")
    col_m, col_p = st.columns(2)
    with col_m:
        st.info(f"**AI Model:** `{POLYMARKET_MODEL}`")
        st.caption(f"Ollama host: {OLLAMA_HOST}")
    with col_p:
        checks = {
            "POLY_PRIVATE_KEY":    bool(POLY_PRIVATE_KEY),
            "POLY_API_KEY":        bool(POLY_API_KEY),
            "POLY_API_SECRET":     bool(POLY_API_SECRET),
            "POLY_API_PASSPHRASE": bool(POLY_API_PASSPHRASE),
        }
        for k, ok in checks.items():
            (st.success if ok else st.error)(f"{'✅' if ok else '❌'}  {k}")

    st.divider()

    with st.expander("1 — Install Ollama and pull a model"):
        st.markdown(f"""
**Install Ollama** (runs models locally, no API key needed):
```bash
# macOS / Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows: download from https://ollama.com
```

**Pull a capable model** — choose based on your RAM:

| Model | RAM needed | Best for |
|---|---|---|
| `qwen2.5:14b` *(current default)* | ~10 GB | Great reasoning, fast |
| `qwen2.5:32b` | ~20 GB | Excellent accuracy |
| `deepseek-r1:14b` | ~10 GB | Strong reasoning |
| `llama3.3:70b` | ~40 GB | Best open model |

```bash
ollama pull {POLYMARKET_MODEL}
```

Set a different model in `.env`:
```
POLYMARKET_MODEL=qwen2.5:32b
OLLAMA_HOST=http://localhost:11434
```
        """)

    with st.expander("2 — Create a Polygon wallet and fund with USDC"):
        st.markdown("""
1. Install **[MetaMask](https://metamask.io)** and create a new wallet
2. Switch to the **Polygon** network (Chain ID 137)
3. Bridge USDC from Ethereum via [Polygon Bridge](https://wallet.polygon.technology/) or buy directly on a CEX and withdraw to Polygon

> ⚠️ Use a **dedicated bot wallet** — never your main wallet. Keep the private key secret.
        """)

    with st.expander("3 — Generate Polymarket API credentials"):
        st.markdown("""
Run this once to derive API credentials from your private key:

```python
from py_clob_client.client import ClobClient
from py_clob_client.constants import POLYGON

client = ClobClient(
    host="https://clob.polymarket.com",
    key="0xYOUR_PRIVATE_KEY",
    chain_id=POLYGON,
)
creds = client.create_or_derive_api_creds()
print("API Key:",        creds.api_key)
print("API Secret:",     creds.api_secret)
print("API Passphrase:", creds.api_passphrase)
```

Add all values to `.env`:
```
POLY_PRIVATE_KEY=0x...
POLY_API_KEY=...
POLY_API_SECRET=...
POLY_API_PASSPHRASE=...
```
        """)

    with st.expander("4 — Optional: password-protect the UI"):
        st.markdown("""
Add to `.env` to require a password at startup:
```
APP_PASSWORD=your_secure_password
```
        """)

    with st.expander("5 — Email notifications (optional)"):
        st.markdown("""
Add to `.env`:
```
EMAIL_SMTP_HOST=smtp.gmail.com
EMAIL_SMTP_PORT=587
EMAIL_USER=yourbot@gmail.com
EMAIL_PASSWORD=your_app_password
EMAIL_RECIPIENT=you@youremail.com
```
For Gmail, create an **App Password** at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
        """)

    with st.expander("6 — Running the bot"):
        st.markdown("""
```bash
# Install all dependencies
pip install -r requirements.txt

# Start the bot
streamlit run polymarket_app.py
```

**For 24/7 operation** on a VPS:
```bash
nohup streamlit run polymarket_app.py --server.headless true --server.port 8502 &
```
        """)
