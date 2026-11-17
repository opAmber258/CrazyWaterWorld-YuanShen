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

## 交接快照（2026-11-16）

**新会话先读仓库根 `STATUS.md` 顶部「2026-11-15」一节。** 那里是当前交接：
哨站服务 + 关卡洞窟网络 + 电力/采矿/装备批次在 PR #61
（`feat/outpost-batch-20261115`）等 CodeRabbit 复审；PR 的修复在独立 worktree
`barotrauma-pr61` 完成，主工作区还有未验收的电池/修理改动，**本地 `main` 还没 pull**。
本文件以下只保留长期项目状态。

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

  **第四次 full review（`f3a07ff` 之后，3 条）**：`onUseTick` 改看三态 `CutResult`（CUT/MISS/DENIED），拒绝目标保留 `allowed=false` 记录到换目标，按住被拒方块不再每 2 tick 重发 `BreakEvent`；`inRouteZone`/`inMiningZone` 保持 base±1 候选、活动槽位单独补查并去重（旧槽位 100 时不会遍历 50 个中间槽位）；`siteXAt` 先认活动锚点/出发站再套默认 `|z| <= 64` 过滤，南北出发站（z 可到 ±1600）也能识别，补对应断言。

  **第五次 full review（`fc8e028` 之后，10 条 + outside-diff）**：舱口水密判定取左/中/右三段最大水量（三格下可能是两个舱）；接线盒按船体几何归属选核心（不再只认最近核心）；反应堆 `effectCenter` 先判 `VsState.isPhysical` 再换算警报/烟雾坐标；诊所与商店界面文字全部改 `Component.translatable` 并补 `zh_cn`/`en_us` 键；`resetShipRuntime` 先清 `MissionInstance.routeLevel`（单人换世界不重启 JVM）；切割器只在命中格真属于物理船时走 `VsState.tryCut`，三格舱口整套拆除且只掉一个；`maxWaterFraction` 只看直接相邻水体（不再经 `pumpBody` 穿墙，新增 `AutomationWaterTest`）；`StoreService.sellCart` 拒绝任务货箱；`ROUTER.md` 交接入口指到 STATUS 顶部的 2026-11-15 节。

  **第六次 full review（`8ae8635` 之后，10 条 + 10 条 minor）**：`resolveShip` 改按 `isPhysical` + 船体几何归属选核心（泵/门/舱口/反应堆共用）；泵欠压停转；舱口外海连线三段都查；接线盒面板 `PowerStatePacket.open` 区分打开/刷新（协议 15）；诊所 7 行不压按钮、零价治疗不再误报余额；商店出售行带物品 ID 并由服务端校验、提交后锁定到下一快照、减号 0 也消费；物理船切割补映射后世界坐标的 `BreakEvent`/`blockActionRestricted`（拒绝保留记录）；三格舱口/两格舱门整套拆除只掉一个；声纳矿脉去重只让本次可见邻居抑制并先查区块；矿脉进度 6 tick 超时；`OceanEnvironment` 用物理船实时逆变换判断舱内；`terrainSeed(level)` 从 `RandomState` 补算；采矿需求累加、预加载按航线长度采样；`RoutePath.distanceToNetworkExcluding` 修正测试；透明潜水服改自定义配方 `ModRecipes` 保留子格氧气瓶。跳过物理船靠泊 `PowerBox` 平移（用户明确先不动电力）和 `openAt` 同列分离区间（需改生成器单区间 API）。

  **第七次 full review（`64c7c4e` 之后，16 条 inline + 2 条 outside-diff）**：接线盒按缓存的归属核心取当前 `ShipState`；`CampaignManager.startTravel` 用深海维度 + 出发站船核心调 `StoreService.deliverPending`；船壳血条先按距离截断，再与矿脉血条合并；`StoreStatePacket.open`（协议 16）区分首开/交易刷新；潜艇出售单行上限 64；诊所 `ClinicActionPacket.maxPrice`（协议 16）校验玩家确认价；切割器舱口逐格查类型、拒绝窗口按住续期、物理船权限缓存带玩家 UUID；`ShipMover` 保留舱口自动模式 NBT；`MissionInstance` 航道缓存用关卡快照锚点、相同关卡提前返回；矿脉候选深度 `ceiling-floor` 修正（旧公式恒负，中间候选从未生成）+ 簇数按需求量下限；`PowerPanelState.bind` 校验设备在 `consumerInfos()`；`DrainPump.assemblyPos`/反应堆上报坐标按 `isPhysical`；声呐 section 先按 Y 区间剪枝。

  **第八次 full review（`2afa0ec` 之后，9 条 + 1 条 outside-diff）**：`HatchBlock`/`BulkheadDoorBlock` 开合改用 `DrainPumpBlockEntity.resolveCore` 重扫归属核心（不再固定 16 格扫描）；接线盒面板每行显示设备坐标；`MineralVeinClient` 只接受本地玩家写入/复位；`PowerPanelState.corePosFor` 按船体几何选核心；`ShipMover` 同时搬舱壁门自动模式 NBT；`ShipState.tickMovement` 用 `engineSupplyFactor()`（每台引擎按实际电压，8 台上限）；物理船熔毁判定改 `VsState.isPhysical`；海底矿脉要求 `touchesAir`。**跳过**：物品浮力在物理船上的方块查询坐标 —— 需实机确认 VS2 世界方块可查性，且当前没有已验证的世界→装配逆变换 API，留待客户端验收。

  **第九次 full review（`c07454e` 之后，13 条 + 1 条 nitpick）**：`docs/OUTPOST_SERVICES.md` 协议更新到 16 并注明出售只收主背包 0–35；`PowerBindPacket` 成功/失败都回刷新，接线盒按钮不会永久禁用；商店提交 3 秒超时解锁；`PlayerSneakEdgeMixin` 改用只判物理船的 `isInsidePhysicalShipCompartment`；`SonarPingPacket` 按声纳/玩家归属解析物理船；`ShipGeometry.collectHull` 增加设备谓词，孤立接线盒不再抢走最近船体（新增回归用例）；靠泊搬人/货改用 `dx - (now - assembledPos)` 的完整位移；`sellCart` 限主背包槽、`resolveItem` 拒绝 AIR；商店/诊所提示全部 `Component.translatable` 并补双语键；`RoutePathTest` 增加中心线高度断言。**跳过**：出生站矿脉预加载耗时（需实机 profile）、旧区块矿脉产量保证（需产品层提示方案）。

  **第十次 full review（`5240cb0` 之后，11 条 + 1 条 outside-diff）**：商店出售列表按 `Inventory.INVENTORY_SIZE` 截断；服务器重置同时复位 `routeOutposts`；切割 `canBreak` 后补发 `ClientboundBlockUpdatePacket`（创造模式不再把矿脉显示成空气）；采矿 READY 后矿物不足退回 ACTIVE（新增 `MissionSavedData.markActive`）；`SonarPingPacket` 物理船判定补舱室/船心距离并排除附近方块船；`VsPilot.adopt` 的 `scanShip` 补 `isShell` 谓词；`shipContainers` 要求柜子落在 `bodyOf >= 0` 的舱室/连海空腔；`LevelParams` 槽位注释改 1024；客户端潜行保护用 `VsPilot.isInsideCompartment` 同步水面盒兜底；`SonarScan` 采样网格向上取整覆盖射程边缘。**跳过**：`fillFromNoise` 已提交任务在 `setRouteLevel` 切换后仍可能读新关卡 —— 需要把 RouteLevel/RoutePath 快照穿透 SeaTerrain 与生成器整条调用链，属结构改造，留待下一轮。

  **第十一次 full review（`07f394f` 之后，11 条 + 1 条 outside-diff）**：舱壁门水密判定同时查上下两格通海与水量；`JunctionBoxScreen` 宽度按窗口收缩、设备坐标单独一行；商店提交超时保持锁定并显示“结果未知”（避免重复交易）；`ItemBuoyancyEvents` 记录接管物品，离开扫描范围恢复重力；切割器在船壳每次扣耐久前和设施完成时复查 `canBreak`，整套设施任一格被拒就不拆；`SonarPingPacket` 的 `physical` 解析结果贯穿 `hullForSonar`/`coreWorld`/`geometryCore`/数据包标志，附近核心只参与距离兜底消歧；商店货柜改用 `tracksHullBlock` 判定；`DockingBay.sitesNear/siteAt` 单独补查旧布局活动槽位（旧槽位 5 锚点 25120 可查），新增 `legacySlotFiveSiteLookup` 回归。**跳过**：矿脉批量预加载（需异步/分 tick 改造）、`fillFromNoise` 关卡快照穿透（结构改造）。

  **第十二次 full review（`ee432c1` 之后，7 条）**：`MissionInstance.paramsForColumn` 先按保存锚点 + `SeaTerrain.levelInfluence` 认领活动关卡，生成器矿脉密度不再因旧间距反推错槽位（新增 `legacyAnchorColumnParams`）；`VsPilot.launch` 的 `clearFixtureCut`/`PowerPanelState.clearAll` 移到“已有物理船”拒绝之后；`ModShipBlocks.isEquipment` 纳入 `SMALL_PUMP`（接线盒+小型泵设备块不再抢船壳，新增回归用例）；`SonarPingPacket` 物理船只用 `VsPilot.state()`，不再接受附近方块船核心；切割器船壳分支在 `startingCut` 前补 `denied()`，拒绝窗口内不扣耐久；`ItemBuoyancyEvents` 用 `hold/release` 精确跟踪接管物品，修复 `CONTROLLED` 永为空；`HullDamageEvents` 按爆炸所属船分流（`VsState.isPhysicalWorld` 装配包围盒 + 核心身份），旁边方块船熔毁仍走船壳耐久。**跳过**：矿脉批量预加载、`fillFromNoise` 关卡快照穿透。

  **第十三次 full review（`d71d2fc` 之后，8 条）**：商店分类下发稳定 id（`medical/diving/tools/fuel/container`），客户端按 `store.category.*` 双语显示；物理船三格舱口删除前逐格查 `BreakEvent`+`blockActionRestricted`，任一格被拒整套保留并留进度；`VsPilot` 新增只增不减的 `knownInteriorCells`，切掉外壁舱口后靠泊吸附仍认得房里的人/任务货（几何重扫会把破洞舱的 interior 清空）；三种矿脉加入 `mineable/pickaxe`；`OceanEnvironment.isInsidePhysicalShipCompartment` 先查 `VsSupport.available()`，未装 VS2 不加载 `VsPilot`；物品浮力接管标记写入掉落物持久 NBT，`EntityJoinLevelEvent` 重建跟踪，且只还原自己打的标；`HullDamageEvents` 改为物理船按空间相交独立结算后再处理方块船（最近核心不再能否定物理船伤害）；`StoreScreen` 补键盘焦点（Tab/方向键/Enter/空格的标签、行、提交三层）与旁白，购买确认弹窗支持左右+Enter。**跳过**：矿脉批量预加载、`fillFromNoise` 关卡快照穿透。

  **第十四次 full review（`fc6648` 之后，7 条）**：`SonarScan` 地形点预留 `VEIN_POINT_RESERVE=512`，地形表面点 ≥4096 时矿脉金点不再整批丢失；`MissionProgress.itemById` 改 `BuiltInRegistries.ITEM.getOptional`（DefaultedRegistry 的 `get` 对未知 ID 返回 AIR）；`ItemBuoyancyEvents` 跳过没有本处理器标记的无重力物品，`release` 只还原带标记的；`HullDamageEvents` 去掉只含平移的装配包围盒粗筛，恢复 `VsState.active()`（旋转后的船首爆炸不再漏结算，`damageExplosion` 内部按 `shipToWorld` 判命中），同时删除 `VsState.isPhysicalWorld`/`Probe.coversWorld`/`VsPilot.PROBE`；商店旁白焦点行读实际已选数量；`MedicalClinicScreen` 补键盘焦点（Tab/方向键/Enter）与旁白；`HatchBlock`/`BulkheadDoorBlock.setPlacedBy` 调 `ShipCores.refreshNearby`（重扫附近核心 + 物理船几何，新门格不再漏出舱室/水量）。**跳过**：矿脉批量预加载、`fillFromNoise` 关卡快照穿透。

  **第十五次 full review（`8aa5843` 之后，8 条 + 1 条 nitpick）**：商店出售列表过滤任务箱（空箱也带任务标记，服务端整单会拒，普通商品会被连坐）；`StoreScreen` 面板宽/高/可见行按 GUI 尺寸收缩（320 宽窄屏不再把左半边画到屏幕外），数量 −/+ 控件改按面板右边对齐、绘制与命中共用 `quantityMinusX/quantityPlusX`；等离子切割机氧气改按成功应用次数结算（每 10 次扣 1 点，MISS/暂停不清零，堵住“在扣氧 tick 前移开准星”的免费切割）；`canBreak` 补 `level.mayInteract`（出生点保护，安全审查 Major）；潜行防掉边的物理船分支在客户端改读同步水盒 + 客户端船逆变换（专用服务器客户端也生效，方块船显式排除，新增 `ShipWaterRenderer.isInsidePhysicalCompartment`）；`ShipGeometry.Result` 增加 `equipment` 集合与 `owns()`：船壳选择仍排除设备块，但船内独立接线盒/小型泵仍被 `tracksHullBlock` 认领（新增两条回归）；靠泊搬人/货改用船体逆变换映射（偏航非零不再把人放到错误舷侧，`dockRestorePoint`）；`MissionInstance.publishRoute` 一次写锁原子发布 RouteLevel + 两端哨站标记（`restoreRoute`/`startTravel`/服务器复位都改走它）；`DockingBay.activeSites` 用保存的槽位间距/出发距离；`RoundStateTest` 补实例锚点迁移与往返。**跳过**：`restoreBlocks` 的 PowerBox 坐标平移（用户明确先不动电力）、矿脉批量预加载、`fillFromNoise` 关卡快照穿透。

  **第十六次 full review（`994bde4` 之后，13 条）**：`JunctionBoxBlock.setPlacedBy` 调 `ShipCores.refreshNearby`（新盒子不在旧几何里时 `tracksHullBlock` 会一直拒绝，设备拿不到供电）；从无人航路点续航时“送至潜艇”的交付锚点改取活动物理船的船坞核心（`VsPilot.corePos`→`toYard`），否则 `shipContainers` 扫不到货柜；VS2 船查询/逆变换移到 `ship/vs/VsClientCompartments`（`client` 包不再直接引用 `ClientShip`/`ShipTransform`）；`StoreScreen` 窄面板隐藏分类/库存/槽位次要列并截断商品名，`submitCart`/`sendLines` 统一挡 `awaitingState`（键盘连按 Enter 不会重复下单）；`HullDamageEvents` 按“命中格归属哪艘船”逐格匹配附近所有核心（A 船熔毁打到 B 船时，更近的 C 船不再把 B 的船壳挤进原版删除列表）；`MissionScreen` 焊枪报价改 150 与服务端 `COST_WELDER` 一致；`PowerPanelState.refresh` 先核对会话维度（换维度后旧会话作废）；`AutomationUtil.opensToSea` 把包围盒内的连海空腔（openPocket，编号 ≥ compartments().size()）也算外海（凹进式外门不再被水密模式自动打开，新增 2 条纯逻辑测试）；`VsPilot.removeFixture` 对舱壁门按上下两格整套删除、只掉一次（不再刷两个门物品）；`StoreService` 消息参数改传 `Component`（专用服务器不再把物品名固定成英文）；`DockingBay.anchorsNear` 按 x 去重并优先活动槽位（旧槽位 4 的 24096 与新布局槽位 2 重合时不再认虚构出发站 24288，新增回归）；`OceanEnvironment.isInsidePhysicalShipCompartment` 删掉实时查询未命中后的纯平移回退（`queryPlayerInDryCompartment` 非 null 即代表在舱内）。**跳过**：`restoreBlocks` 的 PowerBox 坐标平移（用户明确先不动电力）、矿脉批量预加载、`fillFromNoise` 关卡快照穿透。

  **第十七次 full review（`94eeede` 之后，2 条）**：`ShipSavedData.AssembledShip` 新增 `knownInterior`（历史舱内格，LongArray 落盘）：`recordAssembled`（含 `shipState` 几何里的 interior）、每 5 秒 `syncAssembledRecord`、认领 `adopt` 三处都带上它——切掉外壁舱口后几何 interior 会被外部洪泛裁空，存档/重启后靠泊吸附仍能认出房里的人/任务货（新增 `ShipSavedDataTest` 2 条 NBT 往返/旧档回退）；起航交付改 `StoreService.deliverPendingOnDeparture` + `StoreDeliverySavedData.takeAll()`，按待交付订单 UUID 遍历（含已下线玩家），货柜交付不需要玩家对象、只有背包回退才要在线玩家，离线送不出去的继续留待下次起航（新增 `StoreDeliverySavedDataTest` 2 条）。**跳过**：`restoreBlocks` 的 PowerBox 坐标平移、矿脉批量预加载、`fillFromNoise` 关卡快照穿透；两条端到端回归（切舱口→重载→靠泊、A 下单退出→B 起航）需活的 ServerLevel/VS2 运行时，留待实机验收。

  **第十八次 full review（`7f07a3e` 之后，6 条）**：`VsPilot.rebuildGeometry` 增加维度守卫（`lastLevel != level` 直接返回）：主世界开关/放置门不再用那边方块当船坞重扫，深海活动船的几何/水量/耐久记录不会被清空；`MedicalClinicScreen.mouseScrolled` 把焦点行夹回可视区（滚出屏幕后 Enter 不会误治不可见行）；`StoreScreen.keyPressed` 在 `awaitingState` 期间只放行 Esc（等待交易响应时键盘不能改购物车）；`VsPilot.dock` 的任务散箱搜索框中心改用 `toWorld(corePos)` 的当前变换位置（偏航后不再整体偏出舱室）；`ShipSimulation.engineSupplyFactor` 只取系数最高的 `MAX_ENGINES=8` 台（9 台引擎额定电压不再多出一台推力；`VsThruster.setEngineCount` 共用常量，新增 `EngineSupplyFactorTest` 3 条）；`MissionInstance.paramsForColumn` 负槽位返回 null（`BASE_X` 以西的列不再误用槽位 0 的活动参数，新增回归）。**跳过**：`restoreBlocks` 的 PowerBox 坐标平移、矿脉批量预加载、`fillFromNoise` 关卡快照穿透；跨维度/靠泊搜索框两条端到端回归需活的 ServerLevel/VS2，留待实机验收。

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
  - **缺氧回氧路径（2026-11-09 修复；2026-11-16 与远端合并后改判据）**：`oxygenlow` 原本只有注入、没有任何恢复路径（缺氧自身 `strengthchange` 为 0，五药里也没有治缺氧的），溺水一次就永久带伤持续掉血。现按原作 `CharacterHealth.UpdateOxygen:1036-1048`（有氧 `increaseSpeed=10`/s、无氧 `decreaseSpeed=-5`/s，强度 = 100 − OxygenAmount）补上两条速率：`DamageConversions.hypoxiaPerSecond() = 5` 与 `breathRecoveryPerSecond() = 10`。**判据用远端 main 同期落地的 `MedicalEvents.oxygenAvailable()`**：穿潜水服只看内部氧气瓶、没穿服看头部是否泡在原版水/海域水里（哨站干区、干舱、水肺、陆地、水下呼吸药水都有氧）。本机 WIP 原本写的是"MC 憋气值三态、无氧注入 12/s"，合并时放弃——憋气值区分不了水肺/干舱回氧，也区分不了原版真水溺水（`IS_DROWNING` 伤害已被取消，这条路径是唯一缺氧来源）。底表 §2.5.1 已记录速率。
  - **A 档补齐两种原版药品（2026-11-09）**：原作 `medical.xml` 25 种药品里只有消辐灵（`antirad`，辐射病 −100/s×1s）与液态氧矿（`liquid_oxygenite`，缺氧 −2.5/s×60s 缓释 + 器官损伤 +1.5/s×5s）的靶病症在已实现的病症集内，故零新机制落地，共 9 种药。**消辐灵补的是真窟窿**：反应堆熔毁会注入 75 强度 `RADIATION`（`ReactorBlockEntity:353`），此前既无药可解、UI 连显示名都没有。其余 18 种依赖未实现病症（4 种中毒/infection/huskinfection/psychosis/paralysis/chemaddiction），属后续阶段。**贴图照原作切图**：`Medicines.png` 的 sourcerect（消辐灵 `149,142,37,67`、液态氧矿 `187,138,38,71`），惯例=裁出后居中贴到 max(宽,高) 的正方形画布（用绷带/吗啡/血袋反推验证过）；脚本 `D:\圆神\drafts\gen\CropMedicines.java`。
  - **H 健康面板（2026-11-10，用户指定交互）**：`key.barotrauma.health` 默认 H。单击开自己；**长按 0.67 秒**锁定那一刻的最近队友并挂 `MobEffects.GLOWING` 高光，**松开时**才开他的面板。用 `System.nanoTime()` 不用 tick（20 tick 量化误差可达 50ms）；有界面开着时不计时（否则在背包里按 H 再关背包会误开）。高光仅客户端本地，20 tick 续期、松开主动 `removeEffect`。
  - **倒地禁止开背包（2026-11-10）**：`ScreenEvent.Opening` 拦截原版 `InventoryScreen` 直接 `setCanceled(true)`。原来只是换成医疗背包界面，而医疗背包的健康栏能拖药使用——等于倒地还能自救，违反"自己倒地禁用药"。
  - **UI 断线同轮修复（2026-11-09）**：`globalLines` 原写死 `{BLOODLOSS, OXYGEN_LOW}`，导致 `RADIATION`/`ORGAN_DAMAGE`（都是 `limbSpecific=false` 且 `headIndicator=false`）在面板里**完全不可见**；现改成"全部非部位、非头部的全身病症"自动列出。三处补"辐射病"显示名；`MedicalScreen.SUITABLE` 补成瘾/戒断/辐射病/缺氧；药品清单统一走 `ModMedicalItems.all()`（原 `allMedicalItems()` 漏芬太尼）。**坑**：`Map.of` 最多 10 组键值，第 11 组编译报"找不到合适的方法"，需换 `Map.ofEntries`。
  - **队友治疗距离 2 格（2026-11-10 用户实测反馈）**：原先 `36.0`（=6²）在**三处各自硬编码**（`MedicalClientHooks.NEARBY_RADIUS` H 键选人、`MedicalRequestPacket` 请求快照、`MedicalTreatPacket` 用药校验），改一处漏两处就会出现"客户端能选中、服务端回距离太远"。现抽成 `MedicalTuning.TREAT_RANGE_BLOCKS = 2.0` 单点来源，`MedicalTuningTest` 3 项护栏锁住。
  - **HUD 病症图标改原作位图（2026-11-16）**：`BaroHud.drawStatusIcon` 原来画两字中文标签（"缺氧"/"失血"）+ 彩色方框，用户要求换成原版图标。数据源是游戏自带 `Content/Afflictions.xml` 每个病症元素末尾的 `<icon texture="Content/UI/MainIconsAtlas.png" sourcerect="x,y,128,128" color="r,g,b,a"/>`。`drafts/gen/GenAfflictionAtlas.java`（JDK ImageIO，本机无 PIL）裁 11 个切片、把 `color` 乘算烘进像素、拼成 **512×384** 的 `textures/gui/afflictions.png`（4 列×3 行×128px），`BaroHud` 用 `graphics.blit` 整格贴图。**关键坑**：图集切片是**灰阶蒙版**（亮部 218-236、描边纯黑、外圈全透明），必须乘 XML 的 `color` 才是游戏里的样子，直接贴原图只会得到灰白圆圈。
  - **PR #67 三轮审查要点（2026-11-17）**：①`severityRatio` 取"活力消耗占比"与"强度/上限"的 **max**——只按消耗算会让辐射病（各段消耗为 0）永不染色，但"消耗为 0 才回退"又会在分界处骤降（成瘾 80→80.1 从 0.8 掉到 0.001）；②`vitalityDecrease` 返回的是**每 100 点活力的消耗**（出血 50 = 500），必须先除 100 再截断；③H 键高光**必须服务端挂** `GLOWING`——1.20.1 的 `Entity.isCurrentlyGlowing()` 客户端读的是 `getSharedFlag(6)`（已 javap 核对），本地 `addEffect` 不写 flag 也不同步，等于没有描边，故新增 `MedicalHighlightPacket`（协议 17→18）；④治疗面板诊断区改用上游的**滚动**方案（`statusScroll` + scissor），放弃我原先的"限高 + 截断"；⑤液态氧矿 2.5/s **压不住**溺水（环境 +5/s，净 +2.5），原作注释"Oxygen 5 is enough"说的正是这点——已加组合回归锁定。
  - **未决**：L2 医疗用例仍缺（GameTest 只覆盖物理船 3 项）；两个新药配方是**占位的 MC 原版物品组合**（非原作配方，等制造模块）；两个新药、H 键单击/长按、倒地禁背包、2 格距离均待实机验收。
  - **L2 基建坑（2026-11-10 补齐）**：`runGameTestServer` 需要 `run/gameteststructures/*.snbt`（**运行目录，不是 mod 资源**），缺文件报 `Could not find structure file gameteststructures\empty_16x8x16.snbt`。本机 Python 是 WindowsApps 占位符跑不了 `tools/gen_gametest_structure.py`，按脚本逻辑直接写出两个模板即可（内容是 `{size:[..],palette:[{Name:"minecraft:air"}],blocks:[],entities:[],DataVersion:3465}`）。**不入仓**，删 `run/` 后要重生成。另外 Gradle 默认走 7897 代理，Clash 没开时 `downloadMCMeta` 会 `Connection refused`，加 `-Dhttp.proxyHost= -Dhttps.proxyHost=` 可绕过。
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
