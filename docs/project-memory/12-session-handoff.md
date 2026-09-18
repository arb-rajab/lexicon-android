# Session Handoff

> Project: lexicon-android (public)
> Last updated: 2026-09-18

## Session N+1: CI actually verified green, MockWebServer tests, QueryScreen pull-to-refresh, retry button

Session N's own handoff (below) explicitly flagged one thing as unverified:
whether the instrumented-tests CI job it wrote actually ran and passed, since
that sandbox had no path to `dl.google.com` to confirm it. This session's
job was to close that gap for real, not just re-confirm the gap exists — and
along the way, three more backlog items were completed.

**CI verification — the actual, not inferred, outcome:** the
`instrumented-tests` job's branch (`claude/lexicon-android-ci-sync-ytuyjf`,
already containing the work Session N described) was merged to `main` and
pushed for real. The first run failed — genuinely, not as a formality — and
it took **five** iterations to reach green, each fixing a real bug CI's
first actual execution surfaced (none of these were guessed at or fixed
speculatively; each was root-caused from the actual CI log before being
fixed):

1. `android-actions/setup-android@v3`'s default `packages` input
   (`"tools platform-tools"`) requests the legacy "Android SDK Tools"
   package, which Google has since removed from the SDK repository —
   failed the `Set up Android SDK` step in *both* jobs before either could
   even start. Fixed by overriding `packages: "platform-tools"` (Gradle/AGP
   auto-installs whatever compileSdk/build-tools it actually needs via the
   already-accepted licenses).
2. `PullToRefreshBox` actually lives in
   `androidx.compose.material3.pulltorefresh`, not `androidx.compose.material3`
   directly — both `CorpusListScreen` (Session N) and `QueryScreen` (this
   session) imported the wrong package, failing as "Unresolved reference"
   with cascading `@Composable` errors from the broken type inference.
3. `CorpusListScreen` also had a stray
   `import androidx.compose.foundation.layout.weight`: that package also
   declares an *internal* `RowColumnParentData?.weight` property, and the
   explicit import shadowed the intended `ColumnScope.weight` member
   extension, failing as "it is internal in file". (`ColumnScope.weight`
   needs no import at all — it resolves via the enclosing `Column` scope.)
4. ktlint formatting violations across the new/changed test and view-model
   code: line length, `= runTest { }` merged onto the same line as the `fun`
   signature where it fits, and `QueryViewModel._isRefreshing` needing a
   matching public `isRefreshing` `StateFlow` (same backing-property rule
   already satisfied by `_questionInput`/`_isSubmitting` in the same file and
   `CorpusListViewModel._isRefreshing`).
5. `app/src/androidTest/.../ConnectivityBannerTest.kt` — written in Session 1
   and **never compiled until this run**, anywhere — referenced
   `androidx.compose.ui.test.assertDoesNotExist`, which failed to resolve.
   Rather than guess at why, the single call site was switched to
   `onAllNodesWithText(...).assertCountEquals(0)`, which current official
   Compose testing docs confirm as a real, current API, sidestepping the
   disputed single-node assertion entirely.

After fix 5, the full workflow (`lint-and-test`: ktlint, unit tests,
`assembleDebug`; `instrumented-tests`: three `ConnectivityBannerTest` cases
on a real API-30 hosted emulator) passed. **The run was then manually
re-triggered a second time on the same commit and passed again**, both jobs,
specifically to check for flakiness given this repo's history of unstable
CI — it did not flake. Both runs are inspectable directly:
`https://github.com/arb-rajab/lexicon-android/actions/runs/35302969876`
(attempts 1 and 2).

**New work this session (all covered by the CI run above):**

- **MockWebServer integration tests** — `LexiconApiIntegrationTest` round-
  trips real HTTP requests through `ApiClientFactory`/`LexiconApi` against a
  `MockWebServer`: path/method construction (including URL-encoding a
  corpus id containing `/`), request body serialization, the configured
  static auth header being attached (and absent when unconfigured), and a
  4xx response surfacing as `HttpException` rather than being swallowed.
- **`QueryScreen` pull-to-refresh** — same `PullToRefreshBox` +
  `CorpusRepository.refresh(corpusId)` pattern `CorpusListScreen` already
  used; no parallel refresh logic.
- **One-tap retry for `FAILED` query results** — `QueryRepository.retryFailed`
  drops the old permanently-failed row and re-submits the same
  `questionText` through `submitQuery` (a fresh `PENDING`/`SYNCED` outcome
  with `attempts` reset to 0, not a resurrection of the failed entry), wired
  to a "Retry" button on the `FAILED` card. Extends the existing
  `SUCCESS`/`RETRYING`/`PERMANENTLY_FAILED` model from ADR-0006 rather than
  changing it.

