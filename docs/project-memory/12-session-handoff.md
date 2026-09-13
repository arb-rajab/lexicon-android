# Session Handoff

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## Session 1 (this session)

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
