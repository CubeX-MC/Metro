# Metro 改进入口

本文档是 agent 进入 Metro 项目时的首要改进入口。它只保留当前决策所需的信息：项目状态、已完成能力摘要、剩余优先级、工作边界和验收标准。

完整历史计划与已完成细节已归档到：

- `docs/archive/improve-plan-2026-04-29.md`

最近整理时间：2026-05-16

## 1. 当前状态

- Maven 单模块项目，主插件版本 `1.1.6`。
- 最近记录的验证结果：`mvn verify` 已通过。
- 最近记录的测试状态：518 个单元测试，通过率 100%。
- 最近记录的静态检查：SpotBugs 0 个问题。
- 最近记录的覆盖率：JaCoCo 行覆盖率约 43.21%，质量门最低行覆盖率 25%。
- 核心能力已覆盖线路、站点、矿车运行、站台提示、计分板、音效、GUI、Vault、BlueMap/Dynmap/Squaremap、Folia 调度适配和数据迁移。
- 已集成 PriceRule 定价系统（flat/distance/interval）、LineStatus 线路状态（NORMAL/SUSPENDED/MAINTENANCE）和 MetroAPI。

如果准备发布、调整质量门或做跨模块重构，应先重新运行本地验证，不要只依赖上述历史记录。

## 2. Agent 使用方式

进入项目后优先读本文件，然后按任务读取相关上下文：

- 调度、Folia、线程边界：`docs/architecture.md`
- 手工回归场景：`docs/regression-baseline.md`
- 发布验证：`docs/release-checklist.md`
- 兼容性说明：`docs/compatibility.md`
- 已完成改进细节：`docs/archive/improve-plan-2026-04-29.md`
- CYY 分支合并计划：`MERGING_PLAN.md`

只有在需要追溯历史实现理由时才打开归档文件。日常改进应以本文件为准。

## 3. 已完成摘要

以下阶段已经完成，除非发现回归，不应重复规划同一批大任务：

