---
name: router
description: Session bootstrap and navigation hub. Read at the start of every session before any task. Contains project state, routing table, and behavioural contract.
edges:
  - target: context/architecture.md
    condition: when working on system design, integrations, or understanding how components connect
  - target: context/stack.md
    condition: when working with specific technologies, libraries, or making tech decisions
  - target: context/conventions.md
    condition: when writing new code, reviewing code, or unsure about project patterns
  - target: context/decisions.md
    condition: when making architectural choices or understanding why something is built a certain way
  - target: context/setup.md
    condition: when setting up the dev environment or running the project for the first time
  - target: patterns/INDEX.md
    condition: when starting a task — check the pattern index for a matching pattern file
  - target: patterns/add-ship-block.md
    condition: when adding a new submarine hull block
  - target: patterns/blockbench-modeling.md
    condition: when building a `java_block`-format model in Blockbench, or debugging an existing Java block model (purple-black textures, UV out of 0–16, item icon showing a block projection)
  - target: patterns/deliver-model-asset.md
    condition: when delivering a Bedrock-format asset that ships a .geo.json (prop / item / creature / GeckoLib block)
  - target: patterns/cthulhu-post-processing.md
    condition: when adding or tuning a Cthulhu-gaze visual preset or its shader
  - target: patterns/developer-tools.md
    condition: when adding a creative-tab debug item, screen, or server-validated edit packet
  - target: patterns/debug-ship-water.md
    condition: when a ship is dry, flooding incorrectly, or missing water visuals
  - target: patterns/debug-hull-damage.md
    condition: when hull durability, damage bars, welding, or breach-on-zero-HP behavior looks wrong
  - target: patterns/run-tests.md
    condition: when building or running the verification layers
  - target: patterns/publish.md
    condition: when publishing changes (走流程 / /publish) — art-only checks and direct main push, or general MEX bookkeeping and PR
  - target: patterns/vs2-physical-ship.md
    condition: when launching, docking, or debugging a VS2 physical ship
last_updated: 2026-11-15
---

# Session Bootstrap

If you haven't already read `AGENTS.md`, read it now — it contains the project identity, non-negotiables, and commands.

General code/shared-tool tasks read this file fully. Art-only tasks use the entry below and need not load the unrelated handoff, module roadmap, architecture or Current Project State sections.

## 美术任务入口

先运行 `python tools/asset_delivery.py status <资产目录>` 获取短状态，再按任务读 `.mex/patterns/deliver-model-asset.md` 或 `publish.md` 的相应分流。规则见 `docs/ASSET_DELIVERY_CONVENTION.md`，建档/迁移才查 `docs/ASSET_VALIDATION.md`。事实从资产提取，validation 是唯一验证摘要；无需默认加载详细审计、内嵌图片、其他资产或历史版本。

## 交接快照（2026-11-10）

**新会话先读仓库根 `STATUS.md` 顶部「2026-11-10」一节。** 那里是当前交接：任务目录 Phase 0 +
任务界面迁到导航终端，代码全在工作区（14 改 + 6 未跟踪，**未提交、未 push、未 pull**），
L1 203 项通过，等实机验收和用户的「提交」授权。本文件以下只保留长期项目状态。

## 合作者下次开工提醒：先读模块还原路线

开工前请阅读 [潜渊症八大模块协作路线](../docs/BAROTRAUMA_MODULE_ROADMAP.md)。它结合官方 Wiki 与仓库现状，包含白话职责、完整子步骤、首轮交付、接口边界及夜间 `/goal` 模板。

先与人类确认认领的大模块和共同基线，再开始实现；路线不是已分配任务，也不是已完成功能清单。不同模块使用独立工作目录，先约定公共接口，避免各自重造或同时覆盖船、水、供电、伤病等核心系统。方案内精确原作数值仍须逐项核实。

这是随 Git 分享的开工阅读入口，不是自动通知；合作者获取包含本提醒的提交后才能看到。

## Current Project State

