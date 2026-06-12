import requests
from typing import Optional

GAMMA_BASE = "https://gamma-api.polymarket.com"
CLOB_BASE  = "https://clob.polymarket.com"

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
            raise RuntimeError(f"Failed to fetch markets: {e}")

        # Build keyword set for category filtering
        keywords: set[str] = set()
        if categories:
            for cat in categories:
                keywords.update(k.lower() for k in _CATEGORY_KEYWORDS.get(cat, []))

        markets = []
        for m in data:
            try:
                vol = float(m.get("volume") or 0)
                if vol < min_volume:
                    continue

                # Category filter: check question + tag labels
                if keywords:
                    question_lower = (m.get("question") or "").lower()
                    tag_labels = " ".join(t.get("label", "") for t in (m.get("tags") or [])).lower()
                    combined = question_lower + " " + tag_labels
                    if not any(kw in combined for kw in keywords):
                        continue

                tokens     = m.get("tokens") or []
                yes_token  = next((t for t in tokens if (t.get("outcome") or "").upper() == "YES"), None)
                no_token   = next((t for t in tokens if (t.get("outcome") or "").upper() == "NO"), None)

                markets.append({
                    "market_id":    m.get("conditionId") or m.get("id") or "",
                    "question":     m.get("question") or "",
                    "category":     m.get("category") or "",
                    "description":  (m.get("description") or "")[:500],
                    "volume_usd":   vol,
                    "end_date":     m.get("endDate") or "",
                    "tags":         [t.get("label", "") for t in (m.get("tags") or [])],
                    "yes_token_id": (yes_token or {}).get("token_id") or "",
                    "no_token_id":  (no_token  or {}).get("token_id") or "",
                    "yes_price":    float((yes_token or {}).get("price") or 0.5),
                    "no_price":     float((no_token  or {}).get("price") or 0.5),
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
            return float(resp.json().get("price", 0.5))
        except Exception:
            return None

    def get_resolved_markets(self, limit: int = 50) -> list[dict]:
        """Resolved markets for backtesting."""
        try:
            data = self._get("/markets", {"closed": "true", "limit": limit, "order": "volume", "ascending": "false"})
        except Exception as e:
            raise RuntimeError(f"Failed to fetch resolved markets: {e}")

        markets = []
        for m in data:
            try:
                tokens    = m.get("tokens") or []
                yes_token = next((t for t in tokens if (t.get("outcome") or "").upper() == "YES"), None)
                markets.append({
                    "market_id":          m.get("conditionId") or m.get("id") or "",
                    "question":           m.get("question") or "",
                    "category":           m.get("category") or "",
                    "volume_usd":         float(m.get("volume") or 0),
                    "yes_price_at_open":  float((yes_token or {}).get("price") or 0.5),
                    "resolved_outcome":   m.get("resolvedOutcome") or "",
                    "end_date":           m.get("endDate") or "",
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
            raise RuntimeError(f"Polymarket client init failed: {e}")

    def get_balance(self) -> float:
        try:
            result = self._clob.get_balance()
            if isinstance(result, dict):
                return float(result.get("balance", 0))
            return float(result)
        except Exception as e:
            raise RuntimeError(f"get_balance failed: {e}")

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
            raise RuntimeError(f"Order placement failed: {e}")

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
