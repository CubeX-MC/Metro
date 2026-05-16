# Metro Release Checklist

## Pre-release

- [ ] `mvn test` passes.
- [ ] `mvn verify` passes, including tests, coverage gate, and SpotBugs.
- [ ] `mvn clean verify package` generates `target/metro-<version>.jar`.
- [ ] GitHub Actions CI workflow is green on the release branch.
- [ ] Manual baseline checklist completed (`docs/regression-baseline.md`).
- [ ] Regression world scenarios A-F from `docs/regression-baseline.md` are present or intentionally skipped with notes.
- [ ] Language keys verified for `en_US`, `zh_CN`, `zh_TW`, `de_DE`, `es_ES`, `nl_NL`.
- [ ] `plugin.yml` version and command/permission descriptions are accurate.
- [ ] `plugin.yml` permissions match the README permission table.
- [ ] Default `config.yml` keys match the paths read by `ConfigFacade`.
- [ ] Compatibility notes reviewed against `docs/compatibility.md`.

## Runtime Validation

- [ ] Boarding, departure, arrival, terminal flows validated.
- [ ] Multi-line boarding choice and transfer hub display validated.
- [ ] Route recording, route protection, and portal ride scenarios validated.
- [ ] Enabled map provider renders route lines, stop markers, transfer details, line width, and legacy/hex line colors.
- [ ] GUI and command teleport permissions are consistent.
- [ ] Reload path validated (`/m reload`) with defaults merged correctly.
- [ ] Data migration backup files (`*.bak-<schema_version>`) are created when sample legacy data is migrated.
- [ ] No severe errors in server log during ride lifecycle.

## Packaging

- [ ] Final artifact generated under `target/metro-<version>.jar`.
- [ ] Changelog includes behavior changes and migration notes.
- [ ] Release notes are drafted from `docs/release-notes-template.md`.
- [ ] Rollback instructions documented for operators.

