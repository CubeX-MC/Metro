# Minecart Improvements 与 Metro

## 如何使用

本次实验物理适配以 **Paper 26.1.2 / Java 25** 为运行验证目标。Metro 根据矿车所在世界的 `minecraft:minecart_improvements` 特性标记自动选择行为，无需新增 Metro 开关。没有标记的世界继续使用旧物理。

- `settings.cart_speed` 是默认单车速度上限，线路 `max_speed` 可覆盖它，单位均为 **格/tick**。
- `speed_control.mode: BLOCK_BASED` 仍按轨道下方方块调整单车上限。
- 例如 `max_speed: 3.0` 对应名义 60 格/秒（20 TPS）。这是上限设置，不是定速指令；实际速度受动力、坡道、轨道、原生加速顺序和 TPS 影响。
- 不需要用世界 gamerule 提高所有原版矿车的速度：Paper 26.1.2 的原生实现采用 Metro 写入的单车 `maxSpeed`。
- `speed_control.cruise_control` 已移除。配置升至 v5 时自动删除此节，并将原文件备份到 `plugins/Metro/backups/migrations/<时间>/config.yml`。`settings.safe_mode` 的卡停恢复保留，包括原有 `min_cruise_speed` 阈值；该阈值不是巡航定速开关。

## 启用世界实验

Metro 不修改世界实验或游戏规则。实验会影响该世界的其他矿车，先在副本验证现有线路。

**新世界**：可以在创建世界时启用 Minecart Improvements；专用服务器也可在首次生成世界前设置 `server.properties` 的 `initial-enabled-packs=vanilla,minecart_improvements`。它不用于修改已经存在的世界。

**已有 26.1.2 世界**：停服并备份完整世界，用 NBT 编辑器修改该世界 `level.dat`，保留其他条目：

1. 在 `Data.enabled_features` 字符串列表中加入 `minecraft:minecart_improvements`。
2. 在 `Data.DataPacks.Enabled` 中加入 `minecart_improvements`，并从 `Data.DataPacks.Disabled` 删除同名条目（如果有）。
3. 保存后重启，用新生成的矿车测试。确认启用的数据包和世界特性均已生效；只调整速度 gamerule 不会切换矿车物理。

回退实验世界应恢复停服前的完整备份。其他 Minecraft 版本的存储和实验支持应按对应版本确认。

## 适配行为

实验世界内不再执行旧物理的上坡强推，斜坡、过弯和行驶加速由原生物理处理。Metro 只在接近目标停车点时降低单车上限；制动可以在进入停靠区之前发生，以免一次高速移动跨过窄站台。到达既有的 0.8 格停靠半径后沿用原有到站流程。制动采用停车点直线距离，绕行线路靠近停车点时也可能提前减速；离开其附近后恢复制动前上限。

原生实验物理可能在应用 `maxSpeed=0` 后仍受到动力铁轨补推，因此停站期间将水平漂移校正回上一位置，并允许原生铁轨高度对齐。Paper 26.1.2 的同世界传送保留乘客。行驶中不使用定时 velocity 写入来维持目标速度；安全模式仅处理卡停。

高速下一次 `VehicleMoveEvent` 仍可能跨越多个轨道块。仅占一个方块的传送门/变速标记，以及复杂线路上的站区事件和路径计量，需要实服线路回归，不能把本次适配理解为逐轨道块事件重放。

## 验证记录（2026-09-13）

运行环境：隔离 Paper 26.1.2 build 74，Java 25.0.4.1，加载构建后的 Metro 与 Vault，无经济提供方。使用强制加载区块和激活的矿车，以无 AI 的猪作为乘客；没有真人客户端。源代码见 [PhysicsProbe.java](../tools/physics-probe/PhysicsProbe.java)。该探针会铺设测试铁轨并关闭服务器，只能在可丢弃的测试服运行。

| 检查 | 结果 |
| :--- | :--- |
| 实验标记识别 | 实验世界 true，普通世界 false |
| cap=3 的载客直线位移 | 实验物理峰值 3.06 格/tick，旧物理 1.5 格/tick |
| cap=8 接近 x=100.5 停车点 | 在 x≈99.814 停住，处于 0.8 格停靠半径内；随后保持原位 |
| 靠墙动力铁轨停站 | 矿车 x=0.5 保持不变，乘客数保持 1 |
| 配置升级 | v4 → v5 成功，巡航节删除，迁移备份生成 |

3.06 而不是精确 3.0 源于原生动力铁轨在限速之后的补推。测试读的是服务端位移，不是客户端速度显示。

探针使用实际 VehicleListener 和 TrainPhysicsController；完整 TrainMovementTask 到站状态流另有单元回归。最终执行 `.\gradlew.bat :Metro:build :Metro:jarGate` 通过，627 项测试全部通过（0 失败、0 跳过）；`git diff --check -- Metro` 通过。`jarGate` 确认 Java 17 字节码和共享库隔离。真人上下车、持续方向输入、转弯/坡道手感、票价/传送门全流程、Geyser 和 Folia 尚需按 [回归清单](regression-baseline.md#experimental-minecart-regression) 验收。

隔离服另发现已有的无 Vault 启动问题：`ClassNotFoundException: net.milkbowl.vault.economy.Economy`。本轮物理改动未修改经济模块，不能据此次测试宣称无 Vault 启动已通过。

## 实现依据

- [Paper 26.1.2 NewMinecartBehavior 补丁](https://github.com/PaperMC/Paper/blob/ver/26.1.2/paper-server/patches/sources/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java.patch)：单车速度上限覆盖。
- [Paper World API](https://jd.papermc.io/paper/26.1.2/org/bukkit/World.html#getFeatureFlags())：世界特性查询。
- [Paper Entity API](https://jd.papermc.io/paper/26.1.2/org/bukkit/entity/Entity.html#teleport(org.bukkit.Location))：同世界传送的乘客处理。
