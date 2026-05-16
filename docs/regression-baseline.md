# Metro Runtime Baseline Checklist

This checklist is used to validate behavior after refactors and hotfixes.

## Scope

- Boarding flow (`right-click rail` -> minecart spawn -> auto board)
- Waiting/departure flow (`waiting title/actionbar/sound` -> movement starts)
- Arrival flow (`arrive title/sound` -> stop transition)
- Terminal flow (`terminal title` -> forced dismount -> cleanup)
- Manual exit flow (`vehicle exit` -> scoreboard/title cleanup -> despawn rules)

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
6. Verify exiting minecart mid-route clears title/actionbar/scoreboard.
7. Verify `/m stop tp <stop_id>` works with `metro.tp` and fails without it.
8. Verify GUI teleport behavior matches command permission semantics.
9. Verify `/m line delete <line_id>`, `/m stop delete <stop_id>`,
   `/m portal delete <portal_id>`, and `/m line clearroute <line_id>` warn
   without `confirm` and only mutate when rerun with `confirm`.
10. Run `/m reload`; verify new config defaults are present and plugin remains functional.

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

