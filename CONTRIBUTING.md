# Contributing to Metro

## Local Setup

- Use Java 17.
- Use Maven 3.9+.
- Run `mvn -pl metro-modern -am test` before opening a PR.
- Run `mvn -pl metro-modern -am verify` to include coverage and SpotBugs gates.

## Branch and PR Rules

- Keep each PR focused on one concern (tests, refactor, bugfix, docs).
- Prefer small, reviewable commits.
- Do not mix behavior changes with formatting-only edits.
- Include a short test plan in each PR description.

## Required Checks

- CI must pass on `main` and on your branch.
- Coverage and SpotBugs checks must pass.
- For runtime-impacting changes, run manual checks from `docs/regression-baseline.md`.

## Coding Conventions

- Keep command handlers small and route-only in entry classes.
- Avoid adding new magic strings for IDs and objective names; use constants.
- Add defensive null checks on config-driven paths and log actionable warnings.

## Release Preparation

- Follow `docs/release-checklist.md`.
- Ensure language keys remain consistent across locale files.
- Update `CHANGELOG.md` for user-visible changes.
