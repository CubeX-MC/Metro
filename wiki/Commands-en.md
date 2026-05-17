# Command Reference

Main command: `/m`

---

## Line Management

| Command | Description |
| :--- | :--- |
| `/m line create <line_id> <display_name>` | Create a new line |
| `/m line delete <line_id> confirm` | Delete an existing line |
| `/m line list` | List all lines |
| `/m line rename <line_id> <new_name>` | Rename a line |
| `/m line setcolor <line_id> <color_code>` | Set line color (e.g. `&9` for blue) |
| `/m line setterminus <line_id> <terminus_name>` | Set terminus description |
| `/m line setmaxspeed <line_id> <speed>` | Set maximum speed for the line |
| `/m line setprice <line_id> <price>` | Set legacy flat ticket price |
| `/m line setprice <line_id> flat <base>` | Set flat pricing |
| `/m line setprice <line_id> distance <base> <perBlock> [max]` | Set distance-based pricing |
| `/m line setprice <line_id> interval <base> <perStop> [max]` | Set interval-based pricing |
| `/m line setprice reset <line_id>` | Reset to legacy flat pricing |
| `/m line priceinfo <line_id>` | View pricing details and active discounts |
| `/m line setstatus <line_id> <normal&#124;suspended&#124;maintenance>` | Set operational status |
| `/m line addstop <line_id> <stop_id> [index]` | Add a stop to the line (optional position) |
| `/m line delstop <line_id> <stop_id>` | Remove a stop from the line |
| `/m line stops <line_id>` | Show all stops on the line |
| `/m line addportal <line_id> <portal_id>` | Allow the line to use a portal |
| `/m line delportal <line_id> <portal_id>` | Remove a portal from the line |
| `/m line portals <line_id>` | Show portals enabled for the line |
| `/m line info <line_id>` | Display detailed line info and members |
| `/m line recordroute <line_id>` | Record or save route points |
| `/m line clearroute <line_id> confirm` | Clear recorded route points |
| `/m line routeinfo <line_id>` | Show route recording status |
| `/m line protect <line_id> <on&#124;off>` | Toggle rail protection |
| `/m line clonereverse <source_id> <new_id> [suffix]` | Clone line in reverse direction |
| `/m line trust <line_id> <player>` | Add a line administrator |
| `/m line untrust <line_id> <player>` | Remove a line administrator |
| `/m line owner <line_id> <player>` | Transfer line ownership |

## Stop Management

| Command | Description |
| :--- | :--- |
| `/m stop create <stop_id> <display_name>` | Create a new stop |
| `/m stop delete <stop_id> confirm` | Delete a stop and its configuration |
| `/m stop list` | List all stops |
| `/m stop rename <stop_id> <new_name>` | Rename a stop |
| `/m stop info <stop_id>` | Show detailed info for a stop |
| `/m stop setcorners <stop_id>` | Apply the currently selected region |
| `/m stop setpoint [stopId] [yaw]` | Set the StopPoint (powered rail) |
| `/m stop addtransfer <stop_id> <line_id>` | Add a transfer line |
| `/m stop deltransfer <stop_id> <line_id>` | Remove a transfer line |
| `/m stop listtransfers <stop_id>` | List transfer lines |
| `/m stop settitle <stop_id> <type> <key> <text>` | Set custom title display |
| `/m stop deltitle <stop_id> <type> [key]` | Delete custom title (or specific key) |
| `/m stop listtitles <stop_id>` | List custom title configurations |
| `/m stop tp <stop_id>` | Teleport to a stop |
| `/m stop trust <stop_id> <player>` | Add a stop administrator |
| `/m stop untrust <stop_id> <player>` | Remove a stop administrator |
| `/m stop owner <stop_id> <player>` | Transfer stop ownership |
| `/m stop link <allow&#124;deny> <stop_id> <line_id>` | Manage line link whitelist |

## Portal Management

| Command | Description |
| :--- | :--- |
| `/m portal create <portal_id>` | Create a minecart portal entrance |
| `/m portal setdest <portal_id>` | Set the portal destination |
| `/m portal link <portal_id_1> <portal_id_2>` | Link two portals both ways |
| `/m portal delete <portal_id> confirm` | Delete a portal |
| `/m portal list` | List all portals |
| `/m portal trust <portal_id> <player>` | Add a portal administrator |
| `/m portal untrust <portal_id> <player>` | Remove a portal administrator |
| `/m portal owner <portal_id> <player>` | Transfer portal ownership |
| `/m portal reload` | Reload portal configuration |

## System Management

| Command | Description |
| :--- | :--- |
| `/m gui` | Open the Metro GUI |
| `/m reload` | Reload all plugin configs and data |
