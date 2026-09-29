# Backlog

> Project: lexicon-android (public)
> Last updated: 2026-09-28

## Near-term

Empty. The three items tracked as of Session N's handoff (MockWebServer
integration tests, `QueryScreen` pull-to-refresh, a `FAILED`-result retry
button) were completed in Session N+1, and — unlike every prior round of
work on this project — actually confirmed green in CI, not just written and
hoped for. See `12-session-handoff.md`'s Session N+1 entry for what CI's
first real end-to-end run surfaced and how each issue was fixed (a wrong
`PullToRefreshBox` import package, a stray import that shadowed an internal
Compose symbol, ktlint formatting, and one genuinely wrong Compose-testing
API reference in code that had never compiled before). Nothing is currently
tracked as near-term backlog beyond the design gaps and explicitly-out-of-
scope items below.

## Admin-only, not actionable by any automated session

- [ ] **Add the security checks to `main`'s required status checks.**
      `main` is now protected (set by the repo owner after the Session N+4
      audit found it unprotected): PR required, "require branches to be up to
      date", administrators not exempt, and status checks `lint-and-test` and
      `instrumented-tests (30)` required. Still to add under Settings →
      Branches → the `main` rule → required status checks (admin-only; no
      session tooling can read or write this): `Secret scan (gitleaks)`,
      `CodeQL (java-kotlin)`, `Dependency scan (osv-scanner)` (all from
      `.github/workflows/security.yml`; none are path-filtered). NOTE: the
      required names match the job `name:` values in `android-ci.yml`
      exactly — renaming those jobs un-requires the check and blocks every
      merge until the rule is updated (this happened once while adding the
      security workflow). Optional hardening discussed but not enabled:
      required approvals, CODEOWNERS on `.github/workflows/` and
      `osv-scanner.toml`, code-owner review.

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
