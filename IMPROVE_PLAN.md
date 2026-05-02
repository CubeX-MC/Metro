# Metro 改进入口

本文档是 agent 进入 Metro 项目时的首要改进入口。它只保留当前决策所需的信息：项目状态、已完成能力摘要、剩余优先级、工作边界和验收标准。

完整历史计划与已完成细节已归档到：

- `docs/archive/improve-plan-2026-04-29.md`

最近整理时间：2026-05-01

## 1. 当前状态

- Maven 单模块项目，主插件版本 `1.1.5`。
- 最近记录的验证结果：`mvn verify` 已通过。
- 最近记录的测试状态：401 个单元测试，通过率 100%。
- 最近记录的静态检查：SpotBugs 0 个问题。
- 最近记录的覆盖率：JaCoCo 行覆盖率约 42%，指令覆盖率约 41%，质量门最低行覆盖率 25%。
- 核心能力已覆盖线路、站点、矿车运行、站台提示、计分板、音效、GUI、Vault、BlueMap/Dynmap/Squaremap、Folia 调度适配和数据迁移。

如果准备发布、调整质量门或做跨模块重构，应先重新运行本地验证，不要只依赖上述历史记录。

## 2. Agent 使用方式

进入项目后优先读本文件，然后按任务读取相关上下文：

- 调度、Folia、线程边界：`docs/architecture.md`
- 手工回归场景：`docs/regression-baseline.md`
- 发布验证：`docs/release-checklist.md`
- 兼容性说明：`docs/compatibility.md`
- 已完成改进细节：`docs/archive/improve-plan-2026-04-29.md`

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

## 4. 当前剩余重点

### P0：Minecraft 26.1.2 兼容升级

目标：发布一个继续向前兼容到 Minecraft 1.18 的 Metro jar，同时确认它能在 Minecraft/Paper 26.1.2 上加载并完成核心流程。

决策：

- 主构建继续使用 Java 17 bytecode 和 Spigot API 1.18.2 编译，避免把 1.18 支持切断。
- `plugin.yml` 继续声明 `api-version: 1.18`，除非后续明确放弃 1.18。
- 不直接引入 26.1.2 专属 API；必须使用时放进反射/适配层，并提供旧版本 fallback。
- Paper 26.1.2 作为运行时验证目标处理，不作为主编译 API 基线。
- 26.1.2 服务端需要 Java 25 运行；Metro jar 本身仍以 Java 17 产物发布。

实施顺序：

1. 补强版本解析，确保 `1.18.2-R0.1-SNAPSHOT`、`1.21.11-R0.1-SNAPSHOT`、`26.1.2-R0.1-SNAPSHOT` 等格式都有测试覆盖。
2. 检查命令注册适配层，重点关注 `PaperCommandManager` / `LegacyPaperCommandManager` 在 Paper 26.1.2 的加载表现。
3. 保持 Bukkit/Spigot/Paper 公共 API 优先；禁止新增 NMS、CraftBukkit 或版本包依赖。
4. 建立运行矩阵：`1.18.2 + Java 17`、当前 LTS Paper（如 1.21.x + Java 21）、`26.1.2 + Java 25`。
5. 在真服 smoke test 覆盖插件加载、命令注册、GUI 打开、站点/线路管理、矿车发车、乘车扣费和地图软依赖禁用场景。
6. 如果 26.1.2 真服暴露 cloud 命令框架问题，再升级 `cloud-paper` / `cloud-minecraft-extras`，并回跑旧版本 smoke test。
7. 后续为世界标识增加 `world_key` 持久化字段，旧数据继续按 world name fallback。

### P1：推进核心服务与列车类覆盖率

目标：把关键 service/train 类逐步推到 70% 以上覆盖率，并在稳定后考虑提高 JaCoCo 质量门。

已知进度（2026-05-01 更新）：

- `ConfigFacade` 约 78% 行覆盖率。
- `LineManager` 约 74% 行覆盖率。
- `StopManager` 约 83% 行覆盖率。
- `TrainMovementAssistController` 约 95% 行覆盖率。
- `TrainTaskRegistry` 100% 行覆盖率。
- `TrainSession` 约 95% 行覆盖率。
- `TrainEventPublisher` 100% 行覆盖率。
- `TrainStateMachine` 100% 行覆盖率。
- `ScoreboardManager` 约 86% 行覆盖率。
- `TrainDisplayController` 约 83% 行覆盖率。
- `TrainMovementTask` 约 56% 行覆盖率。
- `TrainPhysicsController` 100% 行覆盖率。
- `PlayerInteractListener` 约 67% 行覆盖率。
- `VehicleListener` 约 85% 行覆盖率。

下一步：

1. 重新生成最新 JaCoCo 报告，确认当前低覆盖核心类。（✓ 已完成 2026-04-30）
2. 优先补纯服务、状态机、边界条件和失败路径测试。（进行中：已覆盖 TrainDisplayController 0→83%、ScoreboardManager 0→86%、TrainMovementTask 36→56%、TrainPhysicsController 54→100%、PlayerInteractListener 19→67%、VehicleListener 37→85%）
3. 对 Bukkit/Folia 事件流使用现有 Mockito 测试风格扩展，不引入重型测试框架，除非收益明确。
4. 覆盖率稳定后，再把 JaCoCo 行覆盖率门槛从 25% 提到下一个安全档位。
5. 当前仍需重点提高的低覆盖核心类：`TrainMovementTask`（56%）。

### P1：防止核心交互路径回归

重点守住这些路径：

- 多线路/换乘站右键站台时，候选线路、GUI 展示和实际发车线路必须一致。
- 终点站、反向线路、重叠站台不能把玩家送到错误方向。
- 乘车扣费必须和实际发车绑定，失败、取消、中途下车不能产生异常扣费。
- reload、disable、高频保存和保存失败不能造成旧数据覆盖新数据。
- GUI 删除、票价、颜色、终点方向、route 和 protection 操作必须继续经过确认或权限边界。

### P2：保持服务边界清晰

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

### P3：发布与文档持续维护

每次面向用户的功能变化都应同步：

- README / README_en 中的命令、权限、配置说明。
- `docs/release-checklist.md` 中的发布前检查。
- `docs/release-notes-template.md` 或实际发布说明。
- `docs/compatibility.md` 中的 Java、Minecraft、服务端和软依赖兼容信息。

## 5. 修改时的硬边界

避免重新引入这些问题：

- 不新增未声明权限。
- 不绕过 `ConfigFacade` 读取已有配置域，除非该配置确实只属于局部实现。
- 不把玩家可见消息硬编码在 Java 里，统一走语言文件和语言管理器。
- 不让旧异步保存结果覆盖新状态。
- 不让 GUI 点击处理直接吞进大量业务逻辑。
- 不让命令层重新承担权限、查找、校验、写入和展示的全部职责。
- 不在没有测试或回归清单更新的情况下修改核心乘车、保存、迁移、权限或调度路径。

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
