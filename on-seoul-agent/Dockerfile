FROM python:3.13-slim

# uv 설치
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app

# 의존성 파일만 먼저 복사 — 레이어 캐시 활용
COPY pyproject.toml uv.lock* ./

# 빌드 시 캐시는 활용하되 런타임에는 캐시 불필요
RUN uv sync --no-dev --no-editable

# 소스 복사
COPY . .

RUN addgroup --system jazz \
 && adduser --system --ingroup jazz --home /app jazz \
 && chown -R jazz:jazz /app

USER jazz:jazz

ENTRYPOINT ["uv", "run", "--no-cache", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]