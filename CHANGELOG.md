# Changelog

## Unreleased

- **Building workflow**: create complete stops from selection, standing powered rail and facing; optional names and contextual stop IDs for linking and editing, with overlap protection and language v4 migration.
- **Station Titles**: honor arrival enablement and waiting timings/countdown; render custom MiniMessage templates; restore re-entry and cross-world displays; support multi-line templates and arrival/terminal actionbars; publish departure after clearing the waiting display.

- **Experimental minecarts**: detect the world's Minecart Improvements flag without raising the Spigot 1.18.2 / Java 17 build baseline. Use native slope movement, brake before a fast cart crosses a narrow station, and correct powered-rail drift while docked. Paper 26.1.2 runtime results and remaining player/Folia checks are recorded in `docs/minecart-improvements.md`.
- **Cruise control removed**: remove the ineffective velocity-based cruise task and its config/getters. Config v4 → v5 removes only `speed_control.cruise_control`, with the migration runner's automatic backup. Safe-mode stall recovery remains.

- **Fare destination**: fares from a line with **no owner** used to be withdrawn
  and destroyed. The new `economy.account` names the server account they are
  paid into instead (player UUID, `name:<account>`, a player name, or
  `bank:<name>`), reusing the shared `cubex-economy` routing. Owned lines are
  unchanged - they still pay their owner. Config migrates to v4 on first start
  with the key empty, which is exactly the old behaviour.
- **Economy compatibility**: rebind the active Vault economy provider when
  services register/unregister and during `/m reload`, so late-loading or
  hot-reloaded currency providers remain usable.
- **Fare settlement**: charge interval fares from the last settled station,
  settle a mid-route exit through the current target station, and refund the
  passenger if the line-owner payout fails.
- **Mid-route dismount**: `settings.safe_mode.passenger_exit_lock` now defaults
  to off, so a passenger can never be trapped in a cart that stopped short of a
  station. Fare evasion is handled by the new `economy.mid_route_exit_fare`
  (`NEXT_STOP` by default), which bills a mid-route exit as if the passenger had
  ridden through to the next stop. Config and language files migrate to v3 on
  first start; an existing `passenger_exit_lock: true` is kept as-is and
  reported in the server log rather than flipped.
- **GUI safety**: reject drag operations that touch Metro GUI slots while
  allowing drags confined to the player's own inventory.

## 1.1.9 (2026-07-15)

- **Folia**: rebuild protected-route rail indexes through region-owned chunk
  tasks and atomically publish the completed index, preventing startup and
  reload failures caused by global-thread block reads.

## 1.1.8 (2026-07-10)

- **Train display**: improve circular-line station planning, folded terminal
  display, scoreboard rendering, and hex color handling for titles/action bars.
- **Portals**: simplify portals to a one-way destination model; remove the
  deprecated bidirectional `portal link` command and linked-portal list text.
- **Kotlin migration**: continue the Metro Kotlin migration across API,
  config/manager/service, train, lifecycle, and utility code paths.
- **Spatial**: fix `Range3D.contains` half-open interval bug (mismatch with
  Bukkit `BoundingBox`); remove `Range3D` dependency from `Stop.java`
- **Docs**: add JavaDoc to `Range3D`, `Point3D`, `Octree`; add `@since`
  annotations to all `MetroAPI` methods; configure `maven-javadoc-plugin`

## 1.1.6

- **Pricing**: add `PriceRule` with flat/distance/interval modes, time-based
  discounts; replace flat `ticketPrice` with rule-based `PriceService`
- **Line status**: add `NORMAL` / `MAINTENANCE` / `SUSPENDED` states with
  alternate-route suggestions on suspension
- **Public API**: introduce `MetroAPI.getInstance()` with snapshot records,
  ownership queries, line status, pricing, portal mutations
- **Commands**: `setprice` (flat/distance/interval/reset), `setstatus`,
  `priceinfo`
- **Events**: `LineStatusChangeEvent`
- **Train**: distance/interval fare settlement at station arrival
- **Map**: BlueMap 3D stop volumes, orthogonal routes, route sampling config;
  dynmap/squaremap improvements
- **Folia**: 26.1.2 compat, command/scoreboard fallbacks

## 1.1.5

- **Spatial**: introduce `Octree` + `Range3D` spatial index for O(log N) stop
  queries; use Bukkit `BoundingBox` for containment checks
- **Selection**: `SelectionManager` + selection tool (default golden shovel)
- **GUI**: line boarding choice GUI for stops served by multiple lines
- **Stop titles**: configurable `stop_continuous`, `arrive_stop`,
  `terminal_stop`, `departure` title/subtitle/actionbar
- **Commands**: `cloud` command framework migration complete
- **Portal**: admin permission checks, line-bound portal usage, link command
- **Protection**: rail break protection in safe mode
- **Lifecycle**: configurable async save coordinator

## 1.1.4

- **Route recording**: `RouteRecorder` with collinear point normalisation
- **Scheduling**: Folia-aware `SchedulerUtil` (region/entity/global dispatch)
- **Map integration**: Dynmap, BlueMap, Squaremap lifecycles
- **Scoreboard**: per-train in-game ETA display
- **Ownership**: stop/line/portal admin and permission model
- **Localisation**: `zh_CN`, `zh_TW`, `en_US`, `de_DE`, `es_ES`, `nl_NL`,
  `tr_TR`
- **Data migration**: auto-upgrade from 1.0.x schemas

## 1.0.10

- Fix minecart stopping at terminal stops
- Basic continuous title display
- Original command structure (pre-`cloud` framework)