**Explicitly not touched:** the Dependabot-enabled confirmation loose end
(admin-only, out of scope per this session's instructions).

**Remaining backlog:** none tracked as near-term (see `11-backlog.md`) — this
closes out every item Session N's handoff listed as outstanding. The
design-gaps-acknowledged-not-solved items (coarse staleness fingerprint, no
pagination, placeholder auth model) and the Dependabot admin-action loose
end remain, as before, untouched by design.

## Session N: CI-instrumented tests, pull-to-refresh, sync retry/backoff cap

**Scope worked (backlog cleanup):**

1. **Wired instrumented tests into CI** (`.github/workflows/android-ci.yml`,
   new `instrumented-tests` job) using
   `reactivecircus/android-emulator-runner@v2` on GitHub-hosted
   `ubuntu-latest` runners (API 30, `google_apis`/`x86_64`,
   `-no-window -gpu swiftshader_indirect`), with the standard "Enable KVM"
   udev-rule step. This is viable — unlike bookslot-mobile's KVM blocker —
   because GitHub enabled hardware-accelerated Android virtualization on its
   own hosted Linux runners in February 2023; this environment's earlier
   inability to run `connectedAndroidTest` was about *this sandbox* having no
   emulator/KVM access, not about GitHub Actions lacking it. See
   `07-testing-strategy.md` for the full reasoning and citation.
   **Not verified green from inside this sandbox** — this session has no
   network path to `dl.google.com` either (confirmed again: `./gradlew
   ktlintCheck` fails at AGP plugin resolution before reaching any Android
   SDK step), so CI is still the first real compile/run of this project, now
   including the instrumented job. Whoever picks this up next should confirm
   it's actually green and fix whatever it surfaces.
2. **Pull-to-refresh** on `CorpusListScreen` via Material3's
   `PullToRefreshBox`, wired to the same `CorpusListViewModel.refresh()` →
   `CorpusRepository.refresh()` path already used on screen entry — no
   parallel refresh logic. (Document-list pull-to-refresh on `QueryScreen`
   was **not** done this session — see backlog.)
3. **Sync retry/backoff cap** for the offline query queue — see ADR-0006 in
   `09-decision-log.md` for the full decision. Short version:
   `QueryRepository.MAX_SYNC_ATTEMPTS = 5`; a query that keeps failing past
   that cap is dequeued for good and its cached result flips to
   `QuerySyncState.FAILED`, which `QueryScreen` already had a (previously
   dead) UI branch for. Covered by two new unit tests in
   `QueryRepositoryTest` (the existing "increments attempts and keeps
   queued" test plus a new "gives up after MAX_SYNC_ATTEMPTS" test) — no
   emulator needed, per `07-testing-strategy.md`'s existing philosophy of
   keeping this logic unit-testable.

**Explicitly not touched:** the Dependabot-enabled confirmation loose end
(admin-only, out of scope per this session's instructions).

**Remaining backlog** (see `11-backlog.md` for the authoritative, living
list — this is a snapshot as of this session):
- Confirm the new `instrumented-tests` CI job is actually green on a real
  run (see above) — the single highest-priority follow-up, same as last
  session's CI-verification item but now scoped to the instrumented job
  specifically.
- MockWebServer-based integration tests for `LexiconApi`/`ApiClientFactory`
  (carried over, untouched this session).
- Pull-to-refresh on `QueryScreen`'s document/result list (only the corpus
  list got it this session).
- A one-tap retry affordance for `FAILED` query results (today, retrying a
  permanently-failed query means manually re-typing and resubmitting the
  same question).
- Design gaps acknowledged-not-solved from before (coarse staleness
  fingerprint, no pagination, placeholder auth model) — untouched, still
  accurate.
- The Dependabot admin-action loose end — explicitly out of scope, still
  outstanding, still not this session's to attempt.

Nothing from this session's assigned scope (CI instrumented tests wiring,
pull-to-refresh, sync retry/backoff cap) was silently dropped — each is
either done-and-verified-by-tests-where-testable, or done-but-flagged as
CI-unverified-from-this-sandbox above.

## Session 1

**Environment constraints hit, and how they were handled:**

- No Android SDK and no network access to `dl.google.com` (Google's Maven
  repository, which hosts the Android Gradle Plugin and all AndroidX/Compose
  artifacts) in this execution environment. This means **the build has not
  been compiled locally** — not by choice, but because it was impossible
  here. `.github/workflows/android-ci.yml` runs `ktlintCheck`,
  `testDebugUnitTest`, and `assembleDebug` on GitHub's runners, which do have
  that access; CI is the first real compile of this project, not a
  formality layered on top of a locally-verified build. If CI is red, start
  there — it's genuinely unverified code, not a rubber-stamp.
- The Gradle wrapper (`gradlew`, `gradle/wrapper/`) was generated locally
  using the sandbox's own pre-installed Gradle 8.14.3 (targeting the
  project's declared 8.9), by temporarily emptying `build.gradle.kts` so the
  `wrapper` task didn't need to resolve the (network-blocked) AGP plugin
  first, then restoring the real build file. This is a one-time bootstrapping
  detail, not something future sessions need to repeat.
- Instrumented (on-device) Compose UI tests exist
  (`app/src/androidTest/.../ConnectivityBannerTest.kt`) but have never been
  run — no emulator/device was available. See `07-testing-strategy.md` and
  the backlog.

**What was read before designing anything:** lexicon's
`docs/project-memory/05-api-contracts.md` in full, plus the actual FastAPI
route handlers (`backend/src/lexicon/api/{corpora,documents,query}.py`,
`main.py`) to confirm there is no auth middleware — this directly shaped
ADR-0001.

**What works end-to-end (by code review; unverified by an actual compile —
see above):** server config → corpus list refresh/cache → corpus detail
(documents + query) → ask question (online path caches `SYNCED`; offline
path queues `PENDING` and triggers `SyncQueueWorker`) → sync worker replays
queue and refreshes staleness flags.

**What's next:** see `11-backlog.md`. The single highest-priority item for
whoever picks this up next is confirming CI is actually green (this session
could not watch it run to completion in a way disconnected from network
constraints it hit at the plugin-resolution layer) and, if not, fixing
whatever the first real compile surfaces.
