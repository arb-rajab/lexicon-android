# Backlog

> Project: lexicon-android (public)
> Last updated: 2026-09-17

## Near-term

- **MockWebServer-based integration tests** for `LexiconApi`/`ApiClientFactory`
  — today's unit tests fake the API interface directly; a real
  HTTP-round-trip test would catch serialization/URL-construction bugs the
  fakes can't.
- **Pull-to-refresh on the document list within a corpus** (`QueryScreen`) —
  Session N added it to the corpus list (`CorpusListScreen`) only; the query
  screen still refreshes only on entry. Same `PullToRefreshBox` +
  `CorpusRepository.refresh(corpusId)` pattern, just not done this session for
  scope reasons.
- **Per-corpus retry button for `FAILED` query results.** Session N's
  backoff cap (`QueryRepository.MAX_SYNC_ATTEMPTS`, see ADR-0006) surfaces a
  permanently-failed query as `QuerySyncState.FAILED` in `QueryScreen`, but
  the only way to retry it today is to re-type and resubmit the same
  question by hand. A "retry" affordance on the `FAILED` card that
  re-submits `questionText` directly would be a small, real UX improvement.

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
