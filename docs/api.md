# Metro Developer API

This document describes the public integration surface available through
`org.cubexmc.metro.api.MetroAPI`.

Metro exposes a compact API for reading network data, working with portals,
checking ownership and permissions, calculating fares, and checking boarding
eligibility. Prefer `MetroAPI` over raw managers whenever possible.

## Dependency Setup

Declare Metro as a plugin dependency:

```yaml
softdepend:
  - Metro
```

Use `depend` if your plugin cannot run without Metro:

```yaml
depend:
  - Metro
```

Get the API after Metro has enabled:

```java
MetroAPI api = MetroAPI.getInstance();
if (api == null) {
    return;
}
```

## Stability Rules

- `MetroAPI` is the recommended integration entry point.
- `getLineManager()`, `getStopManager()`, `getPortalManager()`, and `getPlugin()`
  are advanced and unstable.
- `getLine()`, `getStop()`, `getPortal()`, and list query methods return live
  model objects. Use snapshot methods for read-only integration data.
- Methods touching Bukkit `Player`, `Location`, worlds, or entities should be
  called from the correct server thread or Folia region context.

## Lifecycle

| Method | Description |
| :-- | :-- |
| `static MetroAPI getInstance()` | Returns the active API instance, or `null` before Metro initialization. |

`MetroAPI.initialize(Metro plugin)` is called by Metro internally.

## Read-only Settings

| Method | Description |
| :-- | :-- |
| `boolean isEconomyEnabled()` | Returns the configured economy toggle. |
| `boolean isVaultEconomyAvailable()` | Returns true when Vault economy is available. |
| `boolean isPortalsEnabled()` | Returns the configured portal toggle. |
| `String getPortalTriggerBlock()` | Returns the portal trigger block material name. |
| `double getDefaultCartSpeed()` | Returns the default cart speed. |
| `long getCartDepartureDelay()` | Returns the station departure delay in ticks. |
| `int getPortalTeleportDelay()` | Returns portal teleport delay in ticks. |
| `boolean isPassengerRailBreakProtectionEnabled()` | Returns the safe-mode passenger rail break protection toggle. |
| `boolean isFoliaRuntime()` | Returns true when Metro detected Folia. |

## Lines

| Method | Description |
| :-- | :-- |
| `Line getLine(String lineId)` | Returns a live line by id, or `null`. |
| `List<Line> getAllLines()` | Returns live loaded lines. |
| `List<Line> getLinesForStop(String stopId)` | Returns live lines containing the stop id. |
| `LineSnapshot getLineSnapshot(String lineId)` | Returns an immutable line snapshot, or `null`. |
| `List<LineSnapshot> getLineSnapshots()` | Returns immutable snapshots for all lines. |

`LineSnapshot` contains id, name, stop ids, portal ids, route points, color,
terminus name, max speed, ticket price, rail protection, route recording
metadata, owner, admins, world name, price rule snapshot, status, alternatives,
and suspension message.

## Stops

| Method | Description |
| :-- | :-- |
| `Stop getStop(String stopId)` | Returns a live stop by id, or `null`. |
| `List<Stop> getAllStops()` | Returns live loaded stops. |
| `StopSnapshot getStopSnapshot(String stopId)` | Returns an immutable stop snapshot, or `null`. |
| `List<StopSnapshot> getStopSnapshots()` | Returns immutable snapshots for all stops. |

`StopSnapshot` contains id, name, corner snapshots, stop point snapshot, launch
yaw, transferable lines, owner, admins, linked line ids, and world name.

## Portals

| Method | Description |
| :-- | :-- |
| `Portal getPortal(String portalId)` | Returns a live portal by id, or `null`. |
| `List<Portal> getAllPortals()` | Returns live loaded portals. |
| `Portal getPortalAt(Location location)` | Returns the portal matching a rail/block location, or `null`. |
| `PortalSnapshot getPortalSnapshot(String portalId)` | Returns an immutable portal snapshot, or `null`. |
| `List<PortalSnapshot> getPortalSnapshots()` | Returns immutable snapshots for all portals. |
| `PortalWriteResult createPortal(String portalId, Location entrance, UUID ownerId)` | Creates a portal entrance. |
| `PortalWriteStatus setPortalDestination(String portalId, Location destination)` | Updates a portal destination. |
| `PortalWriteStatus linkPortals(String firstPortalId, String secondPortalId)` | Links two portals both ways. |
| `PortalWriteStatus deletePortal(String portalId)` | Deletes a portal. |

