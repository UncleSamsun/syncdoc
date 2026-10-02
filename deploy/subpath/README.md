# Public subpath deployment

After activation, SyncDoc uses `/syncdoc/` and `/` redirects to `/grafana/`. This branch prepares the change; the public deployment has not switched yet. Existing local builds default to `/`.

## Configuration

- Frontend build: `SYNCDOC_BASE_PATH=/syncdoc/` (Docker build argument). After this change reaches main, [the publish workflow](../../.github/workflows/publish.yml) supplies this value for every web image, including manual main runs. The reusable build workflow and local Docker builds default to `/`; changing the runtime environment cannot change an already built web image.
- Backend runtime: `SYNCDOC_PUBLIC_BASE_PATH=/syncdoc` (no trailing slash).
- OAuth runtime: `SYNCDOC_GITHUB_REDIRECT_URI=https://211.45.127.56/syncdoc/api/v1/auth/github/callback`.
- GitHub App must allow that exact callback before activation. Retain localhost callback entries for development.
- Set the two backend values through the existing Flux Kustomization patches, preserve other patches and Secure cookies, and roll out the backend. Do not edit only the live ConfigMap because reconciliation replaces it.
- Traefik forwards `/syncdoc/` after stripping `/syncdoc`; backend and nginx still serve internal `/api/` and `/`. Health probes retain internal paths.
- Stored snapshots retain `/projects/...` and `/api/v1/projects/...` URLs. DocumentBody rebases those DOM attributes at display time; no data migration/recollection is needed.

## Rollout order

1. Snapshot the existing Flux patches, ingress routes and applied image digests. Add the new GitHub callback.
2. Stage the subpath route while retaining the old catch-all route. Configure the backend prefix and callback through Flux.
3. Merge the reviewed changes and release the explicitly selected complete dev revision. Wait for verify → GHCR → Flux and inspect both source revisions/digests.
4. Verify subpath HTML/assets/API and OAuth redirect. Replace the old catch-all ingress with the final routes in `routes.yaml`; root now points to Grafana.
5. Verify personal login, deep-link refresh, document links/assets, logout, unauthenticated API 401, root redirect and Grafana login. Update blackbox public URL checks and operational runbooks.

This file and routes are operator-managed; workload renderer does not apply cluster edge resources. Never expose the backing database, K3s API or Grafana port 13000.

## Rollback

Restore the captured routes and Flux patches and promote the previously captured image pair using the existing deployment rollback procedure. Keep the new OAuth callback until old sessions are no longer in flight. A code/image rollback does not automatically reverse unrelated database migrations in a combined release.

## References

- [Vite public base path](https://vite.dev/guide/build#public-base-path)
- [Traefik StripPrefix](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/stripprefix/)
