# Backlog

> Project: lexicon-android (public)
> Last updated: 2026-09-17

## Near-term

All three items tracked here as of Session N's handoff were completed in
Session N+1:

- **MockWebServer-based integration tests** for `LexiconApi`/`ApiClientFactory`
  — done. `LexiconApiIntegrationTest` round-trips real HTTP requests through
  `ApiClientFactory` against a `MockWebServer`, covering path/method
  construction (including URL-encoding a corpus id containing `/`), request
  body serialization, the configured static auth header being attached (and
  absent when unconfigured), and a 4xx response surfacing as `HttpException`
  rather than being swallowed.
- **Pull-to-refresh on the document list within a corpus** (`QueryScreen`) —
  done, via the same `PullToRefreshBox` + `CorpusRepository.refresh(corpusId)`
  pattern `CorpusListScreen` already used.
- **Per-corpus retry button for `FAILED` query results** — done.
  `QueryRepository.retryFailed` drops the old permanently-failed row and
  re-submits the same `questionText` through `submitQuery` (a fresh
  `PENDING`/`SYNCED` outcome with `attempts` reset to 0, not a resurrection
  of the failed one), wired to a "Retry" button on the `FAILED` card.

Nothing is currently tracked as near-term backlog beyond the design gaps and
explicitly-out-of-scope items below.

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
