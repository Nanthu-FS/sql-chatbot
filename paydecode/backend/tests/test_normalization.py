import pytest
from app.services.normalization import normalize_gateway_code


def test_stripe_insufficient_funds(db_session):
    assert normalize_gateway_code(db_session, "stripe", "insufficient_funds") == "INSUFFICIENT_FUNDS"


def test_stripe_expired_card(db_session):
    assert normalize_gateway_code(db_session, "stripe", "expired_card") == "EXPIRED_CARD"


def test_stripe_fraudulent(db_session):
    assert normalize_gateway_code(db_session, "stripe", "fraudulent") == "BANK_FRAUD_BLOCK"


def test_razorpay_insufficient_funds(db_session):
    assert normalize_gateway_code(db_session, "razorpay", "BAD_REQUEST_ERROR/insufficient_funds") == "INSUFFICIENT_FUNDS"


def test_unknown_code_falls_back(db_session):
    assert normalize_gateway_code(db_session, "stripe", "totally_made_up_code") == "UNKNOWN"


def test_unknown_gateway_falls_back(db_session):
    assert normalize_gateway_code(db_session, "unknown_gateway", "insufficient_funds") == "UNKNOWN"
