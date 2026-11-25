---
name: architecture
description: How the major pieces of this project connect and flow. Load when working on system design, integrations, or understanding how components interact.
triggers:
  - "architecture"
  - "system design"
  - "how does X connect to Y"
  - "integration"
  - "flow"
edges:
  - target: context/stack.md
    condition: when specific technology details are needed
  - target: context/decisions.md
    condition: when understanding why the architecture is structured this way
  - target: context/tools.md
    condition: when changing texture generation, GameTest templates, or the AsmDump helper
grounds_to: []
last_updated: 2026-11-24
mex:
  id: mx_01M6FTBTA32ESJGHB433FWZZZ3
  type: architecture
  status: promoted
  revision: 4
  title: architecture
  relations:
    - type: related_to
      target: mx_01M6FTBTC471WET2A2MK599QB1
      note: when specific technology details are needed
    - type: related_to
      target: mx_01M6FTV797VEN7Z57WW58775G4
      note: when understanding why the architecture is structured this way
    - type: related_to
      target: mx_01M6FTMGAC6EFK2JXPVN38G81K
      note: when changing texture generation, GameTest templates, or the AsmDump helper
---

# Architecture

## System Overview

一个 Minecraft 1.20.1 Forge 模组，用服务端权威的方式模拟《潜渊症》式深海潜艇：

1. Forge 调用 `BarotraumaMod` 构造器，注册方块、物品、方块实体、菜单、实体和维度生成器。
2. 声纳台方块实体（`SonarBlockEntity`，继承 `ShipCoreBlockEntity`）扫描周围船体方块，`ShipGeometry` 把船体分成密闭舱室和连海空腔。
3. `ShipSimulation` 用 `ShipFluidField` 为实际舱内格维护水量；同一水体归平，舱与舱按开口两侧的水头交换。船壳本身是逐方块耐久（`HullDamage`）：左键/爆炸/撞击先扣剩余比例；伤害 ≥10% 开始小漏、≥70% 大漏，0% 时方块切成 `HullBlock.BREACHED`（保留数据、无碰撞、不可见），几何扫描把它当洞，走连海空腔进水；焊接回 >0 恢复实体。
4. 如果装了 Valkyrien Skies 2，`VsPilot` 把整船方块收进 VS2 物理船，逐 tick 把推进、压载、进水比例同步给 `VsThruster`，并用 `ShipWaterStatePacket` 同步水盒、破口和实际来水路径。方块船用同一个包。
5. 深海维度是空气海域。`DeepSeaChunkGenerator` 生成岩层和空腔，`OceanEnvironment` 用实际船舱、哨站和对接通道判浸没。`UnderwaterEffects` 按寒冷深穴配色控制外海散射和海雪，人物的外海视线按 7.4 到 18.7 格衰减。外海与舱内水共享 `ShipWaterMaterial` 的场景深度与散射色。
6. `ShipWaterRenderer` 逐帧插值 10 Hz 水面包。`ShipWaterMesh` 按格列合并湿区，分别更新各甲板的涟漪。`ShipWaterFlowMesh` 沿舱壁或空旷落差画连续水面，水珠和气泡跟随客户端世界刻。`ShipWaterMaterial` 只在有光学厚度的水界面折射。水中直视地板和设备按水程着色。`UnderwaterMovement` 读取服务端的可游水深和原版疾跑绑定。`HullDamageRenderer` 显示受损方块的耐久条、裂痕和渗漏粒子。

## Key Components