- **哨站服务 + 关卡洞窟网络 + 电力/采矿/装备批次（2026-11-15，PR 待审）**：工作区累积的未提交改动一次性提交到 `feat/outpost-batch-20261115` 并开 PR，**未合并**。内容：① 哨站服务——商人/医生/站务员三个独立 NPC，商店真实库存/商人余额 5000/±5%/补货、三标签（购买/出售/潜艇）、购物车数量选择、购买确认两个交付选项（立即获得 / 起航时送至潜艇，`StoreDeliverySavedData`）、医疗诊所原作收费公式；② 关卡地形——`RoutePath` 大洞窟网络（主航道随机上下+左右游走、侧隧道可挂侧隧道、洞室、`distanceToNetwork`）、`LevelType/LevelParams/LevelSelector`、`RoundState` 持久化；③ 电力——`PowerGrid`/接线盒/配电界面/螺丝刀；④ 采矿与装备——`MineralVeins`、矿脉方块、等离子切割机、玻璃潜水服、物品浮力；⑤ 配套任务/船只/渲染修正。`./gradlew test --offline` 272 项 0 失败（47 个测试类）；客户端未起，实机验收待用户。细节见 `docs/OUTPOST_SERVICES.md`。本地 `main` 仍在 `77009a7`，`origin/main` 已到 `7de2fc5`（多个美术提交）；本地 `feat/outpost-batch-20261115` 还停在 `6a07c48`，主工作区有未提交的电池/修理改动，**还没 pull/ff**；要拉需先提交或暂存那批改动。

  **第四轮 CodeRabbit 修复（`6a07c48` 之后）**：电力面板改为服务端按玩家记录目标身份（维度 + 盒子坐标 + 坐标系），`bind`/`refresh` 不再按“坐标处有没有方块实体”重猜坐标系；`build`/`bind` 查询前先 `hasChunkAt`，客户端坐标不会触发同步区块加载；快捷打开面板同时扫描活动物理船和附近方块船，取最近的有效接线盒（物理船不再全局屏蔽方块船）；玩家退出时清面板会话。因并行电池任务正在改 `PowerPanelState.java`，本轮修复在独立 worktree `barotrauma-pr61` 里完成，主工作区未动。

  **第五轮（`64985dd`）**：`ShipCores.coresNear` 列出附近所有核心，快捷面板遍历每艘方块船（跳过已装配物理船，含 yardOffset 已换算但 probe 未就绪的兜底），不再只认最近核心；`core.state()` 为空时先 `rescan(level)` 再取接线盒，避免读档后打开面板 NPE。

  **outside-diff 补修（审核暂停期间）**：`VsPilot.toWorld` 改读已加载船句柄（`view`），不再用装配快照坐标；`PowerPanelState.build/send` 接收打开请求已选中的船并记进会话（`refresh` 复用），方块船找船半径统一 64 格；矿脉切割进度条不再因船壳映射失败被吞。**仍欠**：靠泊位移后 `PowerBox` 绝对坐标换算（Heavy lift；`PowerBox` 存的是装配坐标，靠泊后 yardOffset 重置，旧绑定会失配，尚未改）。

  **第六轮（`04dc30d`）**：面板会话改存船核心坐标，`refresh` 按核心取当前 `ShipState`（rescan 出新对象后不再显示旧设备表）；`fixtureCut` 按装配生命周期清空（launch/dock/dropStale/reset）；靠泊吸附只搬真正在舱内的玩家和 `TAG_PINNED` 任务货（世界坐标逆变换回装配坐标查 `interiorCells`）。

  **full review 批次（`361011b` 之后，20 条）**：诊所包区分首次打开/刷新（协议 14）；接线盒操作文字本地化 + 面板高度自适应 240 高窗口；接线盒宽限 setChanged、按盒高判泡水；`ShipMover` 平移保留重绑定（坐标随位移）；水密门/舱口遇 `SEA_BODY` 连线保持关闭；切割按整套拆三格舱口、只掉一次、射线跳过 `BREACHED` 船壳；`interiorCells` 改查真正船内格、靠泊乘员不再用 32 格球预筛；全船负载跳过断开支路；门/舱口自动化按船身份换算坐标；商店车行数上限统一 64 并提示；快捷面板只认同维度物理船；面板绑定后直接读 BE 绑定显示；物品浮力用完整干湿链；`LevelSelector` 改读 `LevelParams`。

  **第二次 full review（`a802289` 之后，11 条 + outside-diff + nitpick）**：旧存档关卡锚点/间距持久化（`RouteLevel.anchorX`、`RoundState.levelSpacing`，旧档回退 192/1024）；`CampaignManager.restoreRoute` 统一读档/启动/tick 三处恢复；接线盒 tick 用 `VsState.isPhysical` 判归属、方块船用原始坐标；矿脉密度改 `floorMod` 模数（不再用不连续位掩码），邻列矿脉用邻列 `floorY`；旧焊接购买价对齐商店 150 防套利；切割射线穿过打开的舱门/舱口、并加 `player.mayBuild` 权限检查；`ShipMover` 保留接线盒耐久 NBT；反应堆警报用 `effectCenter` 世界坐标；声纳矿脉点可作自动驾驶目标。

  **第三次 full review（`b08b508` 之后，3 条 + nitpick）**：声纳目标选择套用 `drawCloud` 的 `depth >= camDistance`，相机背后的矿脉不再能被点成自动驾驶目标；切割器在矿脉/舱门分支前调用 `ForgeHooks.onBlockBreakEvent` + `blockActionRestricted`（只在换目标后的第一次应用触发，拒绝时清进度）；旧存档活动槽位改按保存的 `anchorX` 认站（`siteSlot`/`siteXAt` 精确匹配活动锚点与出发站，`inRouteZone`/`inMiningZone` 强制包含活动槽位），补 1024 间距槽位 2/3 单测；`LevelParamsTest` 删恒真断言并补 `veinModulus` 边界（零概率 baseline×4、极大概率 128）。

