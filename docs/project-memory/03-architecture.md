# Architecture

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## Layers

```
ui/            Jetpack Compose screens + ViewModels (per-screen state, one Factory each)
data/repository/  CorpusRepository, QueryRepository — the only classes that know about
                   both Room and Retrofit; own the offline/sync/staleness logic
data/local/    Room: AppDatabase, entities, DAOs
data/remote/   Retrofit LexiconApi + kotlinx.serialization DTOs
connectivity/  ConnectivityObserver (wraps ConnectivityManager.NetworkCallback as a Flow)
sync/          SyncQueueWorker (WorkManager CoroutineWorker)
LexiconApplication  Composition root (see ADR-0004 on why no DI framework)
```

Data flows one way for reads: Room is the single source of truth for
everything the UI renders (`Flow`s from DAOs, combined into UI state in each
ViewModel). The network is only ever a *write-through* path: a repository
call fetches from `LexiconApi`, writes the result into Room, and the UI
updates because it's observing Room — screens never render a Retrofit
response directly. This is what makes "already-cached content works with the
network off" true by construction rather than by a special-cased offline
mode.

## Key flows

**Opening a corpus (online):** `QueryViewModel` combines
`CorpusRepository.observeDocuments()`, `QueryRepository.observeResults()`,
`ConnectivityObserver.observe()`, and `QueryRepository.observePendingCount()`
into one `QueryScreenUiState`. `CorpusListViewModel.refresh()` (and
`CorpusRepository.refresh()`) populate Room from the network on screen entry
and pull-to-refresh.

**Asking a question while online:** `QueryRepository.submitQuery()` checks
connectivity, calls `LexiconApi.askQuestion`, and on success writes a
`QueryResultEntity` with `syncState = SYNCED`. If the live call throws
`IOException` (network flakiness despite `ConnectivityManager` reporting
online — a real, common case) it falls back to the offline path rather than
surfacing a raw error.

**Asking a question while offline:** `QueryRepository.submitQuery()` inserts
a `PendingQueryEntity` (the durable queue) plus a placeholder
`QueryResultEntity` with `syncState = PENDING`, so the UI shows "queued"
immediately, then calls the `onQueryQueued` hook, which enqueues
`SyncQueueWorker` with a `NetworkType.CONNECTED` constraint.

**Sync worker draining the queue:** `SyncQueueWorker` (also enqueued
opportunistically from `MainActivity.onCreate`, to catch anything queued in
a previous session) reads every `PendingQueryEntity`, replays each through
`QueryRepository.replayPending()`, and finally refreshes corpus/document
metadata — so staleness flags (ADR-0003) settle in the same pass that
actually answered those questions.

**Staleness re-evaluation:** every `CorpusRepository.refresh()` recomputes a
per-corpus fingerprint (`documentCount:maxDocumentVersion`) and flags any
`SYNCED` cached result whose stored fingerprint no longer matches as
`possiblyStale = true` (never deletes it — see ADR-0003).

## Why not Hilt/Dagger

See ADR-0004. The dependency graph here is small and mostly one repository
per data source; a hand-rolled composition root in `LexiconApplication` is
easier to read end-to-end than a generated one, and keeps the build free of
an annotation-processor dependency this project doesn't otherwise need.

## Why not a login flow

See ADR-0001 — lexicon v1 has no server-side auth to log into.
