# Metro 改进计划书

本文档用于指导 Metro 插件后续改进。目标不是一次性重写，而是在保留现有可用能力的基础上，按优先级逐步提升稳定性、用户体验、代码质量和长期可维护性。

## 1. 当前基线

评估时间：2026-04-27

当前工程状态：

- Maven 项目，主插件版本 `1.1.5`。
- `mvn verify` 已通过。
- 单元测试：17 个，通过率 100%。
- SpotBugs：0 个问题。
- JaCoCo 质量门存在，但最低行覆盖率仅为 6%。
- 核心能力已覆盖线路、站点、矿车运行、站台提示、计分板、音效、GUI、Vault、BlueMap/Dynmap/Squaremap、Folia 调度适配、数据迁移。

综合判断：

- 插件已经具备真实服务器使用价值。
- 下阶段重点应从“继续增加功能”转为“稳定交互路径、清理历史包袱、提高测试和发布可信度”。

## 2. 改进目标

长期目标：

- 让管理员能稳定、低成本地维护复杂地铁网络。
- 让玩家在多线路、多方向、换乘站中能明确选择想乘坐的线路。
- 让插件在 Paper/Folia、地图插件、经济插件共存场景下保持可预测。
- 让代码结构支持持续演进，避免命令层、GUI 层和启动类继续膨胀。
- 让每次发布都有可执行的验证流程和足够自动化测试支撑。

优先级定义：

- P0：必须优先处理，影响权限、安全、数据可靠性或核心体验。
- P1：重要改进，影响日常使用体验或维护成本。
- P2：结构优化，降低未来开发成本。
- P3：增强项，可在核心稳定后推进。

## 3. 阶段路线图

### 阶段 A：修正明显不一致项

目标：消除权限、文档、配置和命令入口中的明显误导。

建议周期：1 个小版本。

任务：

1. [x] 补齐 `metro.gui` 权限声明。
2. [x] 更新 README 和 README_en 的构建说明、权限说明和 GUI 命令说明。
3. [x] 梳理 `titles.enter_stop` 与 `titles.stop_continuous` 的历史兼容关系。
4. [x] 明确旧命令类是否废弃，并在代码结构中做标记或移除。
5. [x] 提高最基础测试覆盖阈值，先从 6% 提升到 15%-20%。

验收标准：

- 新安装服务器中 `/m gui` 权限行为与 `plugin.yml` 一致。
- README 中不再出现与当前单模块 Maven 项目不一致的构建路径。
- 配置文件中的 title 键有唯一推荐写法。
- `mvn verify` 继续通过。

### 阶段 B：解决多线路站台体验

目标：玩家在换乘站或重叠站台中能准确选择线路，站台展示也能稳定反映线路。

建议周期：1-2 个小版本。

任务：

1. [x] 新增线路选择服务。
2. [x] 右键站台时按规则解析候选线路。
3. [x] 候选线路超过 1 条时打开线路选择 GUI。
4. [x] 站台持续提示支持多线路摘要。
5. [x] 记录玩家最近选择的线路，提高重复乘车效率。

验收标准：

- 一个站点同时属于多条线路时，玩家不会被随机分配到第一条线路。
- 管理员能配置或理解线路选择规则。
- GUI、ActionBar、Title 显示的线路与实际发车线路一致。

### 阶段 C：提高数据保存可靠性

目标：避免异步保存乱序、异常中断和 reload 期间产生数据丢失风险。

建议周期：1 个小版本。

任务：

1. [x] 引入统一保存协调器。
2. [x] 采用版本化快照，避免旧异步写入覆盖新数据。
3. [x] 使用临时文件加原子替换写入 YAML。
4. [x] `reload` 和 `onDisable` 前强制 flush。
5. [x] 为保存失败、重试和最终失败增加日志。

验收标准：

