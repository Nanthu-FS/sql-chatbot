"""Run: python -m app.seed.seed"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(__file__))))

from app.db import engine, SessionLocal
from app.models.core import Base, DeclineCode, GatewayCodeMapping
from app.seed.taxonomy import DECLINE_CODES, GATEWAY_CODE_MAPPINGS


def seed():
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        for code_data in DECLINE_CODES:
            existing = db.get(DeclineCode, code_data["code"])
            if not existing:
                db.add(DeclineCode(**code_data))

        db.flush()

        for mapping_data in GATEWAY_CODE_MAPPINGS:
            existing = (
                db.query(GatewayCodeMapping)
                .filter_by(gateway=mapping_data["gateway"], gateway_code=mapping_data["gateway_code"])
                .first()
            )
            if not existing:
                db.add(GatewayCodeMapping(**mapping_data))

        db.commit()
        print(f"Seeded {len(DECLINE_CODES)} decline codes and {len(GATEWAY_CODE_MAPPINGS)} gateway mappings.")
    finally:
        db.close()


if __name__ == "__main__":
    seed()
