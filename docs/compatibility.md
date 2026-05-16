# Metro Compatibility

## Runtime Requirements

- Java: 17+
- Minecraft server API: 1.18+
- Primary build API: Spigot API 1.18.2
- Plugin API version: `1.18`

## Minecraft 26.1.2 Strategy

- Metro keeps Java 17 bytecode and Spigot API 1.18.2 as the build baseline so one jar can continue to support 1.18+ servers.
- Minecraft/Paper 26.1.2 is treated as a runtime validation target, not as the compile API baseline.
- Paper 26.1.2 servers require Java 25 at runtime. This does not require Metro to compile with Java 25 unless Metro intentionally adopts Java 25 language/runtime APIs.
- Newer Paper-only APIs must stay behind reflection or compatibility adapters with older-version fallbacks.
- Do not add NMS, CraftBukkit, or versioned server package references for this compatibility work.

Recommended smoke-test matrix before releasing a 26.1.2-compatible build:

- 1.18.2 server on Java 17.
- Current stable Paper 1.21.x server on Java 21.
- Paper 26.1.2 server on Java 25.

Smoke tests should cover plugin startup, Cloud command registration, GUI opening, line/stop management, train departure, passenger billing, and optional map/economy dependencies disabled.

## Server Platforms

- Spigot: supported for core gameplay and administration features.
- Paper: supported and recommended for production servers.
- Folia: marked `folia-supported: true`; Metro uses `SchedulerUtil` to route entity, region, global, and async work through Folia APIs when available.

## Optional Dependencies

- Vault: optional economy integration for ticket pricing and owner payouts.
- BlueMap: optional map marker integration.
- dynmap: optional map marker integration.
- squaremap: optional map marker integration.
- ViaVersion: optional soft dependency for mixed-client environments.

## Folia Notes

- Entity work should run through entity scheduling.
- World/block work should run through region scheduling.
- Async work must not access Bukkit worlds, entities, blocks, inventories, or player state.
- On shutdown, Metro cleans active train sessions through its train registry. Paper/Bukkit additionally run a fallback world scan for old Metro minecart leftovers; Folia schedules active train cleanup on each minecart's entity scheduler and skips that fallback scan to avoid unsafe cross-region access.
