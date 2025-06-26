# Metro Subway System

[中文](README.md) | English

## Plugin Overview

Metro is a subway transit system plugin that lets administrators create automated subway lines and provides players with a convenient riding experience, inspired by Newnan.city.

![Demo](https://i.imgur.com/K335iWj.gif)

## Basic Concepts

* **Line**: Defines a subway route by an ordered list of stops.  
* **Stop**: A station area in the world, defined by two corner points.  
* **StopPoint**: A powered rail within a Stop where players right-click to board a minecart.  
* **Transfer**: Connections from one Stop to other Lines for route changes.

## Admin Commands (main: `/m`)

### Line Management

| Command                                           | Description                                |
| :-----------------------------------------------  | :----------------------------------------- |
| `/m line create <line_id> <display_name>`         | Create a new line                          |
| `/m line delete <line_id>`                        | Delete an existing line                    |
| `/m line list`                                    | List all lines                             |
| `/m line rename <line_id> <new_name>`             | Rename a line                              |
| `/m line setcolor <line_id> <color_code>`         | Set line color (e.g. `&9` for blue)         |
| `/m line setterminus <line_id> <terminus_name>`   | Set terminus description                   |
| `/m line setmaxspeed <line_id> <speed>`           | Set maximum speed for the line             |
| `/m line addstop <line_id> <stop_id> [index]`     | Add a stop to the line (optional position) |
| `/m line delstop <line_id> <stop_id>`             | Remove a stop from the line                |
| `/m line stops <line_id>`                         | Show all stops on the line                 |

### Stop Management

| Command                                                       | Description                        |
| :------------------------------------------------------------  | :--------------------------------- |
| `/m stop create <stop_id> <display_name>`                      | Create a new stop                  |
| `/m stop delete <stop_id>`                                     | Delete a stop and its configuration|
| `/m stop list`                                                 | List all stops                     |
| `/m stop rename <stop_id> <new_name>`                          | Rename a stop                      |
| `/m stop info <stop_id>`                                       | Show detailed info for a stop      |
| `/m stop setcorner1 <stop_id>`                                 | Set first corner point of the area |
| `/m stop setcorner2 <stop_id>`                                 | Set second corner point            |
| `/m stop setpoint [yaw]`                                       | Set the StopPoint (powered rail)   |
| `/m stop addtransfer <stop_id> <line_id>`                      | Add a transfer line                |
| `/m stop deltransfer <stop_id> <line_id>`                      | Remove a transfer line             |
| `/m stop listtransfers <stop_id>`                              | List transfer lines                |
| `/m stop settitle <stop_id> <type> <key> <text>`               | Set custom title display           |
| `/m stop deltitle <stop_id> <type> [key]`                      | Delete custom title (or specific)  |
| `/m stop listtitles <stop_id>`                                 | List custom title configurations   |
| `/m stop tp <stop_id>`                                         | Teleport to a stop                 |

### System Management

| Command          | Description                         |
| :---------------  | :---------------------------------- |
| `/m reload`       | Reload all plugin configs and data  |

## Quick Start

### Create Your First Line

1. `/m line create line1 Line 1`  
2. `/m line setcolor line1 &9`  
3. `/m line setterminus line1 East Terminus`

### Create a Stop

1. `/m stop create station1 Central Station`  
2. Stand at one corner and run `/m stop setcorner1 station1`  
3. At the opposite corner, run `/m stop setcorner2 station1`  
4. On the powered rail, run `/m stop setpoint`  
5. `/m line addstop line1 station1`

### Player Usage

Players right-click the powered rail inside a Stop to summon and board a minecart. The system will handle travel, stops, and arrivals automatically.

## Permissions

| Permission       | Description                               |
| :--------------- | :---------------------------------------- |
| `metro.admin`    | Allows use of all admin commands          |
| `metro.use`      | Allows players to use the subway system   |

## Configuration Files

* `config.yml` – Global settings (display templates, sounds, minecart behavior)  
* `lines.yml` – Line definitions and ordering  
* `stops.yml` – Stop definitions and properties  
* `zh_CN.yml` – Chinese language file  