- 权限、README、构建说明、GUI 命令说明与 `plugin.yml` 已对齐。
- `titles.stop_continuous` 已作为推荐配置键，`titles.enter_stop` 作为历史兼容键处理。
- 多线路站台选择、线路候选排序、选择 GUI、玩家最近选择记录已落地。
- 乘车扣费流程已改为更可靠的 ticket/transaction 模型，经济消息已进入语言文件。
- 数据保存已引入统一协调器、版本化快照、临时文件与原子替换、reload/disable flush 和失败日志。
- 旧命令类已清理，命令参数校验、权限判断和业务写操作已逐步收敛到 command service。
- GUI 渲染和点击处理已拆分到 view/controller，`GuiManager` 主要保留路由和返回栈逻辑。
- `Metro` 启动类已拆出命令注册、监听器注册、地图集成生命周期和定时任务生命周期。
- 列车运行逻辑已拆分出 session、状态机、调度、物理控制、显示事件、计分板和 active task registry。
- Rail Protection、路线录制、routeinfo、保护索引统计和 GUI 入口已补强。
- BlueMap/Dynmap/Squaremap 已抽象为统一 map integration，并支持 provider 选择、延迟刷新和异常隔离。
- Folia 调度策略已写入架构文档，当前 Paper/Bukkit 可接受，Folia 风险边界已有记录。
- 硬编码玩家消息已清理，语言 key 已做跨语言对齐测试。
- CI、发布清单、发布说明模板、兼容性文档和 release workflow 已建立。
- CYY 分支功能已整合：PriceRule 三种定价模式、LineStatus 状态系统、暂停线路拦截乘车、距离扣费、MetroAPI。
- `setprice` 命令已扩展支持 flat/distance/interval/reset 模式，新增 `priceinfo` 和 `setstatus` 命令。
- MetroAPI 已提供线路查询、票价计算、状态管理和 Vault 集成接口。
- PriceRule (25)、PriceService (11)、LineStatusService (18)、LineStatus (5)、LineCommandService (+12)、TicketService (+3)、RouteNormalizer (11)、PortalManager (13)、ScheduledTaskLifecycle (4) 已补单元测试；总计 518 测试。
- README / README_en 已更新所有命令说明。
- Minecraft 26.1.2 兼容已实现：`VersionUtil` 正则支持 26.1.2 格式，`LegacyPaperCommandManager` fallback 已就绪，`docs/compatibility.md` 有完整策略。
- `RouteNormalizer` 已实现：路线点吸附到铁轨方块中心 + 共线冗余删除，集成到 `RouteRecorder.saveSession()`。
- 2026-05-10 推进：`ScheduledTaskLifecycle` 已在 Folia 下跳过 legacy minecart 全世界扫描，并把 `PortalManager` 纳入自动保存任务。
- 2026-05-10 推进：`PortalManager` 已迁移到读写锁 + 快照构建 + `SaveCoordinator` 保存模型，`Metro.flushPersistentData()` 会同步 flush `portals.yml`。
- 2026-05-10 推进：`PortalManager.teleportMinecart()` 的 passenger restore 回调已先回到区域调度器，再访问 Bukkit 实体。
- 2026-05-10 推进：README 构建产物示例已同步到 `metro-1.1.6.jar`。
- 2026-05-10 推进：`LineStatus.fromConfig()` 已补空值、空白、大小写与非法值边界测试，并使用 `Locale.ROOT` 做稳定大小写归一化。
- 2026-05-10 推进：`PortalManager` 已补保存失败重试和并发修改/保存测试，确认 dirty 状态不会在协调器失败后丢失。
- 2026-05-10 推进：`PortalManager.teleportMinecart()` 已补目标世界不可用早退测试，确认不会触碰源矿车或进入调度流程。
- 2026-05-16 推进：Folia shutdown 下 active train cleanup 已改为通过矿车 entity scheduler 执行，不再由 disable 主流程直接访问活跃矿车实体。
- 2026-05-16 推进：`PortalManager.teleportMinecart()` 已补在线乘客恢复、玩家离线、目标区块未加载、新矿车失效和源矿车已失效测试；目标区块未加载时不会强制加载或生成新矿车。
- 2026-05-16 推进：`PortalManager.teleportMinecart()` 已处理 `SchedulerUtil.teleportEntity()` 返回失败的回调，失败时清理已生成的新矿车并复位旧任务 teleporting 标记，避免传送失败后留下孤立矿车。
- 2026-05-16 推进：Kyori Adventure 已通过 BOM 收敛到 4.25.0，shade 已过滤 manifest 与 module-info 元数据，`dependency-reduced-pom.xml` 不再生成到仓库根目录。
- 2026-05-16 推进：`RouteNormalizer` 已补真实 mock 世界/铁轨方块吸附测试，覆盖成功吸附与找不到铁轨保留原点。

## 4. 当前剩余重点

### P1：Folia 支持声明与线程边界

当前 `plugin.yml` 标记 `folia-supported: true`，主要已通过调度封装降低线程风险；发布前仍建议用真实服务端 smoke test 防止声明强于实际线程安全程度：

- `Metro.onDisable()` 已在 Folia 下跳过 fallback world scan，active train cleanup 已转入矿车 entity scheduler；仍需真实 Folia shutdown smoke test 验证 disable 阶段调度执行时序。
- `PortalManager.teleportMinecart()` 的 passenger restore 已转入区域调度器，并已有单元测试覆盖在线乘客、玩家离线、目标世界/区块不可用和矿车失效路径；仍建议补真实 Folia/Paper smoke test 覆盖跨世界传送。
- 如果短期内无法完全加固 Folia，应在兼容性文档中把 Folia 标为“实验/部分支持”，不要让发布说明承诺过满。

### P1：PortalManager 持久化与并发模型

