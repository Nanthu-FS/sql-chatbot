import logging
import requests
from typing import Optional

logger = logging.getLogger(__name__)

GAMMA_BASE = "https://gamma-api.polymarket.com"
CLOB_BASE  = "https://clob.polymarket.com"

# Hard caps for API response field lengths (VULN-07)
_MAX_ID       = 128
_MAX_QUESTION = 500
_MAX_DESC     = 500
_MAX_TOKEN_ID = 128
_MAX_CATEGORY = 100
_MAX_DATE     = 64
_MAX_LABEL    = 100


def _s(val, max_len: int) -> str:
    """Coerce to string and truncate — sanitizes external API data (VULN-07)."""
    return str(val or "")[:max_len]


def _f(val, default: float = 0.0) -> float:
    """Safe float conversion with fallback."""
    try:
        return float(val)
    except (TypeError, ValueError):
        return default


# Rough keyword mapping used to filter by category via tags
_CATEGORY_KEYWORDS = {
    "Politics":      ["politics", "election", "government", "congress", "president", "senate", "vote"],
    "World Events":  ["world", "news", "geopolitics", "war", "conflict", "international"],
    "Crypto":        ["crypto", "bitcoin", "ethereum", "defi", "nft", "blockchain"],
    "Sports":        ["sports", "nba", "nfl", "soccer", "football", "basketball", "tennis"],
}


class GammaClient:
    """Public Polymarket market-data client — no authentication required."""

    def __init__(self):
        self._s = requests.Session()
        self._s.headers["User-Agent"] = "PolymarketBot/1.0"

    def _get(self, path: str, params: dict = None, timeout: int = 20) -> dict | list:
        resp = self._s.get(f"{GAMMA_BASE}{path}", params=params, timeout=timeout)
        resp.raise_for_status()
        return resp.json()

    def get_markets(
        self,
        min_volume: float = 100_000,
        categories: list[str] = None,
        limit: int = 100,
    ) -> list[dict]:
        params = {
            "active":    "true",
            "closed":    "false",
            "limit":     limit,
            "order":     "volume",
            "ascending": "false",
        }
        try:
            data = self._get("/markets", params)
        except Exception as e:
            logger.error("get_markets failed: %s", e, exc_info=True)
            raise RuntimeError("Failed to fetch markets — check logs for details.")

        keywords: set[str] = set()
        if categories:
            for cat in categories:
                keywords.update(k.lower() for k in _CATEGORY_KEYWORDS.get(cat, []))

        markets = []
        for m in data:
            try:
                vol = _f(m.get("volume"))
                if vol < min_volume:
                    continue

                if keywords:
                    question_lower = _s(m.get("question"), _MAX_QUESTION).lower()
                    tag_labels = " ".join(
                        _s(t.get("label"), _MAX_LABEL) for t in (m.get("tags") or [])
                    ).lower()
                    combined = question_lower + " " + tag_labels
                    if not any(kw in combined for kw in keywords):
                        continue

                tokens    = m.get("tokens") or []
                yes_token = next((t for t in tokens if _s(t.get("outcome"), 8).upper() == "YES"), None)
                no_token  = next((t for t in tokens if _s(t.get("outcome"), 8).upper() == "NO"),  None)

                markets.append({
                    "market_id":    _s(m.get("conditionId") or m.get("id"), _MAX_ID),
                    "question":     _s(m.get("question"), _MAX_QUESTION),
                    "category":     _s(m.get("category"), _MAX_CATEGORY),
                    "description":  _s(m.get("description"), _MAX_DESC),
                    "volume_usd":   vol,
                    "end_date":     _s(m.get("endDate"), _MAX_DATE),
                    "tags":         [_s(t.get("label"), _MAX_LABEL) for t in (m.get("tags") or [])],
                    "yes_token_id": _s((yes_token or {}).get("token_id"), _MAX_TOKEN_ID),
                    "no_token_id":  _s((no_token  or {}).get("token_id"), _MAX_TOKEN_ID),
                    "yes_price":    _f((yes_token or {}).get("price"), 0.5),
                    "no_price":     _f((no_token  or {}).get("price"), 0.5),
                })
            except (ValueError, TypeError):
                continue

        return markets

    def get_market_price(self, token_id: str) -> Optional[float]:
        """Best-ask price for a YES token."""
        try:
            resp = self._s.get(
                f"{CLOB_BASE}/price",
                params={"token_id": token_id, "side": "BUY"},
                timeout=10,
            )
            resp.raise_for_status()
            return _f(resp.json().get("price"), 0.5)
        except Exception:
            return None

    def get_resolved_markets(self, limit: int = 50) -> list[dict]:
        """Resolved markets for backtesting."""
        try:
            data = self._get("/markets", {
                "closed": "true", "limit": limit, "order": "volume", "ascending": "false"
            })
        except Exception as e:
            logger.error("get_resolved_markets failed: %s", e, exc_info=True)
            raise RuntimeError("Failed to fetch resolved markets — check logs for details.")

        markets = []
        for m in data:
            try:
                tokens    = m.get("tokens") or []
                yes_token = next((t for t in tokens if _s(t.get("outcome"), 8).upper() == "YES"), None)
                markets.append({
                    "market_id":         _s(m.get("conditionId") or m.get("id"), _MAX_ID),
                    "question":          _s(m.get("question"), _MAX_QUESTION),
                    "category":          _s(m.get("category"), _MAX_CATEGORY),
                    "volume_usd":        _f(m.get("volume")),
                    "yes_price_at_open": _f((yes_token or {}).get("price"), 0.5),
                    "resolved_outcome":  _s(m.get("resolvedOutcome"), 8),
                    "end_date":          _s(m.get("endDate"), _MAX_DATE),
                })
            except (ValueError, TypeError):
                continue

        return markets


