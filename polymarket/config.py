import logging
import os
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

POLY_PRIVATE_KEY     = os.getenv("POLY_PRIVATE_KEY", "")
POLY_API_KEY         = os.getenv("POLY_API_KEY", "")
POLY_API_SECRET      = os.getenv("POLY_API_SECRET", "")
POLY_API_PASSPHRASE  = os.getenv("POLY_API_PASSPHRASE", "")

POLYMARKET_MODEL = os.getenv("POLYMARKET_MODEL", "qwen2.5:14b")

# Validate OLLAMA_HOST at startup (VULN-08)
from polymarket.security import validate_ollama_host  # noqa: E402
_raw_host = os.getenv("OLLAMA_HOST", "http://localhost:11434")
try:
    OLLAMA_HOST = validate_ollama_host(_raw_host)
except ValueError as exc:
    logger.error("Invalid OLLAMA_HOST %r — falling back to localhost: %s", _raw_host, exc)
    OLLAMA_HOST = "http://localhost:11434"

EMAIL_SMTP_HOST  = os.getenv("EMAIL_SMTP_HOST", "smtp.gmail.com")
EMAIL_SMTP_PORT  = int(os.getenv("EMAIL_SMTP_PORT", "587"))
EMAIL_USER       = os.getenv("EMAIL_USER", "")
EMAIL_PASSWORD   = os.getenv("EMAIL_PASSWORD", "")
EMAIL_RECIPIENT  = os.getenv("EMAIL_RECIPIENT", "")

MIN_VOLUME_USD       = float(os.getenv("MIN_VOLUME_USD", "100000"))
MAX_OPEN_POSITIONS   = int(os.getenv("MAX_OPEN_POSITIONS", "5"))
MAX_RISK_PER_TRADE   = float(os.getenv("MAX_RISK_PER_TRADE", "0.01"))
CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.90"))
DAILY_LOSS_LIMIT     = float(os.getenv("DAILY_LOSS_LIMIT", "0.03"))
CATEGORIES           = [c.strip() for c in os.getenv("POLY_CATEGORIES", "Politics,World Events").split(",")]
