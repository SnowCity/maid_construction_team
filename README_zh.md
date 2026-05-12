# 🧩 Maid Construction Team

## 注意，目前的代码处于早期测试阶段，请勿用在重要领域内使用

**为车万女仆赋予自动建筑能力！**  
一个专为 [Touhou Little Maid](https://github.com/tartaricacid/TouhouLittleMaid) 设计的附属模组，支持机械动力蓝图，提供完整的劳动力管理、契约加成和高精度预览系统。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)  
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)  
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.219%2B-orange)

---

## 📖 简介

**狐闹重工** 让您的女仆们真正参与建造！导入机械动力的 `.nbt` 蓝图后，您可以通过预览系统精确放置建筑位置，然后派遣女仆或宠物作为劳动力，自动完成方块放置、材料消耗和工具模拟。激活生物契约来获取各种建造加成，使工程更加高效。

---

## ✨ 核心特性

### 🏗️ 蓝图建造与预览
- 导入机械动力（Create）的 `.nbt` 蓝图文件。(目前只支持Create的蓝图文件)
- **半透明投影预览**：实时显示建筑全貌，支持移动、旋转和双步长精确调整。
- 渐进式放置，可配置速度、材料消耗和工具损坏模拟。
- 离线持久化：未完成的建筑在玩家重连后自动断点续建。

### 👷 劳动力系统
- 支持**车万女仆**和原版宠物（狼、鹦鹉等）作为劳动力。
- 花名册 UI：搜索、派遣、批量派遣、召回，实时查看工作状态。
- 女仆拥有专用建造动画（面向目标、移动），宠物则挥臂助威。
- 无劳动力时自动暂停建造。

### 📜 契约加成系统
- 用“契约”物品捕捉低血量生物，签订契约。
- 契约之书：存储、命名、激活/召回契约，提供**分工指南**。
- **四种分工**：
    - 💪 **建筑工**：单次放置更多方块（亡灵夜间翻倍）。
    - ⚡ **工程师**：缩短放置间隔（骷髅低光额外加速）。
    - 🛠️ **养护工**：节省工具耐久（苦力怕雷雨翻倍）。
    - ⛏️ **采集工**：破坏方块时额外掉落（蜘蛛夜间翻倍）。
- 一键打印材料清单到书本或剪贴板（支持机械动力）。

### 🎮 机械动力式控制
- 按住 `Alt` 进入预览模式，`Alt + 滚轮` 切换工具，`Ctrl + 滚轮` 执行微调，`Shift` 加速。
- 三种工具：水平移动、垂直移动、旋转（每次90°）。

### 📦 物资登记与工具
- 物资登记表：标记容器作为材料来源。
- 工具模拟：自动寻找合适工具，消耗耐久，回收掉落物。

### 🔌 扩展性
- 通过注解 `@AutoContractEffect` 可轻松添加新分工效果。
- 实现 `ILaborProvider` 接口即可支持新的劳动力类型。
- 自定义蓝图预览渲染器实现 `IPlacedPreviewRenderer`。

---

## 📥 安装

1. 确保已游戏版本为 **Minecraft 1.21.1** 和 **NeoForge 21.1.219** 或更高版本。
2. 下载并安装 **[Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid)**（必须）。
3. （可选）安装 **[Create](https://www.curseforge.com/minecraft/mc-mods/create)** 以获得完整的蓝图及剪贴板支持。
4. 将本模组的 `.jar` 文件放入 `mods` 文件夹。
5. 启动游戏！

---

## ⚙️ 配置

所有设置可在 `maid_construction_team.toml` 中调整：
- 放置速度、材料消耗开关
- 工具模拟、劳动力加成
- 预览边框颜色、深度测试
- 契约生命值阈值、分工上限

---

## 🧪 使用入门

1. 制作 **蓝图纸**，右键打开 GUI 导入 `.nbt` 蓝图。
2. 潜行右键方块选择放置位置，进入预览模式。
3. 使用 `Alt/Ctrl + 滚轮` 调整位置和旋转，按 `Enter` 确认。
4. 派遣女仆/宠物到该会话（用花名册）。
5. 签订生物契约，存入契约之书并激活到当前会话。
6. 建造自动进行，可使用规划表查看进度，打印材料清单。

---

## 🖼️ 截图

<!-- 建议添加 2-3 张截图：分工指南界面、半透明投影预览、印刷的材料书 -->

---

## 🛠️ 开发与扩展

如果您是一名整合包作者或模组开发者，可以轻松扩展本模组的功能：
- 实现 `IContractEffect` 并标注 `@AutoContractEffect` 自动注册新分工。
- 实现 `ILaborProvider` 添加自定义劳动力。
- 实现 `IPlacedPreviewRenderer` 替换预览视觉效果。

详细 API 文档请参考源码或即将发布的 Wiki。

---

## 💬 反馈与支持

- 提交BUG或建议：[Issues](https://github.com/yourrepo/issues)
- 加入社区：[Discord 链接](https://discord.gg/yourinvite)（如果有）

---

## 👥 致谢

- [Touhou Little Maid](https://github.com/tartaricacid/TouhouLittleMaid) 提供女仆实体和 API。
- [Create](https://github.com/Creators-of-Create/Create) 提供蓝图格式和灵感。
- 所有参与测试和反馈的玩家。

---

**⚒️ 让女仆成为您最可靠的建筑工！**