- **资产记录分工（2026-11-15，维护者确认）**：采用唯一验证摘要与按需上下文；公共入口为 `tools/asset_delivery.py`，数据格式与命令见 `docs/ASSET_VALIDATION.md`。这里只保留规则入口，不逐件复制规格和验收。

- **美术发布规则（2026-11-15，维护者确认）**：用户要求发布指定纯美术资产时，默认交付自查后直接推送上游 `main`，不创建 PR、不等待 CodeRabbit 或 PR 门禁；资产 README、validation 与 Discussion #39 登记承担本轮记账。代码、运行时资源、共享工具及规则变更仍走通用 PR 流程。范围与步骤见 `docs/ASSET_DELIVERY_CONVENTION.md` §7、`patterns/publish.md`。

- **大型钢柜 LargeSteelCabinet 候选 A（2026-11-13）**：`art/models/large_steel_cabinet_20261113/` 收录按用户授权直推 main 的低多边形静态设备（制作来源：人工 + AI，用户修订几何/贴图，GPT-6 Codex + Blockbench 5.2.2 完成初稿与交付）。103 cubes / 10 bones / 618 声明面，512² RGBA，3×2.5×0.8753125 格；三列五门、八边形观察窗，五门独立分组并预留铰链轴心，X 居中、Y 贴地；保留用户背部伸出导致的 Z 中点 -0.1 BB。内嵌贴图工程、Bedrock 原生几何、四向 1200×900 原生预览、可重跑脚本和验证记录齐备；最终工程重开后原生导出完全一致，独立几何、UV、贴图检查通过。Discussion #39 登记候选 A；本地按用户要求归入 curated，不代表上游评选通过。仅美术归档，无开门动画和交互，未接入游戏、客户端未测试。

- **引擎 Engine（2026-11-15 实体检修板与三条动画版，取代 2026-11-13 候选 C）**：`art/models/engine_20261115/`（制作来源：人工 + AI，TakaraMiyuki + GPT-6 / Codex + Blockbench 5.2.2）。200 cubes / 35 bones / 1200 声明面，512² RGBA 映射 256² 逻辑 UV，约 5.377×2.75×2.259 格；在用户手改候选 C 的基础上补实体检修板与背板电机结构、把转子改四叶并新增双活塞，删除此前新增的 76 组螺钉与垫片。新增 `engine.animation.json` 三条 clip：`animation.engine.running`（2.4 s 组合，默认）、`rotor_spin`（0.8 s）、`pistons`（1.2 s）。四向 1200×900 原生预览 + 三条 GIF。本版是仓库首件 schema 2 验证记录（`validation.json`），三项验收均有实际操作证据；工程重开与原生重导出的 geo 逐字节一致。**原 `art/models/engine_20261113/` 已删除**，候选 A（扁平 `engine.*`）与候选 B（`engine_20261111_v2`）保留。仅美术交付，未改 `src/main/resources`、未接入游戏、客户端未测试；尚待评选。

- **高级注射器枪 AdvancedSyringeGun 美术资产（2026-11-14 最终人工调整版）**：按用户授权直接覆盖 `art/models/advanced_syringe_gun_20261113/` 原候选（人工 + AI：TakaraMiyuki + GPT-6 / Codex + Blockbench 5.2.2）。82 cubes / 8 bones / 492 声明面，256² 单贴图、64² 逻辑 UV，约 1.054×0.531×0.161 格；完整保留人工几何、UV、骨骼语义及内嵌 PNG，仅清理外部路径与编辑器选择标记。原生重开、重新导出严格一致，六向 1200×900 预览与离线检查齐备；同路径替换旧 69-cube 版本，更新 Discussion #39 已有候选 A，仍待评审，不新增候选。本地 curated 同步当前版本，不代表上游评审通过；未接入游戏、客户端未测试。

