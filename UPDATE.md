# Metro 插件功能增强

**核心需求：**

1.  **增强停靠区 (`stop`) 内信息显示：**
    * **持续显示：** 玩家进入 `stop` 区域后，通过 Title 和 Subtitle/Actionbar 持续显示当前线路、当前站、前后站、开往方向、下一站及可换乘信息。
    * **实现建议：** 优化 `PlayerMoveEvent` 监听，增加区域内停留检测逻辑，定期刷新或在玩家小范围移动时刷新信息。

2.  **丰富上车后行程信息：**
    * **动态提示：** 乘客上车后，通过 Title/Subtitle/Actionbar 或 BossBar 显示下一站、终点站以及下一站可换乘的线路信息。
    * **计分板 (Scoreboard) 更新：** 计分板应主要显示当前线路名称（带颜色）和终点站名称。

3.  **数据模型扩展：**
    * **`stops.yml`：**
        * 为每个 `stop_id` 添加 `transferable_lines` 字段 (列表类型)，存储该停靠区（逻辑站点）可换乘的其他 `line_id`。
        * 示例： `transferable_lines: ["line_2_north", "line_5_circle"]`
    * **`lines.yml`：**
        * 为每条 `line_id` 添加 `color` 字段，用于定义线路的显示颜色 (例如，使用 Minecraft 颜色代码 `&9` 或预设颜色名 "BLUE")。
        * 为每条 `line_id` 添加 `terminus_name` (可选) 字段，定义线路的终点方向描述 (例如 "星海总站")，用于 "开往 XX 方向" 的提示。

4.  **新增管理员命令：**
    * 线路管理：
        * `/m line setcolor <line_id> <颜色>`
        * `/m line setterminus <line_id> <"终点方向名称">` (若实现 `terminus_name`)
    * 停靠区换乘管理：
        * `/m stop addtransfer <stop_id> <transfer_line_id>`
        * `/m stop deltransfer <stop_id> <transfer_line_id>`
        * `/m stop listtransfers <stop_id>`

5.  **配置文件 (`config.yml`) 调整：**
    * **新增消息模板：** 为新的显示场景（如持续停靠区提示的 Subtitle/Actionbar，上车后行程信息）添加可配置的消息模板。
    * **新增占位符：** 支持新的占位符，如：
        * `{destination_stop_name}`: 当前线路的终点站显示名称。
        * `{terminus_name}`: 线路的终点方向描述。
        * `{transfer_lines}`: 格式化后的下一站可换乘线路列表 (应包含线路颜色和名称)。
        * `{line_color_code}`: 当前线路的颜色代码 (供内部模板使用)。

**实现要点与建议：**

* **信息获取逻辑：**
    * 终点站信息：通过当前线路 `ordered_stop_ids` 的最后一个元素获取。
    * 换乘信息：通过下一站 `stop_id` 查询其 `transferable_lines`，并进一步获取这些线路的颜色和显示名称。
* **显示格式化：**
    * 线路名称和换乘线路信息应使用其定义的颜色进行显示。
    * 换乘信息可以列表形式展示，例如: "可换乘: <颜色1>1号线</颜色1>, <颜色2>2号线</颜色2>"。
* **事件监听与处理：**
    * `PlayerMoveEvent`：用于检测玩家进入/停留在 `stop` 区域。
    * 矿车相关事件（或定期位置检测）：用于更新上车后的行程信息，特别是接近下一站时的提示。
    * `PlayerInteractEvent`：保持右键红石铁轨呼叫/上车逻辑。
* **性能优化：** 对于持续性的信息显示，注意控制刷新频率，避免对服务器造成过大负担。可以考虑仅在玩家显著移动或特定事件触发时更新。

**请 AI Agent 根据以上指导，对 Metro 插件的Java代码进行修改和功能添加。**