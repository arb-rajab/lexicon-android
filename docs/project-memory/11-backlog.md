# Backlog

> Project: lexicon-android (public)
> Last updated: 2026-10-03

## Near-term

- [ ] **Re-justify or drop the 11 `osv-scanner.toml` package overrides
      before 2026-12-28.** Each entry in the suppression list (added
      alongside the security workflow — netty, guava, protobuf, commons-io,
      bouncycastle, logback, all confirmed build/test-tooling-only, none on
      a shipped classpath) carries `effectiveUntil = 2026-12-28`, matched by
      exact name+version so a new package or version is never silently
      covered. When that date arrives, `dependency-scan` starts failing on
      whichever of the 11 are still present unless someone either re-checks
      each one is still tooling-only and pushes the expiry out, or the
      underlying tools (ktlint/AGP/ksp/Robolectric) have been bumped past
      the flagged versions by then, in which case the override can just be
      deleted. `.github/CODEOWNERS` now requires @arb-rajab's review on any
      change to `osv-scanner.toml` itself, so this won't land unreviewed.
      **Update (Session N+6, toolchain upgrade):** there are now 15
      overrides. Four new ones (approved by the owner) cover AGP 9's
      `androidLintTool` configuration: bcprov-jdk18on and bcpkix-jdk18on
      1.80.2, commons-lang3 3.16.0, httpclient 4.5.6. **Eight of the
      original 11 now match nothing** in the regenerated lockfile:
      protobuf-java 3.22.3, commons-io 2.13.0 and the six netty
      4.1.93.Final entries all came from AGP 8.7's old
      `_internal-unified-test-platform-*` configurations, which AGP 9 no
      longer has. Unused overrides don't fail the scan; whether to delete
      them is the owner's call (deliberately not done in the upgrade PR).
      Still used: logback-core 1.3.14 (ktlint), guava 31.1-jre (ksp),
      bcprov-jdk18on 1.78.1 (Robolectric).
      **Update (2026-10-03, cleanup PR):** the eight dead overrides were
      deleted at the owner's request after re-checking each against the
      current `app/gradle.lockfile` (exact name+version absent). **7 remain,
      all still matched by the lockfile:** logback-core 1.3.14 (`ktlint`),
      guava 31.1-jre and bcprov-jdk18on 1.78.1 (`debugUnitTest*` classpaths
      only — guava's reason text said "ksp", corrected), and the four AGP 9
      `androidLintTool` entries. All 7 still expire 2026-12-28.

The three items tracked as of Session N's handoff (MockWebServer
integration tests, `QueryScreen` pull-to-refresh, a `FAILED`-result retry
button) were completed in Session N+1, and — unlike every prior round of
work on this project — actually confirmed green in CI, not just written and
hoped for. See `12-session-handoff.md`'s Session N+1 entry for what CI's
first real end-to-end run surfaced and how each issue was fixed (a wrong
`PullToRefreshBox` import package, a stray import that shadowed an internal
Compose symbol, ktlint formatting, and one genuinely wrong Compose-testing
API reference in code that had never compiled before).

## Admin-only, not actionable by any automated session

- [x] **Add the security checks to `main`'s required status checks.**
      Done — the repo owner added `Secret scan (gitleaks)`,
      `CodeQL (java-kotlin)`, and `Dependency scan (osv-scanner)` to the
      `main` rule's required status checks alongside the existing
      `lint-and-test` and `instrumented-tests (30)`, confirmed via
      `GET /repos/arb-rajab/lexicon-android/branches/main`'s
      `protection.required_status_checks.contexts` listing all five. No
      session tool could do this directly (confirmed again immediately
      before: `GET/PATCH .../branches/main/protection` 403s with "Resource
      not accessible by integration" even when the repo is attached with
      push access) — it took the owner doing it by hand in Settings →
      Branches. NOTE for future changes to these jobs: the required names
      match the job `name:` values in `android-ci.yml`/`security.yml`
      exactly — renaming a job un-requires its check and blocks every merge
      until the rule is updated (this happened once already, while adding
      the security workflow). `.github/CODEOWNERS` (scoped to
      `.github/workflows/`, `osv-scanner.toml`, `app/gradle.lockfile`,
      owner @arb-rajab) is also done; required-approvals review is still
      optional hardening, not enabled.

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
