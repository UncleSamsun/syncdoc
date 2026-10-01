#!/usr/bin/env bash
# Server-side setup with the caller's personal kubeconfig; no sudo/password.
set -euo pipefail
base=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
version=2.9.5
tools="$HOME/.local/share/syncdoc-tools"
mkdir -p "$tools"
if [ ! -x "$tools/flux" ] || ! "$tools/flux" --version | grep -q "version $version$"; then
  curl -fsSL --retry 3 "https://github.com/fluxcd/flux2/releases/download/v$version/flux_${version}_linux_amd64.tar.gz" -o "$tools/flux.tar.gz"
  curl -fsSL --retry 3 "https://github.com/fluxcd/flux2/releases/download/v$version/flux_${version}_checksums.txt" -o "$tools/checksums.txt"
  expected=$(awk "/ flux_${version}_linux_amd64.tar.gz$/ {print \$1}" "$tools/checksums.txt")
  test -n "$expected"
  printf '%s  %s\n' "$expected" "$tools/flux.tar.gz" | sha256sum -c -
  tar -xzf "$tools/flux.tar.gz" -C "$tools" flux
fi
"$tools/flux" check --pre
kubectl apply -f "$base/bootstrap.yaml"
"$tools/flux" install --namespace syncdoc-cd --version "v$version" \
  --components source-controller,kustomize-controller --watch-all-namespaces=false
kubectl apply -f "$base/source.yaml"
kubectl -n syncdoc-cd rollout status deployment/source-controller --timeout=180s
kubectl -n syncdoc-cd rollout status deployment/kustomize-controller --timeout=180s
echo 'Controllers installed. Application reconciliation is suspended until images and secrets are ready.'
