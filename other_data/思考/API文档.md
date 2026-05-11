# 🧩 Maid Construction Team 模组 API 文档

**版本：** 1.0.0  
**Minecraft 版本：** 1.21.1 (NeoForge)

本文档提供模组的完整文件结构、核心 API 列表及功能说明，帮助开发者快速了解系统架构与扩展点。

---

## 📁 模组文件结构

```
com.snowcity.maid_construction_team
│
├── MaidConstructionTeam.java          ← 模组主类
│
├── api                                ← 对外扩展接口层
│   ├── contract                       ← 契约加成系统
│   │   ├── AutoContractEffect.java    (自动注册分工注解)
│   │   ├── ContractBonusManager.java  (契约激活/召回/清除状态)
│   │   ├── ContractRoleRegistry.java  (分工效果与生物映射注册)
│   │   ├── ContractScanner.java       (自动扫描@AutoContractEffect)
│   │   ├── DefaultBonusCollector.java (默认加成计算器)
│   │   ├── IBonusCollector.java       (加成计算器接口)
│   │   ├── IContractEffect.java       (分工效果接口)
│   │   ├── IContractValidator.java    (契约验证器接口)
│   │   ├── impl                       (内置分工实现)
│   │   │   ├── BuilderEffect.java     (建筑工效果)
│   │   │   ├── EngineerEffect.java    (工程师效果)
│   │   │   └── MaintenanceEffect.java (养护工效果)
│   │   └── validator                  (验证器实现)
│   │       └── MaxSameRoleValidator.java
│   │
│   └── labor                          ← 劳动力提供者接口
│       ├── ILaborProvider.java        (劳动力提供者接口)
│       ├── LaborInfo.java             (劳动力统一数据记录)
│       ├── LaborStatus.java           (劳动力状态枚举)
│       ├── LaborProviderRegistry.java (劳动力提供者注册表)
│       ├── PetTracker.java            (宠物列表维护)
│       ├── PetList.java               (宠物UUID数据组件)
│       └── provider
│           └── PetLaborProvider.java  (宠物提供者实现)
│
├── client                             ← 客户端专用层
│   ├── cache
│   │   └── ClientSessionCache.java    (会话名称缓存)
│   ├── event
│   │   └── ClientRenderEvents.java    (容器高亮渲染)
│   ├── key
│   │   └── ModKeyMappings.java        (自定义按键绑定)
│   ├── preview                        ← 蓝图预览子系统
│   │   ├── IPlacedPreviewRenderer.java   (预览渲染器接口)
│   │   ├── LineBoxPreviewRenderer.java  (线框预览)
│   │   ├── SchematicProjectionRenderer.java (半透明投影预览，VBO优化)
│   │   ├── PlacementPreviewRenderer.java   (预览事件监听)
│   │   ├── PlacementPreviewTool.java       (预览微调工具枚举)
│   │   ├── PreviewInputHandler.java        (预览输入处理)
│   │   ├── PreviewManager.java             (预览生命周期管理)
│   │   └── PreviewPlacementContext.java    (预览上下文)
│   └── screen                         ← GUI 界面
│       ├── BlueprintSelectionScreen.java   (蓝图选择)
│       ├── ContractBookScreen.java         (契约之书管理)
│       ├── ContractGuideScreen.java        (分工指南)
│       ├── MaterialChecklistScreen.java    (物资登记表)
│       ├── RosterScreen.java               (花名册)
│       ├── ScheduleScreen.java             (规划表)
│       ├── SessionDetailScreen.java        (会话详情)
│       └── SessionSelectionScreen.java     (会话选择)
│
├── component                          ← 自定义数据组件
│   ├── BlueprintData.java             (蓝图数据)
│   ├── ContractBookData.java          (契约之书内部条目)
│   ├── ContractData.java              (契约通用数据)
│   ├── MaterialChecklistData.java     (物资登记表条目)
│   └── ServantContractData.java       (单个契约数据)
│
├── config
│   └── MaidConstructionTeamConfig.java (全局配置)
│
├── core                               ← 核心业务逻辑层
│   ├── event
│   │   ├── PlacementEventHandler.java (放置 Tick 驱动)
│   │   └── ServantDeathHandler.java   (契约生物死亡处理)
│   ├── init
│   │   ├── ModAttachments.java        (AttachmentType 注册)
│   │   ├── ModDataComponents.java     (数据组件注册)
│   │   ├── ModEntities.java           (实体类型注册)
│   │   └── ModItems.java              (物品注册)
│   ├── manager
│   │   ├── PlacementSessionManager.java (玩家专属会话管理器)
│   │   ├── PlayerSessionManager.java    (全局会话管理器)
│   │   └── SessionStateMachine.java     (会话状态机)
│   ├── schematic                      ← 蓝图放置核心
│   │   ├── BlockInfo.java             (方块信息)
│   │   ├── EntityInfo.java            (实体信息)
│   │   ├── SchematicData.java         (蓝图数据)
│   │   ├── ISchematicReader.java      (蓝图解析器接口)
│   │   ├── IMaterialProvider.java     (材料供应接口)
│   │   ├── IToolLackNotifier.java     (工具缺失通知)
│   │   ├── PlacementContext.java      (放置上下文)
│   │   ├── PlacementProgress.java     (放置进度)
│   │   ├── FeedbackMessage.java       (反馈消息)
│   │   ├── ProgressivePlacer.java     (渐进式放置引擎)
│   │   ├── MaterialShortageStrategy.java (材料不足策略)
│   │   ├── IBlueprintPersistence.java (蓝图持久化接口)
│   │   ├── PlacementSession.java      (放置会话)
│   │   └── persistence
│   │       └── SessionPersistenceHelper.java (离线持久化工具)
│   └── compat
│       └── CreateSchematicReader.java (机械动力蓝图兼容)
│
├── entity                             ← 自定义实体 (已废弃)
│   └── ServantEntity.java
│
├── item                               ← 自定义物品
│   └── custom
│       ├── BlueprintPaperItem.java    (蓝图纸)
│       ├── MaterialChecklistItem.java (物资登记表)
│       ├── ContractItem.java          (契约物品)
│       ├── ContractBookItem.java      (契约之书)
│       ├── RosterItem.java            (花名册)
│       └── ScheduleItem.java          (规划表)
│
└── network                            ← 网络通信层
    ├── handler                        ← 处理器
    │   ├── blueprint
    │   │   ├── ImportBlueprintServerHandler.java
    │   │   └── StartPlacementServerHandler.java
    │   ├── labor
    │   │   ├── DispatchLaborServerHandler.java
    │   │   ├── RecallLaborServerHandler.java
    │   │   ├── RequestLaborListServerHandler.java
    │   │   └── LaborListClientHandler.java
    │   └── session
    │       ├── RequestSessionsServerHandler.java
    │       ├── SessionsResponseClientHandler.java
    │       ├── ControlSessionServerHandler.java
    │       └── SessionStateChangedClientHandler.java
    └── payload                        ← 数据包定义
        ├── blueprint
        │   ├── ImportBlueprintPayload.java
        │   └── StartPlacementPayload.java
        ├── labor
        │   ├── DispatchLaborPayload.java
        │   ├── RecallLaborPayload.java
        │   ├── RequestLaborListPayload.java
        │   └── LaborListPayload.java
        └── session
            ├── RequestSessionsPayload.java
            ├── SessionsResponsePayload.java
            ├── ControlSessionPayload.java
            ├── SessionStateChangedPayload.java
            ├── ModifyChecklistPayload.java
            ├── ModifyContractBookPayload.java
            └── SyncHandItemPayload.java
```

