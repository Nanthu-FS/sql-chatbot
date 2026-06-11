from sqlalchemy.orm import Session

from app.models.core import GatewayCodeMapping


def normalize_gateway_code(db: Session, gateway: str, gateway_code: str) -> str:
    """Map a raw gateway code to a unified decline code. Falls back to UNKNOWN."""
    mapping = (
        db.query(GatewayCodeMapping)
        .filter_by(gateway=gateway, gateway_code=gateway_code)
        .first()
    )
    return mapping.unified_code if mapping else "UNKNOWN"
