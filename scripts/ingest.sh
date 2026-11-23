#!/usr/bin/env bash
# RAG 데이터 적재 실행기. 사용: scripts/ingest.sh [--source laws|precedents] [--dry-run] [--force] [--data-dir PATH]
# 가상환경(build/ingest-venv)을 만들고, .env에서 DB_* 값만 읽어 ingest_rag.py를 실행한다. 로컬 Postgres가 떠 있어야 한다.
set -euo pipefail
cd "$(dirname "$0")/.."

venv=build/ingest-venv
if [ ! -x "$venv/bin/python" ]; then
  python3 -m venv "$venv"
  "$venv/bin/pip" install -q -r scripts/requirements.txt
fi

if [ -f .env ]; then
  while IFS='=' read -r key value; do
    case "$key" in DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|DB_PASSWORD) export "$key=$value" ;; esac
  done < <(grep -E '^DB_(HOST|PORT|NAME|USERNAME|PASSWORD)=' .env)
fi

exec "$venv/bin/python" scripts/ingest_rag.py "$@"