---

## 📋 扩展接口 (Addon 开发核心)

| 接口 / 类 | 作用 | 备注 |
|-----------|------|------|
| `ILaborProvider` | 自定义劳动力提供者，如宠物、佣兵。需实现 `scanLabor`, `dispatch`, `recall` 方法。 | 需在 `LaborProviderRegistry` 注册 |
| `IContractEffect` | 定义一种分工效果（建筑工/工程师等）。需实现加成数值计算和描述。 | 使用 `@AutoContractEffect` 可自动注册 |
| `IBonusCollector` | 加成计算器接口。内置 `DefaultBonusCollector` 遍历激活契约并求和。 | 可替换实现自定义计算 |
| `IContractValidator` | 验证契约激活条件，如上限控制。内置 `MaxSameRoleValidator`。 | 实现后在激活流程调用 |
| `IPlacedPreviewRenderer` | 蓝图预览渲染器接口。自定义视觉效果（线框/半透明）。 | 通过 `PreviewManager.setActiveRenderer()` 切换 |
| `IMaterialProvider` | 建造材料供应接口，从容器/玩家背包消耗材料。 | 内置容器标记提供者 |
| `IBlueprintPersistence` | 蓝图持久化接口，自定义保存/加载逻辑。 | 内置磁盘文件实现 |

---

## 🏛️ 核心系统类

| 类名 | 功能 | 包位置 |
|------|------|--------|
| `MaidConstructionTeam` | 模组主入口，注册物品、组件、网络包。 | 根包 |
| `ProgressivePlacer` | 渐进式放置引擎，集成材料消耗、工具模拟、劳动力动画、契约加成。每刻执行放置。 | `core.schematic` |
| `PlacementSession` | 一次建造会话，包含进度、参与者、激活契约列表、材料策略等。 | `core.schematic` |
| `PlacementSessionManager` | 管理单个玩家的所有会话：启动、暂停、恢复、完成、取消。 | `core.manager` |
| `PlayerSessionManager` | 全局玩家会话管理器，处理玩家上下线的持久化恢复。 | `core.manager` |
| `SessionStateMachine` | 会话状态机，定义合法状态转换（RUNNING, PAUSED, WAITING_MATERIALS 等）。 | `core.manager` |
| `PreviewManager` | 预览生命周期管理：进入/更新、确认、取消、切换渲染器。 | `client.preview` |
| `PreviewPlacementContext` | 保存预览中的锚点、旋转、浮点偏移，供渲染和定位。 | `client.preview` |
| `ContractBonusManager` | 契约加成激活、取消激活、状态清除工具类。 | `api.contract` |
| `ContractRoleRegistry` | 生物分工映射与效果注册表。 | `api.contract` |
| `ContractScanner` | 自动扫描 `@AutoContractEffect` 注解并注册分工。 | `api.contract` |
| `DefaultBonusCollector` | 默认加成计算器，从会话收集所有激活契约的加成数值。 | `api.contract` |
| `LaborProviderRegistry` | 管理已注册的劳动力提供者。 | `api.labor` |