class ClobClient:
    """Authenticated Polymarket trading client wrapping py-clob-client."""

    def __init__(self, private_key: str, api_key: str, api_secret: str, api_passphrase: str):
        try:
            from py_clob_client.client import ClobClient as _Clob
            from py_clob_client.constants import POLYGON
            from py_clob_client.clob_types import ApiCreds

            self._clob = _Clob(
                host=CLOB_BASE,
                key=private_key,
                chain_id=POLYGON,
                creds=ApiCreds(
                    api_key=api_key,
                    api_secret=api_secret,
                    api_passphrase=api_passphrase,
                ),
            )
        except ImportError:
            raise RuntimeError("py-clob-client not installed. Run: pip install py-clob-client")
        except Exception as e:
            # Log full detail but never surface key material to the caller (VULN-03)
            logger.error("ClobClient init failed: %s", e, exc_info=True)
            raise RuntimeError("Polymarket client init failed — check logs for details.")

    def get_balance(self) -> float:
        try:
            result = self._clob.get_balance()
            if isinstance(result, dict):
                return float(result.get("balance", 0))
            return float(result)
        except Exception as e:
            logger.error("get_balance failed: %s", e, exc_info=True)
            raise RuntimeError("Balance lookup failed — check logs for details.")

    def get_open_orders(self) -> list[dict]:
        try:
            return list(self._clob.get_orders() or [])
        except Exception:
            return []

    def place_order(self, token_id: str, size: float, price: float) -> dict:
        """GTC limit-buy order for a YES token."""
        try:
            from py_clob_client.clob_types import OrderArgs
            from py_clob_client.order_builder.constants import BUY

            args = OrderArgs(
                token_id=token_id,
                price=round(price, 4),
                size=round(size, 2),
                side=BUY,
            )
            signed = self._clob.create_order(args)
            return self._clob.post_order(signed) or {}
        except Exception as e:
            # Full exception (potentially containing signing details) goes only to logs (VULN-03)
            logger.error("place_order failed: %s", e, exc_info=True)
            raise RuntimeError("Order placement failed — check logs for details.")

    def cancel_order(self, order_id: str) -> bool:
        try:
            self._clob.cancel(order_id)
            return True
        except Exception:
            return False

    def get_positions(self) -> list[dict]:
        try:
            return list(self._clob.get_positions() or [])
        except Exception:
            return []