- 高频创建、删除、修改线路和站点时，最终 YAML 与内存状态一致。
- 插件关闭时不会丢弃 dirty 数据。
- 保存失败时日志明确指出文件、原因和是否已重试。

### 阶段 D：拆分臃肿类，建立服务边界

目标：降低命令、GUI、启动类和运行状态机的复杂度。

建议周期：2-3 个小版本，分批做。

任务：

1. [x] 清理旧 `MetroAdminCommand` 和 `MetroAdminTabCompleter`。
2. 将命令参数校验、权限判断、业务操作从命令类中抽离。
3. 将 GUI 渲染和 GUI 点击处理拆成更小的 view/controller。
4. 将 `Metro` 启动类拆成生命周期注册步骤。
   - [x] 已将 Cloud 命令管理器创建、suggestion providers 和 annotation command 注册拆到 `CommandRegistration`。
   - [x] 已将 Bukkit listener 创建和事件注册拆到 `ListenerRegistration`。
   - [x] 已将 BlueMap/Dynmap/Squaremap 启停与刷新队列拆到 `MapIntegrationLifecycle`。
   - [x] 已将自动保存定时任务和旧矿车 PDC 兼容迁移任务拆到 `ScheduledTaskLifecycle`。
5. 将列车运行状态机拆分为状态、调度和显示事件三层。

验收标准：

- 主要命令类单文件长度下降到 500 行以内。
- GUI 类不再同时承担数据查询、渲染和业务写入。
- 新增一个命令或 GUI 功能不需要修改多个巨型 switch。

### 阶段 E：补测试与发布流程

目标：让关键用户路径可回归、可验证、可发布。

建议周期：持续执行。

任务：

1. 为线路选择、权限、数据保存、配置迁移补单元测试。
2. 引入 MockBukkit 或等效测试方案覆盖 Bukkit 事件流。
3. 建立手工回归清单和测试地图场景。
4. 在 CI 中运行 `mvn verify`。
5. 发布前生成变更摘要和兼容性说明。

验收标准：

- 核心路径失败能被自动化测试捕获。
- PR 合并前必须通过测试和静态检查。
- 发布包对应的配置迁移路径经过验证。

## 4. 详细任务清单

### 4.1 权限与文档一致性

优先级：P0

问题：

- `/m gui` 使用 `metro.gui` 权限，但 `plugin.yml` 未声明。
- README 中构建说明仍包含旧的多模块路径描述。
- README 权限表缺少 GUI 权限、传送权限细节和部分新增功能说明。

实现方法：

1. [x] 在 `src/main/resources/plugin.yml` 中新增：

```yaml
metro.gui:
  description: Allow players to open the Metro GUI
  default: true
```

2. [x] 评估是否将 `metro.gui` 加入 `metro.admin` children。
   结论：不加入。GUI 默认向所有玩家开放，界面内部按权限显示可用操作。

3. [x] 更新 `README.md` 和 `README_en.md`：
   - 构建命令改为当前单模块项目：`mvn clean package`。
   - 输出文件说明改为 `target/metro-<version>.jar`。
   - 补充 `/m gui`。
   - 补充 `metro.gui`、`metro.tp`、线路/站点创建权限。

4. [x] 更新 `docs/release-checklist.md`：
   - 发布前检查 `plugin.yml` 权限与 README 权限表一致。
   - 发布前检查默认配置与 `ConfigFacade` 读取路径一致。

验收：

- 无未声明却被代码使用的权限。
- README 与实际 Maven 构建结果一致。

### 4.2 配置键统一与兼容迁移

优先级：P0

问题：

- 默认配置中主站台提示使用 `titles.stop_continuous`。
- `ConfigFacade` 仍读取 `titles.enter_stop`。
- 部分代码直接访问 `plugin.getConfig()`，绕过 `ConfigFacade`。

实现方法：

1. [x] 确认推荐配置键：
   - 推荐保留 `titles.stop_continuous`。
   - 将 `titles.enter_stop` 标记为历史兼容键。

