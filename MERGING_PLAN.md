# Metro Merge Plan

This document records the concrete merge strategy for integrating features from
the `CYY` branch into `main`.

## Branch Strategy

**Create an integration branch `integration/cyy-merge` from `main`** — do NOT
modify `main` directly. The 73 files / 3369 lines are too large to review in one
PR and touch too many subsystems simultaneously.

Workflow:
1. `git checkout main && git checkout -b integration/cyy-merge`
2. Merge features in phases via separate PRs targeting `integration/cyy-merge`
3. After all phases are merged and tested, `integration/cyy-merge` → `main`

## Feature Overview (from CYY)

| Feature | Status in CYY | Merge Action |
|---------|--------------|--------------|
| FareRule model | Complete | Cherry-pick as-is |
| LineStatus enum | Complete | Cherry-pick as-is |
| FareService | Complete | Cherry-pick as-is |
| LineStatusService | Complete | Cherry-pick with minor wiring |
| LineStatusChangeEvent | Complete | Cherry-pick as-is |
| MetroAPI | Complete but exposes managers | Rewrite to route through services |
| Line new fields (fareRule, status, etc.) | Complete | Cherry-pick fields only, skip Javadoc removal |
| LineManager save/load | Complete | Cherry-pick new field persistence |
| CYY setfare logic (FareRule + FareService) | Complete | Fold into existing setprice command |
| Distance tracking (TrainSession/TrainMovementTask) | Infrastructure only | Cherry-pick, then wire end-to-end |
| TicketService fare integration | Partial | Rewrite to fully wire fare calculation |
| GUI status/pricing integration | Complete | Cherry-pick with command alignment |
| Stop status commands | Complete | Cherry-pick |
| Language files | Complete | Merge with command name adjustments |
| config.yml additions | Complete | Merge with backward compat |
| 1.16 compatibility changes | Invasive | EXCLUDE — handle in separate branch |

## Phase 1: Foundation Models (can cherry-pick)

**Files:**
- `model/FareRule.java` — new file, pure model
- `model/LineStatus.java` — new file, pure enum
- `model/RoutePoint.java` — cherry-pick utility additions only
- `train/TrainSession.java` — cherry-pick distanceTraveled field + methods only

**Line.java additions** (cherry-pick only new fields, skip Javadoc/style changes):
- `fareRule` field + getter/setter
- `lineStatus` field + getter/setter
- `alternativeRouteIds` field + getter/setter/add/remove
- `suspensionMessage` field + getter/setter

**Verification**: compiles, existing tests pass.

## Phase 2: Service Layer (minimal adjustments)

**New files (cherry-pick as-is):**
- `service/FareService.java`
- `service/LineStatusService.java`
- `event/LineStatusChangeEvent.java`

