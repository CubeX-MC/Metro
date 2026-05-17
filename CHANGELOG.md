# Changelog

## 1.1.7 (unreleased)

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
