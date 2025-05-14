# Metro插件类职责说明

## 核心类

### Metro.java
**职责**: 插件主类，负责初始化插件、注册命令和事件、管理配置。
**方法**:
- `onEnable()`: 插件启动时的初始化
- `onDisable()`: 插件关闭时的清理
- `ensureDefaultConfigs()`: 确保所有默认配置文件存在
- `createDefaultConfigFiles()`: 创建默认配置文件
- `saveDefaultConfigFiles()`: 保存默认配置文件
- `getLineManager()`: 获取线路管理器
- `getStopManager()`: 获取站点管理器
- `getLanguageManager()`: 获取语言管理器
- 各种配置获取方法: 如title显示、音效等配置

## 模型类

### Line.java
**职责**: 表示地铁系统中的一条线路。
**方法**:
- `Line(String, String)`: 创建新线路
- `getId()`: 获取线路ID
- `getName()/setName()`: 获取/设置线路名称
- `getColor()/setColor()`: 获取/设置线路颜色
- `getTerminusName()/setTerminusName()`: 获取/设置终点站方向名称
- `getOrderedStopIds()`: 获取有序停靠站点ID列表
- `addStop()`: 向线路添加站点
- `removeStop()`: 从线路移除站点
- `getStopIndex()`: 获取站点在线路中的索引
- `containsStop()`: 检查线路是否包含指定站点
- `getNextStopId()`: 获取指定站点的下一个站点ID
- `getPreviousStopId()`: 获取指定站点的上一个站点ID

### Stop.java
**职责**: 表示地铁系统中的一个停靠站点。
**方法**:
- `Stop(String, String)`: 创建新停靠站点
- `Stop(String, ConfigurationSection)`: 从配置节加载站点
- `saveToConfig()`: 将站点保存到配置节
- `getCustomTitle()/setCustomTitle()/removeCustomTitle()`: 获取/设置/移除站点自定义title配置
- `isInStop()`: 检查指定位置是否在站点区域内
- 各种getter/setter方法: ID、名称、位置、发车方向等
- `getTransferableLines()/addTransferableLine()/removeTransferableLine()`: 管理可换乘线路

## 管理器类

### LineManager.java
**职责**: 管理地铁线路，提供线路的增删改查功能。
**方法**:
- `LineManager(Metro)`: 构造函数，初始化线路管理器
- `loadLines()`: 从配置文件加载所有线路
- `saveLines()`: 保存所有线路到配置文件
- `createLine()`: 创建新线路
- `getLine()/getLines()`: 获取指定线路/所有线路
- `lineExists()`: 检查线路是否存在
- `deleteLine()`: 删除线路
- `updateLine()`: 更新线路信息
- `addStopToLine()/removeStopFromLine()`: 添加/移除站点到/从线路

### StopManager.java
**职责**: 管理地铁站点，提供站点的增删改查功能。
**方法**:
- `StopManager(Metro)`: 构造函数，初始化站点管理器
- `loadStops()`: 从配置文件加载所有站点
- `saveStops()`: 保存所有站点到配置文件
- `createStop()`: 创建新站点
- `getStop()/getStops()`: 获取指定站点/所有站点
- `stopExists()`: 检查站点是否存在
- `deleteStop()`: 删除站点
- `updateStop()`: 更新站点信息
- `getStopAt()`: 获取指定位置的站点

### LanguageManager.java
**职责**: 管理插件的多语言支持。
**方法**:
- `LanguageManager(Metro)`: 构造函数，初始化语言管理器
- `loadLanguage()`: 加载语言文件
- `getMessage()`: 获取本地化消息
- `formatMessage()`: 格式化消息，替换变量

### ScoreboardManager.java
**职责**: 管理玩家界面显示的计分板信息。
**方法**:
- `initialize(Metro)`: 初始化计分板管理器
- `showStatusBoard()`: 显示状态计分板
- `hideStatusBoard()`: 隐藏状态计分板
- `updateStatusBoard()`: 更新状态计分板信息

## 命令类

### MetroAdminCommand.java
**职责**: 处理地铁系统的管理命令。
**方法**:
- `onCommand()`: 命令执行入口
- 各种子命令处理方法: 如创建线路、站点、设置属性等

### MetroAdminTabCompleter.java
**职责**: 为管理命令提供Tab自动完成功能。
**方法**:
- `onTabComplete()`: Tab补全入口
- 各种子命令补全方法

## 事件监听器类

### PlayerInteractListener.java
**职责**: 处理玩家交互事件。
**方法**:
- `onPlayerInteract()`: 处理玩家交互事件
- 处理玩家使用工具选择区域、设置站点等功能

### PlayerMoveListener.java
**职责**: 处理玩家移动事件。
**方法**:
- `onPlayerMove()`: 处理玩家移动事件
- 检测玩家进入站点区域，显示提示等

### VehicleListener.java
**职责**: 处理车辆相关事件。
**方法**:
- `onVehicleCreate()`: 处理车辆创建事件
- `onVehicleDestroy()`: 处理车辆销毁事件
- `onVehicleEnter()/onVehicleExit()`: 处理玩家进入/离开车辆事件

## 列车管理类

### TrainMovementTask.java
**职责**: 控制列车的移动和行为。
**方法**:
- `TrainMovementTask(Metro, Minecart, Player)`: 创建列车移动任务
- `run()`: 任务执行方法
- 列车路径规划、速度控制、停站等逻辑

## 工具类

### LocationUtil.java
**职责**: 提供位置相关的工具方法。
**方法**:
- 位置比较、检测、格式化等方法

### SchedulerUtil.java
**职责**: 提供任务调度相关的工具方法。
**方法**:
- 异步任务、延迟任务等方法

### SoundUtil.java
**职责**: 提供声音效果相关的工具方法。
**方法**:
- 播放音乐、音效等方法

### TextUtil.java
**职责**: 提供文本处理相关的工具方法。
**方法**:
- 文本格式化、颜色处理等方法
