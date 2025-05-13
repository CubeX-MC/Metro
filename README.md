# Metro 地铁系统

[English](README_en.md) | 简体中文

## 插件概述

Metro是一个受到牛腩小镇服务器启发的Minecraft服务器插件，为服务器提供一套精简且功能完善的地铁交通系统。该插件允许管理员轻松创建和管理自动化的地铁线路网络，并为玩家提供便捷、信息化的乘车体验。核心设计理念是易用性和灵活性。

## 核心功能

### 线路与停靠区管理

* **线路管理 (Line Management)**:
    * 创建、删除和管理地铁线路。
    * 每条线路拥有唯一的内部ID和玩家可见的显示名称。
    * 每条线路包含一个按顺序排列的停靠区ID (`stop_id`)列表，定义了列车的行驶路径。
    * **线路颜色**: 每条线路可配置独特的颜色代码，用于在UI和计分板中直观显示线路标识。
    * **终点方向**: 支持为每条线路配置终点方向描述（如"星海总站方向"），增强导航信息。
* **停靠区管理 (Stop Management)**:
    * 创建、删除和管理地铁停靠区 (`stop`)。一个停靠区代表一个逻辑上的站台区域。
    * 每个停靠区拥有唯一的内部ID和玩家可见的显示名称。管理员可以通过为不同的停靠区设置相似的显示名称（例如，"主城广场 - 东行区"，"主城广场 - 西行区"）来表示它们同属于一个逻辑站点。
    * **空间定义:** 每个停靠区是一个由两个对角点定义的三维空间区域。当玩家进入此区域时，会触发信息显示（如Title）。
    * **精确交互点 (`stoppoint`):** 在每个停靠区空间内，会定义一个精确的"停靠点" (`stoppoint`)。这是一个特定的红石铁轨方块，作为玩家右键呼叫/上车以及矿车实际停靠/发车的位置。
    * 每个停靠点 (`stoppoint`) 关联一个"发车朝向 (Launch Yaw)"，决定矿车从该点启动时的初始方向。
    * **换乘系统**: 支持为每个停靠区配置可换乘的线路列表，实现多线路换乘功能。

### 乘客体验改进

* **无碰撞矿车**: 乘客乘坐的矿车设置为无碰撞体积。
* **脱轨检测与处理**: 系统能检测矿车是否意外离开铁轨并自动处理。
* **非停靠区下车清理**: 若玩家在非指定的停靠区空间外下车，其乘坐的矿车将自动清理。
* **自定义信息显示**:
    * 支持通过 `config.yml` 高度自定义各类信息提示的格式与内容。
    * **进入停靠区提示:** 当玩家进入一个已定义的"停靠区 (`stop`)"空间时，通过Title（或其他可配置方式）显示当前线路、当前停靠区、上一停靠区和下一停靠区的信息。
    * **换乘信息智能显示:** 只有当站点确实有可换乘线路时才显示相关换乘信息，保持界面简洁明了。
    * **持续显示模式:** 在站台区域可持续显示信息，提高用户体验。
    * 支持占位符如 `{line_name}`, `{current_stop_name}`, `{next_stop_name}`, `{prev_stop_name}`, `{terminus_name}`, `{transfer_lines}`等。
* **自定义音乐/音效**: 可在 `config.yml` 中为矿车发车、到站等事件配置。
* **多语言支持**: 完整的语言系统，支持将所有消息文本从代码中分离，实现多语言界面。

### 操作与交互

* **呼叫与上车**: 玩家通过右键点击已配置为停靠区内"停靠点 (`stoppoint`)"的红石铁轨，即可呼叫（生成）矿车并自动上车。
* **行程信息**:
    * 乘坐过程中，通过计分板 (Scoreboard) 显示当前线路信息、前方目标停靠区及可换乘线路。
    * **线路标识符**: 使用统一的线路标识符（如"■"），通过线路颜色区分不同线路。
    * **站点列表**: 计分板上直观显示当前线路的站点列表，用不同样式标记当前站和下一站。
* **自动到站处理**:
    * 矿车在到达线路序列中的下一个停靠区的"停靠点 (`stoppoint`)"后自动停止。
    * 玩家可以选择下车。若在预设时间内未下车，矿车将自动载着玩家继续向线路的再下一个停靠区行驶（如果存在）。
    * 玩家下车后，空矿车自动清理。
* **到站、终点站提示**: 使用Title和Subtitle提示乘客已到站或到达终点站，并根据站点是否有可换乘线路智能显示换乘信息。

## 管理员命令 (主指令: `/m`)

* **线路管理:**
    * `/m line create <line_id> <"显示名称">`
    * `/m line delete <line_id>`
    * `/m line list`
    * `/m line setcolor <line_id> <颜色>`: 设置线路的显示颜色
    * `/m line setterminus <line_id> <"终点方向名称">`: 设置线路的终点方向描述
