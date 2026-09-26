# Metro Runtime Baseline Checklist

This checklist is used to validate behavior after refactors and hotfixes.

## Scope

- Boarding flow (`right-click rail` -> minecart spawn -> auto board)
- Waiting/departure flow (`waiting title/actionbar/sound` -> movement starts)
- Arrival flow (`arrive title/sound` -> stop transition)
- Terminal flow (`terminal title` -> forced dismount -> cleanup)
- Manual exit flow (`vehicle exit` -> pending fare settlement -> scoreboard/title cleanup -> despawn rules)

## Preconditions

- At least one line with 3+ stops configured.
- Every stop has valid corners and a stop point.
- `metro.use` is granted for test player.
- `metro.tp` is granted for command/gui teleport checks.
- Optional dependency checks use one enabled map provider at a time: BlueMap, Dynmap, or Squaremap.

## Test Map Scenarios

Build or keep a small regression world with these named scenarios. The IDs below are suggestions; using stable IDs makes screenshots, logs, and release notes easier to compare between versions.

### Scenario A: Single Line

- Stops: `base_a`, `base_b`, `base_c`
- Line: `base_line`
- Shape: one simple three-stop route in one world, with route points recorded from A to C.
- Purpose: baseline boarding, waiting, departure, arrival, terminal cleanup, scoreboard, and map line rendering.

### Scenario B: Bidirectional Overlap

- Stops: `north`, `center`, `south`
- Lines: `northbound`, `southbound`
- Shape: two lines share the same stop regions and rails but have opposite stop order.
- Purpose: verify right-clicking an overlapping platform opens the line choice GUI, the selected direction matches the actual train, and the most recent player choice is preferred next time.

### Scenario C: Transfer Hub

- Stops: `hub`, `east`, `west`, `market`, `harbor`
- Lines: `east_west`, `market_harbor`, optional `hub_shuttle`
- Shape: three lines include `hub`; at least two have different next stops from the hub.
- Purpose: verify multi-line ActionBar summaries, GUI line IDs, transfer details on map stop markers, and stable ordering by recent choice/yaw/line ID.

### Scenario D: Terminal Stop

- Stops: reuse the final stop from Scenario A or C.
- Purpose: verify terminal stations are excluded from boardable candidates when there is no next stop, and terminal title/actionbar messaging does not invite boarding.

### Scenario E: Cross-world Portal

- Stops: `overworld_gate`, `nether_gate`, plus one downstream stop after the destination.
- Portal pair: two crying obsidian trigger rails or the configured portal trigger block.
- Purpose: verify minecart teleport delay/effects, session continuity after transfer, and cleanup if the destination world or paired portal is unavailable.

### Scenario F: Protected Route

- Line: any recorded route with `rail_protected` enabled.
- Purpose: verify protected rail break behavior for ordinary players, line admins, OP/admin users, and players currently riding a Metro minecart.

## Manual Regression Steps

1. Right-click a rail in a non-terminal stop; verify only one minecart is pending/spawned.
2. Verify waiting countdown and waiting sound appear before departure.
3. Verify minecart departs automatically after `settings.cart_departure_delay`.
4. Verify station entry shows arrival info and station-arrival sound.
5. Verify terminal stop ejects passenger and removes minecart.
6. Verify exiting a minecart mid-route clears title/actionbar/scoreboard and
   charges an interval fare through the current target station. For distance
   pricing, verify it charges the distance actually travelled before exit.
7. Verify `/m stop tp <stop_id>` works with `metro.tp` and fails without it.
8. Verify GUI teleport behavior matches command permission semantics.
9. Verify `/m line delete <line_id>`, `/m stop delete <stop_id>`,
   `/m portal delete <portal_id>`, and `/m line clearroute <line_id>` warn
   without `confirm` and only mutate when rerun with `confirm`.
10. Run `/m reload`; verify new config defaults are present and plugin remains functional.
11. As a player with `metro.gui`, run `/m` and `/metro`; verify both open the
    same main menu as `/m gui`. Run `/m help`; verify it shows command help.
    Without `metro.gui`, verify the root command is denied consistently with
    `/m gui`; from console, verify the root command shows help instead of
    attempting to open a GUI.
12. Clone a two-stop line with `/m line clonereverse <source> <new>`; verify the
    copied line has reversed stop order, each `_rev` stop keeps the same stop
    point and has its launch yaw rotated by 180 degrees, and the source stops
    remain unchanged.

## Scenario Checks

### Multi-line Boarding

1. At Scenario B `center`, right-click the shared boarding rail.
2. Verify the line choice GUI opens and shows both candidate lines with next stop and terminus direction.
3. Choose `northbound`; verify the train departs toward `north`.
4. Return to `center`, right-click again, and verify `northbound` is sorted first due to recent choice.
5. Choose `southbound`; verify the train departs toward `south`.
6. Remove `metro.use`, open the line choice GUI as an administrator, and verify
   lines show a no-permission blocked state and clicking a line sends a localized
   denial message without boarding.

### Transfer Hub Display

