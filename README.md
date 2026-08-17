# Metro 地铁系统

[English](README_en.md) · 简体中文
[Discord](https://discord.com/invite/7tJeSZPZgv) · [QQ频道](https://pd.qq.com/s/1n3hpe4e7?b=9) · [Wiki](https://github.com/CubeX-MC/Metro/wiki)

![](https://img.shields.io/badge/Minecraft-1.18%2B-blue) ![](https://img.shields.io/badge/Folia-supported-brightgreen) ![](https://img.shields.io/badge/Java-17%2B-orange) ![](https://img.shields.io/github/v/release/CubeX-MC/Metro?label=version)

---

Metro 是一个 Minecraft 地铁交通系统插件。管理员创建自动化的地铁线路网络，玩家右键红石铁轨即可呼叫矿车并自动乘坐。

## 定位

把"服务器里的交通"做成一套可运营的基础设施，而不是一堆手搓的命令方块。
管理员用 GUI 铺线路、划停靠区、设换乘与票价；玩家的操作只有一个——**右键红石铁轨**。

差异化在于它是**线路网络**而不是单纯的传送点：停靠区有顺序、有换乘、有按距离或站数的计价，
矿车会自动发车与到站，网页地图上能看到整张网。

**不做什么**（避免误装）：

- 不是传送插件；玩家仍然坐着矿车在世界里真实移动。
- 不接管铁轨建造，线路铺设仍是管理员的工作。
- **Metro 与 Metro/Railway 的另一侧不支持同时安装**（两者同源、包名相同）。

## 功能特性

- **多线路网络** — 创建多条线路，设置停靠区和换乘
- **图形化管理** — 内置 GUI 界面
- **定价系统** — 单一票价、固定票价、按距离/站数计价；中途下车默认按坐到下一站结算（`economy.mid_route_exit_fare`）
- **权限管理** — 元素级信任与所有权
- **安全模式** — 保护矿车免受推动、攻击和破坏
- **矿车传送门** — 跨区域 / 跨世界传送
- **网页地图** — 支持 BlueMap / Dynmap / Squaremap
- **经济系统** — 可选 Vault 集成
- **多语言** — 中英文等多国语言
- **Folia 兼容** — 支持 Folia 多线程服务端

## 基本概念

| 概念 | 说明 |
| :--- | :--- |
| **线路 (Line)** | 按顺序排列的停靠区列表，定义列车行驶路径 |
| **停靠区 (Stop)** | 由两个对角点定义的三维站台区域 |
| **停靠点 (StopPoint)** | 停靠区内的红石铁轨，玩家右键乘车的位置 |
| **换乘 (Transfer)** | 在停靠区转乘其他线路 |

## 运行要求

| 项 | 要求 |
|---|---|
| 服务端 | Paper 1.18+ |
| Java | 17 |
| 必需依赖 | 无 |
| 可选依赖 | Vault（经济）、BlueMap / Dynmap / Squaremap（网页地图）、PlaceholderAPI |
| Folia | 支持 |

## 安装

1. 把 `metro-<version>.jar` 放进服务器 `plugins/`。
2. 启动服务器生成默认配置。
3. 输入 `/m` 或 `/metro` 打开管理 GUI 开始铺线路；`/m help` 查看命令帮助。

> 部署用的是 `build/libs/metro-<version>.jar`；同目录的 `*-plain.jar` **不要**部署。

## 命令

完整命令与权限清单见 [Metro Wiki](https://github.com/CubeX-MC/Metro/wiki)。

## 构建

```powershell
.\gradlew.bat :Metro:build      # 编译 + 测试 + 部署 jar
.\gradlew.bat :Metro:test       # 只跑测试
.\gradlew.bat :Metro:jarGate    # 部署 jar 门禁
```

Windows 必须用 PowerShell 跑 `.\gradlew.bat`（仓库路径含空格）。

## 已知边界

- **Metro 与另一侧（Railway）包名相同，不能同时安装。** 这是有意设计，便于两边同步功能更新。
- 网页地图集成依赖对应地图插件在场；缺席时该能力不宣告为可用。

## 相关文档

- 完整文档：[Metro Wiki](https://github.com/CubeX-MC/Metro/wiki)
- 待办与路线：仓库根 [`PLAN.md`](../PLAN.md)
- 版本记录：[`CHANGELOG.md`](CHANGELOG.md)

---

![](https://bstats.org/signatures/bukkit/Metro.svg)
[![](https://img.shields.io/github/stars/CubeX-MC/Metro?style=social)](https://github.com/CubeX-MC/Metro/stargazers) [![](https://img.shields.io/github/forks/CubeX-MC/Metro?style=social)](https://github.com/CubeX-MC/Metro/network/members)