2. [x] 修改 `ConfigFacade`：
   - 新增 `StopContinuousConfig` 或等价字段。
   - 所有站台持续显示配置从 `titles.stop_continuous` 读取。
   - 如果旧配置存在 `titles.enter_stop` 且新配置不存在，则迁移到新路径。

3. [x] 修改 `ConfigUpdater`：
   - 增加旧键迁移逻辑。
   - 迁移后保留注释尽量不强求，但必须保证行为一致。

4. [x] 修改 `PlayerMoveListener`：
   - 减少直接 `plugin.getConfig()` 调用。
   - 通过 `ConfigFacade` 获取 interval、always、fade、title、subtitle、actionbar。

5. [x] 增加测试：
   - 旧配置只含 `titles.enter_stop` 时，能被迁移或兼容读取。
   - 新配置优先级高于旧配置。
   - reload 后缓存值更新。

验收：

- 默认配置、代码读取、文档说明三者一致。
- 旧配置升级后站台提示不丢失。

### 4.3 多线路站台选择

优先级：P0

问题：

- `PlayerInteractListener` 在候选线路中取第一条。
- `PlayerMoveListener` 显示站台信息时也取第一条。
- `HashMap` / `HashSet` 顺序不保证稳定，实际体验可能近似随机。

设计目标：

- 单线路站点保持一键乘车。
- 多线路站点提供明确选择。
- 可根据玩家朝向、铁轨方向或上次选择自动推荐线路。
- GUI 与命令逻辑共用同一个线路解析服务。

实现方法：

1. [x] 新增服务类：

```text
org.cubexmc.metro.service.LineSelectionService
```

职责：

- `List<Line> getBoardableLines(Stop stop)`
- `Line resolveDefaultLine(Player player, Stop stop, Location clickedLocation)`
- `boolean requiresChoice(Player player, Stop stop)`
- `void rememberChoice(Player player, String stopId, String lineId)`

2. [x] 候选线路过滤规则：
   - 必须包含当前 stop。
   - 当前 stop 不能是该线路终点。
   - 下一站必须存在且站点配置完整。
   - 如果线路世界已设置，则必须与站点世界一致。
   - 若站点有 linked line 限制，则遵守授权关系。

3. [x] 推荐排序规则：
   - 玩家最近在此站选择过的线路优先。
   - 与玩家 yaw 最接近的 stop launchYaw 优先。
   - 线路 ID 字典序作为最终稳定排序。

4. [x] 新增乘车选择 GUI：

```text
GuiType.LINE_BOARDING_CHOICE
```

内容：

- 显示线路名称、颜色、下一站、终点方向、票价。
- 左键选择并生成矿车。
- 右键可查看线路途经站点。
- 无权限或余额不足时用 barrier/lore 显示原因。

5. [x] 调整 `PlayerInteractListener`：
   - 右键站点后调用 `LineSelectionService`。
   - 单候选线路：直接乘车。
   - 多候选线路：打开选择 GUI，不立即扣费、不立即创建 pending minecart。
   - 玩家选择线路后再扣费和生成矿车。

6. [x] 调整站台提示：
   - 单线路：保持现有 title/subtitle。
   - 多线路：ActionBar 显示可乘线路摘要，例如 `可乘: 1号线 -> A站 | 2号线 -> B站`。
   - Title 保持站名，避免闪烁或信息过载。

7. 增加测试：
   - 多线路站点候选线路稳定排序。
   - 终点站线路不出现在可乘列表。
   - 选择线路后实际发车线路与 GUI 显示一致。

验收：

- 同一站点属于多条线路时，乘车线路不再依赖集合遍历顺序。
- 玩家能在 GUI 中明确选择线路。
- 站台提示不会误导玩家。

### 4.4 乘车经济流程改进

优先级：P1

问题：

