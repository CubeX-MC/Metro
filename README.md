# Metro 地铁系统

[English](README_en.md) | 简体中文<br>
[Discord](https://discord.com/invite/7tJeSZPZgv) | [QQ频道](https://pd.qq.com/s/1n3hpe4e7?b=9) | [Wiki](https://github.com/CubeX-MC/Metro/wiki)
![bstats](https://bstats.org/signatures/bukkit/Metro.svg)

Metro 是一个 Minecraft 地铁交通系统插件，允许管理员创建自动化的地铁线路网络，为玩家提供便捷的乘车体验。支持 Paper 1.18+ 与 Folia。

![Imgurl](https://i.imgur.com/K335iWj.gif)

## 基本概念

* **线路 (Line)** — 包含按顺序排列的停靠区列表，定义列车行驶路径
* **停靠区 (Stop)** — 由两个对角点定义的三维空间区域（站台）
* **停靠点 (StopPoint)** — 停靠区内的红石铁轨，玩家右键乘车的具体位置
* **换乘** — 在停靠区转乘其他线路

## 快速开始

### 创建第一条线路

1. `/m line create line1 1号线`
2. `/m line setcolor line1 &9`
3. `/m line setterminus line1 东城总站方向`

### 创建停靠区

1. `/m stop create station1 中央车站`
2. 手持金锄头左/右键点击停靠区的两个对角点
3. `/m stop setcorners station1` — 应用选区
4. 站在红石铁轨上执行 `/m stop setpoint` — 设置停靠点
5. `/m line addstop line1 station1`

### 玩家使用

右键点击停靠区内的红石铁轨即可呼叫矿车并自动乘坐。

## 完整命令参考

详细命令列表请查看 [Wiki 命令参考](https://github.com/CubeX-MC/Metro/wiki/Commands-zh)。

## 权限

| 权限 | 默认值 | 描述 |
| :--- | :--- | :--- |
| `metro.admin` | OP | 允许使用所有管理员命令，并继承传送权限 |
| `metro.use` | 所有人 | 允许玩家使用地铁系统（右键乘车等） |
| `metro.gui` | 所有人 | 允许打开 `/m gui` 图形管理界面 |
| `metro.tp` | 否 | 允许传送至停靠区 |
| `metro.line.create` | 否 | 允许创建新线路 |
| `metro.stop.create` | 否 | 允许创建新停靠区 |
| `metro.portal.create` | 否 | 允许创建矿车传送门 |

## 所有权与权限管理

* 新创建的线路/停靠区/传送门自动将创建者设为所有者并加入管理员列表。
* 使用 `trust`/`untrust`/`owner` 子命令管理各元素的管理成员。
* 停靠区可通过 `/m stop link allow/deny` 为特定线路开放接入。
* 旧版本数据中没有权限配置的元素被视为"服务器所有"，仅 OP 或 `metro.admin` 可操作。

## 附属插件

| 名称 | 作者 | 描述 |
| :--- | :--- | :--- |
| [Metro-Altroutes](https://github.com/ALingqing/Metro-Altroutes) | ALingqing | 线路运营状态管理 · 暂停公告 · 替代路线推荐 · 乘车拦截 |

> 如需添加你的附属插件，请提交 Pull Request。

## 配置文件

* `config.yml` — 全局配置（显示样式、音效、矿车设置等）
* `lines.yml` — 线路数据存储
* `stops.yml` — 停靠区数据存储
* `lang/` — 语言文件目录

配置文件详细说明请查看 [Wiki 配置参考](https://github.com/CubeX-MC/Metro/wiki)。

## 构建

```bash
mvn clean package
# 生成 target/metro-<version>.jar
```

当前仓库为单模块 Maven 项目，面向 Java 17 与 1.18+ 服务器。

[![Forkers](https://reporoster.com/forks/CubeX-MC/Metro)](https://github.com/CubeX-MC/Metro/network/members)
[![Stargazers](https://reporoster.com/stars/CubeX-MC/Metro)](https://github.com/CubeX-MC/Metro/stargazers)
