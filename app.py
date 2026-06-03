import streamlit as st
import plotly.express as px
import pandas as pd
import io
import re
import ast
from langchain_ollama import OllamaLLM
from langchain_classic.chains import create_sql_query_chain
from langchain_community.utilities import SQLDatabase
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import PromptTemplate
from langchain_core.runnables import RunnablePassthrough
from config import DB_HOST, DB_PORT, DB_USER, DB_PASSWORD, DB_NAME, OLLAMA_MODEL

# ──────────────────────────────────────────────
# Page Config
# ──────────────────────────────────────────────
st.set_page_config(
    page_title="Query Assistant",
    page_icon="🗄️",
    layout="wide",
    initial_sidebar_state="expanded"
)

# ──────────────────────────────────────────────
# Age Gate
# ──────────────────────────────────────────────
if "age_verified" not in st.session_state:
    st.session_state.age_verified = False

if not st.session_state.age_verified:
    st.markdown("""
    <style>
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600&display=swap');
    *, *::before, *::after { box-sizing: border-box; }
    html, body, [data-testid="stAppViewContainer"] {
        background: #0f0f0f !important;
        font-family: 'Inter', sans-serif !important;
    }
    #MainMenu, footer, [data-testid="stToolbar"], [data-testid="stDecoration"],
    [data-testid="stSidebar"], [data-testid="stHeader"] { display: none !important; }
    .main .block-container { padding: 0 !important; max-width: 100% !important; }
    [data-testid="stBottom"] { display: none !important; }
    .age-gate-wrapper {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 2rem;
    }
    .age-gate-card {
        background: #1a1a1a;
        border: 1px solid #2e2e2e;
        border-radius: 20px;
        padding: 3rem 2.5rem;
        max-width: 420px;
        width: 100%;
        text-align: center;
        box-shadow: 0 24px 80px rgba(0,0,0,0.6);
        animation: gateIn 0.45s cubic-bezier(0.22,1,0.36,1) both;
    }
    @keyframes gateIn {
        from { opacity: 0; transform: translateY(24px) scale(0.97); }
        to   { opacity: 1; transform: translateY(0) scale(1); }
    }
    .age-gate-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 72px; height: 72px;
        background: #ff3b3b;
        border-radius: 50%;
        font-size: 1.9rem;
        font-weight: 800;
        color: #ffffff;
        margin-bottom: 1.5rem;
        letter-spacing: -1px;
        box-shadow: 0 0 0 12px rgba(255,59,59,0.12);
    }
    .age-gate-title {
        font-size: 1.45rem;
        font-weight: 700;
        color: #f5f5f5;
        letter-spacing: -0.5px;
        margin-bottom: 0.6rem;
    }
    .age-gate-subtitle {
        font-size: 0.85rem;
        color: #888;
        line-height: 1.6;
        margin-bottom: 2rem;
    }
    .age-gate-divider {
        height: 1px;
        background: #2e2e2e;
        margin: 1.8rem 0;
    }
    .age-gate-warning {
        font-size: 0.72rem;
        color: #555;
        line-height: 1.6;
        margin-top: 1.5rem;
    }
    div[data-testid="stButton"] > button {
        width: 100% !important;
        padding: 0.85rem 0 !important;
        font-size: 0.95rem !important;
        font-weight: 600 !important;
        border-radius: 12px !important;
        border: none !important;
        cursor: pointer !important;
        transition: transform 0.15s, box-shadow 0.15s, background 0.15s !important;
        letter-spacing: -0.2px !important;
    }
    div[data-testid="stButton"]:first-of-type > button {
        background: #ff3b3b !important;
        color: #ffffff !important;
        box-shadow: 0 4px 20px rgba(255,59,59,0.35) !important;
    }
    div[data-testid="stButton"]:first-of-type > button:hover {
        background: #ff2020 !important;
        transform: translateY(-2px) !important;
        box-shadow: 0 6px 24px rgba(255,59,59,0.45) !important;
    }
    div[data-testid="stButton"]:last-of-type > button {
        background: #2e2e2e !important;
        color: #888 !important;
    }
    div[data-testid="stButton"]:last-of-type > button:hover {
        background: #383838 !important;
        color: #aaa !important;
    }
    </style>
    """, unsafe_allow_html=True)

    st.markdown('<div class="age-gate-wrapper"><div class="age-gate-card">', unsafe_allow_html=True)
    st.markdown("""
    <div class="age-gate-badge">18+</div>
    <div class="age-gate-title">Age Verification Required</div>
    <div class="age-gate-subtitle">
        This application may contain data and content<br>
        restricted to adults aged 18 and over.
    </div>
    <div class="age-gate-divider"></div>
    <div style="font-size:0.82rem;color:#aaa;margin-bottom:1.2rem;font-weight:500;">
        Do you confirm you are 18 years of age or older?
    </div>
    """, unsafe_allow_html=True)

    col_yes, col_no = st.columns(2, gap="small")
    with col_yes:
        if st.button("Yes, I am 18+", key="age_yes"):
            st.session_state.age_verified = True
            st.rerun()
    with col_no:
        if st.button("No, exit", key="age_no"):
            st.markdown("""
            <div style="text-align:center;color:#555;font-size:0.85rem;margin-top:1.2rem;">
                Access denied. Please close this tab.
            </div>
            """, unsafe_allow_html=True)

    st.markdown("""
    <div class="age-gate-warning">
        By clicking "Yes, I am 18+" you confirm that you meet the minimum age
        requirement. Providing false information to access this service may
        violate applicable laws.
    </div>
    """, unsafe_allow_html=True)
    st.markdown('</div></div>', unsafe_allow_html=True)
    st.stop()

