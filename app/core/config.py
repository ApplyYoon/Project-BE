from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """환경변수(.env)에서 읽어오는 설정값. 새 설정은 여기 추가하고 .env.example 에도 적는다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "Project-BE"
    environment: str = "local"
    cors_origins: list[str] = ["http://localhost:5173", "http://localhost:3000"]
    database_url: str | None = None
    openai_api_key: str | None = None


settings = Settings()
