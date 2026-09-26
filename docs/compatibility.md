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
- Geyser-Spigot and floodgate: optional soft dependencies used only for
  Bedrock-player detection. Metro keeps these integrations optional through
  reflection; when detected, mount-aware teleport flows use more conservative
  dismount and remount delays for Bedrock passengers.

## Folia Notes

- Entity work should run through entity scheduling.
- World/block work should run through region scheduling.
- Async work must not access Bukkit worlds, entities, blocks, inventories, or player state.
- Protected-route index rebuilds group candidate blocks by chunk and perform
  each scan on that chunk's region scheduler before atomically publishing the
  completed index.
- On shutdown, Metro cleans active train sessions through its train registry. Paper/Bukkit additionally run a fallback world scan for old Metro minecart leftovers; Folia schedules active train cleanup on each minecart's entity scheduler and skips that fallback scan to avoid unsafe cross-region access.

## Minecart Improvements

Metro reads `World.getFeatureFlags()` reflectively and checks the exact `minecraft:minecart_improvements` key. A missing API/flag uses the legacy movement path. No NMS dependency, world flag mutation, gamerule mutation, or newer compile API is introduced.

In an experimental world, native movement handles slopes, and Metro applies an event-driven station approach limit before the cart enters the stop region. Docked horizontal drift is corrected through the existing `SchedulerUtil.teleportEntity` path. Paper 26.1.2's same-world teleport keeps the rider mounted; older experimental implementations and Folia require their own passenger-retention validation.

See [setup and runtime evidence](minecart-improvements.md). The headless Paper 26.1.2 probe is not a full player, Geyser, Folia, or portal acceptance test. A separate existing startup issue was observed without Vault (`ClassNotFoundException: net.milkbowl.vault.economy.Economy`); physics validation used Vault installed without an economy provider. Standalone startup without Vault remains unresolved by this change.
