# Metro 地铁系统

[English](README_en.md) | 简体中文

![bstats](https://bstats.org/signatures/bukkit/Metro.svg)

## 插件概述

Metro是一个受牛腩小镇启发的地铁交通系统插件，允许管理员创建自动化的地铁线路网络，为玩家提供便捷的乘车体验。

![Imgurl](https://i.imgur.com/K335iWj.gif)

## 基本概念

* **线路(Line)**: 地铁线路，包含按顺序排列的停靠区列表，定义列车行驶路径
* **停靠区(Stop)**: 地铁站台，由两个对角点定义的三维空间区域
* **停靠点(StopPoint)**: 停靠区内的红石铁轨，玩家右键乘车的具体位置
* **换乘**: 在停靠区可以转乘其他线路

## 管理员命令 (主指令: `/m`)

### 线路管理

| 命令                                    | 描述                     |
| :-------------------------------------- | :----------------------- |
| `/m line create <line_id> <显示名称>`    | 创建新线路               |
| `/m line delete <line_id>`               | 删除指定线路             |
| `/m line list`                           | 列出所有线路             |
| `/m line rename <line_id> <新名称>`      | 重命名线路               |
| `/m line setcolor <line_id> <颜色代码>`  | 设置线路颜色             |
| `/m line setterminus <line_id> <名称>`   | 设置终点方向描述         |
| `/m line setmaxspeed <line_id> <速度>`   | 设置线路最大运行速度     |
| `/m line addstop <line_id> <stop_id> [位置索引]` | 将停靠区添加到线路（可指定位置） |
| `/m line delstop <line_id> <stop_id>`    | 从线路中移除停靠区       |
| `/m line stops <line_id>`                | 查看线路的所有停靠区     |

### 停靠区管理

| 命令                                                     | 描述                          |
| :------------------------------------------------------- | :---------------------------- |
| `/m stop create <stop_id> <显示名称>`                   | 选区后创建新停靠区                  |
| `/m stop delete <stop_id>`                              | 删除停靠区及其所有配置        |
| `/m stop list`                                          | 列出所有停靠区                |
| `/m stop rename <stop_id> <新名称>`                     | 重命名停靠区                  |
| `/m stop info <stop_id>`                                | 查看停靠区详细信息            |
| `/m stop setcorners <stop_id>`                          | 设置空间对角点                |
| `/m stop setpoint [朝向角度]`                            | 设置精确停靠点                |
| `/m stop addtransfer <stop_id> <换乘线路ID>`             | 添加可换乘线路                |
| `/m stop deltransfer <stop_id> <换乘线路ID>`             | 移除可换乘线路                |
| `/m stop listtransfers <stop_id>`                       | 查看可换乘线路                |
| `/m stop settitle <stop_id> <类型> <键> <文本内容>`      | 设置自定义 Title 显示        |
| `/m stop deltitle <stop_id> <类型> [键]`                 | 删除自定义 Title 设置         |
| `/m stop listtitles <stop_id>`                          | 查看自定义 Title 配置         |
| `/m stop tp <stop_id>`                                  | 传送到指定停靠区              |

### 系统管理

| 命令               | 描述                         |
| :----------------- | :--------------------------- |
| `/m reload`        | 重新加载配置和数据文件       |

## 权限

| 权限             | 描述                               |
| :--------------- | :--------------------------------- |
| `metro.admin`    | 允许使用所有管理员命令             |
| `metro.use`      | 允许玩家使用地铁系统（右键乘车等） |

## 快速开始

### 创建第一条地铁线路

1. **创建线路**: `/m line create line1 1号线`
2. **设置线路颜色**: `/m line setcolor line1 &9` （蓝色）
3. **设置终点方向**: `/m line setterminus line1 东城总站方向`

### 创建停靠区

1. **创建停靠区**: `/m stop create station1 中央车站`
2. **设置停靠区范围**:
   - 站在停靠区一角: `/m stop setcorner1 station1`
   - 站在停靠区另一角: `/m stop setcorner2 station1`
3. **设置停靠点**: 站在红石铁轨上执行 `/m stop setpoint`
4. **添加到线路**: `/m line addstop line1 station1`

### 玩家使用

玩家右键点击停靠区内的红石铁轨即可呼叫矿车并自动乘坐，系统会自动处理行驶和到站。

## 配置文件

* `config.yml` - 全局配置，包括显示样式、音效、矿车设置等
* `lines.yml` - 线路数据存储
* `stops.yml` - 停靠区数据存储
* `zh_CN.yml` - 中文语言文件
