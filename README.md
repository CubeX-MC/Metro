# Metro 地铁系统

[English](README_en.md) | 简体中文

![bstats](https://bstats.org/signatures/bukkit/Metro.svg)

## 插件概述

Metro 是一个受牛腩小镇启发的地铁交通系统插件，允许管理员创建自动化的地铁线路网络，为玩家提供便捷的乘车体验。

![Imgurl](https://i.imgur.com/K335iWj.gif)

本分支 (Metro-Forked) 由阿清开发维护，在原版 [CubeX-MC/Metro](https://github.com/CubeX-MC/Metro) 基础上进行了大量增强，同时兼容 MC 1.16.5+ 和 Java 8+ 服务器。

### 新增特性

本分支在原版基础上增加了以下功能：

- **高级票价系统**：支持三种计价模式（固定票价、按距离计价、按区间计价），可设置基础票价、每格费率、最高票价上限
- **时段优惠**：支持按游戏内时间段设置折扣（如夜间半价），支持跨夜时段
- **线路运营状态**：支持设置线路为正常/暂停运营/维护中，暂停线路禁止乘客上车
- **替代路线**：线路暂停时自动向玩家推荐替代路线
- **暂停公告**：线路暂停运营时显示自定义提示信息
- **MetroAPI**：提供公开 API 供其他插件调用，可查询线路、站点、票价、线路状态等

## 基本概念

- **线路 (Line)**：地铁线路，包含按顺序排列的停靠区列表，定义列车行驶路径
- **停靠区 (Stop)**：地铁站台，由两个对角点定义的三维空间区域
- **停靠点 (StopPoint)**：停靠区内的红石铁轨，玩家右键乘车的具体位置
- **换乘**：在停靠区可以转乘其他线路

## 管理员命令 (主指令: `/m`)

### 线路管理

| 命令 | 描述 |
| :--- | :--- |
| `/m line create <line_id> <显示名称>` | 创建新线路 |
| `/m line delete <line_id>` | 删除指定线路 |
| `/m line list` | 列出所有线路 |
| `/m line rename <line_id> <新名称>` | 重命名线路 |
| `/m line setcolor <line_id> <颜色代码>` | 设置线路颜色 |
| `/m line setterminus <line_id> <名称>` | 设置终点方向描述 |
| `/m line setmaxspeed <line_id> <速度>` | 设置线路最大运行速度 |
| `/m line setprice <line_id> <价格>` | 设置线路固定票价 |
| `/m line addstop <line_id> <stop_id> [位置索引]` | 将停靠区添加到线路（可指定位置） |
| `/m line delstop <line_id> <stop_id>` | 从线路中移除停靠区 |
| `/m line stops <line_id>` | 查看线路的所有停靠区 |
| `/m line addportal <line_id> <portal_id>` | 允许线路使用传送门 |
| `/m line delportal <line_id> <portal_id>` | 从线路中移除传送门 |
| `/m line portals <line_id>` | 查看线路启用的传送门 |
| `/m line info <line_id>` | 查看线路详细信息及权限 |
| `/m line trust <line_id> <玩家>` | 授予线路管理权限 |
| `/m line untrust <line_id> <玩家>` | 移除线路管理权限 |
| `/m line owner <line_id> <玩家>` | 转移线路所有权 |
| `/m line recordroute <line_id>` | 录制/保存线路轨迹点 |
| `/m line clearroute <line_id>` | 清除线路轨迹点 |
| `/m line routeinfo <line_id>` | 查看线路轨迹与轨道保护状态 |
| `/m line protect <line_id> <on\|off\|status>` | 开启/关闭/查看轨道保护 |
| `/m line clonereverse <source_id> <new_id> [suffix]` | 反向克隆线路 |
| `/m line setfare <line_id> <mode> <baseFare> [perUnit] [maxFare]` | **配置高级票价规则** |
| `/m line fareinfo <line_id>` | **查看线路票价详情与时段优惠** |
| `/m line setstatus <line_id> <status>` | **设置线路运营状态** |
| `/m line setaltroute <line_id> <altLineId>` | **添加替代路线建议** |
| `/m line delaltroute <line_id> <altLineId>` | **移除替代路线** |
| `/m line setsuspensionmsg <line_id> <message>` | **设置暂停运营提示信息** |

#### 票价规则配置详解

```
/m line setfare <line_id> <mode> <baseFare> [perUnit] [maxFare]
```

| 参数 | 说明 |
| :--- | :--- |
| `<mode>` | 计价模式：`flat`（固定票价）、`distance`（按距离计价）、`interval`（按区间计价） |
| `<baseFare>` | 基础票价 |
| `[perUnit]` | 每格/每区间费率（distance 和 interval 模式需要） |
| `[maxFare]` | 最高票价上限（可选，0 表示不设上限） |

**示例：**
```
/m line setfare line1 distance 2.0 0.05 20.0
```
— 线路 line1 按距离计价，基础票价 2.0，每格 0.05，最高 20.0

**时段优惠配置（需手动编辑 lines.yml）：**

在 `lines.yml` 中线路的 `fare_rule` 节点下添加 `time_discounts` 列表：

```yaml
fare_rule:
  mode: distance
  base_fare: 2.0
  per_block_rate: 0.05
  max_fare: 20.0
  time_discounts:
    - start_tick: 0      # 游戏刻 0 = 日出
      end_tick: 6000     # 游戏刻 6000 = 正午
      multiplier: 0.8    # 8 折
    - start_tick: 13000  # 黄昏
      end_tick: 6000     # 跨夜到次日正午
      multiplier: 0.5    # 5 折（夜间优惠）
```

> 游戏刻与时间的对应关系：0=日出(6:00)、6000=正午(12:00)、12000=日落(18:00)、13800=夜晚开始、18000=午夜(0:00)、22200=黎明开始、24000=次日日出

#### 线路运营状态

```
/m line setstatus <line_id> <status>
```

| 状态值 | 说明 |
| :--- | :--- |
| `normal` | 正常运营（默认） |
| `suspended` | 暂停运营，玩家无法上车 |
| `maintenance` | 维护中，玩家仍可正常乘车 |

暂停运营时，尝试乘车的玩家会自动收到提示信息和替代路线建议。

### 停靠区管理

| 命令 | 描述 |
| :--- | :--- |
| `/m stop create <stop_id> <显示名称>` | 选区后创建新停靠区 |
| `/m stop delete <stop_id>` | 删除停靠区及其所有配置 |
| `/m stop list` | 列出所有停靠区 |
| `/m stop rename <stop_id> <新名称>` | 重命名停靠区 |
| `/m stop info <stop_id>` | 查看停靠区详细信息 |
| `/m stop setcorners <stop_id>` | 更新空间对角点 |
| `/m stop setpoint [朝向角度]` | 设置精确停靠点 |
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
| `/m stop link <allow\|deny> <stop_id> <line_id>` | 管理线路接入白名单 |

### 传送门管理

| 命令 | 描述 |
| :--- | :--- |
| `/m portal create <portal_id>` | 创建新的矿车传送门入口 |
| `/m portal setdest <portal_id>` | 设置传送门目标位置 |
| `/m portal link <portal_id_1> <portal_id_2>` | 双向配对两个传送门 |
| `/m portal delete <portal_id>` | 删除传送门 |
| `/m portal list` | 列出所有传送门 |
| `/m portal trust <portal_id> <玩家>` | 授予传送门管理权限 |
| `/m portal untrust <portal_id> <玩家>` | 移除传送门管理权限 |
| `/m portal owner <portal_id> <玩家>` | 转移传送门所有权 |
| `/m portal reload` | 重新加载传送门配置 |

### 系统管理

| 命令 | 描述 |
| :--- | :--- |
| `/m gui` | 打开图形管理界面 |
| `/m reload` | 重新加载配置和数据文件 |
| `/m testendpoint` | 测试终点站提示显示 |
| `/m teststopinfo <line_id> [stop_id]` | 测试线路站点信息 |

## 权限

| 权限 | 默认值 | 描述 |
| :--- | :--- | :--- |
| `metro.admin` | OP | 允许使用所有管理员命令，并继承传送权限 |
| `metro.use` | 所有人 | 允许玩家使用地铁系统（右键乘车等） |
| `metro.gui` | 所有人 | 允许打开 `/m gui` 图形管理界面 |
| `metro.tp` | 否 | 允许在 GUI 中传送到停靠区 |
| `metro.line.create` | 否 | 允许玩家创建新的线路 |
| `metro.stop.create` | 否 | 允许玩家创建新的停靠区 |
| `metro.portal.create` | 否 | 允许玩家创建新的矿车传送门 |

## 所有权与权限管理

- 新创建的线路、停靠区和传送门会自动将创建者设置为所有者，并加入管理员列表。
- 使用 `/m line trust/untrust/owner`、`/m stop trust/untrust/owner` 与 `/m portal trust/untrust/owner` 可以维护各元素的管理成员。
- 停靠区可通过 `/m stop link allow/deny` 为特定线路开放接入；线路管理员必须获得停靠区授权后才能将其加入线路。
- 传送门可独立存在；线路管理员使用 `/m line addportal/delportal` 控制线路是否能使用指定传送门。
- 旧版本数据中没有权限配置的线路/停靠区/传送门会被视为"服务器所有"，只有 OP 或 `metro.admin` 拥有者可以操作。

## 开发者 API (MetroAPI)

本分支提供了 `MetroAPI` 供其他插件调用。可通过 `MetroAPI.getInstance()` 获取实例。

### 获取 API 实例

```java
MetroAPI api = MetroAPI.getInstance();
if (api != null) {
    // 使用 API
}
```

### API 方法列表

```java
// 线路信息
Line getLine(String lineId);
List<Line> getAllLines();
List<Line> getLinesForStop(String stopId);

// 站点信息
Stop getStop(String stopId);
List<Stop> getAllStops();

// 线路状态
LineStatus getLineStatus(String lineId);
boolean setLineStatus(String lineId, LineStatus status);
boolean isLineSuspended(String lineId);
boolean isLineMaintenance(String lineId);
void setSuspensionMessage(String lineId, String message);
List<Line> getAlternativeRoutes(String lineId);

// 票价
FareRule getFareRule(String lineId);
void setFareRule(String lineId, FareRule rule);
double calculateFare(String lineId, String entryStopId, String exitStopId);
double getEstimatedFare(String lineId);
String getPriceDescription(String lineId);

// 购票检查
TicketService.TicketCheck checkCanBoard(Player player, String lineId);

// 直接访问管理器
LineManager getLineManager();
StopManager getStopManager();
```

### 在 plugin.yml 中声明依赖

```yaml
softdepend:
  - Metro
```

或者在 `plugin.yml` 中使用 `depend: [Metro]` 强制要求 Metro 先加载。

## 快速开始

### 创建第一条地铁线路

1. **创建线路**: `/m line create line1 1号线`
2. **设置线路颜色**: `/m line setcolor line1 &9` （蓝色）
3. **设置终点方向**: `/m line setterminus line1 东城总站方向`

### 创建停靠区

1. **创建停靠区**: `/m stop create station1 中央车站`
2. **选区**: 手持金锄头左/右键点击停靠区的两个对角点
3. **应用选区**: `/m stop setcorners station1`
4. **设置停靠点**: 站在红石铁轨上执行 `/m stop setpoint`
5. **添加到线路**: `/m line addstop line1 station1`

### 配置票价

**固定票价：**
```
/m line setprice line1 10.0
```

**按距离计价（每格 0.05，最高 20）：**
```
/m line setfare line1 distance 2.0 0.05 20.0
```

**按区间计价（每站间 5.0，最高 30）：**
```
/m line setfare line1 interval 1.0 5.0 30.0
```

### 设置线路暂停运营

```
/m line setstatus line1 suspended
/m line setsuspensionmsg line1 "&c1号线正在进行设备检修"
/m line setaltroute line1 line2
/m line setstatus line1 normal
```

### 玩家使用

玩家右键点击停靠区内的红石铁轨即可呼叫矿车并自动乘坐，系统会自动处理行驶和到站。

## 配置文件

- `config.yml` — 全局配置，包括显示样式、音效、矿车设置、速度控制模式、地图集成、传送门、脱轨设置等
- `lines.yml` — 线路数据存储（包含票价规则、运营状态配置）
- `stops.yml` — 停靠区数据存储
- `lang/zh_CN.yml` — 中文语言文件
- `lang/en_US.yml` — 英文语言文件

### config.yml 亮点配置

```yaml
# 速度控制模式
speed_control:
  mode: VANILLA_MOMENTUM  # VANILLA_MOMENTUM（原版惯性）或 BLOCK_BASED（方块实时控速）

# 经济系统
economy:
  enabled: true  # 是否启用 Vault 经济收费

# 网页地图集成
map_integration:
  enabled: false  # 是否在地图上显示线路
  provider: AUTO  # AUTO, BLUEMAP, DYNMAP, SQUAREMAP

# 安全模式
settings:
  safe_mode:
    enabled: true  # 保护乘客免受干扰
```

## 构建

- **构建插件**: `mvn clean package`
  - 生成 `target/metro-<version>.jar`
  - 当前版本号为 `2.0.0`

本分支是单模块 Maven 项目，面向 Java 8 与 1.16.5+ 服务器（含 Paper/Folia 兼容逻辑）。

### 依赖

- **Paper 1.16.5 API**（Spigot 兼容）
- **Vault**（可选，用于经济系统）
- **BlueMap / Dynmap / Squaremap**（可选，用于网页地图显示）
- **ViaVersion**（可选，用于跨版本支持）

## 链接

- [原版 GitHub 仓库](https://github.com/CubeX-MC/Metro)
- [Discord](https://discord.com/invite/7tJeSZPZgv)
- [QQ频道](https://pd.qq.com/s/1n3hpe4e7?b=9)

---

*Metro-Forked — 在保留原版核心功能的基础上，增加高级票价、线路状态管理与公开 API 的增强分支。*