# ──────────────────────────────────────────────
# CSS
# ──────────────────────────────────────────────
st.markdown("""
<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:ital,wght@0,300;0,400;0,500;0,600;1,400&display=swap');

*, *::before, *::after { box-sizing: border-box; }

html, body, [data-testid="stAppViewContainer"] {
    background: #f5f5f3 !important;
    font-family: 'Inter', sans-serif !important;
    color: #1c1c1c !important;
}

#MainMenu, footer, [data-testid="stToolbar"], [data-testid="stDecoration"] {
    display: none !important;
}

/* ── Main content area ── */
.main .block-container {
    padding: 0 !important;
    max-width: 100% !important;
}

/* ── Force sidebar always visible, never auto-collapse ── */
[data-testid="stSidebar"][aria-expanded="false"] {
    display: flex !important;
    width: 260px !important;
    min-width: 260px !important;
    transform: none !important;
    visibility: visible !important;
}
section[data-testid="stSidebar"] {
    display: flex !important;
    width: 260px !important;
    min-width: 260px !important;
    visibility: visible !important;
}
[data-testid="stSidebar"] {
    background: #ffffff !important;
    border-right: 1px solid #e8e8e6 !important;
    min-width: 260px !important;
    max-width: 260px !important;
    color: #1c1c1c !important;
}
[data-testid="stSidebar"] p,
[data-testid="stSidebar"] span,
[data-testid="stSidebar"] div,
[data-testid="stSidebar"] label {
    color: #1c1c1c !important;
}
[data-testid="stSidebar"] .block-container {
    padding: 1.5rem 1.2rem 1.5rem 1.2rem !important;
}
/* Sidebar toggle button — make it visible on light bg */
[data-testid="stSidebarCollapsedControl"],
[data-testid="stSidebarCollapsedControl"] button,
button[kind="header"] {
    background: #ffffff !important;
    border: 1px solid #e8e8e6 !important;
    border-radius: 8px !important;
    color: #1c1c1c !important;
}
[data-testid="stSidebarCollapsedControl"] svg {
    color: #1c1c1c !important;
    fill: #1c1c1c !important;
}

/* ── Sidebar brand ── */
.sb-brand {
    display: flex;
    align-items: center;
    gap: 9px;
    padding-bottom: 1.2rem;
    border-bottom: 1px solid #f0f0ee;
    margin-bottom: 1.4rem;
}
.sb-brand-dot {
    width: 28px; height: 28px;
    background: #1c1c1c;
    border-radius: 6px;
    display: flex; align-items: center; justify-content: center;
    font-size: 13px; flex-shrink: 0;
}
.sb-brand-name {
    font-size: 0.9rem; font-weight: 600;
    color: #1c1c1c; letter-spacing: -0.3px;
}
.sb-brand-sub {
    font-size: 0.68rem; color: #9ca3af; margin-top: 1px;
}

/* ── Sidebar sections ── */
.sb-section { margin-bottom: 1.3rem; }
.sb-label {
    font-size: 0.62rem; font-weight: 600;
    color: #b0b0a8; text-transform: uppercase;
    letter-spacing: 0.1em; margin-bottom: 0.5rem;
}
.sb-row {
    display: flex; align-items: center;
    justify-content: space-between;
    padding: 0.42rem 0;
    border-bottom: 1px solid #f5f5f3;
}
.sb-row:last-child { border-bottom: none; }
.sb-key { font-size: 0.78rem; color: #6b7280; }
.sb-val {
    font-size: 0.75rem; color: #1c1c1c;
    font-weight: 500; background: #f5f5f3;
    padding: 2px 7px; border-radius: 4px;
    font-family: 'SF Mono', 'Fira Mono', monospace;
}

/* ── Table tags ── */
.sb-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 0.4rem; }
.sb-tag {
    font-size: 0.68rem; color: #4b5563;
    background: #f5f5f3; border: 1px solid #e8e8e6;
    border-radius: 4px; padding: 2px 7px;
    font-family: 'SF Mono', 'Fira Mono', monospace;
}

/* ── Status ── */
.sb-status {
    display: flex; align-items: center; gap: 6px;
    margin-top: 1.2rem; padding: 0.55rem 0.75rem;
    background: #f0fdf4; border: 1px solid #d1fae5;
    border-radius: 6px; font-size: 0.72rem;
    color: #15803d; font-weight: 500;
}
.sb-dot {
    width: 6px; height: 6px; background: #22c55e;
    border-radius: 50%; flex-shrink: 0;
    animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.3} }

/* ── Sidebar table buttons ── */
[data-testid="stSidebar"] [data-testid="stButton"] button {
    background: #f5f5f3 !important;
    border: 1px solid #e8e8e6 !important;
    color: #374151 !important;
    font-size: 0.72rem !important;
    border-radius: 6px !important;
    font-family: 'SF Mono','Fira Mono',monospace !important;
    font-weight: 500 !important;
    padding: 4px 6px !important;
    text-align: center !important;
    transition: all 0.15s !important;
    width: 100% !important;
}
[data-testid="stSidebar"] [data-testid="stButton"] button:hover {
    background: #1c1c1c !important;
    color: #ffffff !important;
    border-color: #1c1c1c !important;
}

/* ── Chat wrapper ── */
.chat-wrapper {
    max-width: 680px;
    margin: 0 auto;
    padding: 3rem 1.5rem 9rem 1.5rem;
    min-height: 100vh;
}

/* ── Welcome heading ── */
.welcome-heading {
    font-size: 1.9rem;
    font-weight: 600;
    color: #1c1c1c;
    letter-spacing: -0.6px;
    text-align: center;
    margin-bottom: 2.2rem;
    line-height: 1.2;
}

/* ── Suggestion chips ── */
.chips-row {
    display: flex; flex-wrap: wrap;
    gap: 8px; justify-content: center;
    margin-bottom: 2.5rem;
}
.chip {
    background: #ffffff;
    border: 1px solid #e8e8e6;
    border-radius: 20px;
    padding: 7px 15px;
    font-size: 0.78rem;
    color: #374151;
    cursor: default;
    transition: border-color 0.15s;
}

/* ── Messages ── */
[data-testid="stChatMessage"] {
    background: transparent !important;
    padding: 0 !important;
    margin-bottom: 1.1rem !important;
    gap: 10px !important;
}

/* User bubble — right aligned, dark */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) {
    flex-direction: row-reverse !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) [data-testid="stChatMessageAvatarUser"] {
    display: none !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) .stMarkdown {
    background: #1c1c1c !important;
    color: #f5f5f3 !important;
    border-radius: 18px 18px 4px 18px !important;
    padding: 0.7rem 1rem !important;
    max-width: 75% !important;
    margin-left: auto !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) .stMarkdown p {
    color: #f5f5f3 !important;
    font-size: 0.88rem !important;
    line-height: 1.55 !important;
}

/* Assistant bubble — left, white card */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) [data-testid="stChatMessageAvatarAssistant"] {
    display: none !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) .stMarkdown {
    background: #ffffff !important;
    border: 1px solid #e8e8e6 !important;
    border-radius: 4px 18px 18px 18px !important;
    padding: 0.7rem 1rem !important;
    max-width: 85% !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) .stMarkdown p {
    color: #1c1c1c !important;
    font-size: 0.88rem !important;
    line-height: 1.6 !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) .stMarkdown li {
    color: #1c1c1c !important;
    font-size: 0.87rem !important;
}

/* code inline */
[data-testid="stChatMessage"] code {
    font-family: 'SF Mono','Fira Mono',monospace !important;
    font-size: 0.77rem !important;
    background: #f5f5f3 !important;
    padding: 1px 5px !important;
    border-radius: 3px !important;
    color: #1c1c1c !important;
}

/* ── Kill ALL dark bars — top and bottom ── */
[data-testid="stHeader"],
[data-testid="stAppViewBlockContainer"],
header[data-testid="stHeader"] {
    background: #f5f5f3 !important;
    border-bottom: none !important;
}

/* ── Bottom bar ── */
[data-testid="stBottom"],
[data-testid="stBottom"] > div,
.stBottom {
    background: #f5f5f3 !important;
    border-top: 1px solid #e8e8e6 !important;
    padding: 1rem 0 1.2rem 0 !important;
}

/* ── Chat input ── */
[data-testid="stChatInput"],
[data-testid="stChatInput"] > div,
[data-testid="stChatInput"] > div > div {
    background: #ffffff !important;
    border: 1px solid #d6d6d3 !important;
    border-radius: 14px !important;
    box-shadow: 0 1px 4px rgba(0,0,0,0.06) !important;
    transition: border-color 0.15s, box-shadow 0.15s !important;
}
[data-testid="stChatInput"]:focus-within {
    border-color: #1c1c1c !important;
    box-shadow: 0 1px 6px rgba(0,0,0,0.1) !important;
}
[data-testid="stChatInput"] textarea,
[data-testid="stChatInput"] input {
    font-family: 'Inter', sans-serif !important;
    font-size: 0.9rem !important;
    color: #1c1c1c !important;
    background: #ffffff !important;
    caret-color: #1c1c1c !important;
}
[data-testid="stChatInput"] textarea::placeholder,
[data-testid="stChatInput"] input::placeholder { color: #b0b0a8 !important; }
[data-testid="stChatInputSubmitButton"] button {
    background: #1c1c1c !important;
    border-radius: 9px !important;
}

/* ── Disclaimer ── */
.disclaimer {
    text-align: center;
    font-size: 0.7rem;
    color: #b0b0a8;
    margin-top: 0.5rem;
}

/* ── Spinner ── */
[data-testid="stSpinner"] p {
    font-size: 0.8rem !important; color: #9ca3af !important;
}

/* ── Plotly chart card ── */
[data-testid="stPlotlyChart"] {
    background: #ffffff !important;
    border: 1px solid #e8e8e6 !important;
    border-radius: 12px !important;
    padding: 0.5rem !important;
    margin-bottom: 0.5rem !important;
}

/* ── Download buttons ── */
.dl-row { display: flex; gap: 8px; margin-top: 6px; }
[data-testid="stDownloadButton"] button {
    background: #f5f5f3 !important;
    border: 1px solid #e8e8e6 !important;
    color: #374151 !important;
    font-size: 0.75rem !important;
    border-radius: 7px !important;
    font-family: 'Inter', sans-serif !important;
    padding: 4px 12px !important;
    font-weight: 500 !important;
}
[data-testid="stDownloadButton"] button:hover {
    background: #ebebea !important;
    border-color: #d1d1ce !important;
}

/* ── Error ── */
[data-testid="stAlert"] {
    background: #fef2f2 !important;
    border: 1px solid #fecaca !important;
    border-radius: 8px !important;
    font-size: 0.8rem !important;
}

::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #d6d6d3; border-radius: 10px; }

/* ═══════════════════════════════════════
   ANIMATIONS
═══════════════════════════════════════ */

/* ── Keyframes ── */
@keyframes fadeSlideUp {
    from { opacity: 0; transform: translateY(16px); }
    to   { opacity: 1; transform: translateY(0); }
}
@keyframes fadeIn {
    from { opacity: 0; }
    to   { opacity: 1; }
}
@keyframes slideInLeft {
    from { opacity: 0; transform: translateX(-18px); }
    to   { opacity: 1; transform: translateX(0); }
}
@keyframes slideInRight {
    from { opacity: 0; transform: translateX(18px); }
    to   { opacity: 1; transform: translateX(0); }
}
@keyframes scaleIn {
    from { opacity: 0; transform: scale(0.95); }
    to   { opacity: 1; transform: scale(1); }
}
@keyframes shimmer {
    0%   { background-position: -400px 0; }
    100% { background-position: 400px 0; }
}

/* ── Welcome screen ── */
.welcome-heading {
    animation: fadeSlideUp 0.55s cubic-bezier(0.22,1,0.36,1) both;
}
.chips-row {
    animation: fadeSlideUp 0.55s 0.12s cubic-bezier(0.22,1,0.36,1) both;
}
.chip {
    transition: background 0.18s, border-color 0.18s, transform 0.18s, box-shadow 0.18s !important;
}
.chip:hover {
    background: #f5f5f3 !important;
    border-color: #c0c0bc !important;
    transform: translateY(-1px) !important;
    box-shadow: 0 3px 8px rgba(0,0,0,0.07) !important;
}

/* ── Chat messages animate in ── */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) {
    animation: slideInRight 0.35s cubic-bezier(0.22,1,0.36,1) both !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) {
    animation: slideInLeft 0.35s cubic-bezier(0.22,1,0.36,1) both !important;
}

/* ── User bubble hover ── */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) .stMarkdown {
    transition: box-shadow 0.2s !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarUser"]) .stMarkdown:hover {
    box-shadow: 0 4px 14px rgba(0,0,0,0.13) !important;
}

/* ── Assistant bubble hover ── */
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) .stMarkdown {
    transition: box-shadow 0.2s, border-color 0.2s !important;
}
[data-testid="stChatMessage"]:has([data-testid="stChatMessageAvatarAssistant"]) .stMarkdown:hover {
    box-shadow: 0 4px 14px rgba(0,0,0,0.07) !important;
    border-color: #d0d0ce !important;
}

/* ── Chart card animate in ── */
[data-testid="stPlotlyChart"] {
    animation: scaleIn 0.4s cubic-bezier(0.22,1,0.36,1) both !important;
    transition: box-shadow 0.2s !important;
}
[data-testid="stPlotlyChart"]:hover {
    box-shadow: 0 4px 16px rgba(0,0,0,0.08) !important;
}

/* ── Download buttons animate in ── */
[data-testid="stDownloadButton"] {
    animation: fadeSlideUp 0.3s 0.1s cubic-bezier(0.22,1,0.36,1) both;
}
[data-testid="stDownloadButton"] button {
    transition: background 0.15s, border-color 0.15s, transform 0.15s, box-shadow 0.15s !important;
}
[data-testid="stDownloadButton"] button:hover {
    transform: translateY(-1px) !important;
    box-shadow: 0 3px 8px rgba(0,0,0,0.08) !important;
}
[data-testid="stDownloadButton"] button:active {
    transform: translateY(0) !important;
}

/* ── Submit button pulse on hover ── */
[data-testid="stChatInputSubmitButton"] button {
    transition: transform 0.15s, box-shadow 0.15s !important;
}
[data-testid="stChatInputSubmitButton"] button:hover {
    transform: scale(1.08) !important;
    box-shadow: 0 3px 10px rgba(0,0,0,0.2) !important;
}

/* ── Sidebar brand animate in ── */
.sb-brand {
    animation: fadeIn 0.4s ease both;
}
.sb-section {
    animation: fadeSlideUp 0.4s cubic-bezier(0.22,1,0.36,1) both;
}
.sb-status {
    animation: fadeIn 0.5s 0.2s ease both;
}

/* ── Sidebar table buttons ── */
[data-testid="stSidebar"] [data-testid="stButton"] button {
    transition: background 0.15s, color 0.15s, border-color 0.15s, transform 0.15s, box-shadow 0.15s !important;
}
[data-testid="stSidebar"] [data-testid="stButton"] button:hover {
    transform: translateY(-1px) !important;
    box-shadow: 0 3px 8px rgba(0,0,0,0.1) !important;
}
[data-testid="stSidebar"] [data-testid="stButton"] button:active {
    transform: translateY(0) !important;
}

/* ── Table inspector card animate in ── */
[data-testid="stSidebar"] > div > div > div > div:last-child {
    animation: scaleIn 0.3s cubic-bezier(0.22,1,0.36,1) both;
}

/* ── Spinner dots animation ── */
[data-testid="stSpinner"] {
    animation: fadeIn 0.3s ease both !important;
}

/* ── Error alert animate in ── */
[data-testid="stAlert"] {
    animation: fadeSlideUp 0.3s cubic-bezier(0.22,1,0.36,1) both !important;
}

/* ── Smooth page load ── */
.chat-wrapper {
    animation: fadeIn 0.4s ease both;
}
</style>
""", unsafe_allow_html=True)

