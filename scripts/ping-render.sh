#!/usr/bin/env bash
set -euo pipefail

# DB를 조회하는 /health 대신 /ping만 호출합니다. URL에는 인증 정보가 필요 없습니다.
ping_url="${RENDER_PING_URL:-https://project-be-mxxc.onrender.com/api/v1/ping}"
response="$(curl --fail --silent --show-error \
  --connect-timeout 10 --max-time 90 \
  --retry 2 --retry-delay 5 --retry-max-time 180 \
  "$ping_url")"

if [[ "$response" != '{"status":"ok"}' ]]; then
  echo 'Ping failed: unexpected response' >&2
  exit 1
fi

echo 'Render ping OK'