**Modified files (partial cherry-pick):**
- `service/LineCommandService.java` — add `setPriceRule()` / `resetPriceRule()` /
  `setLineStatus()` methods (integrate CYY's FareRule logic under setprice)
- `service/TicketService.java` — integrate FareRule into boarding price check;
  wire `calculateFare()` with actual distance traveled
- `manager/LineManager.java` — add save/load for:
  - `fare_rule` section (mode, base_fare, per_block_rate, per_interval_rate,
    max_fare, time_discounts)
  - `status` field (NORMAL / SUSPENDED / MAINTENANCE)
  - `suspension_message` field
  - `alternative_routes` list

**Verification**: save/load round-trip test; boarding check works with FareRule.

## Phase 3: Command Rework (requires rewriting)

**Rewrite `setprice` to absorb FareRule modes:**
```
/m line setprice <line> <price>                    # legacy flat, kept unchanged
/m line setprice <line> flat <base>                # explicit flat
/m line setprice <line> distance <base> <perBlock> [max]  # distance-based
/m line setprice <line> interval <base> <perStop> [max]   # interval-based
/m line setprice reset <line>                      # revert to legacy ticketPrice
```

**Drop `setfare` command entirely.** It duplicates `setprice` conceptually.
Keep the `fareinfo` subcommand under `setprice` or as a top-level info command.

**Files to modify:**
- `command/newcmd/LineCommand.java` — replace setfare methods with extended
  setprice; add fareinfo subcommand
- `command/newcmd/LineCommandView.java` — add fare info display methods
- `command/newcmd/StopCommand.java` — cherry-pick status commands
- `command/newcmd/CommandGuard.java` — cherry-pick permission updates
- `command/newcmd/MetroMainCommand.java` — cherry-pick registration changes
- `lifecycle/CommandRegistration.java` — add fare mode + status suggestions
- `lifecycle/BukkitFallbackCommandRegistration.java` — same

**Verification**: all command forms work; tab completion correct; permissions
unchanged for legacy syntax.

## Phase 4: Public API (rewrite)

**`api/MetroAPI.java`** — rewrite to route through services instead of exposing
managers directly:
- All mutations go through `LineCommandService`, `StopCommandService`,
  `PortalCommandService`
- Read-only queries return DTOs or snapshots, never mutable internal objects
- Fare calculations go through `FareService`
- Status checks go through `LineStatusService`

**`Metro.java`** — add `getFareService()`, `getLineStatusService()` getters.

## Phase 5: Integration Wiring (end-to-end)

**Distance-based fare — complete the circuit:**
- `train/TrainMovementTask.java` — already tracks distance (cherry-pick confirm)
- `train/TrainSession.java` — already stores distanceTraveled (Phase 1)
- Wire `getDistanceTraveled()` → `FareService.calculateDistanceFare()` at
  VehicleExitEvent / arrival, then charge the delta via `TicketService.chargeFare()`

**Status checks during boarding:**
- `listener/PlayerInteractListener.java` — add `LineStatusService` check before
  allowing boarding; show suspension message
- `listener/VehicleListener.java` — cherry-pick status-related changes
- `listener/PlayerMoveListener.java` — cherry-pick status display changes

**Map integration:**
- `integration/DynmapIntegration.java` — cherry-pick status rendering
- `integration/BlueMapIntegration.java` — cherry-pick status rendering
- `integration/SquaremapIntegration.java` — cherry-pick status rendering
- `integration/MapGeometry.java` — cherry-pick
- `integration/MapLineColor.java` — cherry-pick

**Verification**: board a line with distance fare → charged correctly based on
actual blocks traveled; suspended line → boarding blocked; maintenance line →
warning shown.

## Phase 6: GUI (cherry-pick with alignment)

Cherry-pick GUI changes, verifying they align with renamed commands:
- `gui/GuiColors.java`
- `gui/GuiListener.java`
- `gui/GuiManager.java`
- `gui/controller/AddStopController.java`
- `gui/controller/ConfirmActionController.java`
- `gui/controller/LineBoardingChoiceController.java`
- `gui/controller/LineDetailController.java`
- `gui/controller/LineListController.java`
- `gui/controller/LineSettingsController.java`
- `gui/controller/MainMenuController.java`
- `gui/controller/StopListController.java`
- `gui/controller/StopSettingsController.java`
- `gui/view/*` (all modified views)

**Verification**: GUI menus reflect line status, fare info, and route all
actions through services.

## Phase 7: Language & Config

- `resources/lang/*.yml` — merge CYY's fare/status language keys; rename
  `setfare_*` → `setprice_*`; keep existing setprice keys for backward compat
- `resources/config.yml` — add new config sections; keep existing keys unchanged
- `resources/plugin.yml` — only update if adding new permissions; do NOT change
  API version

## Explicitly Excluded

| Change | Reason |
|--------|--------|
| `instanceof` pattern → explicit cast | 1.16 compat — handle in legacy branch |
| `isBlank()` → `trim().isEmpty()` | 1.16 compat — handle in legacy branch |
| `VersionUtil.java` 1.16 support | 1.16 compat — handle in legacy branch |
| Line.java Javadoc removal | Unrelated churn — restore comments |
| `pom.xml` Java version downgrade | Risk of breaking main baseline |
| `plugin.yml` API version claims for 1.16 | Must match actual support |

## Verification Gates (per phase)

1. `mvn compile` passes
2. Existing unit tests pass
3. New tests added for FareRule calculation, LineStatusService, API methods
4. Manual smoke test on a local server:
   - Create line, add stops, set distance/interval fare
   - Ride the line, verify fare charged matches distance traveled
   - Suspend line, verify boarding blocked
   - Check GUI and map display reflect status
