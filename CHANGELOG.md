# Changelog

## [Unreleased]

### Added

- JaCoCo coverage gate and SpotBugs quality gate in Maven build.
- New baseline unit tests for `LineManager`, `StopManager`, and `TrainMovementTask`.
- New `ConfigFacade` for centralized configuration reads.
- New shared constants class for minecart and scoreboard identifiers.
- Contribution guide and architecture document.

### Changed

- CI now runs `verify` before packaging.
- `Metro` delegates configuration access through `ConfigFacade`.
- Shared line command handler no longer uses mutable sender state.
- Runtime safety hardening in movement/listener paths.

### Fixed

- SpotBugs high-severity findings in metrics encoding, scoreboard null handling, and waiting sound repeat calculation.
- Additional logging for invalid line owner/admin UUID entries in `lines.yml`.
