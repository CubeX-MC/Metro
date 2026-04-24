# Metro Release Checklist

## Pre-release

- [ ] `mvn -pl metro-modern -am test` passes.
- [ ] `mvn -pl metro-modern -am package` passes.
- [ ] Manual baseline checklist completed (`docs/regression-baseline.md`).
- [ ] Language keys verified for `en_US`, `zh_CN`, `zh_TW`, `de_DE`, `es_ES`, `nl_NL`.
- [ ] `plugin.yml` version and command/permission descriptions are accurate.

## Runtime Validation

- [ ] Boarding, departure, arrival, terminal flows validated.
- [ ] GUI and command teleport permissions are consistent.
- [ ] Reload path validated (`/m reload`) with defaults merged correctly.
- [ ] No severe errors in server log during ride lifecycle.

## Packaging

- [ ] Final artifact generated under `metro-modern/target`.
- [ ] Changelog includes behavior changes and migration notes.
- [ ] Rollback instructions documented for operators.