# ──────────────────────────────────────────────
# Chart helpers
# ──────────────────────────────────────────────
CHART_KEYWORDS = {
    "bar": ["bar chart", "bar graph", "bar plot", "show as bar", "visualize as bar"],
    "pie": ["pie chart", "pie graph", "pie plot", "show as pie", "donut chart"],
}

def detect_chart_type(question: str):
    q = question.lower()
    for chart_type, keywords in CHART_KEYWORDS.items():
        if any(kw in q for kw in keywords):
            return chart_type
    return None

def parse_db_result(result: str):
    try:
        data = ast.literal_eval(result)
        if not data or not isinstance(data, list):
            return None
        if isinstance(data[0], tuple) and len(data[0]) >= 2:
            df = pd.DataFrame(data, columns=["Label", "Value"] + [f"col{i}" for i in range(2, len(data[0]))])
            df["Value"] = pd.to_numeric(df["Value"], errors="coerce")
            return df[["Label", "Value"]].dropna()
    except Exception:
        return None
    return None

def result_to_dataframe(result: str):
    """Parse any result into a DataFrame for download."""
    try:
        data = ast.literal_eval(result)
        if not data or not isinstance(data, list):
            return None
        if isinstance(data[0], tuple):
            ncols = len(data[0])
            cols = [f"Column_{i+1}" for i in range(ncols)]
            return pd.DataFrame(data, columns=cols)
    except Exception:
        return None
    return None

