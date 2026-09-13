# Backlog

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## Near-term

- **Wire instrumented tests into CI.** `app/src/androidTest/` exists and is
  written against the real Compose UI, but this session had no Android
  emulator/device available, so it has never actually been run. Add a
  hosted-emulator CI job (e.g. `reactivecircus/android-emulator-runner`) or a
  device-lab integration, budget the added CI time/cost, and confirm the
  existing tests actually pass on a real target before trusting them.
- **MockWebServer-based integration tests** for `LexiconApi`/`ApiClientFactory`
  — today's unit tests fake the API interface directly; a real
  HTTP-round-trip test would catch serialization/URL-construction bugs the
  fakes can't.
- **Retry/backoff policy for `SyncQueueWorker`.** Currently a failed replay
  just increments `attempts` and the worker returns `Result.retry()`
  (WorkManager's default exponential backoff applies), but there's no cap or
  dead-letter handling for a query that keeps failing indefinitely (e.g. a
  malformed question that the server always 422s on) — it will retry forever
  rather than surfacing "this one is stuck" to the user.
- **Pull-to-refresh gesture** on the corpus list and document list (currently
  refresh only happens on screen entry / server config save).

## Design gaps acknowledged, not solved

- **Coarse staleness fingerprint** (ADR-0003): `documentCount:maxVersion`
  can't tell you *which* document changed or whether it was even relevant to
  a specific cached question. A per-citation staleness check (does this
  answer's cited `document_id` still exist at the same `version`?) would be
  more precise but needs the citation's document version at answer time,
  which the query response doesn't currently include.
- **No pagination** for documents/corpora lists — matches lexicon's own v1
  (unpaginated document/corpus listing per `05-api-contracts.md`), but would
  need revisiting if that changes upstream.
- **Auth model is a placeholder** (ADR-0001): a single static header covers
  today's lexicon (no auth) and common reverse-proxy setups, but not e.g. an
  OAuth/session-cookie flow if lexicon ever grows one.

## Explicitly out of scope for this project (see ADR-0002 / non-goals)

- Document upload/ingestion from the app.
- Corpus creation/administration.
- Query-log audit UI.
