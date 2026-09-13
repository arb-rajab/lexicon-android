# Project Brief

> Project: lexicon-android (public, portfolio/skill-demo)
> Companion to: [lexicon](https://github.com/arb-rajab/lexicon) (public, v1.0.0)
> Last updated: 2026-09-13

## What this is

A native Android app (Kotlin + Jetpack Compose) that consumes lexicon's REST
API to let a user browse their document corpora and ask questions against
them, from a phone. It is a **consumer query client**, not an admin console:
no document upload, no corpus creation/administration, no query-log audit
UI — those are lexicon's web frontend's job.

## Why this exists

This portfolio already contains (or is building) a React Native app, a
Flutter app, and a Swift/SwiftUI iOS app whose defining feature is live push
notifications. Adding a fourth mobile app that's simply "the same web UI, but
native" would demonstrate nothing new. This project instead demonstrates a
different, harder slice of mobile engineering:

- **On-device caching** of query results and document/corpus metadata (Room).
- **Offline-first access**: previously-seen content works with zero
  connectivity, not just "loads slower."
- **Background sync**: questions asked while offline are queued and answered
  automatically via WorkManager once connectivity returns — this is the
  actual point of the app, not a stub.
- **Explicit conflict/staleness handling**: what happens when cached data and
  server data disagree is a designed decision (ADR-0003), not hand-waved.

## Relationship to lexicon

lexicon is treated as a fixed, external dependency here: this app was built
by reading its actual `docs/project-memory/05-api-contracts.md` and backend
route code (`backend/src/lexicon/api/*.py`) as of this session, not by
guessing an API shape. Two properties of the real API shaped this app's
design directly:

1. **No auth in v1.** lexicon's backend has no login/session/token
   middleware at all in this session's checkout — `05-api-contracts.md`
   explicitly defers "who may access a deployment" to an operator/deployment
   concern. So this app has no login screen; see ADR-0001.
2. **LLM calls are deliberately stubbed** in lexicon (no provider API key in
   its env by design — the demonstrated value there is the retrieval
   pipeline, not live generation quality). This app is built to work
   correctly against that real, stubbed behavior — including all three
   `refusal_reason` values a query can legitimately come back with — not
   against an imagined "real" LLM backend.

## Audience

A single developer's own phone, pointed at their own self-hosted lexicon
instance. No multi-tenant user accounts, no app-store distribution plan.