def render_chart(chart_type, df, title):
    colors = ["#1c1c1c","#404040","#636363","#878787","#ababab",
              "#2d2d2d","#525252","#757575","#999999","#bcbcbc"]
    if chart_type == "bar":
        fig = px.bar(df, x="Label", y="Value", title=title,
                     color="Label", color_discrete_sequence=colors)
        fig.update_layout(
            plot_bgcolor="#ffffff", paper_bgcolor="#ffffff",
            font=dict(family="Inter, sans-serif", size=12, color="#1c1c1c"),
            title=dict(font=dict(size=13, color="#1c1c1c"), x=0),
            showlegend=False,
            xaxis=dict(showgrid=False, tickfont=dict(size=11), linecolor="#e8e8e6"),
            yaxis=dict(gridcolor="#f5f5f3", tickfont=dict(size=11)),
            margin=dict(l=10, r=10, t=40, b=10),
        )
        fig.update_traces(marker_line_width=0)
    elif chart_type == "pie":
        fig = px.pie(df, names="Label", values="Value", title=title,
                     color_discrete_sequence=colors)
        fig.update_layout(
            paper_bgcolor="#ffffff",
            font=dict(family="Inter, sans-serif", size=12, color="#1c1c1c"),
            title=dict(font=dict(size=13, color="#1c1c1c"), x=0),
            margin=dict(l=10, r=10, t=40, b=10),
            legend=dict(font=dict(size=11)),
        )
        fig.update_traces(textposition="inside", textinfo="percent+label")
    st.plotly_chart(fig, width="stretch")

