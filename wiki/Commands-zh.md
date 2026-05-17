# 命令参考

主指令: `/m`

---

## 线路管理

| 命令 | 描述 |
| :--- | :--- |
| `/m line create <line_id> <显示名称>` | 创建新线路 |
| `/m line delete <line_id> confirm` | 删除指定线路 |
| `/m line list` | 列出所有线路 |
| `/m line rename <line_id> <新名称>` | 重命名线路 |
| `/m line setcolor <line_id> <颜色代码>` | 设置线路颜色 |
| `/m line setterminus <line_id> <名称>` | 设置终点方向描述 |
| `/m line setmaxspeed <line_id> <速度>` | 设置线路最大运行速度 |
| `/m line setprice <line_id> <价格>` | 设置单一定价 |
| `/m line setprice <line_id> flat <基础价>` | 设置固定票价 |
| `/m line setprice <line_id> distance <基础价> <每方块> [封顶]` | 按距离计价 |
| `/m line setprice <line_id> interval <基础价> <每站> [封顶]` | 按站数计价 |
| `/m line setprice reset <line_id>` | 重置为默认单一票价 |
| `/m line priceinfo <line_id>` | 查看线路定价详情 |
| `/m line setstatus <line_id> <normal&#124;suspended&#124;maintenance>` | 设置运营状态 |
| `/m line addstop <line_id> <stop_id> [位置索引]` | 将停靠区添加到线路 |
| `/m line delstop <line_id> <stop_id>` | 从线路中移除停靠区 |
| `/m line stops <line_id>` | 查看线路的所有停靠区 |
| `/m line addportal <line_id> <portal_id>` | 允许线路使用传送门 |
| `/m line delportal <line_id> <portal_id>` | 从线路中移除传送门 |
| `/m line portals <line_id>` | 查看线路启用的传送门 |
| `/m line info <line_id>` | 查看线路详细信息及权限 |
| `/m line recordroute <line_id>` | 录制或保存路线轨迹 |
| `/m line clearroute <line_id> confirm` | 清除已录制的路线 |
| `/m line routeinfo <line_id>` | 查看路线录制状态 |
| `/m line protect <line_id> <on&#124;off>` | 启停铁轨保护 |
| `/m line clonereverse <源ID> <新ID> [后缀]` | 反向克隆线路 |
| `/m line trust <line_id> <玩家>` | 授予线路管理权限 |
| `/m line untrust <line_id> <玩家>` | 移除线路管理权限 |
| `/m line owner <line_id> <玩家>` | 转移线路所有权 |

## 停靠区管理

| 命令 | 描述 |
| :--- | :--- |
| `/m stop create <stop_id> <显示名称>` | 选区后创建新停靠区 |
| `/m stop delete <stop_id> confirm` | 删除停靠区及其所有配置 |
| `/m stop list` | 列出所有停靠区 |
| `/m stop rename <stop_id> <新名称>` | 重命名停靠区 |
| `/m stop info <stop_id>` | 查看停靠区详细信息 |
| `/m stop setcorners <stop_id>` | 更新空间对角点 |
| `/m stop setpoint [stopId] [朝向角度]` | 设置精确停靠点 |
| `/m stop addtransfer <stop_id> <换乘线路ID>` | 添加可换乘线路 |
| `/m stop deltransfer <stop_id> <换乘线路ID>` | 移除可换乘线路 |
| `/m stop listtransfers <stop_id>` | 查看可换乘线路 |
| `/m stop settitle <stop_id> <类型> <键> <文本内容>` | 设置自定义 Title 显示 |
| `/m stop deltitle <stop_id> <类型> [键]` | 删除自定义 Title 设置 |
| `/m stop listtitles <stop_id>` | 查看自定义 Title 配置 |
| `/m stop tp <stop_id>` | 传送到指定停靠区 |
| `/m stop trust <stop_id> <玩家>` | 授予停靠区管理权限 |
| `/m stop untrust <stop_id> <玩家>` | 移除停靠区管理权限 |
| `/m stop owner <stop_id> <玩家>` | 转移停靠区所有权 |
| `/m stop link <allow&#124;deny> <stop_id> <line_id>` | 管理线路接入白名单 |

## 传送门管理

| 命令 | 描述 |
| :--- | :--- |
| `/m portal create <portal_id>` | 创建新的矿车传送门入口 |
| `/m portal setdest <portal_id>` | 设置传送门目标位置 |
| `/m portal link <portal_id_1> <portal_id_2>` | 双向配对两个传送门 |
| `/m portal delete <portal_id> confirm` | 删除传送门 |
| `/m portal list` | 列出所有传送门 |
| `/m portal trust <portal_id> <玩家>` | 授予传送门管理权限 |
| `/m portal untrust <portal_id> <玩家>` | 移除传送门管理权限 |
| `/m portal owner <portal_id> <玩家>` | 转移传送门所有权 |
| `/m portal reload` | 重新加载传送门配置 |

## 系统管理

| 命令 | 描述 |
| :--- | :--- |
| `/m gui` | 打开图形管理界面 |
| `/m reload` | 重新加载配置和数据文件 |
