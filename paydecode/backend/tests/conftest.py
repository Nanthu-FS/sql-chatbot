import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.db import Base, get_db
from app.main import app
from app.seed.seed import seed as run_seed

TEST_DB_URL = "sqlite:///./test_paydecode.db"


@pytest.fixture(scope="session")
def db_engine():
    engine = create_engine(TEST_DB_URL, connect_args={"check_same_thread": False})
    Base.metadata.create_all(bind=engine)
    yield engine
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(scope="session")
def db_session(db_engine):
    SessionLocal = sessionmaker(bind=db_engine)
    session = SessionLocal()

    from app.models.core import DeclineCode, GatewayCodeMapping
    from app.seed.taxonomy import DECLINE_CODES, GATEWAY_CODE_MAPPINGS

    for code_data in DECLINE_CODES:
        if not session.get(DeclineCode, code_data["code"]):
            session.add(DeclineCode(**code_data))
    session.flush()
    for m in GATEWAY_CODE_MAPPINGS:
        if not session.query(GatewayCodeMapping).filter_by(gateway=m["gateway"], gateway_code=m["gateway_code"]).first():
            session.add(GatewayCodeMapping(**m))
    session.commit()

    yield session
    session.close()


@pytest.fixture(scope="session")
def client(db_engine, db_session):
    SessionLocal = sessionmaker(bind=db_engine)

    def override_get_db():
        s = SessionLocal()
        try:
            yield s
        finally:
            s.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
