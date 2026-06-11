from sqlalchemy.orm import Session

from app.models.core import DeclineCode, RetryStrategyEnum


def get_retry_strategy(db: Session, unified_code: str) -> RetryStrategyEnum:
    decline = db.get(DeclineCode, unified_code)
    if decline:
        return decline.default_retry_strategy
    return RetryStrategyEnum.none


def is_recoverable(db: Session, unified_code: str) -> bool:
    decline = db.get(DeclineCode, unified_code)
    return decline.recoverable if decline else False


def get_category(db: Session, unified_code: str) -> str:
    decline = db.get(DeclineCode, unified_code)
    return decline.category.value if decline else "UNKNOWN"
