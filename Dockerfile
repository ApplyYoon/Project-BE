# GCP Cloud Run 배포용 (PORT 환경변수를 Cloud Run 이 넣어줌)
FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:0.5 /uv /bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY app ./app
ENV PORT=8080
CMD ["sh", "-c", "uv run --no-dev uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
