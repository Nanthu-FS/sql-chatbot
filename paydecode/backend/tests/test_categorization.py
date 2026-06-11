from app.services.categorization import get_category, get_retry_strategy, is_recoverable


def test_insufficient_funds_is_recoverable(db_session):
    assert is_recoverable(db_session, "INSUFFICIENT_FUNDS") is True


def test_stolen_card_not_recoverable(db_session):
    assert is_recoverable(db_session, "STOLEN_CARD") is False


def test_insufficient_funds_strategy(db_session):
    assert get_retry_strategy(db_session, "INSUFFICIENT_FUNDS").value == "payday"


def test_technical_strategy(db_session):
    assert get_retry_strategy(db_session, "GATEWAY_TIMEOUT").value == "immediate"


def test_expired_card_strategy(db_session):
    assert get_retry_strategy(db_session, "EXPIRED_CARD").value == "prompt_customer"


def test_stolen_card_strategy(db_session):
    assert get_retry_strategy(db_session, "STOLEN_CARD").value == "none"


def test_category_recoverable(db_session):
    assert get_category(db_session, "INSUFFICIENT_FUNDS") == "RECOVERABLE"


def test_category_hard_decline(db_session):
    assert get_category(db_session, "STOLEN_CARD") == "HARD_DECLINE"


def test_category_unknown(db_session):
    assert get_category(db_session, "TOTALLY_FAKE") == "UNKNOWN"
