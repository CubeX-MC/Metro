# Changelog

## [Unreleased]

### Added

- JaCoCo coverage gate and SpotBugs quality gate in Maven build.
- New baseline unit tests for `LineManager`, `StopManager`, and `TrainMovementTask`.
- New model, spatial index, configuration migration, and ownership permission tests.
- New `LineSelectionService` for stable boardable-line filtering, default resolution, and recent player choices.
- New `ConfigFacade` for centralized configuration reads.
- New `SaveCoordinator` for versioned YAML snapshots and atomic persistent data writes.
- New shared constants class for minecart and scoreboard identifiers.
- Contribution guide and architecture document.

### Changed

- CI now runs `verify` before packaging.
- `Metro` delegates configuration access through `ConfigFacade`.
- Shared line command handler no longer uses mutable sender state.
- Runtime safety hardening in movement/listener paths.
- Removed unused legacy `MetroAdminCommand` and `MetroAdminTabCompleter`; Cloud annotation commands remain the active command entry.
- Raised the JaCoCo line coverage gate from 6% to 15%.
- Right-click boarding now resolves lines through `LineSelectionService` instead of relying on collection iteration order.
- Line and stop saves now flush through a shared coordinator before reload/disable.

### Fixed

- SpotBugs high-severity findings in metrics encoding, scoreboard null handling, and waiting sound repeat calculation.
- Additional logging for invalid line owner/admin UUID entries in `lines.yml`.
- Stale async line/stop saves can no longer overwrite newer YAML snapshots.
