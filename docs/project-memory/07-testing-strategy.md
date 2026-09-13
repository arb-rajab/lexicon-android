# Testing Strategy

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## What's actually tested, and why

The offline queueing / caching / staleness logic in `data/repository/` is
the part of this app that has to be correct — everything else is fairly
standard CRUD-over-Retrofit-and-Room plumbing. Testing effort is weighted
accordingly.

### Unit tests (`app/src/test/`, JVM, no device/emulator needed)

- `QueryRepositoryTest` — the core offline/sync contract:
  - online submit → `SYNCED` result cached with the server's answer;
  - online submit whose live call throws `IOException` → falls back to
    queueing rather than surfacing a raw error (a real case: a device can
    report itself online while a specific request still fails);
  - offline submit → `PendingQueryEntity` created, placeholder `PENDING`
    result cached immediately, and the sync-trigger callback fires;
  - `replayPending` success → pending entry removed, placeholder replaced by
    the server-assigned row;
  - `replayPending` failure → `attempts` incremented, entry stays queued
    (never silently dropped).
- `CorpusRepositoryTest` — the staleness fingerprint
  (`computeCorpusFingerprint`): changes when document count changes, changes
  when a document's version changes, stable otherwise; plus a `refresh()`
  smoke test against a fake API.

These use hand-written in-memory fakes for the DAOs and `LexiconApi`
(`app/src/test/.../repository/FakeDaos.kt`, `FakeSupport.kt`) rather than an
in-memory Room database or Robolectric — the logic under test is plain
Kotlin coroutine/state logic, so a real SQLite instance would only add
runtime cost without covering anything a fake `Flow`-backed map doesn't.

### Instrumented / UI tests (`app/src/androidTest/`)

`ConnectivityBannerTest` covers the online/offline/syncing banner's three
visible states via Compose UI testing (`createComposeRule`).

**This environment has no Android emulator or connected device**, so these
tests have been written against the real API but **not executed** in this
session — there was no way to run `./gradlew connectedAndroidTest` here.
Getting them running in CI (most realistically via a hosted-emulator GitHub
Action such as `reactivecircus/android-emulator-runner`, which the current
`android-ci.yml` does not yet include, or a real-device cloud lab) is tracked
as a backlog item rather than silently skipped or claimed as passing.

### What's not tested

- Retrofit/OkHttp wiring itself (network calls) — no MockWebServer-based
  integration test exists yet; see backlog.
- Room's actual SQL behavior (migrations, query correctness) — the DAOs are
  simple enough (single-table CRUD, no complex joins) that the fakes above
  are considered sufficient for now; this is a real gap, not an oversight,
  if the schema grows more complex later.
