# Data Model (local, on-device)

> Project: lexicon-android (public)
> Last updated: 2026-09-13

All tables live in one Room database (`lexicon.db`, `AppDatabase`, version 1).
There is no server-side schema owned by this app — every table here is
either a cache of lexicon server state or a local-only queue.

## `corpora` (cache of `GET /api/v1/corpora` + `.../corpora/{id}`)

| Column | Type | Notes |
|---|---|---|
| `id` (PK) | String (UUID) | Matches server `corpus.id` |
| `name` | String | |
| `createdAt` | String | Server timestamp, stored as-is (display only) |
| `documentCount` | Int | From the detail endpoint; used in the staleness fingerprint |
| `cachedAt` | Long (epoch ms) | When this row was last refreshed from the server |

## `documents` (cache of `GET /api/v1/corpora/{id}/documents`)

| Column | Type | Notes |
|---|---|---|
| `id` (PK) | String (UUID) | Server `document.id` |
| `corpusId` | String | FK (not enforced — see below) |
| `sourceFilename` | String | |
| `version` | Int | Incremented server-side on re-upload; used in the staleness fingerprint |
| `status` | String | `queued`/`processing`/`ready`/`failed` per server contract |
| `chunkCount` | Int | |
| `cachedAt` | Long | |

## `query_results` (every answer this device has ever received or queued)

| Column | Type | Notes |
|---|---|---|
| `id` (PK) | String | Server `query_log_id` once synced; a local UUID while `PENDING` (see ADR-0003) |
| `corpusId` | String | |
| `questionText` | String | |
| `answered` | Boolean | |
| `answerText` | String? | Null when refused or still pending |
| `refusalReason` | String? | `self_refused` \| `verification_failed` \| `no_candidates_retrieved` \| null |
| `retrievedChunkCount` | Int | |
| `citationsJson` | String | Citations serialized as JSON (denormalized — see below) |
| `createdAt` | Long | |
| `cachedAt` | Long | |
| `syncState` | Enum | `SYNCED` \| `PENDING` \| `FAILED` |
| `possiblyStale` | Boolean | Set by staleness re-evaluation (ADR-0003); UI shows a warning, never hides the answer |
| `corpusFingerprint` | String | `"{documentCount}:{maxDocumentVersion}"` at the time this was answered |

Citations are stored as a JSON blob rather than a normalized child table: they
are never queried independently of their parent answer (no "find all
citations of document X across queries" feature exists), so normalizing them
would add a join for zero read benefit.

## `pending_queries` (the offline sync queue)

| Column | Type | Notes |
|---|---|---|
| `localId` (PK) | String (UUID) | Shared with the placeholder `query_results` row until synced |
| `corpusId` | String | |
| `questionText` | String | |
| `createdAt` | Long | Queue order is FIFO by this column |
| `attempts` | Int | Incremented on each failed replay |
| `lastError` | String? | Last exception message, for diagnostics |

## No foreign keys

Room foreign-key constraints are deliberately not declared between
`documents.corpusId` → `corpora.id` etc. `CorpusRepository.refresh()` deletes
and re-inserts a corpus's documents in a single non-atomic pair of DAO calls;
adding `ON DELETE CASCADE` FKs here would need those calls wrapped in a
`@Transaction` to avoid a brief invalid state, which is unnecessary
complexity for a purely local cache with no data-integrity consequence if a
document briefly points at a since-deleted corpus row (it would just
disappear from the UI on the next re-render).
