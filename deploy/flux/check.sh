#!/usr/bin/env bash
set -euo pipefail
expected_commit=${1:?Usage: check.sh <deployment-commit-from-Actions-summary>}
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]]
test "$(kubectl -n syncdoc-cd get kustomization syncdoc-test -o jsonpath='{.spec.suspend}')" != true
expected_revision="syncdoc-test-deploy@sha1:$expected_commit"
# Ready can still refer to the previous release until the next source poll.
for attempt in $(seq 1 "${SYNCDOC_CHECK_ATTEMPTS:-150}"); do
  source_revision=$(kubectl -n syncdoc-cd get gitrepository syncdoc-test -o jsonpath='{.status.artifact.revision}')
  applied_revision=$(kubectl -n syncdoc-cd get kustomization syncdoc-test -o jsonpath='{.status.lastAppliedRevision}')
  if [ "$source_revision" = "$expected_revision" ] && [ "$applied_revision" = "$expected_revision" ]; then break; fi
  sleep "${SYNCDOC_CHECK_INTERVAL:-2}"
done
test "$source_revision" = "$expected_revision"
test "$applied_revision" = "$expected_revision"
kubectl -n syncdoc-cd wait gitrepository/syncdoc-test --for=condition=Ready --timeout=180s
kubectl -n syncdoc-cd wait kustomization/syncdoc-test --for=condition=Ready --timeout=300s
source_revision=$(kubectl -n syncdoc-cd get gitrepository syncdoc-test -o jsonpath='{.status.artifact.revision}')
applied_revision=$(kubectl -n syncdoc-cd get kustomization syncdoc-test -o jsonpath='{.status.lastAppliedRevision}')
test -n "$source_revision"
test "$source_revision" = "$expected_revision"
test "$applied_revision" = "$expected_revision"
for app in postgres backend web; do
  kubectl -n syncdoc-test rollout status "deployment/$app" --timeout=180s
done
port=${SYNCDOC_CHECK_PORT:-18081}
log=$(mktemp)
kubectl -n syncdoc-test port-forward --address 127.0.0.1 service/web "$port:80" >"$log" 2>&1 &
forward_pid=$!
trap 'kill "$forward_pid" 2>/dev/null || true; rm -f "$log"' EXIT
for attempt in $(seq 1 30); do
  kill -0 "$forward_pid"
  if grep -q "Forwarding from 127.0.0.1:$port" "$log" && curl -fsS --max-time 3 "http://127.0.0.1:$port/api/v1/health/ready" >/dev/null; then break; fi
  sleep 1
done
kill -0 "$forward_pid"
grep -q "Forwarding from 127.0.0.1:$port" "$log"
for path in / /api/v1/health/live /api/v1/health/ready; do
  code=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port$path")
  test "$code" = 200
  printf '%s: %s\n' "$path" "$code"
done
code=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/v1/me")
test "$code" = 401
printf 'Unauthenticated /api/v1/me: %s\n' "$code"
kubectl -n syncdoc-cd get gitrepository/syncdoc-test kustomization/syncdoc-test