---

## 🌐 网络通信包

| 数据包 (Payload) | 方向 | 功能描述 |
|------------------|------|----------|
| `RequestSessionsPayload` | C→S | 请求当前玩家的所有会话列表或单个会话详情（可选UUID） |
| `SessionsResponsePayload` | S→C | 返回会话摘要列表或详情 |
| `ControlSessionPayload` | C→S | 暂停/继续/取消/设置材料不足策略 |
| `SessionStateChangedPayload` | S→C | 通知客户端会话状态已变更 |
| `DispatchLaborPayload` | C→S | 派遣劳动力或激活契约加成（通过 `providerId` 区分） |
| `RecallLaborPayload` | C→S | 召回劳动力或取消契约加成 |
| `RequestLaborListPayload` | C→S | 请求劳动力列表 |
| `LaborListPayload` | S→C | 返回劳动力列表（仅宠物等，不含契约） |
| `ModifyChecklistPayload` | C→S | 物资登记表操作：移除/重命名容器 |
| `ModifyContractBookPayload` | C→S | 契约之书操作：全部存入、全部取出、单个取出、重命名 |
| `SyncHandItemPayload` | S→C | 同步手持物品数据到客户端，触发GUI刷新 |

---

## 🧰 自定义物品

| 物品类 | 功能 |
|--------|------|
| `BlueprintPaperItem` | 蓝图纸：导入 `.nbt` 蓝图，进入预览模式 |
| `ContractItem` | 契约：右键低血量生物签订，获得该生物的契约 |
| `ContractBookItem` | 契约之书：存储条约，管理激活/召回，存入/取出契约 |
| `RosterItem` | 花名册：打开劳动力（宠物）管理界面 |
| `ScheduleItem` | 规划表：查看当前所有活跃会话，控制暂停/继续/取消 |
| `MaterialChecklistItem` | 物资登记表：潜行右键容器标记，右键打开管理界面 |

---

## 📦 数据组件

| 组件类 | 存储内容 | 用途 |
|--------|----------|------|
| `BlueprintData` | 蓝图文件名、原始NBT数据 | 蓝图纸物品，供预览和放置使用 |
| `ServantContractData` | 契约ID、生物类型、自定义名称、模型变体、派遣会话ID | 单个契约物品 |
| `ContractBookData` | 多个契约条目的列表 | 契约之书物品 |
| `MaterialChecklistData` | 容器坐标（维度、x/y/z）、自定义名称列表 | 物资登记表物品 |

---

## ⚙️ 配置 (`MaidConstructionTeamConfig`)

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `updateBlocks` | boolean | false | 放置方块时是否引发方块更新 |
| `placeAir` | boolean | true | 是否放置蓝图中的空气方块 |
| `consumeMaterials` | boolean | true | 是否启用材料消耗 |
| `blocksPerTick` | int | 20 | 每刻最大放置方块数 |
| `placementInterval` | int | 1 | 放置间隔刻数 |
| `clearOriginalBlocks` | boolean | true | 是否清除蓝图范围内的原有方块 |
| `enableToolSimulation` | boolean | false | 是否模拟工具破坏与耐久消耗 |
| `laborerBonus` | int | 1 | 每个劳动力提供的额外方块数 |
| `materialShortageStrategy` | enum | PAUSE | 材料不足默认策略（PAUSE/SKIP） |
| `contractHealthThreshold` | double | 0.5 | 签订契约所需生命值比例 |

---

## 🖥️ 蓝图预览相关

| 类/枚举 | 作用 |
|----------|------|
| `PreviewInputHandler` | 处理Alt+滚轮切换工具、Ctrl+滚轮执行操作、Shift加速等交互 |
| `PlacementPreviewTool` | 预览微调工具枚举： `MOVE_XZ`（水平移动）、`MOVE_Y`（垂直移动）、`ROTATE`（旋转） |
| `PreviewPlacementContext` | 保存当前预览的锚点、旋转、偏移量，提供 `moveX/Y/Z` 和旋转方法 |
| `SchematicProjectionRenderer` | 默认高性能投影渲染器，使用VBO批量渲染半透明方块与粉色边框 |
| `LineBoxPreviewRenderer` | 备用线框渲染器 |
| `PreviewManager` | 管理预览数据与渲染器切换，提供 `enterOrUpdate`, `confirm`, `cancel` 等 |

---

本 API 文档涵盖了模组所有公开接口和主要系统类，方便开发者进行扩展或整合。如需特定类的详细方法说明或使用示例，欢迎进一步询问。