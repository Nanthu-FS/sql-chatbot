"""
Shared security utilities used across the Polymarket bot.
All functions in this module are pure helpers with no side effects.
"""
import html
import logging
from urllib.parse import urlparse

logger = logging.getLogger(__name__)

_SAFE_URL_SCHEMES = frozenset({"http", "https"})

# Hard caps applied before injecting external data into LLM prompts
MAX_NEWS_TITLE_LEN   = 200
MAX_NEWS_BODY_LEN    = 300
MAX_QUESTION_LEN     = 300
MAX_CATEGORY_LEN     = 50
MAX_MARKETS_PER_CALL = 20   # Context-window safety cap


def safe_url(url: str) -> str:
    """Return url only when scheme is http/https; return '#' otherwise.

    Prevents javascript: / data: URIs from being rendered as clickable links
    in Streamlit markdown (VULN-04).
    """
    try:
        parsed = urlparse(str(url).strip())
        if parsed.scheme.lower() in _SAFE_URL_SCHEMES and parsed.netloc:
            return url
    except Exception:
        pass
    return "#"


def sanitize_for_prompt(text: str, max_len: int = MAX_NEWS_BODY_LEN) -> str:
    """Truncate and strip control characters before embedding in an LLM prompt.

    Reduces prompt-injection surface by removing null bytes, and limits the
    amount of attacker-controlled text that can reach the model (VULN-01).
    """
    if not isinstance(text, str):
        text = str(text)
    # Strip null bytes and most control chars; keep newline/tab for readability
    text = "".join(ch for ch in text if ord(ch) >= 32 or ch in "\n\t")
    return text[:max_len]


def escape_for_markdown(text: str) -> str:
    """HTML-escape text before passing to st.markdown() to prevent XSS (VULN-16)."""
    return html.escape(str(text))


def sanitize_email_field(text: str, max_len: int = 500) -> str:
    """Strip CRLF sequences to prevent email header injection (VULN-12).

    Also truncates to prevent excessively large email bodies.
    """
    if not isinstance(text, str):
        text = str(text)
    text = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    return text[:max_len]


def validate_ollama_host(host: str) -> str:
    """Validate OLLAMA_HOST at startup; warn if non-local (VULN-08).

    Raises ValueError for clearly invalid values.
    Returns the validated host string.
    """
    try:
        parsed = urlparse(host.strip())
        if parsed.scheme not in ("http", "https"):
            raise ValueError(
                f"OLLAMA_HOST must use http or https scheme, got: {parsed.scheme!r}"
            )
        if not parsed.netloc:
            raise ValueError(f"OLLAMA_HOST has no host: {host!r}")
        hostname = (parsed.hostname or "").lower()
        _local = {"localhost", "127.0.0.1", "::1"}
        if hostname not in _local and not hostname.startswith("192.168."):
            logger.warning(
                "OLLAMA_HOST points to a non-local address (%s). "
                "A compromised remote Ollama server can return arbitrary trade "
                "recommendations. Ensure this host is fully trusted.", hostname
            )
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError(f"Invalid OLLAMA_HOST {host!r}: {exc}") from exc
    return host