`PortalSnapshot` contains entrance coordinates, destination coordinates, linked
portal id, owner, and admins.

`PortalWriteStatus` values:

| Value | Meaning |
| :-- | :-- |
| `SUCCESS` | Operation completed. |
| `NOT_FOUND` | Target portal does not exist. |
| `EXISTS` | Portal already exists. |
| `INVALID_ID` | Portal id is invalid. |
| `INVALID_LOCATION` | Entrance or destination is invalid. |
| `SAME_PORTAL` | Link request used the same portal twice. |
| `FAILED` | Operation failed for another reason. |

## Ownership and Admins

| Method | Description |
| :-- | :-- |
| `UUID getLineOwner(String lineId)` | Returns the line owner, or `null`. |
| `UUID getStopOwner(String stopId)` | Returns the stop owner, or `null`. |
| `UUID getPortalOwner(String portalId)` | Returns the portal owner, or `null`. |
| `Set<UUID> getLineAdmins(String lineId)` | Returns line admins, or an empty set. |
| `Set<UUID> getStopAdmins(String stopId)` | Returns stop admins, or an empty set. |
| `Set<UUID> getPortalAdmins(String portalId)` | Returns portal admins, or an empty set. |
| `boolean isLineServerOwned(String lineId)` | Returns true when the line has no owner. |
| `boolean isStopServerOwned(String stopId)` | Returns true when the stop has no owner. |
| `boolean isPortalServerOwned(String portalId)` | Returns true when the portal has no owner. |
| `boolean isLineAdmin(String lineId, UUID playerId)` | Checks line admin membership. |
| `boolean isStopAdmin(String stopId, UUID playerId)` | Checks stop admin membership. |
| `boolean isPortalAdmin(String portalId, UUID playerId)` | Checks portal admin membership. |

## Permission Checks

| Method | Description |
| :-- | :-- |
| `boolean canManageLine(CommandSender sender, String lineId)` | Mirrors Metro line management ownership rules. |
| `boolean canManageStop(CommandSender sender, String stopId)` | Mirrors Metro stop management ownership rules. |
| `boolean canManagePortal(CommandSender sender, String portalId)` | Mirrors Metro portal management ownership rules. |
| `boolean canModifyLineStops(CommandSender sender, String lineId, String stopId)` | Checks whether a sender may modify a line-stop relationship. |
| `boolean canLinkStopToLine(CommandSender sender, String lineId, String stopId)` | Checks whether a sender may link a stop to a line. |

## Ownership Mutations

| Method | Description |
| :-- | :-- |
| `OwnershipWriteStatus setLineOwner(String lineId, UUID ownerId)` | Transfers line ownership. |
| `OwnershipWriteStatus addLineAdmin(String lineId, UUID adminId)` | Adds a line admin. |
| `OwnershipWriteStatus removeLineAdmin(String lineId, UUID adminId)` | Removes a line admin. |
| `OwnershipWriteStatus setStopOwner(String stopId, UUID ownerId)` | Transfers stop ownership. |
| `OwnershipWriteStatus addStopAdmin(String stopId, UUID adminId)` | Adds a stop admin. |
| `OwnershipWriteStatus removeStopAdmin(String stopId, UUID adminId)` | Removes a stop admin. |
| `OwnershipWriteStatus setPortalOwner(String portalId, UUID ownerId)` | Transfers portal ownership. |
| `OwnershipWriteStatus addPortalAdmin(String portalId, UUID adminId)` | Adds a portal admin. |
| `OwnershipWriteStatus removePortalAdmin(String portalId, UUID adminId)` | Removes a portal admin. |

`OwnershipWriteStatus` values:

| Value | Meaning |
| :-- | :-- |
| `SUCCESS` | Operation completed. |
| `NOT_FOUND` | Target line, stop, portal, or admin relationship was not found. |
| `EXISTS` | Admin already exists. |
| `NOT_ADMIN` | Requested admin removal target is not an admin. |
| `INVALID_PLAYER` | UUID argument was `null`. |
| `OWNER_PROTECTED` | The owner cannot be removed from admins. |
| `FAILED` | Operation failed for another reason. |

## Line Status

| Method | Description |
| :-- | :-- |
| `LineStatus getLineStatus(String lineId)` | Returns the line status, or `NORMAL` when missing. |
| `boolean setLineStatus(String lineId, LineStatus status)` | Updates status through Metro's status service. |
| `boolean isLineSuspended(String lineId)` | Returns true when status is `SUSPENDED`. |
| `boolean isLineMaintenance(String lineId)` | Returns true when status is `MAINTENANCE`. |
| `void setSuspensionMessage(String lineId, String message)` | Sets the suspension message and saves lines data. |

`LineStatus` values:

| Value | Boardable | Meaning |
| :-- | :-- | :-- |
| `NORMAL` | Yes | Normal operation. |
| `SUSPENDED` | No | Boarding should be blocked. |
| `MAINTENANCE` | Yes | Maintenance notice state; boarding is still allowed. |

## Pricing

| Method | Description |
| :-- | :-- |
| `PriceRule getPriceRule(String lineId)` | Returns the live line pricing rule, or `null`. |
| `void setPriceRule(String lineId, PriceRule rule)` | Sets the line pricing rule and saves lines data. |
| `double calculatePrice(String lineId, String entryStopId, String exitStopId, double distanceBlocks, int intervals)` | Calculates a fare for ride data. |
| `double getEstimatedPrice(String lineId)` | Returns Metro's estimated display fare. |
| `String getPriceDescription(String lineId)` | Returns a human-readable pricing description. |

`PriceRuleSnapshot` is used in `LineSnapshot` to avoid exposing the live
`PriceRule`.

## Ticketing

| Method | Description |
| :-- | :-- |
| `TicketService.TicketCheck checkCanBoard(Player player, String lineId)` | Checks whether a player can board a line and returns the price/check status. |

For missing lines, Metro returns an insufficient-funds check with price `0`.

## Advanced Access

| Method | Description |
| :-- | :-- |
| `LineManager getLineManager()` | Advanced access to line persistence and mutation logic. |
| `StopManager getStopManager()` | Advanced access to stop persistence and mutation logic. |
| `PortalManager getPortalManager()` | Advanced access to portal persistence and mutation logic. |
| `Metro getPlugin()` | Advanced access to the plugin instance. |

Use these only when `MetroAPI` does not expose the operation you need.

## Examples

Check whether a line is boardable:

```java
MetroAPI api = MetroAPI.getInstance();
if (api == null) return;

LineStatus status = api.getLineStatus("red");
if (!status.isBoardable()) {
    // Show your own integration message.
}
```

Create a portal and link it:

```java
MetroAPI.PortalWriteResult created = api.createPortal("p1", entrance, ownerId);
if (created.status() == MetroAPI.PortalWriteStatus.SUCCESS) {
    api.setPortalDestination("p1", destination);
    api.linkPortals("p1", "p2");
}
```

Check permissions:

```java
if (api.canManageLine(sender, "red")) {
    // Show management controls.
}
```

Read safe snapshots:

```java
MetroAPI.LineSnapshot snapshot = api.getLineSnapshot("red");
if (snapshot != null) {
    List<String> stops = snapshot.orderedStopIds();
}
```

Check whether a player can board:

```java
TicketService.TicketCheck check = api.checkCanBoard(player, "red");
if (!check.canBoard()) {
    player.sendMessage("You cannot board this line right now.");
}
```

## Completed API Backlog

- [x] Read-only config/settings access.
- [x] Portal query and mutation methods.
- [x] Direct owner/admin query helpers for line, stop, and portal.
- [x] Permission helper methods.
- [x] Direct owner/admin mutation helpers.
- [x] Read-only snapshot records that avoid exposing live models.