- 当前扣费发生在生成矿车流程前。
- 如果生成矿车失败或 addPassenger 失败，可能已经扣费。
- 经济失败时部分消息仍为硬编码英文。

实现方法：

1. [x] 新增 `TicketService`：

```text
org.cubexmc.metro.service.TicketService
```

职责：

- 查询票价。
- 检查 Vault 可用性。
- 检查余额。
- 执行扣费。
- 失败回滚或延迟扣费。

2. [x] 调整扣费时机：
   - 推荐在矿车成功生成且 `addPassenger` 成功后扣费。
   - 若扣费失败，则移除矿车并提示玩家。

3. [x] 增加交易上下文：

```text
TicketTransaction {
  Player player;
  Line line;
  double price;
  boolean charged;
}
```

4. [x] 所有经济消息进入语言文件：
   - `economy.transaction_failed`
   - `economy.vault_unavailable`
   - `economy.refunded`

5. [x] 增加测试：
   - 余额不足不生成矿车。
   - addPassenger 失败不扣费。
   - 扣费成功后向线路 owner 入账。

验收：

- 玩家不会因生成失败被扣费。
- 所有经济提示可本地化。

### 4.5 数据保存可靠性

优先级：P0

问题：

- `LineManager` 和 `StopManager` 各自维护 dirty flag。
- 保存时生成 YAML 字符串后异步写入。
- 多次保存可能出现旧快照晚写入覆盖新快照。

实现方法：

1. [x] 新增统一保存协调器：

```text
org.cubexmc.metro.persistence.SaveCoordinator
```

职责：

- 管理保存版本号。
- 串行化同一文件的写入。
- 支持延迟合并保存。
- 支持 shutdown flush。

2. [x] 保存请求模型：

```text
SaveRequest {
  Path targetFile;
  long version;
  Supplier<String> snapshotSupplier;
}
```

3. [x] 写入策略：
   - 主线程或安全区域内生成快照。
   - 异步线程写入 `file.tmp`。
   - 写入成功后使用 atomic move 替换目标文件。
   - 如果平台不支持 atomic move，则退化为 replace existing 并记录 debug 日志。

4. [x] 防乱序策略：
   - 每个文件保存版本递增。
   - 异步写入完成前检查版本是否仍然是最新。
   - 旧版本完成时直接丢弃，不覆盖目标文件。

5. [x] 修改 `LineManager` / `StopManager`：
   - `saveConfig()` 改为标记 dirty 并提交给 `SaveCoordinator`。
   - `processAsyncSave()` 可删除或变为协调器入口。
   - `forceSaveSync()` 使用协调器 flush。

6. [x] 增加测试：
   - 连续提交 v1、v2，v1 晚完成时不能覆盖 v2。
   - 删除线路后 YAML 不保留旧 section。
   - 插件 disable 时 dirty 数据落盘。

验收：

- 高频修改不会产生旧数据覆盖新数据。
- 保存失败可观测、可定位。

### 4.6 命令体系清理

优先级：P1

问题：

- 新命令使用 Cloud annotation。
- 旧 `MetroAdminCommand` 和 `MetroAdminTabCompleter` 仍存在且体积很大。
- 新贡献者难以判断应修改哪套命令。

实现方法：

1. [x] 确认旧命令未在 `plugin.yml` 或启动代码中注册。
2. [x] 若确实未使用：
   - 删除旧命令类。
   - 删除旧 tab completer。
   - 更新 changelog，说明内部清理不影响用户命令。

3. 若需要保留兼容：
   - 移动到 `command.legacy` 包。
   - 添加类注释：仅供历史参考，不再注册。
   - 不允许新增功能写入 legacy 命令。

4. 抽离命令服务：

