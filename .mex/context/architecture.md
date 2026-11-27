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
last_updated: 2026-11-27
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
   `CampaignLevels` 为固定海图的每条无向连线选定种子、岩层内的坐标区域和两端入口。新航行关的区块生成独立于当前航向和战役阶段。由 `RouteTerrain` 共用噪声生成峡谷或开阔海盆，纵向尺寸取原作宽度区间中值的 1.3 倍，深侧比浅侧低 72 格。完整服务站区由节点定位。靠泊先校验船体落位，再搬船、人和舱内散货，按实际泊位结算。无人端点由舵台确认驶出。旧存档的已生成航次继续从保存的锚点恢复。
   `StarterOutpost` 为主站生成独立舱段、中央对接通廊和上层通道。干区随舱段体积划定，楼梯塔与两侧房间以隔墙和舱门分开。旧模块站识别铜地板标记后可以修复原生成的旧出口、梯口和隔墙。20000 kW 反应堆经配电箱给制造机、分解机和制氧机的固定接点供电，站务员、商人和医生读取各服务舱的站位。
6. `ShipWaterRenderer` 逐帧插值 10 Hz 水面包。`ShipWaterMesh` 按格列合并湿区，分别更新各甲板的涟漪。`ShipWaterFlowMesh` 沿舱壁或空旷落差画连续水面，水珠和气泡跟随客户端世界刻。`ShipWaterMaterial` 只在有光学厚度的水界面折射。水中直视地板和设备按水程着色。`UnderwaterMovement` 读取服务端的可游水深和原版疾跑绑定。`HullDamageRenderer` 显示受损方块的耐久条、裂痕和渗漏粒子。

## Key Components

- **`BarotraumaMod`** — 唯一 `@Mod` 入口，构造器里挂 8 个 `DeferredRegister` 和 `ModNetwork.register()`；注册必须全部在构造阶段完成。
- **`registry/`** — 方块、物品、方块实体、菜单、实体、维度生成器和船体方块的注册表；`ModShipBlocks.isShipBlock()` 是判断“这格属于船”的唯一入口。
- **`ship/ShipState`** — 一艘船的运行时状态：几何、机器统计、`ShipSimulation`、`ShipWater`、逐方块船壳耐久（`HullDamage` + `hullHealthMultiplier`）、油门、功率、自动驾驶和物理速度。
- **`item/WrenchItem` + `block/EquipmentLayout`** — 拆卸把命中分片映射到设备主块与占地。服务端把物理船保护事件发到对应世界坐标，按主块移除整机。隐形菜单锚点只影响选中，真正的拆卸目标仍要通过射线验证。 `VsPilot.recordDismantledEquipment` 从靠港快照剔除已消失的分片，更新水泵、反应堆和引擎代理及分舱。
- **`ship/HullDamage`** — 每格 0..1 剩余比例；重扫/平移/拆装时跟随船状态迁移。基础血量在 `ModShipBlocks.maxHealth`：装甲板 200、舷窗 100、测试船壳 200；声纳不可破坏。
- **`ship/vs/VsPilot`** — VS2 物理船的唯一静态管理器：装配/拆回、推进器、船坞偏移、破口对账、水量同步、声纳代理方块移动。`VsState` 提供不引用 VS2 类型的桥接，`VsSupport` 只做 `ModList.isLoaded("valkyrienskies")` 判断。
- **`ship/water/ShipVolumeGuard` + `mixin/LevelFluidGuardMixin`** — 拦截船只体积内的真实流体写入；空气海域下仍保留用于旧存档兼容。
- **`mixin/ShipBreakMixin`** — 把玩家左键破坏映射到船坞坐标，让物理船也能破口。
- **`world/OceanEnvironment`** — 干湿判定入口：维度高度、真实碰撞体、船舱水位与哨站干区。服务端查询模拟水位，客户端通过 `DryZone` 查询同步的水盒。
- **`network/`** — 6 个包：`ShipActionPacket`（客户端→服务端）、`VesselPalettePacket`（船体渲染）、`SonarPingPacket` / `SonarDataPacket`（声纳）、`ShipWaterStatePacket`（水面包）、`ShipHullDamagePacket`（受损方块 + 剩余比例）。
- **声纳主屏** — `SonarCycle` 属于服务端终端，同台玩家共享同一发脉冲。状态回复按菜单 ID 路由，已收到的脉冲不重复发送。`SonarPulse` 携带发射时的世界原点、tick、模式和朝向；`SonarScope` 按原作的计时、色阶及叠加规则批量画体素反射与径向余辉，并保留可点选的世界坐标。主屏缩放来自 `ScaledContainerScreen`，台面全息从客户端单独取点。
- **驾驶台界面与本船轮廓（协议 38）** — 物理船点云以装配核心为原点，轮廓、回波视点和船头每帧读取 VS2 渲染变换，定位未就绪时用最近一次声纳回复。船壳方块投影合并到像素遮罩，只描覆盖边界，保留台阶和空隙。驾驶目标仍按 3D 投影拖动，目的站沿水平罗盘绘制站名和米数，峡谷远站可用主航道估计里程。
- **驾驶台操舵（协议 46）** — 已恢复重绘前的驾驶台及其鼠标操作，仍可在 3D 声纳上设置或拖动定点。物理船的垂向定点按原作投影 10 秒的运动，修正后距离达到 5 米即可发出全舵令，压载泵、电网、水量与浮力决定实际速度。
- **独立站区水域** — 冰盖下按哨站原点生成带起伏的宽水域，靠泊点的高度保留。旧椭球外的天然岩层按区块识别并迁移，已加载区块分批更新。
- **航行关对接入口** — 入口顶盖上方的岩壁逐渐下延至入口，保留泊位高度和侧门。服务器在恢复航段、离港或初始化入口舱口时给旧区块按同一形状补岩，只填空气。
- **靠泊交接** — 删除 VS2 船前快照乘员和散货的落点。传送乘员之前，服务端清理 VS2 已缓存的随船坐标与位移，先通知客户端解除拖拽，再发绝对坐标。已抵达站区但留在海里的旧存档，重登时走战役当前泊位恢复。
- **驾驶与定点** — `NavigationMotion` 用实际核心速度、联网引擎可用反推和电网余量决定何时制动。3D 按需调整艏向抵消横漂，升沉仍由压载泵执行。锁位后先显示稳船，持续半秒位置、速度和出力都达标再显示保持。`VsThruster` 的轴力换算以儒艮额定横速 21.6 km/h 标定，舵令回中后轴力继续衰减。引擎菜单同时报告目标与当前值。手动舵令最多保留驾驶员最近 20 tick 的授权，超时回中，其他看声纳的玩家不会替驾驶员续期。
- **驾驶台归属** — `SonarMenu` 在服务端绑定打开时解析出的终端方块实体，声纳轮询、舵令、下潜和靠泊都使用同一个绑定。物理船从代理打开时绑定船坞终端，代理移动不影响归属。方块船终端拆除、原址替换或越界会关闭旧菜单。
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
- **手电筒和炮灯照明**：手电筒在服务端消耗电池，并用玩家实体数据同步亮灯位置和低电标志。客户端在水体和手部渲染前处理两种光锥，复用世界画面的颜色、深度和实际投影矩阵。手电筒射程为 12 格，炮灯射程为 50 格。自己的手电发光时关闭贴身微光。固定舱灯仍由 Minecraft 方块亮度和可选的 Colorful Lighting 传播颜色。
- 外海的表面照明交给海雾处理，近处水体散射在海雾之后叠加，并以原始场景深度为终点。