- **注射器枪 SyringeGun 美术资产（2026-11-14 人工调整版）**：按用户授权原路径覆盖 `art/models/syringe_gun_20261113/` 候选 A（人工 + AI：TakaraMiyuki + GPT-6 / Codex + Blockbench 5.2.2）。64 cubes / 7 bones / 384 声明面，128² RGBA 单贴图、32² 逻辑 UV，约 1.023×0.413×0.128 格；完整保留人工几何、UV、骨骼语义和有效 PNG 字节，移除未使用旧贴图并清除外部路径。原生保存重开与重导出严格一致，六向 1200×900 预览和离线检查齐备；更新 Discussion #39 原候选 A，保持待评审，不新增候选。本地 curated 同步当前版本，不代表上游评选通过。未重排人工 UV，未宣称原生重排往返通过；未接入游戏、客户端未测试。

- **潜水服 v2 半透明面罩资产（2026-11-12）**：`art/models/diving_suit_v2_glass_r4_20261112/` 收录已由用户验收的 v2 最终 r4（人工 + AI：TakaraMiyuki + GPT-6 / Codex）。217 导出 cubes / 9 bones / 1198 quads，256² 单贴图；八边形视窗、独立半透明玻璃、中空头壳及贴合封角。保留玩家骨架和人工基线；预览与原生导出对应，发布包含可重跑的封角像素足迹与交付检查。按当次用户要求直推 main，并在 Discussion #39 添加候选 B；旧版保留。壳体需双面 cutout、玻璃需独立 translucent 通道；仅美术交付，未接入游戏、客户端未测试。

- **潜水服护甲美术资产（2026-11-12）**：`art/models/diving_suit_20261112/` 收录人工微调后的潜水服 DivingSuit（人工 + AI：TakaraMiyuki + Codex / GPT-6.1 Sol）。104 cubes / 8 bones / 610 faces，256² 单贴图；几何与四张 1200×900 预览由 Blockbench 5.2.2 原生重出。已通过离线结构、内嵌贴图一致性、工程重开与导出一致性检查；按用户明确要求直推 main。仅美术交付，未接入游戏，客户端未测试；待资产评选。详细来源、尺寸与验证见资产 README/validation。

- **小型泵 SmallPump 4.0 美术资产（2026-11-12）**：`art/models/small_pump_20261112/` 收录人工重制的 4.0 版（纯人工：Han-xing-official，无 AI 参与）。18 bones / 38 cubes / 228 面，64² 单贴图；`art/models/small_pump_20261112/small_pump.geo.json` 为 Bedrock Entity `geometry.small_pump`，`visible_bounds` 2 / 2.5 / offset 0.75，工程 `visible_box` 已统一为 `[2, 2.5, 0.75]`，导出可复现；四张 1200×900 预览。仅美术交付，未接入游戏、客户端未测试。**验收已完成（2026-11-12）**：已在 Blockbench 5.2.2 内重开（18 骨骼 / 38 cube / 1 张 64² 内嵌贴图 / Visible box 2·2.5·0.75，无报错），并用原生 Export Bedrock Geometry 重出与仓库 geo 逐字段 0 差异（仓库 geo 即原生导出原件，sha256 `2a9fc222…`）；`validation.json` 为 `complete`，26 项全通过，PR #56 待合并。注意：本工程为 5.x 工程格式（骨骼存于独立 `groups` 表），用 4.x 打开会静默丢失骨骼名与 pivot，验收须 5.2.x 及以上。