```text
LineCommandService
StopCommandService
PortalCommandService
```

   - [x] 先抽出共享 `CommandGuard`，统一线路/站点查找、权限失败提示和 owner/admin 占位符格式化。
   - [x] 新增 `LineCommandService`，迁移线路写操作和基础参数校验。
   - [x] 将线路 trust/untrust/owner 写操作迁移到 `LineCommandService`。
   - [x] 新增 `StopCommandService`，迁移站点写操作和基础参数校验。
   - [x] 将站点 trust/untrust/owner 写操作收敛到 `StopCommandService`。
   - [x] 新增 `PortalCommandService`，迁移传送门写操作和基础参数校验。
   - [x] 将传送门列表展示数据和 reload 流程收敛到 `PortalCommandService`。
   - [x] 新增 `CommandDisplayService`，统一 Line/Stop/Portal 命令帮助展示、分页和页码夹取。
   - [x] 将线路/站点列表数据准备和稳定排序迁移到对应 command service。
   - [x] 继续收敛命令展示逻辑和页码校验。

命令类只负责：

- 参数接收。
- 调用 service。
- 发送本地化消息。

5. 建立统一参数校验：
   - ID 格式校验。
   - 颜色格式校验。
   - 速度和票价范围校验。
   - 页码范围校验。
   - [x] 线路创建/反向克隆 ID、线路颜色、最大速度、票价已在 `LineCommandService` 中校验。
   - [x] 站点创建 ID、停靠点、标题类型/键、link action 已在 `StopCommandService` 中校验。
   - [x] 传送门创建 ID、入口位置、目标位置和配对目标已在 `PortalCommandService` 中校验。
   - [x] 帮助页页码校验已迁移到 `CommandDisplayService`。
   - [x] 列表类命令如需分页时继续复用统一页码校验。

6. 增加命令测试：
   - create/delete/rename 权限。
   - addstop 权限与 linked line 规则。
   - setprice 非法输入。

验收：

- 没有两套可疑命令入口。
- 命令类长度和复杂度明显下降。

### 4.7 GUI 改进

优先级：P1

问题：

- GUI 已可用，但功能不完整。
- 删除线路/站点缺少确认步骤。
- GUI 渲染和点击业务逻辑耦合较强。
- 多线路乘车选择尚未支持。

实现方法：

1. [x] 增加通用确认 GUI：

```text
GuiType.CONFIRM_ACTION
```

用于：

- 删除线路。
- 删除站点。
- 清空 route points。
- 关闭线路保护。

2. 增加返回栈：
   - 在 `GuiHolder` 中保存 `previousView`。
   - 返回按钮回到实际来源，而不是固定回主菜单或列表。

3. 拆分 GUI：

```text
gui.view.LineListView
gui.view.StopListView
gui.view.LineDetailView
gui.controller.LineGuiController
gui.controller.StopGuiController
```

4. 补齐 GUI 功能：
   - 线路颜色设置。
   - 终点方向设置。
   - 票价设置。
   - route 录制/清空/状态查看。
   - rail protection 开关和状态。
   - stop point 设置提示。

5. 优化显示：
   - 多线路同名时明确显示 ID。
   - 线路 lore 显示下一站、终点方向、票价、管理状态。
   - 无权限按钮使用灰色或 barrier，lore 说明原因。

6. 增加测试或手工回归：
   - 翻页。
   - 筛选我的线路/站点。
   - 删除确认取消。
   - 聊天输入 cancel。

验收：

- GUI 操作不会误删数据。
- GUI 覆盖常用管理功能。
- 多线路场景下 GUI 能明确展示 ID 和方向。

### 4.8 列车状态机稳定性

优先级：P1

问题：

- `TrainMovementTask` 同时处理状态、调度、速度控制、进站停车、路线录制和辅助防卡。
- 真实服务器中的边界场景较多：玩家下线、跨世界传送门、矿车被移除、站点配置缺失、chunk unload。

实现方法：

1. 将状态机拆分：

```text
train.TrainSession
train.TrainStateMachine
train.TrainScheduler
train.TrainPhysicsController
```