`PortalManager` 已迁移到与 `LineManager` / `StopManager` 接近的保存模型，后续重点从“保存架构”转向“传送行为验证”：

- 已完成：读写锁、dirty 标记、快照构建、`processAsyncSave()`、`forceSaveSync()`、自动保存任务和 shutdown flush。
- 已覆盖：Portal create/persist/reload/location lookup、async save、link/delete、线路引用清理、ScheduledTaskLifecycle 自动保存调度、目标世界不可用、目标区块未加载、在线乘客恢复、玩家离线、新矿车失效、源矿车已失效和 passenger teleport 返回失败。
- 仍需覆盖：真实 Folia/Paper 跨世界 passenger restore smoke test。
- 传送门数据迁移已存在于 `DataFileUpdater`，改保存模型时必须验证旧 `portals.yml` 兼容。

### P1：新模块测试覆盖

已覆盖（2026-05-10）：

- `PriceRule` — 25 tests，calculatePrice / deserialize / 折扣 / 封顶 / 边界
- `PriceService` — 11 tests，countStopIntervals / getEstimatedPrice
- `LineStatusService` — 18 tests，setStatus / isBoardable / 替代线路
- `LineCommandService` — 26 tests (+12)，setPriceRule / setLineStatus / resetPriceRule / 新方法
- `TicketService` — 7 tests (+3)，estimatedMinimumPrice / distance boarding check
- `TrainMovementTask` — 39 tests (+3)，settleDistanceFare 新逻辑已覆盖
- `LineStatus` — 5 tests，fromConfig 空值 / 空白 / 大小写 / 非法值、config key、boardable
- `PortalManager` — 13 tests，保存、重载、查询、link/delete、线路引用清理、保存失败重试、并发修改与保存、目标世界不可用早退、目标区块未加载、在线/离线乘客恢复、新旧矿车失效、乘客传送失败清理
- `ScheduledTaskLifecycle` — 4 tests，Folia 跳过 legacy 扫描、非 Folia 调度、自动保存、shutdown cancel
- `TrainTaskRegistry` — 4 tests，注册/迁移/去重 shutdown，以及 Folia shutdown 下转入 entity scheduler

仍需补测：

- `PortalManager` — 真实 Folia/Paper 跨世界 smoke test

### P1：防止核心交互路径回归

重点守住这些路径：

- 多线路/换乘站右键站台时，候选线路、GUI 展示和实际发车线路必须一致。
- 终点站、反向线路、重叠站台不能把玩家送到错误方向。
- 乘车扣费必须和实际发车绑定，失败、取消、中途下车不能产生异常扣费。
- reload、disable、高频保存和保存失败不能造成旧数据覆盖新数据。
- GUI 删除、票价、颜色、终点方向、route 和 protection 操作必须继续经过确认或权限边界。

### P2：依赖收敛与 Shade 治理（已完成）

`mvn verify` 当前通过，Kyori Adventure 已通过 BOM 收敛到 4.25.0，shade 阶段不再输出 `module-info.class` 与资源重叠警告。

已完成：

- 用 `dependencyManagement` 明确统一 Kyori Adventure 版本，并通过 `mvn verify` 确认 scoreboard-library 与 cloud 当前组合可用。
- 为 `module-info.class`、多版本 module-info 和重复 manifest 增加 shade filter。
- 禁用根目录 `dependency-reduced-pom.xml` 生成，并把该生成物加入 `.gitignore`。
- 保持 Spigot API 1.18.2 与 Java 17 作为编译基线，不为了消除警告引入更高 API 绑定。

### P2：Route Normalizer 后续改进

当前路线录制已支持更密采样，并在保存前把浮点轨迹吸附到铁轨方块中心、删除同一直线上的冗余采样点。后续若继续提升在线地图和 rail protection 精度，应继续加固特殊轨道形态：

