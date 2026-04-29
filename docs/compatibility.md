# Metro Compatibility

## Runtime Requirements

- Java: 17+
- Minecraft server API: 1.18+
- Primary build API: Spigot API 1.18.2
- Plugin API version: `1.18`

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
- On shutdown, Metro cleans active train sessions through its train registry. Paper/Bukkit additionally run a fallback world scan for old Metro minecart leftovers; Folia skips that fallback scan to avoid unsafe cross-region access.