2. 明确状态：

```text
WAITING_AT_STATION
DEPARTING
MOVING_BETWEEN_STATIONS
ARRIVING
DOCKED
TERMINATED
CANCELLED
```

3. 统一取消路径：
   - 玩家下车。
   - 玩家下线。
   - 矿车消失。
   - 线路或站点 reload 后失效。
   - 传送门失败。

4. 所有定时任务记录到 session：
   - cancel session 时统一 cancel。
   - 防止 countdown actionbar 任务残留。

5. 增加防御检查：
   - `line == null`
   - `currentStop == null`
   - `targetStop == null`
   - `stopPointLocation == null`
   - `world == null`

6. 增加事件测试：
   - 上车后等待发车。
   - 中途下车取消 session。
   - 终点站自动下车并移除矿车。
   - 传送门转移矿车后 session 继续。

验收：

- 所有 session 都能被正常结束或取消。
- reload / disable 后不残留矿车、计分板、Title 或调度任务。

### 4.9 Rail Protection 与路线录制

优先级：P1

问题：

- route recording 是有价值的新增能力，但需要更清晰的管理闭环。
- rail protection 基于 route points，需要保证记录质量和提示充分。

实现方法：

1. 改进录制流程：
   - `/m line recordroute <id>` 第一次执行开始录制。
   - 再次执行结束录制。
   - 终点自动结束时给管理员明确提示。
   - 录制点过少时提示可能原因。

2. 增加 route 可视化信息：
   - route point 数量。
   - 保护铁轨数量。
   - 最近一次录制时间。
   - 录制使用的玩家/矿车。

3. 增加保护范围校验：
   - route point 世界与 line world 不一致时警告。
   - 找不到铁轨时统计 skipped samples。

4. 增加命令：

```text
/m line routeinfo <id>
/m line clearroute <id>
/m line protect <id> status
/m line protect <id> on
/m line protect <id> off
```

5. [x] 增加 GUI 入口。

6. 增加测试：
   - route points 插值。
   - blockToLines 索引重建。
   - 管理员可破坏自己有权限的受保护线路。
   - 非管理员无法破坏受保护线路。

验收：

- 管理员能理解当前线路是否已经被保护。
- 保护索引 reload 后正确重建。

### 4.10 地图集成稳定性

优先级：P2

问题：

- 同时存在 BlueMap、Dynmap、Squaremap 集成。
- 地图刷新由数据变更触发，但错误隔离和 provider 选择需要进一步收敛。

实现方法：

1. 新增统一接口：

```text
MapIntegration {
  boolean isAvailable();
  void enable();
  void disable();
  void refresh();
}
```

2. 新增 `MapIntegrationManager`：
   - 根据配置 provider 只启用指定 provider。
   - 如果配置 provider 不可用，记录明确日志。
   - 可选支持 AUTO 模式。

3. 刷新策略：
   - 数据保存后延迟合并刷新。
   - 多次变化只刷新一次。
   - 刷新失败不影响主插件运行。

4. 增加地图内容校验：
   - stop marker 开关。
   - transfer info 开关。
   - line width。
   - line color fallback。

验收：

- 启用一个地图 provider 时不会无意义初始化另外两个。
- 地图插件缺失时日志简洁清楚。

### 4.11 Folia 与调度器治理

优先级：P1

问题：

- `SchedulerUtil` 通过反射兼容 Folia，这是必要但较脆弱。
- 部分 Bukkit API 调用仍可能在 Folia 非安全线程中被误用。

实现方法：

1. 审计所有 Bukkit 世界、实体、方块访问：
   - 读取实体位置。
   - spawnEntity。
   - teleport。
   - world.getBlockAt。
   - world.getEntities。

2. 将调度策略文档化：
   - 全局任务何时用。
   - region 任务何时用。
   - entity 任务何时用。
   - async 任务中禁止访问 Bukkit 实体和世界对象。