def to_excel_bytes(df: pd.DataFrame) -> bytes:
    buf = io.BytesIO()
    with pd.ExcelWriter(buf, engine="openpyxl") as writer:
        df.to_excel(writer, index=False, sheet_name="Results")
    return buf.getvalue()

def to_csv_bytes(df: pd.DataFrame) -> bytes:
    return df.to_csv(index=False).encode("utf-8")

# ──────────────────────────────────────────────
# Load Chain
# ──────────────────────────────────────────────
@st.cache_resource
def load_chain():
    connection_string = (
        f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    )
    db = SQLDatabase.from_uri(connection_string)
    llm = OllamaLLM(model=OLLAMA_MODEL)
    sql_chain = create_sql_query_chain(llm, db)

    answer_prompt = PromptTemplate.from_template("""
You are a professional data assistant. Your job is to answer the user's question 
in plain English based on the database result provided.

STRICT RULES:
- Write ONLY a plain English answer. No SQL code, no code blocks, no backticks.
- Do NOT show or explain any SQL query.
- Do NOT use markdown formatting or bullet points unless listing data values.
- Be concise and direct. 1-3 sentences maximum unless listing results.

User Question: {question}
Database Result: {result}

Plain English Answer (no SQL, no code):""")

    def clean_and_run_query(query: str) -> str:
        try:
            clean = query.strip()

            # 1. Extract from ```sql ... ``` or ``` ... ``` blocks first
            if "```" in clean:
                parts = clean.split("```")
                for part in parts:
                    part = part.strip()
                    if part.lower().startswith("sql"):
                        part = part[3:].strip()
                    if part.upper().startswith(("SELECT", "SHOW", "WITH")):
                        clean = part.strip()
                        break

            # 2. If no code block, find the first SQL keyword line and take from there
            if not clean.upper().startswith(("SELECT", "SHOW", "WITH")):
                lines = clean.splitlines()
                sql_lines = []
                capturing = False
                for line in lines:
                    stripped = line.strip().upper()
                    if not capturing and any(stripped.startswith(kw) for kw in ("SELECT", "SHOW", "WITH")):
                        capturing = True
                    if capturing:
                        sql_lines.append(line)
                if sql_lines:
                    clean = "\n".join(sql_lines).strip()

            # 3. Strip any trailing non-SQL text after the semicolon
            if ";" in clean:
                clean = clean[:clean.index(";") + 1].strip()

            # Read-only guard: only allow SELECT/SHOW/WITH queries
            if not clean.upper().startswith(("SELECT", "SHOW", "WITH")):
                return "Query error: only read-only (SELECT) queries are allowed."

            result = db.run(clean)
            return result if result else "No results found."
        except Exception as e:
            return f"Query error: {e}"

    # Turns an already-computed {question, result} into a plain English answer.
    answer_chain = answer_prompt | llm | StrOutputParser()

    raw_chain = (
        RunnablePassthrough.assign(query=sql_chain)
        | RunnablePassthrough.assign(result=lambda x: clean_and_run_query(x["query"]))
    )

    return answer_chain, raw_chain, db

