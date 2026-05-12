# 🧩 Maid Construction Team (狐闹重工)

**Attention: This mod is currently in early testing stage. Do NOT use it in important saves or production environments.**

**Empowers Touhou Little Maids with automatic construction capabilities!**  
A dedicated addon for [Touhou Little Maid](https://github.com/tartaricacid/TouhouLittleMaid) that supports Create mod schematics, offering a complete labour management system, contract bonuses, and a high‑precision preview system.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)  
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)  
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.219%2B-orange)

---

## 📖 Introduction

**Maid Construction Team** lets your maids truly participate in building! Import Create `.nbt` schematics, precisely position the building using the preview system, and then dispatch maids or pets as labour to automatically place blocks, consume materials, and simulate tool usage. Activate creature contracts to obtain various construction bonuses and make your projects even more efficient.

---

## ✨ Core Features

### 🏗️ Schematic Construction & Preview
- Import Create `.nbt` schematic files (currently only Create schematics are supported).
- **Translucent projection preview**: displays the entire building in real time, supporting movement, rotation, and dual‑step precision adjustment.
- Progressive placement with configurable speed, material consumption, and tool damage simulation.
- Offline persistence: unfinished builds automatically resume from the last checkpoint when the player rejoins.

### 👷 Labour System
- Supports **Touhou Little Maids** and vanilla pets (wolves, parrots, etc.) as labour.
- Roster UI: search, single/batch dispatch, recall, and real‑time status overview.
- Maids have dedicated construction animations (face target, move toward it); pets swing their arms in encouragement.
- Construction automatically pauses when no labour is present.

### 📜 Contract Bonus System
- Capture low‑health creatures with a "Contract" item to sign them.
- Contract Book: store, rename, activate/recall contracts; includes a **Role Guide**.
- **Four roles**:
    - 💪 **Builder**: increases blocks placed per tick (doubled for undead at night).
    - ⚡ **Engineer**: reduces placement interval (extra reduction for skeletons in low light).
    - 🛠️ **Maintenance**: reduces tool durability consumption (doubled for creepers in thunderstorms).
    - ⛏️ **Collector**: extra drops when breaking blocks (doubled for spiders at night).
- One‑click printing of material lists into a book or clipboard (Create compatible).

### 🎮 Create‑Style Controls
- Hold `Alt` to enter preview mode, `Alt + Scroll` to switch tools, `Ctrl + Scroll` to adjust, `Shift` for speed boost.
- Three tools: horizontal move, vertical move, rotate (90° increments).

### 📦 Material Marking & Tools
- Material Checklist: mark containers as material sources.
- Tool simulation: automatically finds suitable tools, consumes durability, and collects drops.

### 🔌 Extensibility
- Easily add new contract effects with the `@AutoContractEffect` annotation.
- Implement `ILaborProvider` to support new labour types.
- Customise the blueprint preview renderer by implementing `IPlacedPreviewRenderer`.

---

## 📥 Installation

1. Make sure your game is **Minecraft 1.21.1** running on **NeoForge 21.1.219** or later.
2. Download and install **[Touhou Little Maid](https://www.curseforge.com/minecraft/mc-mods/touhou-little-maid)** (required).
3. (Optional) install **[Create](https://www.curseforge.com/minecraft/mc-mods/create)** for full schematic and clipboard support.
4. Place the mod `.jar` file into your `mods` folder.
5. Launch the game!

---

## ⚙️ Configuration

All settings can be adjusted in `maid_construction_team.toml`:
- Placement speed, material consumption toggle
- Tool simulation, labour bonus
- Preview frame colour, depth test
- Contract health threshold, role limits

---

## 🧪 Getting Started

1. Craft a **Blueprint Paper**, right‑click to open the GUI and import an `.nbt` schematic.
2. Sneak + right‑click a block to choose the placement location and enter preview mode.
3. Use `Alt/Ctrl + Scroll` to adjust position and rotation, then press `Enter` to confirm.
4. Dispatch maids/pets to the session (via the Roster).
5. Sign creature contracts, store them in the Contract Book, and activate them for the session.
6. Construction runs automatically; use the Schedule to track progress and print material lists.

---

## 🖼️ Screenshots

<!-- Add 2-3 screenshots here: Role Guide interface, translucent projection preview, printed material book -->

---

## 🛠️ Development & Extension

If you are a modpack creator or mod developer, you can easily extend this mod:
- Implement `IContractEffect` and annotate it with `@AutoContractEffect` to auto‑register new roles.
- Implement `ILaborProvider` to add custom labour.
- Implement `IPlacedPreviewRenderer` to replace the preview visual effect.

Detailed API documentation can be found in the source code or the upcoming Wiki.

---

## 💬 Feedback & Support

- Report bugs or suggestions: [Issues](https://github.com/yourrepo/issues)
- Join our community: [Discord](https://discord.gg/yourinvite) (if available)

---

## 👥 Acknowledgements

- [Touhou Little Maid](https://github.com/tartaricacid/TouhouLittleMaid) for the maid entity and API.
- [Create](https://github.com/Creators-of-Create/Create) for the schematic format and inspiration.
- All players who participated in testing and provided feedback.

---

**⚒️ Let your maids become your most reliable builders!**