3. 为 `SchedulerUtil` 增加测试或运行时自检：
   - Folia reflection 初始化失败时只提示一次。
   - fallback 到 Bukkit 调度时警告当前可能不完全 Folia safe。

4. 对 `onDisable` 中世界实体扫描做风险评估：
   - Paper 可接受。
   - Folia 可能需要分世界/分 region 安全清理。

验收：

- 代码中异步线程不访问 Bukkit 非线程安全对象。
- Folia 相关限制写入 `docs/architecture.md`。

### 4.12 语言文件与消息系统

优先级：P2

问题：

- 多语言文件较完整，但部分新增消息仍硬编码。
- 消息 key 分布可能逐渐失控。

实现方法：

1. 扫描硬编码玩家可见文本：
   - `ChatColor.RED + "..."`
   - 英文错误消息。
   - Debug 外的中文固定文本。
   - [x] 已先迁移 route recording、route info、rail protection 和 GUI 票价错误相关玩家提示。

2. 全部迁移到语言文件：
   - `en_US.yml` 作为基准。
   - 其他语言缺失时 fallback。
   - [x] 已补齐 route/protection 新消息到 `zh_CN`、`zh_TW`、`en_US`、`de_DE`、`es_ES`、`nl_NL`。

3. 增加语言 key 校验测试：
   - 所有语言文件包含 `en_US` 的 key。
   - 允许少数显式忽略 key。
   - [x] 已新增 route/protection 消息的内置语言 key 回归测试。
   - [x] 已升级为全量跨语言 key 对齐测试，并补齐 `de_DE`、`es_ES`、`nl_NL` 中缺失的旧 GUI/chat/usage key。

4. 定义 key 命名规范：
   - `line.*`
   - `stop.*`
   - `gui.*`
   - `economy.*`
   - `route.*`
   - `protection.*`
   - `portal.*`

验收：

- 玩家可见消息不再硬编码。
- 新增功能必须同步添加语言 key。

### 4.13 测试计划

优先级：P0-P1 持续执行

当前问题：

- 测试数量较少。
- 覆盖面集中在模型、管理器和数据迁移。
- GUI、事件流、经济、地图集成缺少验证。

实现方法：

1. 短期提高单元测试：
   - `LineSelectionServiceTest`
   - `TicketServiceTest`
   - `SaveCoordinatorTest`
   - `ConfigFacadeMigrationTest`
   - `RailProtectionManagerTest`

2. 引入 MockBukkit 或等效方案：
   - 模拟 PlayerInteractEvent。
   - 模拟 VehicleMoveEvent。
   - 模拟 InventoryClickEvent。

3. 增加回归测试地图说明：
   - 单线路普通站。
   - 双向线路重叠站。
   - 三线换乘站。
   - 终点站。
   - 传送门跨世界线路。
   - 受保护铁轨。

4. 覆盖率目标：
   - 第一阶段：15%-20%。
   - 第二阶段：30%。
   - 第三阶段：关键服务类 70% 以上。

5. CI：
   - 每次 PR 跑 `mvn verify`。
   - 发布 tag 跑 `mvn clean verify package`。
   - 上传构建产物。

验收：

- 新增关键服务必须有测试。
- `mvn verify` 是合并前必要条件。

### 4.14 发布与迁移流程

优先级：P1

问题：

- 功能增长后，用户升级风险变高。
- 数据文件包含 schema version，但需要更明确发布流程。

实现方法：

1. 完善 `docs/release-checklist.md`：
   - 构建。
   - 测试。
   - 配置迁移。
   - 数据迁移。
   - README 同步。
   - plugin.yml 权限同步。
   - 服务器实测。

2. 发布说明模板：

```text
Added
Changed
Fixed
Migration Notes
Compatibility Notes
Known Issues
```

