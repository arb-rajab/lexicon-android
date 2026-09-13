# Scope and Non-Goals

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## In scope (v1)

- Server connection setup (URL + optional static auth header).
- Listing corpora and their documents (read-only, cached).
- Asking questions against a corpus and viewing answers/citations/refusals.
- Local caching of corpora, documents, and query results (Room).
- Offline query queueing + automatic background sync (WorkManager) when
  connectivity returns.
- Staleness detection for cached answers when the underlying corpus changes.
- Online/offline/syncing UI state, visible at all times.

## Explicit non-goals

- **Document upload / ingestion.** lexicon's `POST .../documents` endpoint is
  not called anywhere in this app. Uploading a document from a phone (camera
  scan, share-sheet, etc.) is a plausible future feature but a different,
  larger project — this one is scoped to the query/consumption half of
  lexicon's surface only.
- **Corpus administration.** No corpus creation, rename, or delete UI, even
  though `POST /api/v1/corpora` exists and is easy to call — the "corpus
  owner" role in lexicon's own requirements doc is out of scope for this app;
  this app only serves the "knowledge worker" (query-only) role.
- **Query-log audit UI.** `GET .../query-logs` and its per-entry detail
  endpoint (retrieved chunks, fusion ranks, per-claim verification verdicts)
  are an auditor/owner surface in lexicon's own docs — not built here.
- **Un-stubbing lexicon's LLM calls.** This app is built and tested against
  lexicon's actual (stubbed) query behavior. It does not assume, work around,
  or require a different backend with live LLM generation.
- **Multi-user accounts / multi-tenant auth.** One server config, one device,
  one user — matching lexicon's own v1 scope (single authenticated session
  type, no roles UI).
- **Cross-platform reuse.** This is deliberately Kotlin/Compose only, to
  demonstrate native Android engineering specifically, not shared logic with
  the portfolio's other mobile apps.
- **App Store / Play Store distribution.** Built and run as a debug APK for a
  developer's own device; no release signing/publishing pipeline.