# ──────────────────────────────────────────────
# Load Resources
# ──────────────────────────────────────────────
try:
    answer_chain, raw_chain, db = load_chain()
    tables = db.get_usable_table_names()
    connected = True
except Exception as e:
    connected = False
    conn_error = str(e)

# ──────────────────────────────────────────────
# Sidebar
# ──────────────────────────────────────────────
with st.sidebar:
    st.markdown("""
    <div class="sb-brand">
        <div class="sb-brand-dot">🗄</div>
        <div>
            <div class="sb-brand-name">Query Assistant</div>
            <div class="sb-brand-sub">Natural Language → SQL</div>
        </div>
    </div>
    """, unsafe_allow_html=True)

    if connected:
        st.markdown(f"""
        <div class="sb-section">
            <div class="sb-label">Connection</div>
            <div class="sb-row">
                <span class="sb-key">Database</span>
                <span class="sb-val">{DB_NAME}</span>
            </div>
            <div class="sb-row">
                <span class="sb-key">Host</span>
                <span class="sb-val">{DB_HOST}</span>
            </div>
            <div class="sb-row">
                <span class="sb-key">Engine</span>
                <span class="sb-val">MySQL</span>
            </div>
        </div>
        <div class="sb-section">
            <div class="sb-label">AI Model</div>
            <div class="sb-row">
                <span class="sb-key">Model</span>
                <span class="sb-val">{OLLAMA_MODEL}</span>
            </div>
            <div class="sb-row">
                <span class="sb-key">Runtime</span>
                <span class="sb-val">Ollama · Local</span>
            </div>
        </div>
        <div class="sb-section">
            <div class="sb-label">Tables ({len(tables)}) — click to inspect</div>
        </div>
        """, unsafe_allow_html=True)

        # Clickable table buttons
        if "selected_table" not in st.session_state:
            st.session_state.selected_table = None

        # Render table buttons in a grid-like layout
        cols_per_row = 2
        table_rows = [tables[i:i+cols_per_row] for i in range(0, len(tables), cols_per_row)]
        for row in table_rows:
            btn_cols = st.columns(len(row))
            for i, tbl in enumerate(row):
                with btn_cols[i]:
                    if st.button(tbl, key=f"tbl_{tbl}"):
                        st.session_state.selected_table = tbl

        st.markdown(f"""
        <div class="sb-status">
            <div class="sb-dot"></div>
            Connected · {len(tables)} tables
        </div>
        """, unsafe_allow_html=True)

    st.markdown("<br>", unsafe_allow_html=True)
    if st.button("🗑 Clear conversation", width="stretch"):
        st.session_state.messages = []
        st.rerun()

