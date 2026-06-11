from pydantic import BaseModel


class SummaryResponse(BaseModel):
    total_failed_amount: int
    failure_count: int
    recovery_rate_pct: float
    top_failure_category: str | None
    recovered_amount: int


class CategoryBreakdown(BaseModel):
    category: str
    failure_count: int
    lost_amount: int


class CodeBreakdown(BaseModel):
    unified_code: str
    category: str
    failure_count: int
    lost_amount: int


class TimeseriesPoint(BaseModel):
    bucket: str
    category: str
    failure_count: int
    lost_amount: int


class RecoveryFunnel(BaseModel):
    scheduled: int
    executed: int
    recovered: int
    recovered_amount: int
    recovery_rate_pct: float


class FailureItem(BaseModel):
    id: int
    payment_id: int
    external_payment_id: str
    gateway: str
    amount: int
    currency: str
    customer_email: str | None
    gateway_code: str
    unified_code: str
    category: str
    occurred_at: str
    attempt_number: int

    model_config = {"from_attributes": True}


class FailureListResponse(BaseModel):
    items: list[FailureItem]
    total: int
    page: int
    page_size: int


class UnmappedCode(BaseModel):
    gateway: str
    gateway_code: str
    occurrence_count: int


class MappingCreate(BaseModel):
    gateway: str
    gateway_code: str
    unified_code: str
