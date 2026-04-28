# Metro Release Checklist

## Pre-release

- [ ] `mvn test` passes.
- [ ] `mvn package` passes.
- [ ] Manual baseline checklist completed (`docs/regression-baseline.md`).
- [ ] Language keys verified for `en_US`, `zh_CN`, `zh_TW`, `de_DE`, `es_ES`, `nl_NL`.
- [ ] `plugin.yml` version and command/permission descriptions are accurate.
- [ ] `plugin.yml` permissions match the README permission table.
- [ ] Default `config.yml` keys match the paths read by `ConfigFacade`.

## Runtime Validation

- [ ] Boarding, departure, arrival, terminal flows validated.
- [ ] GUI and command teleport permissions are consistent.
- [ ] Reload path validated (`/m reload`) with defaults merged correctly.
- [ ] No severe errors in server log during ride lifecycle.

## Packaging

- [ ] Final artifact generated under `target/metro-<version>.jar`.
- [ ] Changelog includes behavior changes and migration notes.
- [ ] Rollback instructions documented for operators.