**Working:**
- **解构仪 Deconstructor 双轴工作动画（2026-11-15，11:26 版 → 16:09 人工手调版）**：[资产说明](../art/models/deconstructor_20261115/README.md) 对应 `art/models/deconstructor_20261115/`。制作来源：人工 + AI（用户完成外形与材质手调，Codex / GPT-6 在 Blockbench 5.2.2 制作工作循环）。170 cubes / 10 bones，512² PNG 映射 256² 逻辑 UV，静止姿态约 2.0×2.519×1.569 格，X 轴居中 `centerX = 0`。`deconstructor.geo.json` 为 Bedrock Entity `geometry.deconstructor`；`deconstructor.animation.json` 的 clip `animation.deconstructor.work` 长 1 s、双刀轴各每秒一圈且方向相反、线性关键帧、首尾姿态一致。四张 1200×900 预览 + 50 帧 1 s 实速 GIF。**2026-11-15 16:09 用户手调**（`feed_hopper` 料斗静态前倾 5°、位置与深度微调），手调版外框曾偏到 −9…+23（`centerX = 7`），按项目惯例平移 −7 归中，平移量记入 `validation.json` 的 `recenter_dx`，`reference/recenter_x.py` 可复现；手调原稿随 `reference/manual_handedit_20261115_1609.bbmodel` 入库。间隙检测已改为计入父级分组旋转（刀组是料斗子级），重算结果：刀组之间约 0.61 单位，与同侧料斗侧板重叠最多约 0.97 单位，齿尖进入底板约 0.66、侧唇约 0.29，轴端与挡板相交（均未修）。仅美术交付，未改 `src/main/resources`、未接入游戏、客户端未测试；尚待评选。
- **加工台 Fabricator 人工修订美术资产（2026-11-14）**：[资产说明](../art/models/fabricator_20261114/README.md) 对应 `art/models/fabricator_20261114/`，按用户授权直推 main，并在 Discussion #39 登记候选 A。制作来源：人工 + AI（TakaraMiyuki + GPT-6 / Codex）；108 cubes / 11 bones / 648 声明面，512² PNG、256² 逻辑 UV，约 2.75×3.28125×1.4609375 格，X/Z 居中、Y 贴地。完整保留人工几何、UV、骨骼层级及 PNG 字节；重出原生几何和四向 1200×900 预览，工程重开与几何导入重出一致。保留逻辑 UV 与图像尺寸口径、纹素密度差异警告。仅静态美术归档，尚待评选，未接入游戏、客户端未测试；本地仍为 production。
- 1.20.1 Forge mod builds and loads; air-ocean deep dimension, outposts, and starter submarine generate.
- VS2 physical ships: launch, dock, thruster control, breach reconciliation, sonar proxy, and water-state sync.
- **Minimum gameplay loop:** credits + shop (repair/oxygen/welder/hull upgrade), deterministic POI wrecks, sonar-screen「下潜/靠泊」buttons, first-login guidance + objective HUD, pressure/collision threats. **Missions (2026-11-13):** station-clerk NPC (`MissionNpc`/`NpcEntity`) dialogue grants up to 3 offers per visit, each bound to an adjacent destination; offers persist at the station, refresh after undocking/redocking, and completed contracts are reset for re-issue. The campaign map lists only offers for the selected destination; cargo fills ship `crate_shelf` slots (fallback: one loose crate at the player's feet after boarding) and settles at 98% on docking. The navigation terminal's mission tab and `mission_terminal` are deprecated.
- **Persistence:** `data/ShipSavedData` (assembled snapshot + hull damage + shipId + proxy), block-ship hull damage in block-entity NBT, VS2 world-load adoption (`VsPilot.adoptFromSave` + shipyard chunk force-load). `.smoketest-persist` PASS (`船=true 耐久记录=2`), `.smoketest-mission` PASS.
- Per-compartment scalar water simulation, breach inflow splash/bubbles, ballast, pumps, reactor, and `/barotrauma ship flood` diagnostics.
- Per-block hull durability (plating 200 / glass 100), melee + explosion + block-ship collision damage, welding-tool repair, camera-facing billboard bars (readable at any view angle), vanilla-style cracks (all 6 faces, outside-visible), damage-scaled leak splashes, and `ShipHullDamagePacket` sync.
- Sonar screen/hologram, depth HUD, underwater fog, movement drag, and oxygen/drowning survival.
- **Minerals creative tab (2026-11-10):** `barotrauma:minerals` tab with 60 items — 34 ores/minerals (incl. the four alien materials and paralyxis) + 26 materials (copper/iron/lead/etc.). Textures are cropped from the game's `Minerals.png` / `AlienMaterials.png` / `Crystal.png` at 64×64; names come from the official zh/en `entityname.*` texts.
- **Hatch/door fixes (2026-11-10):** hatch `isViewBlocking`/`isSuffocating` are always false (the 1px lid used to trigger the in-block fullscreen overlay), hatch models render single-sided, the deck hatch sits mid-block again, the bulkhead door keeps a full-width 1px collision plate (no sideways squeeze), and `hatch_ladder` keeps the middle ladder collision when closed. Small pump gained `FACING`; the welding-tool item model dropped its `item/handheld` parent (generated-model flattening produced 0 quads).
- **Mission catalog (2026-11-13):** `data/barotrauma/missions/cold_caverns.json` registers all 83 original Cold Caverns missions; `MissionFeature` gates offers, currently 4 cargo entries. `MissionSavedData` stores per-station offers (template → destination) plus active missions; `MissionCargo` creates real crates (contents tagged illegitimate), counts delivered crates in shelves/inventories/loose crates near the ship, and clears all mission cargo at settlement. Network protocol 8 adds `NpcTalkPacket`/`NpcDialoguePacket` and sends per-station offers in `MissionStatePacket`. L1 226 tests pass.
- **Bulkhead door (`barotrauma:bulkhead_door`, 1×2) and deck hatch (`barotrauma:hatch`, 3×1)**, GeckoLib
  rendered, toggled by right-clicking the fixed frame (the open state's pick shape is frame-only, so
  the doorway does not block attacks). The door was redrawn 2026-11-04: 15 axis-aligned cuboids,
  6×8 real window opening with cutout glass, panel slides 16px into the side wall (`open` 0.6 s).
  Pick shapes are cached per `FACING` via `DoorShapes.slab`; a fixed north-facing shape previously
  left east/west doors clickable only at a 1px centre overlap. Toggle sounds are the original
  Barotrauma `Door1..4.ogg`.
- L1 unit tests (46, including `HullDamageNbtTest`) pass; 3 GameTests and L3 `.smoketest-vs` passed earlier.
- **医疗系统（PR #28 已合并 2026-11-07，含 17 条复审修复）**：病症即血量模型（取消原版玩家伤害/回血，vitality=100−Σ病症消耗，原作 CalculateVitality 语义源码核对）；部位鉴定按伤害来源（摔=腿、投射物=命中高度、近战=躯干；头部不做外伤）；六药（绷带/凝胶/吗啡/血袋/盐水/纳洛酮）+芬太尼，数值对照原作 XML；双界面（E 背包=窄条辅助健康栏+拖拽/双击用药，右键药品=治疗面板点选部位+对症方框）；重伤倒地状态机（≤0 倒地可救、≤−100 真死、自然流失同伤势挂钩）；饱食度机制移除。M0 底表 `docs/medical/vanilla-baseline.md`（§8 vitality 语义源码核对）。L1 医疗测试 29 个全绿、L3 任务链冒烟 PASS；合并后 main 复跑 L1 全绿（2026-11-08）。
  - **出血来源已接通（2026-11-08 方案 A）**：近战 +出血 5×HP×50%、投射物 +2.5×HP×100%、爆炸 ≥8HP 时 5% 概率 40 固定强度、摔落 ≥6HP 双腿各 10；失血由出血推进自动产生（原作 AfflictionBleeding 语义）。设计文档 `docs/medical/bleeding-sources.md`（原作 XML 行号索引）。**downedDrain 已按原版语义移除**（0 以下无额外流失，原 CharacterHealth.cs 复核）。
  - **未决**：L2 医疗用例缺 gameteststructures 模板（环境基建）；缺氧第二来源（舱内 O2）依赖氧气模块 issue #24；出血来源待实机验收。
- **开发者工具（PR #37/#38/#40，2026-11-08）**：创造模式独立页签 `itemGroup.barotrauma.dev`（图标医疗调试器）。内容：`medical_debugger`（右键开面板，六部位+全身逐病症 ±1/±10/±100、活力覆盖可设负数；`MedicalDebugPacket` 服务端校验创造/权限≥2；`AfflictionContainer.vitalityOverride` 以 `v|值` 行持久化）、`67`（从主页签挪来）、`cpu_6700x3d`（Blockbench 建模的 2-element 方块，128×64 双贴图 + 32×32 物品栏图标）、`cthulhu_trigger`（一个物品选全部预设/档位/时长）。**坑**：创造模式每秒清空病况的上帝模式要对携带调试器的玩家豁免；`PostChain.passes` 在正式包是 SRG `f_110009_`，反射必须走 `ObfuscationReflectionHelper`。详见 `patterns/developer-tools.md`、`patterns/cthulhu-post-processing.md`、`patterns/blockbench-modeling.md`。
- **物品与材料页签（2026-11-09）**：创造模式新增 `itemGroup.barotrauma.materials`「潜渊症-物品与材料」，首批两个乐器：`clown_horn`（右键一声，服务端 tick 限流，不使用物品冷却 UI）与 `guitar`（长按右键持续演奏 C–Am–F–G 琶音占位曲目，松手停止）。贴图与音效全部程序化生成（`tools/gen_instrument_textures.py`、`tools/gen_instrument_sounds.py`），**不复制原版/维基素材**；6700X3D 放置时不弹提示，右键已放置的方块显示 CPU/GPU/内存面板。
- **克苏鲁的注视（PR #38/#40，2026-11-08；2026-11-09 加平面凝视/万花筒）**：8 个纯视觉 MobEffect 预设（鱼眼/深渊低语/星之彩/时间涟漪/静电噪点/梦境漂移/平面凝视/万花筒），共用一套后处理 `shaders/post/cthulhu_gaze.json`（`Mode` 0–7 + `Strength` uniform）；客户端按 amplifier 换算 0.35/0.65/1.0 强度，按 0.05/tick 淡入、0.028/tick 淡出（中档约 0.7s / 1.2s，满档约 1.0s / 1.8s），叠加 FOV 呼吸与镜头滚转；8 张 18×18 `textures/mob_effect/cthulhu_*.png` 统一暗紫眼图标。静电是 3 像素细行带 + 16Hz 重掷 + 3% 概率错位；梦境是 6×4 网格规则透视错位（阈限空间感）。**平面凝视走投影矩阵**：`CameraProjectionMixin` 把透视与正交按 `FLAT_BASE 0.28 ± FLAT_WOBBLE 0.09` 混合，第四档「满」（仅该预设，amplifier 3）= 完全正交。**万花筒走核心顶点着色器**：`tools/gen_kaleido_shaders.py` 生成 5 个 `assets/minecraft/shaders/core/*.vsh` 覆盖（地形 4 + 粒子；**不覆盖 entity 系列**，否则背包/快捷栏图标和掉落物会被一起折），`ShaderUniformMixin` 用实例字段缓存 location 直传 `Kaleido`；形变是连续径向折返 + 六瓣角向调制（不要用无条件镜像，最弱档也会不可玩），强度 `strength × 0.5 × 呼吸`。详见 `patterns/cthulhu-post-processing.md`。
- **泳姿闪烁修复（PR #36）**：客户端不再用 `isSwimming`（医疗钩子也会设置）判断干湿；服务端用 `OceanEnvironment.isOceanWaterAt`（干舱/哨站优先）判定脚部浸没，经 `SwimStatePacket` 推给 `ClientSwimState`，水中移动入口以服务端状态为准。

**Not yet built:**
- Multi-ship support (one `VsPilot` singleton manages one physical ship).
- Per-block water surface geometry (compartments are sent as greedy rectangular prisms; no per-cell fluid surface).
- Physical-ship collision → hull damage via a VS2 collision callback (currently an AABB sampling fallback).
- Tier/upgrade system beyond the shop's flat +20%/level `hullHealthMultiplier` (no T1/T2/T3 caps yet).
- Compartment water volume / ship oxygen persistence across reload (hull damage and upgrade level persist; water does not).
- Migration of old saves containing real water or removed `hull_plug` blocks.

**Known issues:**
- **Mission catalog entries beyond cargo/salvage have no completion check.** Flipping a `MissionFeature` makes those missions appear in the terminal, but only CARGO and SALVAGE settle; `MissionCatalog.offers()` ids are list-order based (`1..n`), so reordering invalidates mission ids already stored in saves.
- **OPEN (not yet confirmed in game): east/west-facing bulkhead doors close by right-clicking the frame.** Shapes now rotate with `FACING`; the old symptom was "only the door centre reacts".
- **OPEN: the drain pump still empties a whole connected compartment** ("只要在一个房间都会抽" per user). The `[pump]` log (every 20 ticks) in `ShipSimulation` is intentionally kept for this.
- **Do not read "cannot sink inside the ship" as a swim bug.** 2026-11-04: the player stood on the cabin floor while the breached ship sank (world y falling, assembly coordinates constant). On VS2 physical ships `getBlockState` at the ship's world position is always air and `getBlockCollisions` does not see the hull; map back with `pos - VsState.offset*` before concluding anything.
- **OPEN: compartment water surface still shows visible seams/boundary lines** after multiple fix rounds (2026-11-04 user test). Current renderer uses per-corner surface averaging + waves + no step faces; still not clean. Next step: run `-Dbarotrauma.waterDebug=true` in a real flooded scene (assembled physical ship + breaches) and dump the `[water-diag]` log before changing geometry again. See `patterns/debug-ship-water.md`.
- Without any `ShipWaterStatePacket` (fresh world, ship not assembled: `ship=-1 boxes=0`), the water renderer does nothing and the underwater filter tints ship interiors — do not misread that as a water-rendering bug. Check `[water] 画水面` box counts first.
- The current MEX code graph indexes only the 3 files under `tools/`; Java sources are not in the graph. Use Grep/Read for Java.
- `ShipWaterRenderer` draws compartment water client-side by default (10 Hz packet + per-frame interpolation); the air ocean still has no real water blocks. Disable with `-Dbarotrauma.renderWater=false` when isolating data vs render issues.
- The existing `run/world` predates the air-ocean change; L3 `.smoketest-vs` currently stops with “找不到 9×9×9 空气空腔” until `run/world` is deleted and regenerated.
- `docs/IMPLEMENTATION.md` still describes the older water-column world; prefer the code and `.mex/context/architecture.md` when they conflict.
- **Local MVP work is uncommitted** on top of upstream `8751a00` (46 files); commit/push need explicit human authorization. The running `run-client` is still the pre-merge build.
- Full manual playthrough of the 7-step loop (terminal → dive → salvage → dock → turn in → upgrade → quit/reload) has not been done on the merged build; automated coverage is `.smoketest-mission` + `.smoketest-persist`, and a 73-minute idle-render soak showed 0 OOM.
- The hull damage bar is a world-space billboard (not a block texture); if it is missing, load `patterns/debug-hull-damage.md` before touching the water renderer.

## Routing Table

Load the relevant file based on the current task. Always load `context/architecture.md` first if not already in context this session.

| Task type | Load |
|-----------|------|
| Understanding how the system works | `context/architecture.md` |
| Working with a specific technology | `context/stack.md` |
| Writing or reviewing code | `context/conventions.md` |
| Making a design decision | `context/decisions.md` |
| Setting up or running the project | `context/setup.md` |
| Changing dev scripts, textures, or GameTest templates | `context/tools.md` |
| Debugging hull damage, durability bars, or welding | `patterns/debug-hull-damage.md` |
| Building or debugging a `java_block` Java block model in Blockbench | `patterns/blockbench-modeling.md` |
| Delivering a Bedrock-format asset that ships a geo.json | `patterns/deliver-model-asset.md` |
| Adding/tuning a Cthulhu-gaze preset | `patterns/cthulhu-post-processing.md` |
| Adding a creative-tab debug item or panel | `patterns/developer-tools.md` |
| Publishing changes (走流程 / `/publish`) | `patterns/publish.md` |
| Any specific task | Check `patterns/INDEX.md` for a matching pattern |

## Behavioural Contract

For every task, follow this loop:

纯美术资产发布的 VERIFY 使用对应格式的交付检查，GROW 以资产 README、validation 与 Discussion 登记完成，不为每个资产重复更新全局 ROUTER。共享约定、工具或项目能力变化仍执行下方完整流程。

1. **CONTEXT** — Load the relevant context file(s) from the routing table above. Check `patterns/INDEX.md` for a matching pattern. If one exists, follow it.
2. **BUILD** — Do the work. If a pattern exists, follow its Steps. If you are about to deviate from an established pattern, say so before writing any code — state the deviation and why.
3. **VERIFY** — Load `context/conventions.md` and run the Verify Checklist item by item. State each item and whether the output passes. Do not summarise — enumerate explicitly.
4. **DEBUG** — If verification fails or something breaks, check `patterns/INDEX.md` for a debug pattern. Follow it. Fix the issue and re-run VERIFY.
5. **GROW** — After meaningful work, run this binary checklist:
   - **Ground:** What changed in reality? Name the changed behavior, system, command, dependency, or workflow.
   - **Record:** If project state changed, update the "Current Project State" section above. If documented facts changed, update the relevant `context/` file surgically. Record a new behavioral claim about specific code as its own entity (`mex wiki apply` with a `create-entry` operation), grounded to every symbol whose change could make it false and naming the identifiers and literals it depends on. Ground with node ids only; MEX records the fingerprints. Give the entity 3 to 6 `aliases` (short plain-word phrases a developer would type for the symptom or outcome it covers, written from the claim itself, not its title or identifiers).
   - **Connect:** Link section entities to their enclosing knowledge with `refines` using existing ids. Preview missing structural links with `mex wiki link-sections`, then apply explicitly. Review semantic relationships from evidence; do not connect records just because they share keywords or code groundings.
   - **Orient:** If this task can recur and no pattern exists, create one in `patterns/` using `patterns/README.md`, then add it to `patterns/INDEX.md`. If a pattern exists but you learned a gotcha, update it.
   - **Write:** Bump `last_updated` in every scaffold file you changed. Read `mex logging --json` before optional `mex log` notes: `significant` records material rationale, `checkpoints` batches useful notes at task/session boundaries, and `manual` avoids unsolicited notes. Honor explicit user log requests in every mode; mandatory workflow Activity and recovery audits remain required.

发布时走 `.zcode/commands/publish.md`（`/publish`，或用户说「走流程」）：先分流，纯美术资产按交付约定 §7 自查后直推 `main`，以资产文档和 Discussion 登记记账；通用发布仍做 MEX 记账并走 PR。PR 阶段的 `.github/workflows/mex-guard.yml` 只统计 `src/` 与 `tools/`：改这两处却不补 `.mex/` 或 `docs/` 会失败，豁免方式是加 `mex-skip` 并说明原因。`mex check` 需要 CLI 在 PATH 里；若提示 `command not found`，先按 `AGENTS.md` 的"新机器"一节处理。
