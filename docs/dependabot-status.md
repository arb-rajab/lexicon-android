# Dependabot status

_Last updated: 2026-10-08. Maintained during the Dependabot clean-up pass; update when the state changes._

## Configuration

- Ecosystems covered: gradle (`/`), github-actions (`/`).
- Grouping: `kotlin` group for `org.jetbrains.kotlin.*` Gradle plugins (they must move together; see #15). Everything else is one PR per update.
- Schedule: weekly.
- Ignore rules: none.

## State at last update

- Open Dependabot PRs: 0 (each merged or closed only after reading its checks).
- Default-branch CI: green at last check.

## Time-limited exemptions

- `osv-scanner.toml`: five package/version entries in build/test-only packages (logback-core 1.3.14, bcprov 1.80.2, bcpkix 1.80.2, commons-lang3 3.16.0, httpclient 4.5.6) that are absent from the shipped APK; each has `effectiveUntil` 2026-12-28 and clears as AGP/ktlint/Robolectric are bumped. The `guava` 31.1-jre and `bcprov` 1.78.1 entries were removed on 2026-10-08: neither version is in `app/gradle.lockfile` any more (guava is 33.x, bcprov 1.80.2 / 1.85), so they only risked silently waiving those versions if they returned.

## Notes

- Instrumented tests (API 30) run on every PR and take ~3-5 minutes; read all check runs before merging.

## Deferred (not re-raised each pass)

- Ignored major versions are listed in `.github/dependabot.yml` with the reason for each.
- Re-check exemptions before their `effectiveUntil` date (2026-11-15) and drop them once upstream fixes ship.