1. 对交叉轨、并行轨、传送门前后和站台内低速抖动补更贴近真实方块布局的单元测试。
2. 继续确认地图渲染、routeinfo 和 rail protection 均使用保存后的规范化 route points，避免各自猜测拐点。
3. 如果后续暴露出误吸附，考虑把 rail protection 的附近铁轨查找与 RouteNormalizer 合并成共享 helper。

### P2：保持服务边界清晰（已完成）

未来新增功能时遵守现有边界：

- 命令类只负责声明命令和收集输入，业务写操作放到 command service。
- GUI view 只渲染，controller 只处理点击和导航，业务规则放到 service/manager。
- `Metro` 主类只做生命周期编排，不重新塞回具体注册、刷新、迁移或调度逻辑。
- 列车移动相关状态优先放入 `TrainSession`、`TrainStateMachine`、`TrainScheduler` 或对应 controller。

### P2：Folia 与调度器治理

当前 Folia 已有适配和风险记录，但仍应谨慎：

- 涉及实体、世界、玩家、矿车和区块的操作必须先看 `docs/architecture.md` 的 Scheduler Policy。
- 不直接新增裸 Bukkit scheduler 调用，优先使用现有调度封装。
- 如果需要扩大 Folia 支持范围，先补最小可验证场景，再改实现。
- 新增或修改传送门、地图刷新、legacy 数据迁移、shutdown cleanup 时，默认按 Folia 风险路径审查。

### P3：发布与文档持续维护

每次面向用户的功能变化都应同步：

- `docs/release-checklist.md` 中的发布前检查。
- `docs/release-notes-template.md` 或实际发布说明。
- `docs/compatibility.md` 中的 Java、Minecraft、服务端和软依赖兼容信息。
- README 构建产物版本示例必须与 `pom.xml` / `plugin.yml` 当前版本一致，避免发布页出现旧 jar 名。
- 若 `folia-supported` 声明、质量门或兼容矩阵变化，必须同步 `docs/compatibility.md`、README 和 release notes。

## 5. 修改时的硬边界

避免重新引入这些问题：

- 不新增未声明权限。
- 不绕过 `ConfigFacade` 读取已有配置域，除非该配置确实只属于局部实现。
- 不把玩家可见消息硬编码在 Java 里，统一走语言文件和语言管理器。
- 不让旧异步保存结果覆盖新状态。
- 涉及 `portals.yml` 的改动必须继续走快照保存、锁和迁移兼容，不回退到同步裸保存。
- 不让 GUI 点击处理直接吞进大量业务逻辑。
- 不让命令层重新承担权限、查找、校验、写入和展示的全部职责。
- 不重新引入 CYY 分支的 1.16 兼容降级（switch 表达式→语句、record→class、isBlank→trim、instanceof 模式→显式转换）。
- 不在没有测试或回归清单更新的情况下修改核心乘车、保存、迁移、权限或调度路径。
- 不新增或保留 Folia 下未确认安全的全世界实体扫描、异步实体访问或跨 region 实体操作。

## 6. 建议工作顺序

1. 根据任务范围读取相关 service/manager/listener/controller。
2. 先补或定位失败测试，再做实现。
3. 小步提交，避免顺手重构无关区域。
4. 修改玩家消息时同步所有语言文件，并运行语言 key 对齐测试。
5. 修改配置时同步默认配置、迁移逻辑、兼容读取和文档。
6. 修改调度、实体或列车状态时同步架构说明或回归清单。
7. 完成后运行与风险匹配的测试；发布级变更运行 `mvn verify`。

## 7. Definition of Done

一个改进任务完成时应满足：

- 行为符合当前 README、配置和语言文件。
- 自动化测试覆盖主要成功路径和至少一个关键失败/边界路径。
- `mvn test` 或更高等级验证通过；发布前必须通过 `mvn verify`。
- 对用户可见的变化有文档或发布说明入口。
- 数据兼容、权限边界、Folia 调度风险已检查。
- 如果任务完成了本文件中的待办，应更新本文件，保持入口短而准。