# ──────────────────────────────────────────────
# Table Inspector Modal
# ──────────────────────────────────────────────
if st.session_state.get("selected_table") and connected:
    tbl = st.session_state.selected_table

    @st.cache_data
    def get_table_info(table_name):
        try:
            # Get column info
            result = db._engine.connect().execute(
                __import__("sqlalchemy").text(f"DESCRIBE `{table_name}`")
            )
            rows = result.fetchall()
            df = pd.DataFrame(rows, columns=["Field", "Type", "Null", "Key", "Default", "Extra"])
            # Get row count
            cnt = db._engine.connect().execute(
                __import__("sqlalchemy").text(f"SELECT COUNT(*) FROM `{table_name}`")
            ).scalar()
            return df, cnt
        except Exception as e:
            return None, str(e)

    col_df, row_count = get_table_info(tbl)

    with st.sidebar:
        st.markdown(f"""
        <div style="
            background:#ffffff;
            border:1px solid #e8e8e6;
            border-radius:10px;
            padding:1rem;
            margin-top:0.5rem;
        ">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.8rem;">
                <div style="font-size:0.82rem;font-weight:600;color:#1c1c1c;">📋 {tbl}</div>
                <div style="font-size:0.7rem;color:#9ca3af;">{row_count} rows</div>
            </div>
        """, unsafe_allow_html=True)

        if col_df is not None:
            for _, row in col_df.iterrows():
                key_badge = ""
                if row["Key"] == "PRI":
                    key_badge = '<span style="font-size:0.6rem;background:#fef3c7;color:#92400e;padding:1px 5px;border-radius:3px;margin-left:4px;">PK</span>'
                elif row["Key"] == "MUL":
                    key_badge = '<span style="font-size:0.6rem;background:#ede9fe;color:#5b21b6;padding:1px 5px;border-radius:3px;margin-left:4px;">FK</span>'
                null_badge = "" if row["Null"] == "NO" else '<span style="font-size:0.6rem;background:#f3f4f6;color:#6b7280;padding:1px 5px;border-radius:3px;margin-left:4px;">null</span>'
                st.markdown(f"""
                <div style="display:flex;justify-content:space-between;align-items:center;
                    padding:5px 0;border-bottom:1px solid #f0f0ee;">
                    <div style="font-size:0.75rem;color:#1c1c1c;font-weight:500;flex:1;">
                        {row['Field']}{key_badge}{null_badge}
                    </div>
                    <div style="font-family:monospace;font-size:0.68rem;color:#6b7280;flex-shrink:0;margin-left:8px;">
                        {row['Type']}
                    </div>
                </div>
                """, unsafe_allow_html=True)
        else:
            st.error(f"Could not load columns: {row_count}")

        st.markdown("</div>", unsafe_allow_html=True)
        if st.button("✕ Close", key="close_inspector"):
            st.session_state.selected_table = None
            st.rerun()

# ──────────────────────────────────────────────
# Main Chat Area
# ──────────────────────────────────────────────
st.markdown('<div class="chat-wrapper">', unsafe_allow_html=True)

if not connected:
    st.error(f"Connection failed: {conn_error}")
    st.stop()

# Init state
if "messages" not in st.session_state:
    st.session_state.messages = []

# Welcome screen
if not st.session_state.messages:
    st.markdown(f"""
    <div class="welcome-heading">What would you like to know<br>about <em>{DB_NAME}</em>?</div>
    <div class="chips-row">
        <span class="chip">How many records in each table?</span>
        <span class="chip">Show top 5 by value</span>
        <span class="chip">Bar chart of counts by category</span>
        <span class="chip">What is the total revenue?</span>
    </div>
    """, unsafe_allow_html=True)

