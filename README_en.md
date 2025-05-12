# Metro Subway System

English | [简体中文](README.md)

## Plugin Overview

Metro is a Minecraft server plugin that is inspired by newnan.city server, provides a streamlined and feature-complete subway transportation system. This plugin allows administrators to easily create and manage automated subway line networks, providing players with a convenient and informative riding experience. The core design philosophy is ease of use and flexibility.

## Core Features

### Line and Stop Management

* **Line Management**:
    * Create, delete, and manage subway lines.
    * Each line has a unique internal ID and player-visible display name.
    * Each line contains an ordered list of stop IDs (`stop_id`), defining the train's route.
    * **Line Colors**: Each line can be configured with a unique color code, used for intuitive line identification in UI and scoreboard.
    * **Terminus Direction**: Support for configuring terminus direction descriptions (e.g., "Starlight Terminal Direction") to enhance navigation information.
* **Stop Management**:
    * Create, delete, and manage subway stops (`stop`). A stop represents a logical platform area.
    * Each stop has a unique internal ID and player-visible display name. Administrators can indicate stops belonging to the same logical station by using similar display names (e.g., "City Square - Eastbound", "City Square - Westbound").
    * **Spatial Definition**: Each stop is a three-dimensional space region defined by two corner points. When players enter this area, information displays are triggered (such as Title).
    * **Precise Interaction Point (`stoppoint`)**: Within each stop space, a precise "stoppoint" is defined. This is a specific powered rail block that serves as the location for players to right-click to call/board and where minecarts actually stop/depart.
    * Each stoppoint is associated with a "Launch Yaw" that determines the initial direction when the minecart starts from that point.
    * **Transfer System**: Support for configuring a list of transferable lines for each stop, enabling multi-line transfers.

### Passenger Experience Improvements

* **No-Collision Minecarts**: Passenger minecarts are set to have no collision volume.
* **Derailment Detection and Handling**: The system can detect if a minecart accidentally leaves the rails and handle it automatically.
* **Non-Stop Area Dismount Cleanup**: If players dismount outside designated stop spaces, their minecarts will be automatically cleaned up.
* **Custom Information Display**:
    * Support for highly customizing various information prompts via `config.yml`.
    * **Entering Stop Area Prompt**: When a player enters a defined "stop" space, information about the current line, current stop, previous stop, and next stop is displayed via Title (or other configurable methods).
    * **Intelligent Transfer Information Display**: Transfer information is shown only when a station actually has transferable lines, keeping the interface clean and concise.
    * **Continuous Display Mode**: Information can be continuously displayed in the platform area, improving user experience.
    * Support for placeholders such as `{line_name}`, `{current_stop_name}`, `{next_stop_name}`, `{prev_stop_name}`, `{terminus_name}`, `{transfer_lines}`, etc.
* **Custom Music/Sound Effects**: Can be configured in `config.yml` for minecart departure, arrival, and other events.
* **Multi-language Support**: Complete language system that supports separating all message text from code, enabling multi-language interfaces.

### Operation and Interaction

* **Call and Board**: Players can call (generate) a minecart and automatically board it by right-clicking on a powered rail configured as a "stoppoint" within a stop area.
* **Journey Information**:
    * During the ride, the scoreboard displays current line information, upcoming target stops, and transferable lines.
    * **Line Identifier**: Uses a unified line identifier (such as "■") with line colors to distinguish different lines.
    * **Stop List**: The scoreboard visually displays the list of stops on the current line, marking the current stop and next stop with different styles.
* **Automatic Arrival Handling**:
    * The minecart automatically stops after reaching the "stoppoint" of the next stop in the line sequence.
    * Players can choose to dismount. If they don't dismount within the preset time, the minecart will automatically continue carrying the player to the next stop in the line (if one exists).
    * After the player dismounts, the empty minecart is automatically cleaned up.
* **Arrival and Terminal Station Prompts**: Uses Title and Subtitle to notify passengers of arrival or reaching the terminal station, and intelligently displays transfer information based on whether the station has transferable lines.

## Admin Commands (Main Command: `/m`)

* **Line Management:**
    * `/m line create <line_id> <"display_name">`
    * `/m line delete <line_id>`
    * `/m line list`
    * `/m line setcolor <line_id> <color>`: Set the display color of the line
    * `/m line setterminus <line_id> <"terminus_direction_name">`: Set the terminus direction description of the line
