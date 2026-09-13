# lexicon-android

A native Android companion app for [lexicon](https://github.com/arb-rajab/lexicon),
a grounded RAG document Q&A system. This is a **portfolio / skill-demo
project** — not a real product — built to show a different kind of mobile
engineering than this portfolio's other mobile apps: **on-device caching,
offline-first document access, and background sync**, rather than another
push-notification demo.

## What it does

- Connects to a self-hosted lexicon deployment (server URL + optional auth
  header — see [ADR-0001](docs/project-memory/09-decision-log.md) for why
  there's no login screen).
- Lists corpora and documents, caching their metadata locally with Room.
- Lets you ask questions against a corpus and caches every answer (or
  refusal) it gets back.
- Works properly **offline**: previously-viewed corpora, documents, and
  query results stay available with no connection at all.
- Queues questions asked while offline and answers them automatically via a
  WorkManager background job as soon as connectivity returns.
- Flags a cached answer as *possibly stale* if the corpus it was answered
  against has changed since (new/removed document, new version) — see
  [ADR-0003](docs/project-memory/09-decision-log.md).
- Shows an honest online/offline/syncing indicator at all times.

## Non-goals

Document upload/ingestion, corpus administration, and query-log audit are
lexicon's admin-side surface — this is a consumer query client, not an admin
console. See [`01-scope-and-non-goals.md`](docs/project-memory/01-scope-and-non-goals.md).

## Project layout

```
app/src/main/kotlin/dev/arbrajab/lexiconandroid/
  data/remote/       Retrofit API + DTOs matching lexicon's 05-api-contracts.md
  data/local/        Room database, entities, DAOs
  data/repository/   CorpusRepository, QueryRepository — the offline/sync logic
  connectivity/       Online/offline detection (ConnectivityManager)
  sync/              WorkManager worker that replays the offline queue
  ui/                Jetpack Compose screens + ViewModels
docs/project-memory/  Brief, architecture, data model, decisions, backlog, handoff
```

## Building

Requires JDK 17+ and the Android SDK (compileSdk 34). This was scaffolded in
an environment without Android SDK / Google Maven access, so the build has
**not been compiled locally** in that session — CI (`.github/workflows/android-ci.yml`)
runs ktlint, unit tests, and `assembleDebug` on every push/PR.

```
./gradlew ktlintCheck
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Documentation

See [`docs/project-memory/`](docs/project-memory/) for the full project-memory
pack: brief, scope, architecture, data model, testing strategy, decision log
(ADRs), backlog, and session handoff.

## License

MIT — see [LICENSE](LICENSE). (lexicon itself is AGPL-3.0; this client talks
to it over HTTP only and contains none of its code, so a permissive license
for the client is appropriate, though this isn't legal advice.)
