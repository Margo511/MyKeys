#!/usr/bin/env bash

set -uo pipefail

supabase stop --no-backup
supabase start --network-id local-network

reset_status=0
supabase db reset --local || reset_status=$?

# Supabase CLI 2.111 recreates Postgres on its generated network during reset,
# while the remaining services stay on local-network. Reconnect the DB alias and
# restart Storage so the health check becomes deterministic on WSL Docker Engine.
docker network connect \
  --alias supabase_db_my-keys \
  --alias db \
  local-network \
  supabase_db_my-keys >/dev/null 2>&1 || true
docker restart supabase_storage_my-keys >/dev/null

expected=$(find supabase/migrations -maxdepth 1 -type f -name '[0-9]*_*.sql' | wc -l)
applied=$(
  docker exec supabase_db_my-keys \
    psql -U postgres -d postgres -Atqc \
    'select count(*) from supabase_migrations.schema_migrations'
)

if [[ "$applied" -ne "$expected" ]]; then
  exit "${reset_status:-1}"
fi

for _attempt in $(seq 1 30); do
  if supabase status >/dev/null 2>&1; then
    exit 0
  fi
  sleep 1
done

exit "${reset_status:-1}"