3. 数据迁移策略：
   - 所有 schema 变化必须写测试。
   - 迁移前备份旧文件，例如 `lines.yml.bak-<version>`。
   - 迁移日志写明变更文件和 schema version。

4. 兼容性说明：
   - 支持的 Minecraft 版本。
   - 支持的 Paper/Spigot/Folia 状态。
   - 可选依赖版本。

验收：

- 每个 release 都有明确迁移说明。
- 用户升级失败时有备份可恢复。

## 5. 建议实施顺序

推荐顺序：

1. 补 `metro.gui` 权限，更新 README。
2. 统一配置键，减少直接 `plugin.getConfig()`。
3. 实现 `LineSelectionService`，解决多线路站台选择。
4. 加乘车选择 GUI。
5. 引入 `TicketService`，调整扣费时机。
6. 引入 `SaveCoordinator`，改造线路和站点保存。
7. 清理旧命令类。
8. 拆分 GUI。
9. 拆分列车状态机。
10. 加强 route recording / rail protection 管理闭环。
11. 重构地图集成 manager。
12. 提升测试覆盖率和 CI。

每一步都应保持：

- 小范围改动。
- 可单独发布。
- 有测试或手工回归记录。
- 不破坏现有配置和数据文件。

## 6. 风险与注意事项

### 数据兼容风险

涉及 `lines.yml`、`stops.yml`、`portals.yml`、`config.yml` 的修改必须带迁移测试。

建议：

- 所有迁移先备份。
- schema version 单调递增。
- 迁移失败时保留原文件。

### Folia 线程风险

涉及世界、实体、方块 API 的代码不能随意放入 async。

建议：

- 写入文件可以 async。
- 生成快照时不要访问不安全 Bukkit 对象。
- 实体操作使用 entity scheduler。
- 方块和区域操作使用 region scheduler。

### 用户体验风险

多线路选择 GUI 会改变原本“一键上车”的体验。

建议：

- 单线路站点保持一键上车。
- 多线路站点才弹选择。
- 记住玩家上次选择，减少重复操作。

### 重构风险

不要一次性重写 `TrainMovementTask`、GUI 和命令体系。

建议：

- 先加服务层，再迁移调用点。
- 保留旧行为测试。
- 每次只替换一个入口。

## 7. Definition of Done

一个改进任务完成时应满足：

- 代码通过 `mvn verify`。
- 新增或修改的用户可见文本已进入语言文件。
- 配置或数据结构变更有迁移逻辑。
- README 或 docs 已同步。
- 关键路径有测试；无法自动化时有手工回归记录。
- 对 Paper/Folia 的调度影响已评估。
- 不引入未声明权限。

## 8. 短期里程碑

### 里程碑 1：一致性修复版

目标版本建议：`1.1.6`

范围：

- 补齐 `metro.gui`。
- README 构建说明更新。
- 配置键兼容说明。
- 硬编码经济失败消息迁移到语言文件。
- 测试覆盖率阈值提升到 15%。

### 里程碑 2：多线路体验版

目标版本建议：`1.2.0`

范围：

- `LineSelectionService`。
- 多线路乘车选择 GUI。
- 多线路站台提示。
- 最近选择记忆。
- 对应测试。

### 里程碑 3：可靠保存版

目标版本建议：`1.2.1`

范围：

- `SaveCoordinator`。
- 原子写入。
- 保存版本防乱序。
- disable/reload flush。
- 保存失败日志。

### 里程碑 4：维护性治理版

目标版本建议：`1.3.0`

范围：

- 清理旧命令类。
- 拆分 GUI。
- 抽离命令 service。
- 更新架构文档。

## 9. 最终目标评分

完成以上计划后，预期质量评分：

- 插件设计：8.8 / 10
- 用户体验：8.7 / 10
- 代码质量：8.4 / 10
- 稳定性：8.6 / 10
- 可维护性：8.5 / 10
- 测试质量：8.0 / 10

综合目标：8.5 / 10 以上。