1. Stand inside Scenario C `hub`.
2. Verify the Title shows the stop name and the ActionBar lists multiple boardable lines without implying only one route.
3. Open `/m gui`, inspect the line list and stop list, and verify duplicate or similar display names include IDs where needed.
4. With map integration enabled, verify the hub marker lists served lines and transfer information when `map_integration.show_transfer_info` is true.
5. Set `map_integration.show_transfer_info` to false, run `/m reload`, refresh the map, and verify transfer details are hidden while the stop marker remains.

### Route Recording And Protection

1. Run `/m line recordroute <line_id>`, ride the full line, and verify terminal auto-finish reports the saved route point count.
2. Run `/m line routeinfo <line_id>` and verify it reports route points, protected rail count, skipped samples, recorded time, recorder, and cart ID.
3. Run `/m line protect <line_id> on`, then try breaking protected rails as an ordinary player; verify the break is blocked.
4. Try breaking the same rails as the line owner or a user with `metro.admin`; verify allowed behavior matches the configured permission model.
5. Run `/m line clearroute <line_id>` through the confirmation GUI and verify route points and protection index are cleared.
6. Open a line or stop settings GUI, revoke the player's ownership/admin rights,
   then click a mutating action. Verify the action is denied and no stale GUI
   action mutates line or stop data.
7. On Folia, keep a protected recorded route enabled in each configured world
   (including the Nether), restart the server, and verify Metro enables without
   a thread-check failure.
8. On Folia, run `/m reload` and verify the completed protection index still
   blocks the recorded rails without global-thread block-read warnings.

### Portal Ride

1. Board at Scenario E `overworld_gate` and ride into the portal trigger.
2. Verify teleport effects occur after `portals.teleport_delay`.
3. Verify the same ride session continues after teleport and the scoreboard/Title target the downstream stop.
4. Temporarily misconfigure the paired portal target, run `/m reload`, and verify the ride fails cleanly without leaving an active cart or stale scoreboard.

### Map Provider Pass

Run one pass per provider that the test server has installed:

1. Set `map_integration.enabled: true`.
2. Set `map_integration.provider` to `AUTO` for the first pass, then to `BLUEMAP`, `DYNMAP`, or `SQUAREMAP` for provider-specific passes.
3. Set a line color to a legacy color, such as `&a`, and verify the route renders with that color.
4. Set a line color to a hex color, such as `&#55AAFF`, and verify the route renders with the hex color.
5. Toggle `map_integration.show_stop_markers` and verify stop markers appear/disappear without affecting route lines.
6. Change `map_integration.line_width`, run `/m reload`, trigger a map refresh through a line or stop edit, and verify rendered route width changes.

## Debug Log Categories

- `settings.debug.train_state_transitions`
- `settings.debug.interaction_flow`

Enable with:

```yml
settings:
  debug:
    enabled: true
```


## Experimental Minecart Regression

On a copied Paper 26.1.2 world with Minecart Improvements enabled:

1. Use a powered straight track and a line cap of 3 blocks/tick. Measure displacement over server ticks; confirm occupied speed exceeds 30 blocks/second at 20 TPS. Repeat without the world flag as the legacy control.
2. Approach a narrow destination region at high speed; verify braking begins before region entry, arrival settles once, and a winding approach can regain its line cap when moving away from the destination.
3. Wait through a full departure delay on powered rail next to a solid launch block. Verify no horizontal drift, no dismount, and normal departure. Repeat with player directional input.
4. Ride slopes, curves, a low-speed BLOCK_BASED section, intermediate/terminal stops, and a portal between legacy/experimental worlds. Ensure departure and transferred carts use the intended line speed.
5. Repeat boarding/docking with Java and Bedrock clients, and then on Folia with region boundaries near a station. These player/platform checks remain manual; the headless occupied-cart probe does not establish their support.
6. Upgrade a v4 config containing enabled cruise settings. Verify v5 removes only `speed_control.cruise_control`, preserves speed and safe-mode values, and saves the original under `backups/migrations/`. Repeat reload without creating a new migration.

## Station building and Title verification (2026-09-13)

- `:Metro:build :Metro:jarGate`: 644 tests passed; embedded Java 17 artifact gate passed.
- BuildingWorkflowTest covers complete creation, area-only fallback, cross-world rejection, permissions, standing rail lookup, implicit linking and overlapping-stop rejection.
- Title tests cover arrival enablement, waiting MiniMessage/countdown refresh and exit cancellation, configured timings, terminal precedence, re-entry, cross-world teleport, multi-line overrides and quit cleanup.
- Language migration test covers optional command help, added feedback, custom translation preservation and repeat application.
- Isolated Paper 26.1.2 build 74 / Java 25: command registration and all seven v3-to-v4 language migrations completed. Vault and the existing physics probe were present. No human client was connected.
- Before release, manually select an area, stand on a powered rail facing departure, create a stop without a name and verify its point/direction. Add it to a line without supplying a stop ID; repeat inside overlapping stops and check explicit-ID feedback.
- With a real client, verify waiting countdown, departure visibility, arrival/terminal toggles, custom templates and multi-line station displays; re-enter after editing templates. Repeat scheduler-sensitive flows on Folia. These visual/Folia checks are not established by the unit tests or Paper startup probe.