# Render message history
for msg in st.session_state.messages:
    with st.chat_message(msg["role"]):
        # Re-render charts
        if msg.get("chart_type") and msg.get("chart_data"):
            df_chart = pd.DataFrame(msg["chart_data"])
            render_chart(msg["chart_type"], df_chart, msg.get("chart_title", ""))

        st.markdown(msg["content"])

        # Re-render download buttons for messages that had data
        if msg.get("raw_result") and msg["role"] == "assistant":
            df_dl = result_to_dataframe(msg["raw_result"])
            if df_dl is not None:
                col1, col2 = st.columns([1, 1], gap="small")
                with col1:
                    st.download_button(
                        "⬇ Download CSV",
                        data=to_csv_bytes(df_dl),
                        file_name="query_results.csv",
                        mime="text/csv",
                        key=f"csv_{msg.get('msg_id', id(msg))}",
                        width="stretch"
                    )
                with col2:
                    st.download_button(
                        "⬇ Download Excel",
                        data=to_excel_bytes(df_dl),
                        file_name="query_results.xlsx",
                        mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        key=f"xlsx_{msg.get('msg_id', id(msg))}",
                        width="stretch"
                    )

# ── Chat input ──
if prompt := st.chat_input("Ask anything about your data..."):
    st.session_state.messages.append({"role": "user", "content": prompt})
    with st.chat_message("user"):
        st.markdown(prompt)

    with st.chat_message("assistant"):
        with st.spinner("Thinking..."):
            try:
                chart_type = detect_chart_type(prompt)
                msg_id = len(st.session_state.messages)
                MAX_RETRIES = 3

                # ── Retry prompts — each attempt rephrases the question differently
                def make_retry_prompt(original: str, attempt: int) -> str:
                    if attempt == 1:
                        return (
                            f"Rewrite this as a simpler SQL query using only basic SELECT, "
                            f"FROM, JOIN and WHERE. Question: {original}"
                        )
                    elif attempt == 2:
                        return (
                            f"Try a different approach. Use aggregation (COUNT, SUM, AVG) "
                            f"and GROUP BY if needed. Question: {original}"
                        )
                    return original

                def is_bad_result(result: str) -> bool:
                    lowered = result.lower()
                    return (
                        not result.strip()
                        or result.strip() == "[]"
                        or "query error" in lowered
                        or "no results found" in lowered
                        or "error" in lowered[:30]
                    )

                raw_result = ""
                raw = None
                attempt = 0
                success = False

                while attempt < MAX_RETRIES:
                    if attempt == 0:
                        query_prompt = prompt
                        spinner_msg = "Thinking..."
                    else:
                        query_prompt = make_retry_prompt(prompt, attempt)
                        spinner_msg = f"Retrying with a different query (attempt {attempt + 1}/{MAX_RETRIES})..."

                    with st.spinner(spinner_msg):
                        try:
                            raw = raw_chain.invoke({"question": query_prompt})
                            raw_result = raw.get("result", "")
                            if not is_bad_result(raw_result):
                                success = True
                                break
                        except Exception:
                            pass
                    attempt += 1

                # Get text answer, reusing the result we already fetched (no extra SQL gen)
                answer = answer_chain.invoke({"question": prompt, "result": raw_result})

                # Render chart if requested
                chart_data = None
                if chart_type:
                    df_chart = parse_db_result(raw_result)
                    if df_chart is not None and not df_chart.empty:
                        render_chart(chart_type, df_chart, prompt.capitalize())
                        chart_data = df_chart.to_dict()
                    else:
                        st.caption("⚠️ Could not render chart — result format not suitable for visualization.")

                st.markdown(answer)

                if attempt > 1 and success:
                    st.caption(f"✓ Succeeded on attempt {attempt + 1} with a rephrased query.")
                elif not success:
                    st.caption("⚠️ All retry attempts exhausted. The answer above is based on the best available result.")

                # Download buttons
                df_dl = result_to_dataframe(raw_result)
                if df_dl is not None:
                    col1, col2 = st.columns([1, 1], gap="small")
                    with col1:
                        st.download_button(
                            "⬇ Download CSV",
                            data=to_csv_bytes(df_dl),
                            file_name="query_results.csv",
                            mime="text/csv",
                            key=f"csv_new_{msg_id}",
                            width="stretch"
                        )
                    with col2:
                        st.download_button(
                            "⬇ Download Excel",
                            data=to_excel_bytes(df_dl),
                            file_name="query_results.xlsx",
                            mime="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            key=f"xlsx_new_{msg_id}",
                            width="stretch"
                        )

                # Save to history
                hist_entry = {
                    "role": "assistant",
                    "content": answer,
                    "raw_result": raw_result,
                    "msg_id": msg_id,
                }
                if chart_data:
                    hist_entry["chart_type"] = chart_type
                    hist_entry["chart_data"] = chart_data
                    hist_entry["chart_title"] = prompt.capitalize()
                st.session_state.messages.append(hist_entry)

            except Exception as e:
                err = f"Error: {e}"
                st.error(err)
                st.session_state.messages.append({"role": "assistant", "content": err})

st.markdown("""
<div class="disclaimer">Query Assistant can make mistakes. Please verify important results.</div>
""", unsafe_allow_html=True)

st.markdown('</div>', unsafe_allow_html=True)
