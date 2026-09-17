# Testing Strategy

> Project: lexicon-android (public)
> Last updated: 2026-09-17

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
    (never silently dropped) as long as attempts remain under the cap;
  - `replayPending` failure once `QueryRepository.MAX_SYNC_ATTEMPTS` (5) is
    reached → the pending entry is dequeued for good and the cached result
    is marked `FAILED` instead of retrying forever (ADR-0006).
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

**This environment (both the original session and Session N) has no Android
emulator or connected device**, so these tests have been written against the
real API but have never been run **from this sandbox** — there is no way to
run `./gradlew connectedAndroidTest` here (confirmed again in Session N: the
local `./gradlew` invocation fails at plugin resolution because
`dl.google.com` is unreachable from this environment, before it would even
get to running an emulator).

As of Session N, `.github/workflows/android-ci.yml` has an `instrumented-tests`
job wired up (`reactivecircus/android-emulator-runner@v2`, API 30,
`google_apis`/`x86_64`, `-no-window -gpu swiftshader_indirect`) that runs
`connectedDebugAndroidTest` on GitHub's own `ubuntu-latest` runners. This is
believed to work because GitHub enabled KVM hardware acceleration on
GitHub-hosted Linux runners in February 2023 (see the GitHub changelog:
"Hardware accelerated Android virtualization on Actions Windows and Linux
larger hosted runners"), which is what `reactivecircus/android-emulator-runner`
needs to boot an x86_64 emulator at usable speed — the job includes the
standard "Enable KVM" udev-rule step this action's docs call for on Linux
runners. **This has not been watched running to a green result from inside
this sandbox** (same class of constraint as the original session's CI, and
the same class of blocker bookslot-mobile hit with KVM — the difference here
is that this session found a documented, non-fudged path around it rather
than being stuck: GitHub's own Linux runners, unlike a from-scratch
self-hosted or fully-virtualized environment, have that acceleration
available). Whoever picks this up next should confirm the `instrumented-tests`
job is actually green on the first real run and fix anything it surfaces —
same standard as the rest of this project's CI story.

### What's not tested

- Retrofit/OkHttp wiring itself (network calls) — no MockWebServer-based
  integration test exists yet; see backlog.
- Room's actual SQL behavior (migrations, query correctness) — the DAOs are
  simple enough (single-table CRUD, no complex joins) that the fakes above
  are considered sufficient for now; this is a real gap, not an oversight,
  if the schema grows more complex later.