- **`BarotraumaMod`** — 唯一 `@Mod` 入口，构造器里挂 8 个 `DeferredRegister` 和 `ModNetwork.register()`；注册必须全部在构造阶段完成。
- **`registry/`** — 方块、物品、方块实体、菜单、实体、维度生成器和船体方块的注册表；`ModShipBlocks.isShipBlock()` 是判断“这格属于船”的唯一入口。
- **`ship/ShipState`** — 一艘船的运行时状态：几何、机器统计、`ShipSimulation`、`ShipWater`、逐方块船壳耐久（`HullDamage` + `hullHealthMultiplier`）、油门、功率、自动驾驶和物理速度。
- **`ship/HullDamage`** — 每格 0..1 剩余比例；重扫/平移/拆装时跟随船状态迁移。基础血量在 `ModShipBlocks.maxHealth`：装甲板 200、舷窗 100、测试船壳 200；声纳不可破坏。
- **`ship/vs/VsPilot`** — VS2 物理船的唯一静态管理器：装配/拆回、推进器、船坞偏移、破口对账、水量同步、声纳代理方块移动。`VsState` 提供不引用 VS2 类型的桥接，`VsSupport` 只做 `ModList.isLoaded("valkyrienskies")` 判断。
- **`ship/water/ShipVolumeGuard` + `mixin/LevelFluidGuardMixin`** — 拦截船只体积内的真实流体写入；空气海域下仍保留用于旧存档兼容。
- **`mixin/ShipBreakMixin`** — 把玩家左键破坏映射到船坞坐标，让物理船也能破口。
- **`world/OceanEnvironment`** — 干湿判定入口：维度高度、真实碰撞体、船舱水位与哨站干区。服务端查询模拟水位，客户端通过 `DryZone` 查询同步的水盒。
- **`network/`** — 6 个包：`ShipActionPacket`（客户端→服务端）、`VesselPalettePacket`（船体渲染）、`SonarPingPacket` / `SonarDataPacket`（声纳）、`ShipWaterStatePacket`（水面包）、`ShipHullDamagePacket`（受损方块 + 剩余比例）。
- **`event/`** — `DeepSeaEvents`（登录、命令、自检）、`UnderwaterSurvival`（空气海域的氧气/溺水）。
- **`mission/` + `campaign/`** — 站务员 NPC（`NpcEntity`/`MissionNpc`）右键对话：`NpcTalkPacket` 请求活儿，服务端按站点发最多 3 条报价（任务模板 → 相邻目的地），`NpcDialoguePacket` 回任务列表；`MissionSavedData` 按站点保存报价与进行中任务，未接的留站、离站后重新靠泊刷新、完成复位可再发；`CampaignMapScreen` 只列当前站去所选目的地的报价并勾选；`MissionCargo` 把真箱子装进 `crate_shelf`（装配后的货架按船坞偏移读取），无货架时在玩家进艇后于脚边生成散箱，靠泊目的站按 98% 结算并清货；`MissionStatePacket` 下发当前站报价 + 进行中任务目的地，协议 8。

## External Dependencies

- **Minecraft 1.20.1 + Forge 47.4.0** — 所有方块、实体、网络和事件都走 Forge API；不能用 Fabric 或更高版本的类。
- **Valkyrien Skies 2 2.4.11** — 物理船刚体、船坞坐标和推进器；`compileOnly` + `runtimeOnly`，正式包不内嵌。
- **Kotlin for Forge 4.12.0** — VS2 的类依赖 kotlin-stdlib，但它的 `mods.toml` 没有声明，必须作为运行时依赖。
- **Gradle 8.1.1 / ForgeGradle 6.0.54** — 构建链；只能跑在 JDK 17 上。
- **本地 Maven 镜像与代理** — 见 `context/setup.md`；不是运行时依赖，但缺了会构建失败。

## What Does NOT Exist Here

- **没有真实海水方块**：深海维度是空气，水下感由客户端雾/滤镜和 `UnderwaterSurvival` 提供；不要再往海里放 `Blocks.WATER`。
- **没有物理船持久化/世界加载认领**：服务器重启后已装配的 VS2 船不会被 `VsPilot` 找回，`ShipState`、guard cells、声纳代理和推进器都要重新建立。
- **没有多船支持**：`VsPilot` 是 `static` 单例，同一时刻只管理一艘物理船。
- **大船水盒兜底**：单船超过 2048 个盒子时，每个水体用一个包围盒发送。落水路径仍按实际舱内格计算。
- **没有物理船碰撞→船壳伤害**：VS2 2.4.11 没有暴露方便的 CollisionEvent 订阅入口；目前只有玩家左键、爆炸、方块船撞击会扣耐久。物理船撞地形仍是“刚体被挡住”，不掉血。
- **没有 T1/T2/T3 升级系统**：官方基础外壳都是 200；tier 只限制 `increasewallhealth` 升级等级（每级 +20%，T1 2 / T2 4 / T3 6）。`ShipState.hullHealthMultiplier` 已预留，但没有商店/升级 UI。
- **没有船壳耐久持久化**：`HullDamage` 只在运行时；世界存档或物理船拆回再装会重置。
- **没有服务端渲染**：所有画面都在 `client/`，服务端只发数据包。
- **手电筒照明**：服务端消耗电池并用玩家实体数据同步亮灯位置和低电标志。客户端在水体和手部渲染前处理光锥，复用世界画面的颜色、深度和实际投影矩阵。固定舱灯仍由 Minecraft 方块亮度和可选的 Colorful Lighting 传播颜色。