* **停靠区/点管理 (Stop Management):**
    * `/m stop create <stop_id> <"显示名称">`: 创建新的停靠区。
    * `/m stop delete <stop_id>`: 删除停靠区及其所有配置。
    * `/m stop list`: 列出所有停靠区。
    * `/m stop setcorner1 <stop_id>`: 设置停靠区空间的第一个对角点 (管理员看着方块执行)。
    * `/m stop setcorner2 <stop_id>`: 设置停靠区空间的第二个对角点。
    * `/m stop setpoint <stop_id> [yaw]`: 在当前停靠区空间内，设置精确的"停靠点 (`stoppoint`)"位置 (管理员看着目标红石铁轨执行)。可选 `yaw` 参数设置发车朝向。
    * `/m stop addtransfer <stop_id> <transfer_line_id>`: 为停靠区添加可换乘线路。
    * `/m stop deltransfer <stop_id> <transfer_line_id>`: 从停靠区移除可换乘线路。
    * `/m stop listtransfers <stop_id>`: 列出停靠区的所有可换乘线路。
* **线路与停靠区关联:**
    * `/m line addstop <line_id> <stop_id> [顺序索引]`: 将停靠区加入线路。
    * `/m line delstop <line_id> <stop_id>`: 从线路中移除停靠区。
* **系统:**
    * `/m reload`: 重新加载插件配置。
    * `/m help [command]`: 显示帮助信息，简化的命令结构使界面更清晰。

## 配置文件 (`config.yml` 及数据文件)

* **`config.yml`**:
    * 全局设置，如默认矿车停留时间、信息显示格式模板 (包括进入停靠区Title的格式)、音效、脱轨检测等。
    * **UI配置**: 可配置Title、Subtitle和ActionBar的显示内容、显示时间和刷新频率。
    * **计分板样式**: 自定义计分板的标题、内容和样式，包括站点标记样式和线路标识符。
    * **语言设置**: 配置默认语言。
* **`lines.yml`**:
    * 存储所有线路定义。
    * 每条线路包含其显示名称、颜色代码、终点方向和按顺序排列的 `stop_id` 列表。
    * 示例:
        ```yaml
        line_1_eastbound:
          display_name: "1号线 (东行)"
          color: "&9" # 蓝色
          terminus_name: "东城总站"
          ordered_stop_ids:
            - "central_stop_e"
            - "market_stop_e"
        ```
* **`stops.yml`**:
    * 存储所有停靠区的定义。
    * 每个停靠区 (`stop_id`) 包含:
        * `display_name`: (String) 停靠区的显示名称。
        * `corner1_location`: (String) 定义停靠区空间的第一个对角点坐标 (`"world,x1,y1,z1"`)。
        * `corner2_location`: (String) 定义停靠区空间的第二个对角点坐标 (`"world,x2,y2,z2"`)。
        * `stoppoint_location`: (String) 该停靠区内精确的"停靠点"红石铁轨坐标 (`"world,x,y,z"`)。
        * `launch_yaw`: (float) 从`stoppoint_location`发车时的朝向。
        * `transferable_lines`: (List) 可从该站点换乘的其他线路ID列表。
    * 示例:
        ```yaml
        central_stop_e:
          display_name: "中央车站 - 东行区"
          corner1_location: "world,98,63,198"
          corner2_location: "world,102,67,202"
          stoppoint_location: "world,100,64,200" 
          launch_yaw: 90.0
          transferable_lines:
            - "line_2_north"
            - "line_5_circle"
        ```
* **`zh_CN.yml` 等语言文件**:
    * 存储所有消息文本，支持多语言配置。

## 权限

* `metro.admin`: 允许使用所有 `/m` 管理员命令。
* `metro.use`: 允许玩家使用地铁系统。

## 玩家交互逻辑补充

* **进入停靠区 (`stop`) 空间:**
    * 通过监听 `PlayerMoveEvent` (或其他更优化的区域进入检测事件)。
    * 当玩家进入任何一个已定义的 `stop` 的三维空间区域时，触发相应的Title信息显示 (内容可配置，包含当前线路、当前停靠区名、上一停靠区名、下一停靠区名)。
    * 支持在站台区域持续显示信息，提高用户体验。
* **右键停靠点 (`stoppoint`):**
    * 通过监听 `PlayerInteractEvent`。
    * 当玩家右键点击的是一个已定义的 `stoppoint_location` 上的红石铁轨时，执行呼叫/上车逻辑。
* **换乘体验:**
    * 在站台、乘车期间和到站时，根据是否有可换乘线路智能显示换乘信息。
    * 计分板上使用颜色标识符显示可换乘线路，提供直观的换乘指引。

## Folia支持

从版本1.0.0起，Metro插件支持在Folia服务器上运行。Folia是一个基于区域线程化的高性能Minecraft服务器实现，可以更好地利用多核CPU。

Metro插件通过以下方式实现对Folia的支持：

1. 使用`folia-supported: true`标记在plugin.yml中声明支持
2. 引入通用的调度器工具类`SchedulerUtil`，自动检测当前服务器是否为Folia并使用相应的API
3. 所有涉及调度器的代码都使用SchedulerUtil进行封装，确保兼容性

在普通的Paper/Spigot服务器上，插件会使用标准的Bukkit调度器。在Folia服务器上，插件会自动切换到Folia的区域化调度器，确保任务在正确的线程上执行。
