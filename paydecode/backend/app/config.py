from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "sqlite:///./paydecode.db"
    webhook_secret: str = "dev-secret-key-change-in-prod"
    cors_origins: str = "http://localhost:5173"

    model_config = {"env_file": ".env"}


settings = Settings()
