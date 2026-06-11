import enum
from datetime import datetime
from typing import Any

from sqlalchemy import (
    Boolean,
    DateTime,
    Enum,
    ForeignKey,
    Integer,
    JSON,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class GatewayEnum(str, enum.Enum):
    stripe = "stripe"
    razorpay = "razorpay"
    mock = "mock"


class PaymentStatusEnum(str, enum.Enum):
    failed = "failed"
    recovered = "recovered"
    abandoned = "abandoned"
    succeeded = "succeeded"


class RetryStrategyEnum(str, enum.Enum):
    none = "none"
    immediate = "immediate"
    scheduled = "scheduled"
    payday = "payday"
    prompt_customer = "prompt_customer"


class RetryOutcomeEnum(str, enum.Enum):
    pending = "pending"
    succeeded = "succeeded"
    failed = "failed"
    cancelled = "cancelled"


class CategoryEnum(str, enum.Enum):
    RECOVERABLE = "RECOVERABLE"
    HARD_DECLINE = "HARD_DECLINE"
    TECHNICAL = "TECHNICAL"
    FRAUD_RISK = "FRAUD_RISK"
    CUSTOMER_ACTION = "CUSTOMER_ACTION"
    UNKNOWN = "UNKNOWN"


class Customer(Base):
    __tablename__ = "customers"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    external_customer_id: Mapped[str] = mapped_column(String, unique=True, index=True)
    email: Mapped[str] = mapped_column(String)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=func.now())

    payments: Mapped[list["Payment"]] = relationship("Payment", back_populates="customer")


class DeclineCode(Base):
    __tablename__ = "decline_codes"

    code: Mapped[str] = mapped_column(String, primary_key=True)
    category: Mapped[CategoryEnum] = mapped_column(Enum(CategoryEnum))
    description: Mapped[str] = mapped_column(String)
    recoverable: Mapped[bool] = mapped_column(Boolean)
    default_retry_strategy: Mapped[RetryStrategyEnum] = mapped_column(Enum(RetryStrategyEnum))

    mappings: Mapped[list["GatewayCodeMapping"]] = relationship("GatewayCodeMapping", back_populates="decline_code")
    failures: Mapped[list["PaymentFailure"]] = relationship("PaymentFailure", back_populates="decline_code_rel")


class GatewayCodeMapping(Base):
    __tablename__ = "gateway_code_mappings"
    __table_args__ = (UniqueConstraint("gateway", "gateway_code"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    gateway: Mapped[str] = mapped_column(String)
    gateway_code: Mapped[str] = mapped_column(String)
    unified_code: Mapped[str] = mapped_column(String, ForeignKey("decline_codes.code"))

    decline_code: Mapped["DeclineCode"] = relationship("DeclineCode", back_populates="mappings")


class PaymentEvent(Base):
    __tablename__ = "payment_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    event_id: Mapped[str] = mapped_column(String, unique=True, index=True)
    gateway: Mapped[str] = mapped_column(String)
    raw_payload: Mapped[dict[str, Any]] = mapped_column(JSON)
    received_at: Mapped[datetime] = mapped_column(DateTime, default=func.now())

    failures: Mapped[list["PaymentFailure"]] = relationship("PaymentFailure", back_populates="event")


class Payment(Base):
    __tablename__ = "payments"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    external_payment_id: Mapped[str] = mapped_column(String, index=True)
    gateway: Mapped[str] = mapped_column(String)
    amount: Mapped[int] = mapped_column(Integer)
    currency: Mapped[str] = mapped_column(String, default="INR")
    customer_id: Mapped[int | None] = mapped_column(Integer, ForeignKey("customers.id"), nullable=True)
    status: Mapped[PaymentStatusEnum] = mapped_column(Enum(PaymentStatusEnum), default=PaymentStatusEnum.failed)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=func.now(), onupdate=func.now())

    customer: Mapped["Customer | None"] = relationship("Customer", back_populates="payments")
    failures: Mapped[list["PaymentFailure"]] = relationship("PaymentFailure", back_populates="payment")
    retry_attempts: Mapped[list["RetryAttempt"]] = relationship("RetryAttempt", back_populates="payment")


class PaymentFailure(Base):
    __tablename__ = "payment_failures"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    payment_id: Mapped[int] = mapped_column(Integer, ForeignKey("payments.id"))
    event_id: Mapped[int] = mapped_column(Integer, ForeignKey("payment_events.id"))
    gateway_code: Mapped[str] = mapped_column(String)
    unified_code: Mapped[str] = mapped_column(String, ForeignKey("decline_codes.code"))
    occurred_at: Mapped[datetime] = mapped_column(DateTime, default=func.now())
    attempt_number: Mapped[int] = mapped_column(Integer, default=1)

    payment: Mapped["Payment"] = relationship("Payment", back_populates="failures")
    event: Mapped["PaymentEvent"] = relationship("PaymentEvent", back_populates="failures")
    decline_code_rel: Mapped["DeclineCode"] = relationship("DeclineCode", back_populates="failures")


class RetryAttempt(Base):
    __tablename__ = "retry_attempts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    payment_id: Mapped[int] = mapped_column(Integer, ForeignKey("payments.id"))
    scheduled_for: Mapped[datetime] = mapped_column(DateTime)
    executed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    outcome: Mapped[RetryOutcomeEnum] = mapped_column(Enum(RetryOutcomeEnum), default=RetryOutcomeEnum.pending)
    strategy_used: Mapped[str] = mapped_column(String)

    payment: Mapped["Payment"] = relationship("Payment", back_populates="retry_attempts")
