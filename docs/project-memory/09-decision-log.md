# Decision Log (ADRs)

> Project: lexicon-android (public)
> Last updated: 2026-09-13

## ADR-0001: No login screen — server URL + optional static header instead

**Status:** Accepted

**Context:** The task for this app assumed it would "authenticate against
lexicon's existing API." Reading lexicon's actual
`docs/project-memory/05-api-contracts.md` and its FastAPI route code
(`backend/src/lexicon/api/*.py`, `backend/src/lexicon/main.py`) shows there
is no auth middleware, no login/session/token endpoint, and no
`Depends(get_current_user)`-style dependency anywhere in the backend as of
this session's checkout. The API contract doc says this explicitly:
"Instance-level authentication... is an operator/deployment concern, not
designed in this session."

**Decision:** This app does not implement a login flow against endpoints
that don't exist. Instead, `ServerConfigScreen` collects a base URL and an
optional single static HTTP header (name + value), stored via DataStore
(`ServerConfigStore`) and attached to every request by
`StaticHeaderInterceptor`. This covers the realistic self-hosted deployment
shapes: a reverse proxy doing HTTP basic auth translated to a header, an API
gateway requiring a static token, or nothing at all if the deployment is
only reachable on a private network.

**Consequences:** If lexicon ever grows real instance-level auth (its own
docs flag this as a known future decision), this app's `ServerConfigStore`
and `StaticHeaderInterceptor` are the two places to extend — likely into a
proper login screen storing a session token instead of a user-typed static
header. No other part of the app assumes anything about the auth mechanism.

## ADR-0002: Consumer query client only — no admin surface

**Status:** Accepted

**Context:** lexicon's API also exposes document upload, corpus creation,
and query-log audit — all real, working endpoints.

**Decision:** This app calls only `GET /corpora`, `GET /corpora/{id}`,
`GET /corpora/{id}/documents`, and `POST /corpora/{id}/query`. See
`01-scope-and-non-goals.md`.

**Consequences:** A corpus with zero documents (nothing uploaded yet via the
web UI) will show correctly as empty here, but there is no way to fix that
from this app — by design.

## ADR-0003: Cached answers are flagged stale, never silently deleted or blocked

**Status:** Accepted

**Context:** The task explicitly calls out conflict/staleness handling as a
real design decision, not something to hand-wave. The concrete conflict this
app can actually have: a query result was cached against a corpus in one
state (N documents, certain versions), and the corpus has since changed
(a document added, removed, or re-ingested at a new version) — so the cached
answer might no longer reflect what the corpus would answer today.

**Alternatives considered:**
- *Evict stale cache entries entirely.* Rejected: this is an offline-first
  app: deleting a cached answer the moment it might be stale means a user
  with no connectivity loses access to it entirely, which is worse than
  showing a possibly-outdated answer with a visible warning.
- *Block/hide the answer until re-verified online.* Rejected for the same
  reason — it makes "offline" strictly worse than "no app," which defeats
  the point of building this app at all.
- *Silently keep showing it with no indication.* Rejected: this is exactly
  the kind of hidden-degradation the project brief calls out as
  unacceptable ("clear, honest UI indication" is a hard requirement, and
  that requirement applies to data staleness, not just connectivity state).

**Decision:** Each cached `query_results` row stores a
`corpusFingerprint = "{documentCount}:{maxDocumentVersion}"` computed at
answer time. Every `CorpusRepository.refresh()` (on corpus-list load,
pull-to-refresh, and after a sync-worker pass) recomputes the current
fingerprint and marks any `SYNCED` row whose stored fingerprint no longer
matches as `possiblyStale = true`. The UI (`QueryScreen`) shows the answer
as normal but with a visible "this corpus changed since this answer was
generated" warning above it. The user decides whether to re-ask.

Queued (`PENDING`) results never need this: they only get an answer once
they've actually been replayed against the live corpus, so by construction
their answer is never stale relative to the corpus state at the time it was
produced — the server response always wins over any local placeholder.

**Consequences:** The fingerprint is coarse (it can't tell you *which*
document changed, or whether the change was even relevant to the cached
question) — a deliberate simplification given lexicon has no
change-notification or diff endpoint to do better with. Documented in
`11-backlog.md` as a possible future refinement (e.g., per-citation
document-version checks) rather than solved now.

## ADR-0004: Manual dependency injection, no Hilt/Dagger

**Status:** Accepted

**Context:** This app's object graph is small: two repositories, a database,
a config store, a connectivity observer, and one API-client factory that has
to be rebuilt (not cached) whenever the user changes their server config.

**Decision:** `LexiconApplication` is a hand-written composition root.
ViewModels take their dependencies through a small `ViewModelProvider.Factory`
per screen rather than through `@HiltViewModel` injection.

**Consequences:** Adding a new dependency means one more constructor
parameter threaded through by hand instead of an annotation. Given the size
of the graph, this was judged more legible than adding a KSP-based DI
framework (and its associated build-time/version-matching surface) for a
graph this small. Revisit if the graph grows meaningfully (e.g., a second
build variant, more cross-cutting singletons).

## ADR-0005: Retrofit + kotlinx.serialization, not Moshi/Gson

**Status:** Accepted

**Context:** Needed a JSON layer for `LexiconApi`'s DTOs.

**Decision:** `kotlinx-serialization-json` + the
`retrofit2-kotlinx-serialization-converter` bridge, with the Kotlin
serialization Gradle plugin. DTOs mirror lexicon's actual snake_case field
names via `@SerialName` (verified directly against
`backend/src/lexicon/api/schemas.py`-equivalent response shapes in
`05-api-contracts.md` and the route handlers), rather than assuming a naming
convention.

**Consequences:** Consistent with using Kotlin's own serialization plugin
throughout (also used by Room's type converters indirectly); avoids adding
Moshi's separate codegen step for a project this size.