* **Stop/Point Management:**
    * `/m stop create <stop_id> <"display_name">`: Create a new stop.
    * `/m stop delete <stop_id>`: Delete a stop and all its configurations.
    * `/m stop list`: List all stops.
    * `/m stop setcorner1 <stop_id>`: Set the first corner point of the stop space (admin looks at the block when executing).
    * `/m stop setcorner2 <stop_id>`: Set the second corner point of the stop space.
    * `/m stop setpoint <stop_id> [yaw]`: Set the precise "stoppoint" location within the current stop space (admin looks at the target powered rail when executing). Optional `yaw` parameter sets the departure direction.
    * `/m stop addtransfer <stop_id> <transfer_line_id>`: Add a transferable line to a stop.
    * `/m stop deltransfer <stop_id> <transfer_line_id>`: Remove a transferable line from a stop.
    * `/m stop listtransfers <stop_id>`: List all transferable lines for a stop.
* **Line and Stop Association:**
    * `/m line addstop <line_id> <stop_id> [order_index]`: Add a stop to a line.
    * `/m line delstop <line_id> <stop_id>`: Remove a stop from a line.
* **System:**
    * `/m reload`: Reload the plugin configuration.
    * `/m help [command]`: Display help information, with a simplified command structure for a clearer interface.

## Configuration Files (`config.yml` and Data Files)

* **`config.yml`**:
    * Global settings such as default minecart dwell time, information display format templates (including the format of entering stop Title), sound effects, derailment detection, etc.
    * **UI Configuration**: Configure the display content, display time, and refresh rate of Title, Subtitle, and ActionBar.
    * **Scoreboard Style**: Customize scoreboard title, content, and style, including stop marker styles and line identifiers.
    * **Language Settings**: Configure the default language.
* **`lines.yml`**:
    * Stores all line definitions.
    * Each line includes its display name, color code, terminus direction, and an ordered list of `stop_id`s.
    * Example:
        ```yaml
        line_1_eastbound:
          display_name: "Line 1 (Eastbound)"
          color: "&9" # Blue
          terminus_name: "East City Terminal"
          ordered_stop_ids:
            - "central_stop_e"
            - "market_stop_e"
        ```
* **`stops.yml`**:
    * Stores all stop definitions.
    * Each stop (`stop_id`) contains:
        * `display_name`: (String) The display name of the stop.
        * `corner1_location`: (String) The coordinates of the first corner point defining the stop space (`"world,x1,y1,z1"`).
        * `corner2_location`: (String) The coordinates of the second corner point defining the stop space (`"world,x2,y2,z2"`).
        * `stoppoint_location`: (String) The coordinates of the precise "stoppoint" powered rail within the stop (`"world,x,y,z"`).
        * `launch_yaw`: (float) The departure direction from the `stoppoint_location`.
        * `transferable_lines`: (List) A list of other line IDs that can be transferred to from this stop.
    * Example:
        ```yaml
        central_stop_e:
          display_name: "Central Station - Eastbound"
          corner1_location: "world,98,63,198"
          corner2_location: "world,102,67,202"
          stoppoint_location: "world,100,64,200" 
          launch_yaw: 90.0
          transferable_lines:
            - "line_2_north"
            - "line_5_circle"
        ```
* **`en_US.yml` and other language files**:
    * Store all message text, supporting multi-language configuration.

## Permissions

* `metro.admin`: Allows the use of all `/m` admin commands.
* `metro.use`: Allows players to use the subway system.

## Player Interaction Logic Supplement

* **Entering Stop (`stop`) Space:**
    * Through listening to `PlayerMoveEvent` (or other more optimized area entry detection events).
    * When a player enters any defined `stop` three-dimensional space area, the corresponding Title information display is triggered (content configurable, including current line, current stop name, previous stop name, next stop name).
    * Support for continuously displaying information in the platform area, improving user experience.
* **Right-click Stoppoint (`stoppoint`):**
    * Through listening to `PlayerInteractEvent`.
    * When a player right-clicks on a powered rail at a defined `stoppoint_location`, the call/board logic is executed.
* **Transfer Experience:**
    * Transfer information is intelligently displayed based on whether there are transferable lines during platform stay, riding, and arrival.
    * Transferable lines are displayed using colored identifiers on the scoreboard, providing intuitive transfer guidance. 