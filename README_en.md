# Metro Transit System

English | [简体中文](README.md)

![bstats](https://bstats.org/signatures/bukkit/Metro.svg)

##概述

Metro is a transit system plugin inspired by the Niú nǎn xiǎo zhèn server, allowing administrators to create automated metro line networks and provide players with a convenient riding experience.

This fork (Metro-Forked) Developed and maintained by A-Qing and others adds significant enhancements to the original [CubeX-MC/Metro](https://github.com/CubeX-MC/Metro) while maintaining compatibility with MC 1.16.5+ and Java 8+ servers.

### New Features

This fork adds the following features on top of the original:

- **Advanced Fare System**: Three pricing modes (flat, distance-based, interval-based) with configurable base fare, per-unit rate, and maximum fare cap
- **Time Discounts**: Discount periods based on in-game time (e.g., half-price at night), supports overnight ranges
- **Line Operation Status**: Set lines to normal/suspended/maintenance; suspended lines prevent boarding
- **Alternative Routes**: Automatically suggest alternative routes to players when a line is suspended
- **Suspension Notices**: Custom notification messages when a line is suspended
- **MetroAPI**: Public API for other plugins to query lines, stops, fares, line status, etc.

## Basic Concepts

- **Line**: A metro line containing an ordered list of stops that defines the train's path
- **Stop**: A metro station defined as a 3D region by two diagonal corner points
- **StopPoint**: A powered rail within a stop area where players right-click to board
- **转移**: Players can transfer between lines at stops

## Admin Commands (Main Command: `/m`)

### Line Management

| Command | Description |
| :--- | :--- |
| `/m line create <line_id> <display_name>` | Create a new line |
| `/m line delete <line_id>` | Delete a line |
| `/m line list` | List all lines |
| `/m line rename <line_id> <new_name>` | Rename a line |
| `/m line setcolor <line_id> <color>` | Set line color |
| `/m line setterminus <line_id> <name>` | Set terminus direction description |
| `/m line setmaxspeed <line_id> <speed>` | Set line maximum speed |
| `/m line setprice <line_id> <price>` | Set line flat ticket price |
| `/m line addstop <line_id> <stop_id> [index]` | Add a stop to the line (optional position) |
| `/m line delstop <line_id> <stop_id>` | Remove a stop from the line |
| `/m line stops <line_id>` | List all stops on the line |
| `/m line addportal <line_id> <portal_id>` | Allow line to use a portal |
| `/m line delportal <line_id> <portal_id>` | Remove a portal from the line |
| `/m line portals <line_id>` | List portals enabled for the line |
| `/m line info <line_id>` | View line details and permissions |
| `/m line trust <line_id> <player>` | Grant line management permission |
| `/m line untrust <line_id> <player>` | Remove line management permission |
| `/m line owner <line_id> <player>` | Transfer line ownership |
| `/m line recordroute <line_id>` | Record/save route points |
| `/m line clearroute <line_id>` | Clear route points |
| `/m line routeinfo <line_id>` | View route and rail protection status |
| `/m line protect <line_id> <on\|off\|status>` | Enable/disable/view rail protection |
| `/m line clonereverse <source_id> <new_id> [suffix]` | Clone line in reverse |
| `/m line setfare <line_id> <mode> <baseFare> [perUnit] [maxFare]` | **Configure advanced fare rules** |
| `/m line fareinfo <line_id>` | **View fare details and time discounts** |
| `/m line setstatus <line_id> <status>` | **Set line operation status** |
| `/m line setaltroute <line_id> <altLineId>` | **Add alternative route suggestion** |
| `/m line delaltroute <line_id> <altLineId>` | **Remove alternative route** |
| `/m line setsuspensionmsg <line_id> <message>` | **Set suspension notification message** |

#### Fare Rule Configuration

```
/m line setfare <line_id> <mode> <baseFare> [perUnit] [maxFare]
```

| Parameter | Description |
| :--- | :--- |
| `<mode>` | Pricing mode: `flat`, `distance`, or `interval` |
| `<baseFare>` | Base fare amount |
| `[perUnit]` | Per-block or per-interval rate (required for distance/interval modes) |
| `[maxFare]` | Maximum fare cap (optional, 0 = no cap) |

**Example:**
```
/m line setfare line1 distance 2.0 0.05 20.0
```
— Line line1 uses distance-based pricing, base fare 2.0, per-block rate 0.05, max 20.0

**Time Discount Configuration (manual edit in lines.yml):**

Add `time_discounts` under the line's `fare_rule` node in `lines.yml`:

```yaml
fare_rule:
  mode: distance
  base_fare: 2.0
  per_block_rate: 0.05
  max_fare: 20.0
  time_discounts:
    - start_tick: 0      # Tick 0 = sunrise
      end_tick: 6000     # Tick 6000 = noon
      multiplier: 0.8    # 20% off
    - start_tick: 13000  # dusk
      end_tick: 6000     # overnight until next noon
      multiplier: 0.5    # 50% off (night discount)
```

> Game tick to time mapping: 0=Sunrise(6:00), 6000=Noon(12:00), 12000=Sunset(18:00), 13800=Night starts, 18000=Midnight(0:00), 22200=Dawn starts, 24000=Next sunrise

#### Line Operation Status

```
/m line setstatus <line_id> <status>
```

| Value | Description |
| :--- | :--- |
| `normal` | Normal operation (default) |
| `suspended` | Suspended, players cannot board |
| `maintenance` | Under maintenance, players can still ride |

When a line is suspended, players attempting to board will receive a notification message and alternative route suggestions.

### Stop Management

| Command | Description |
| :--- | :--- |
| `/m stop create <stop_id> <display_name>` | Create a new stop from selection |
| `/m stop delete <stop_id>` | Delete a stop and all its configuration |
| `/m stop list` | List all stops |
| `/m stop rename <stop_id> <new_name>` | Rename a stop |
| `/m stop info <stop_id>` | View stop details |
| `/m stop setcorners <stop_id>` | Update zone corner points |
| `/m stop setpoint [yaw]` | Set stopping point |
| `/m stop addtransfer <stop_id> <line_id>` | Add a transferable line |
| `/m stop deltransfer <stop_id> <line_id>` | Remove a transferable line |
| `/m stop listtransfers <stop_id>` | List transferable lines |
| `/m stop settitle <stop_id> <type> <key> <value>` | Set custom title display |
| `/m stop deltitle <stop_id> <type> [key]` | Remove custom title |
| `/m stop listtitles <stop_id>` | List custom title config |
| `/m stop tp <stop_id>` | Teleport to a stop |
| `/m stop trust <stop_id> <player>` | Grant stop management permission |
| `/m stop untrust <stop_id> <player>` | Remove stop management permission |
| `/m stop owner <stop_id> <player>` | Transfer stop ownership |
| `/m stop link <allow\|deny> <stop_id> <line_id>` | Manage line access whitelist |

### Portal Management

| Command | Description |
| :--- | :--- |
| `/m portal create <portal_id>` | Create a minecart portal entrance |
| `/m portal setdest <portal_id>` | Set portal destination |
| `/m portal link <id1> <id2>` | Bidirectionally link two portals |
| `/m portal delete <portal_id>` | Delete a portal |
| `/m portal list` | List all portals |
| `/m portal trust <portal_id> <player>` | Grant portal management permission |
| `/m portal untrust <portal_id> <player>` | Remove portal management permission |
| `/m portal owner <portal_id> <player>` | Transfer portal ownership |
| `/m portal reload` | Reload portal configuration |

### System Management

| Command | Description |
| :--- | :--- |
| `/m gui` | Open GUI management panel |
| `/m reload` | Reload configuration and data files |
| `/m testendpoint` | Test terminal station prompt |
| `/m teststopinfo <line_id> [stop_id]` | Test line stop information |

## Permissions

| Permission | Default | Description |
| :--- | :--- | :--- |
| `metro.admin` | OP | Allow all admin commands, inherits tp |
| `metro.use` | Everyone | Allow players to use the metro (board, etc.) |
| `metro.gui` | Everyone | Allow opening `/m gui` panel |
| `metro.tp` | false | Allow teleporting to stops via GUI |
| `metro.line.create` | false | Allow creating new lines |
| `metro.stop.create` | false | Allow creating new stops |
| `metro.portal.create` | false | Allow creating new portals |

## Ownership & Permission Management

- Newly created lines, stops, and portals automatically set the creator as owner and add them to the admin list.
- Use `/m line trust/untrust/owner`, `/m stop trust/untrust/owner`, and `/m portal trust/untrust/owner` to manage members.
- Stops can use `/m stop link allow/deny` to grant access to specific lines; line admins must obtain stop authorization before adding it.
- Portals can exist independently; line admins use `/m line addportal/delportal` to control line portal usage.
- Legacy data without permission config is considered "server-owned" and only OP/metro.admin can manage.

## Developer API (MetroAPI)

This fork provides `MetroAPI` for other plugins to integrate. Access via `MetroAPI.getInstance()`.

### Getting the API Instance

```java
MetroAPI api = MetroAPI.getInstance();
if (api != null) {
    // Use the API
}
```

### API Methods

```java
// Line Info
Line getLine(String lineId);
List<Line> getAllLines();
List<Line> getLinesForStop(String stopId);

// Stop Info
Stop getStop(String stopId);
List<Stop> getAllStops();

// Line Status
LineStatus getLineStatus(String lineId);
boolean setLineStatus(String lineId, LineStatus status);
boolean isLineSuspended(String lineId);
boolean isLineMaintenance(String lineId);
void setSuspensionMessage(String lineId, String message);
List<Line> getAlternativeRoutes(String lineId);

// Fares
FareRule getFareRule(String lineId);
void setFareRule(String lineId, FareRule rule);
double calculateFare(String lineId, String entryStopId, String exitStopId);
double getEstimatedFare(String lineId);
String getPriceDescription(String lineId);

// Boarding Check
TicketService.TicketCheck checkCanBoard(Player player, String lineId);

// Direct Manager Access
LineManager getLineManager();
StopManager getStopManager();
```

### Declaring Dependency in plugin.yml

```yaml
softdepend:
  - Metro
```

Or use `depend: [Metro]` to force Metro to load first.

## Quick Start

### Creating Your First Metro Line

1. **Create a line**: `/m line create line1 Line1`
2. **Set line color**: `/m line setcolor line1 &9` (blue)
3. **Set terminus direction**: `/m line setterminus line1 Towards East City`

### Creating a Stop

1. **Create a stop**: `/m stop create station1 Central Station`
2. **Select area**: Left/right-click with a golden shovel to mark two corner points
3. **Apply selection**: `/m stop setcorners station1`
4. **Set stop point**: Stand on a powered rail and run `/m stop setpoint`
5. **Add to line**: `/m line addstop line1 station1`

### Configuring Fares

**Flat fare:**
```
/m line setprice line1 10.0
```

**Distance-based (0.05 per block, max 20):**
```
/m line setfare line1 distance 2.0 0.05 20.0
```

**Interval-based (5.0 per interval, max 30):**
```
/m line setfare line1 interval 1.0 5.0 30.0
```

### Setting Line Suspension

```
/m line setstatus line1 suspended
/m line setsuspensionmsg line1 "&cLine 1 is undergoing maintenance"
/m line setaltroute line1 line2
/m line setstatus line1 normal
```

### Player Usage

Players right-click on powered rails within a stop area to call a minecart and board automatically. The system handles travel and station arrival automatically.

## Configuration Files

- `config.yml` — Global config: display styles, sounds, minecart settings, speed control modes, map integration, portals, derailment settings
- `lines.yml` — Line data storage (includes fare rules, operation status config)
- `stops.yml` — Stop data storage
- `lang/zh_CN.yml` — Chinese language file
- `lang/en_US.yml` — English language file

### Notable Config.yml Settings

```yaml
# Speed control mode
speed_control:
  mode: VANILLA_MOMENTUM  # VANILLA_MOMENTUM or BLOCK_BASED

# Economy
economy:
  enabled: true  # Enable Vault economy billing

# Web map integration
map_integration:
  enabled: false  # Show lines on map
  provider: AUTO  # AUTO, BLUEMAP, DYNMAP, SQUAREMAP

# Safe mode
settings:
  safe_mode:
    enabled: true  # Protect passengers from interference
```

## Building

- **Build plugin**: `mvn clean package`
  - Produces `target/metro-<version>.jar`
  - Current version: `2.0.0`

This fork is a single-module Maven project targeting Java 8 and servers 1.16.5+ (including Paper/Folia compatibility).

### Dependencies

- **Paper 1.16.5 API** (Spigot compatible)
- **Vault** (optional, for economy system)
- **BlueMap / Dynmap / Squaremap** (optional, for web map display)
- **ViaVersion** (optional, for cross-version support)

## Links

- [Original GitHub Repository](https://github.com/CubeX-MC/Metro)
- [Discord](https://discord.com/invite/7tJeSZPZgv)

---

*Metro-Forked — An enhanced fork of Metro adding advanced pricing, line status management, and a public API.*
