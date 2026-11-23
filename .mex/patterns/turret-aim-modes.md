---
name: turret-aim-modes
description: 炮塔原型的瞄准模式与弹道实现：三种方块共用 BE、mixin 接管输入与相机、以炮口为起点的 hitscan/落点预判、水下炮弹特效、右键瞄准与 HUD 括号/命中反馈。
triggers:
  - "炮塔"
  - "turret"
  - "瞄准"
  - "准心"
  - "弹道"
  - "粒子束"
  - "跟随炮台"
  - "自由瞄准"
  - "炮塔模型"
  - "旋转基座"
  - "瞄准站"
  - "炮台绑定器"
  - "炮塔绑定"
edges:
  - target: patterns/blockbench-modeling.md
    condition: when changing the turret block model or barrel rendering geometry
  - target: patterns/run-tests.md
    condition: when verifying the turret change beyond a compile
last_updated: 2026-11-23
mex:
  id: mx_01M7M7HRZYXWEE1W4VEB95FRVX
  type: pattern
  status: promoted
  revision: 6
  title: turret-aim-modes
  relations:
    - type: related_to
      target: mx_01M6YVNXNWH92SGKPF7J6H6M9C
      note: when changing the turret block model or barrel rendering geometry
    - type: related_to
      target: mx_01M6FTV7PNBD1ZG84B1K5GCG1N
      note: when verifying the turret change beyond a compile
---

# 炮塔原型的瞄准模式与弹道

## Context

三个原型方块 `barotrauma:turret`（常规）、`free_aim_turret`（鼠标拖动光标引导视角）、
`tracking_turret`（视角自由、炮塔准心限速追中心）共用：

- `TurretBlockEntity`：服务端权威 `yaw/pitch`，`aim()` 按 `MAX_TURN_PER_TICK = 6°`（120°/s）截断；
  `fire()` 射程 48、单发冷却 10 tick、全自动冷却 4 tick（5 发/秒）、hitscan 命中方块或最近实体
  （`inflate(0.25)`）、12 点伤害。
- 五个网络包：`TurretAimPacket` / `TurretFirePacket` / `TurretModePacket` / `TurretExitPacket` /
  `TurretStatePacket`（协议号见 `ModNetwork.PROTOCOL`）。
- 客户端 `TurretClient`（状态机）、`TurretRenderer`（炮管旋转 + 后坐）、`TurretHud`（观察窗 + 准心）。

模式差异只在客户端输入层，由方块类判断：`block instanceof FreeAimTurretBlock` 等。

## Steps

1. 输入走 mixin，不要直接改玩家朝向：
   - `TurretMouseMixin` 取消 `MouseHandler#turnPlayer` 并转发累积像素（跟随模式不取消，保留原版视角）；
   - `TurretCameraMixin` 在 `Camera#setup` 尾部改相机位置（`TurretClient.cameraPosition()`）；
   - `TurretInteractionMixin` 拦 `startUseItem`（右键瞄准，只取消不做事）与
     `startAttack`（左键开火）以及 `continueAttack`（禁止挖炮塔）。
2. **每帧推进的逻辑放 `ViewportEvent.ComputeCameraAngles`**（`GameRenderer.renderLevel` 每帧一次）：
   自由瞄准的限速跟随 `stepFreeAim`、跟随模式的 `stepTracking`、粒子束 `tickBeam` 都在这里。
   `ClientTickEvent` 只有 20 Hz，放那儿会“一卡一卡”。
3. 弹道起点统一用 `TurretBlockEntity.muzzlePosition()`：几何来自方块 `TurretSpec`
   （当前默认 `pivotY = 0.75`、`muzzleLength = 1.14`），服务端/客户端/粒子三处同源。
   服务端 `fire()`、客户端 `predictedImpact()`、粒子束三处共用，别各写一份。
4. 落点预判返回 `ImpactPrediction(point, halfWidth, halfHeight, kind)`：实体取包围盒，方块按 1×1；
   `TurretHud` 按透视把半宽/半高投成像素，竖边限制 3…7 px、横边至少 3× 竖边、上限 34 px。
5. 曳光用自定义粒子 `ModParticles.TURRET_TRACER` + `TurretTracerParticle`，逐帧按
   `BEAM_SPEED * dt` 推进头部、沿新段落按间距撒点；不要给初速和抖动。
6. **每发炮弹一个 `TurretShotFx`**：`TurretClient` 固定 16 个槽位（正常帧率射程 48 / 24 格每秒 = 2 秒，
   全自动 5 发/秒最多约 10 发并发，单发最多 4 发；低帧率时弹道按帧推进更慢，16 个槽位兜底）。
   弹速固定 24 格/秒，但**服务端 fire() 是瞬时 hitscan**：最远 48 格时客户端命中特效/标记会比真实伤害
   晚约 2 秒（已知限制，弹道表现优先）。
   空中间距 0.12，水下间距 0.24 且同时撒 `TURRET_TRACER` 能量核心 + `TURRET_BUBBLE` 气泡尾。
   介质按每帧弹道段中点查 `OceanEnvironment.isSubmerged`，跨水面时二分找水线再补一圈气泡。
7. **命中先分类再出特效**：`ImpactPrediction` 带 `HitKind`（CREATURE / HULL_INTACT /
   HULL_BREACHED / GLASS / STRUCTURE / SEABED / MISS）。破口方块碰撞为空，COLLIDER 射线会穿过去，
   必须再用 `ClipContext.Block.OUTLINE` 扫一次（`HullBlock#getShape` 在 breached 时仍返回整格）。
   各分支的粒子和声音见 `TurretClient#spawnImpactEffects`；**命中示意指针只在 `CREATURE` 时播放**
   （船壳/玻璃/结构/海床/打空都不触发），颜色白色、比主准心短、从 10°/24°/38°/52°/66°/80°
   六个预设角中随机取一个（连续不重复），用 `QUADS` 原生四边形画 1.1 GUI px 斜线，
   8 tick 线性淡出；新命中直接替换旧指针。
8. **三段式模型（v2）**：`art/models/turret_20261119/` 提供固定底座 / 水平旋转基座 /
   俯仰炮管三组。固定底座导出为 Java 方块模型（只含 `base` 组），旋转基座与炮管由
   `TurretRenderer` 用 BER 绘制：yaw 只转基座，炮管再叠 pitch，后坐沿局部 -Z。
   轴心仍是 `TurretSpec.DEFAULT` 的 `pivotY=0.75`（模型 y=12），炮口 `muzzleLength=1.14`
   （模型 z=26.24），与弹道/落点预判共用，不要各改一份。
   贴图是一张 512×256 的 8×4 瓦片图集：20 种零件各占一块 64×64 瓦片（同型号共享），
   瓦片内分 top / Z 面 / X 面三个 32×32 象限，所以每个面取的是不同像素区域，不是整图重复。
   三种炮塔用 `turret_atlas` / `turret_atlas_freeaim` / `turret_atlas_tracking` 三张同版式图集。
   新增/改形 cube 时先给它分配瓦片号，再同步 `TILE_STYLES`、`TurretRenderer` 的瓦片号和
   `reference/palette.json`。
9. **挂载面（MOUNT）**：`TurretBlock.MOUNT` 六向表示“离开支撑方块”的方向；
   落地/吊顶/四面墙共用世界 yaw/pitch 和同一套操作，只改底座模型、相机高度和俯仰窗口：
   地板 `-89..+20`、吊顶 `-20..+89`、墙面 `-80..+80`。墙面/吊顶底座是贴面法兰
   （墙：竖向短颈；顶：细主轴），不是水平托架；blockstate 每个 mount×facing 都有变体。
   BER 用组级透明度做近处淡出：炮管/转盘各自取本组 cube 到相机的最小 AABB 距离，
   整组一起淡出；固定底座是方块模型，不参与淡出。
10. **右键瞄准（ADS）**：按住 `keyUse` 进入，FOV ×0.55、普通炮塔鼠标增益 ×0.35，
   `aimBlend` 每帧插值；左键开火（`startAttack`）。跟随炮塔的视角中心是鼠标十字，
   瞄准时把它的 alpha 压到 35%，把画面让给炮塔落点括号。
   HUD 的世界点投影和自由光标换算必须用 `effectiveFov()`，否则缩放后括号会错位。
11. **瞄准站 + 炮台绑定器（开发者工具）**：`TurretStationBlock` 只存一个 `TurretPos`
   （BE 的 long NBT），右键站台时服务端校验并调用
   `TurretBlockEntity.enterFromStation(player, stationPos)`。相机仍在炮塔本体位，
   操作/准心/落点预判与直接进炮塔完全一致；唯一区别是炮塔 BE 记一个**临时**
   `operatorAnchor`（不存档），`withinReach()` / `serverTick` 在锚点存在时跳过 6 格距离，
   改为每 tick 校验“站台还在且仍绑定本炮塔”，站被拆/改绑立即退出。
   - 绑定由 `TurretBinderItem` 两端完成：右键炮塔/站台记录一端，再右键另一端写入站台 BE；
     待绑定是服务端内存 `Map<UUID, Pending>`（2 分钟过期），只允许同维度、一端炮塔一端站台，
     距离不限；潜行右键站台解绑。
   - 站台 BE 必须覆盖 `getUpdateTag()`/`getUpdatePacket()` 并在 `setChanged()` 里
     `sendBlockUpdated`，否则客户端要等区块重载才看到绑定。
   - `TurretBinderRenderer` 在手持绑定器时每 10 tick 扫描已加载区块，取站台 BE 的
     `TurretPos` 画带下垂的线缆；绿=炮塔存在、琥珀=目标区块未加载、红=目标已不是炮塔。
   - 同一渲染器还用 64 格准星射线找“已绑定的方块”：指向站台 → 描边它的炮塔；指向炮塔 →
     描边所有绑定它的站台。描边用 `NO_DEPTH_TEST` 的 lines（隔墙可见）+ 外圈淡晕，
     脉冲呼吸；炮塔视角里跳过（准星是炮塔相机，不是玩家身体视角）。
   - 站台可能离炮塔超过 128 格，`enter`/`clearOperator`/`setRemoved` 必须把
     `TurretStatePacket` **直接发给占用者**，不能只靠 `broadcastState()` 的 128 格半径。
   - `TurretStatePacket` 带 `fromStation`（协议 18→19）：客户端命中预判据此决定是否把
     操作者本体算作合法目标；直连炮塔排除自己，站台遥控包含自己。
   - 炮塔/站台视角强制 `THIRD_PERSON_BACK`：第一人称会剔除本机玩家模型，看起来像“人消失”。
     `Camera#setup` 尾部仍把相机覆盖到炮塔，第三人称只负责保留本体渲染；退出恢复原相机类型。
12. **炮塔伤害玩家**：`findTarget()` 不再排除操作者——站台遥控时本体留在站台，可能正好在
    炮口线上；所有存活、非旁观 `LivingEntity` 都是候选。普通玩家仍走
    `target.hurt(playerAttack(operator), 12)` → 医疗系统转换；创造模式下原版 `hurt` 会在
    事件前拒绝，检测到 `!damaged && targetPlayer.isCreative() && hurtTime <= 0` 时调用
    `MedicalEvents.applyDirectDamage()`（近战通用：躯干钝器伤 + 50% 概率出血），
    这样创造模式单机也能验收“炮塔打玩家”。`hurtTime` 检查防止绕过原版无敌帧双倍结算。
13. **单发/全自动（Q）**：炮塔内按 `KeyMapping` Q（`key.barotrauma.turret_fire_mode`）发
    `TurretModePacket`；服务端 `toggleFireMode()` 校验占用者/距离后翻转 `autoFire`，
    `setChanged()` + `broadcastState()` 并给操作者 actionbar。全自动 `fire()` 冷却
    `AUTO_FIRE_COOLDOWN_TICKS = 4`，客户端按住左键时按同款 4 tick 本地门控续发；
    进入炮塔时只取一次服务端剩余冷却，之后不再用逐 tick 状态包覆盖，避免延迟快照把冷却拉长。
    `requestFire()` 用 `lastFireTick` 挡同 tick 的 `startAttack` + 轮询双触发。
    模式随炮塔 NBT `AutoFire` 持久化；`TurretStatePacket` 末尾带 `autoFire`（协议 30）。
    Q 与原版丢弃同键，`LocalPlayerMixin` 在 `TurretClient.isActive()` 时取消 `LocalPlayer#drop`。

## 模块边界（换模型 / 2×2·3×3 时只动这些）

- `block/TurretSpec`：尺寸（sizeX/sizeZ）、轴心、炮口长度、相机高度、三种挂载的俯仰窗口。
  改 2×2/3×3 时覆盖 `TurretBlock#spec()` 即可；`TurretBlockEntity` 的 `minPitch/maxPitch`、
  `pivotPosition`、`muzzlePosition`、`cameraPosition` 全部读它。
- `client/TurretModels`：图集、turntable/barrel 的 cube 数组、图集 UV 布局。
  换模型只改这里 + 对应 png/json；`TurretRenderer` 只负责 yaw/pitch、后坐、近处淡出。
- **不要在瞄准/开火/相机/HUD 里写回 0.5、0.75、1.14 这类几何常数**；服务端弹道、
  客户端落点预判、粒子起点必须继续共用 `muzzlePosition()`。
- 多块预留：`TurretSpec.sizeX/sizeZ`；`TurretBlockEntity.masterPos()` 把任意一块换到主方块，
  `at(level,pos)` 统一解析；瞄准站和绑定器已经按主坐标存储/绘制高亮。
- 换模型后验收：三种炮塔能进/瞄/开火/退出；相机高度、炮口粒子、落点括号、近处淡出
  都跟随新 spec；`getRenderBoundingBox()` 覆盖新模型范围（2×2/3×3 必须检查，否则边缘会整根剔除）。

## Gotchas

- 炮管伸出方块 1.14 格且要随 yaw/pitch 旋转，**不能**放进方块模型（超出 0…16 会被裁切），只能走 BER。
- 三段式模型的旋转基座也必须走 BER，不能留在方块模型里；方块模型只保留 `base` 组。
- 运行时 Java 模型 UV 一律 0…16：512×256 图集里 u 按 32px/单位、v 按 16px/单位归一化。
  用像素坐标（0..512/0..256）写 item/block JSON 会被原版当成超界 UV，表现为贴图被裁成一条。
- 瓦片号、象限 UV、`TurretRenderer` 的 `boxPx(..., tile)` 三处必须同步；只改一边会出现
  “底座对、旋转部件错位/整面同图”的贴图。
- 固定底座是方块模型，**无法**跟随 BER 透明度淡出；近处挡视野时只能把底座几何搬进 BER
  或做额外的客户端方块渲染过滤。当前淡出只覆盖转盘和炮管。
- 挂载面只改底座/相机高度/俯仰窗口，不要把它写成安装面局部坐标系：那会让墙面/吊顶的
  yaw/pitch 方向反转，用户明确要求三种挂载操作一致。
- 相机眼位 `EYE_HEIGHT`（0.5+0.55=1.05）比炮口高 0.3 格；从相机发射粒子会糊在镜头前挡视线。
- 原版没有又细又不带火焰的粒子：`END_ROD` 光晕偏粗，`CRIT` 受重力会下垂，所以用自定义粒子把
  `quadSize` 压到 0.03，并覆写 `getLightColor` 返回满亮度。
- 新增网络包必须 bump `ModNetwork.PROTOCOL`；合并上游时该行与包注册块最容易冲突，两侧都要保留。
- 跟随炮塔退出时不能 snap 玩家视角（`deactivate` 里只有 `snapPlayerRotation && !tracking`）。
- HUD 层级靠绘制顺序：拖动光标先画，炮塔准心、落点括号后画（后画的在上）。
- 自由瞄准光标按屏幕像素换算角度；基础 FOV 不能直接用 `options.fov()`，瞄准缩放后要用
  `TurretClient.effectiveFov()`，否则光标和落点括号会和画面错位。
- breached 的 `HullBlock` 碰撞为空，`ClipContext.Block.COLLIDER` 射线会直接穿过去；
  判“打中破口”必须补一次 `ClipContext.Block.OUTLINE`（它的 `getShape` 仍返回整格）。
- 命中指针、弹道特效、右键瞄准都只是客户端预测/表现；服务端仍按 hitscan 瞬时结算，且没有瞄准散布。
  客户端弹道比服务端晚到、实体位置还差一 tick，所以指针可能“假阳性/假阴性”，**不能用它判定真实伤害**。
  要做“腰射散、瞄准不散”或服务端确认命中，必须改 `fire()`/加确认包，同时 bump 协议。
- 地面波 `TurretGroundWave` 只在命中点 0.9 格内能找到可站立地面时触发，且按每个角度单独采样地形；
  不要在每帧对所有环点做全量地形扫描。
- 破口 `OUTLINE` 只用于特效分类，**不要**拿 `breachPoint` 截短实体搜索：服务端 fire() 只做
  COLLIDER，子弹会穿过破口打中后面的生物；截短会导致“服务端掉血、客户端显示穿洞没命中”。
- ADS 灵敏度三种模式要一致：普通炮塔在角度增量上乘 `mouseAimScale()`；跟随炮塔由
  `TurretMouseMixin` 把原版累积增量乘同一个系数；自由瞄准不额外乘（光标保持 1:1 像素），
  靠 FOV 缩放提升角度精度。
- 炮塔的 `ComputeFov` 在 `ComputeCameraAngles` 之前执行；HUD 投影要读 `onComputeFov`
  缓存的最终 FOV（`effectiveFov()`），不能自己用 `options.fov()` 再算一遍。
- 服务端 aim() 每 tick 最多转 6°，客户端 fire 用的是本地当帧角度且 `TurretFirePacket` 不带角度；
  快速甩炮时服务端可能滞后 1 tick（48 格处约数格偏差）。要彻底对齐需让 fire 包携带角度并 bump 协议。
- 客户端粒子只有本机可见；要让其他玩家看到弹道，需要新增服务端广播包。
- 瞄准站绑定是**单向**的（站台存炮塔坐标），不要为了“查所有绑定”在炮塔里存站台列表：
  一个炮塔可以接多个站台，反向列表只会增加同步/清理负担；客户端连线靠扫描站台 BE。
- 站台没有 ticker，绑定变化只靠 `setChanged()` → `sendBlockUpdated` 同步；漏掉这步
  绑定器连线要等区块重载才刷新，右键进入却已经生效，表现会像“绑定丢了”。
- 读目标方块前必须 `level.hasChunkAt(pos)`；`getBlockEntity` 在未加载区块会同步加载，
  客户端画线/服务端校验都不能为了一个坐标把区块拉起来。
- **Forge 1.20.1 的方块交互顺序是「方块 use → 物品 useOn」**（潜行时跳过方块 use），
  和原版“物品优先”相反。绑定器这类工具必须在 `TurretBlock.use` / `TurretStationBlock.use`
  里检测手持物品并返回 `PASS`，否则右键只会触发方块自己的进入/未绑定提示，物品的
  `useOn` 永远不执行。客户端 `MultiPlayerGameMode` 也是同一顺序，两边都要让位。
- 站台操作距离不限是用户定稿（2026-11-19）：如果炮塔区块在客户端没加载，相机不能丢进虚空，
  `TurretClient.onState` 会直接回 `TurretExitPacket`；不要改成自动加载/强加载。
- 站台方块正面朝 +Z（south），blockstate 用 south=0°/west=90°/north=180°/east=270°；
  改模型朝向时和 `FACING` 一起改，否则屏幕会背对玩家。
- 瞄准站是落地控制台：`getStateForPlacement` / `canSurvive` 要求下方完整可站立面，
  下方被拆会掉落；要支持贴墙/吊顶时应该另做模型和挂载属性，不要直接放宽支撑检查。
- 炮塔 `findTarget()` 不能把操作者排除掉：站台模式下本体在别处，排除后单机永远无法验收
  “炮塔打玩家”；只有直连炮塔时排除自己才说得通，统一包含更简单。
- 创造模式玩家走 `MedicalEvents.applyDirectDamage()` 前必须同时检查 `!damaged` 和
  `hurtTime <= 0`：只看创造标志会在无敌帧里每发都重复注入病症。
- 绑定器描边的准星射线是玩家身体视角（`getEyePosition/getViewVector`）；炮塔视角里必须跳过，
  否则准星在炮塔相机、射线却从站台身体发出，会描错方块。
- `runClient` 运行期间不要跑 Gradle：会重写 `build/classes` 并触发 `NoClassDefFoundError`。

## Verify

- [ ] `./gradlew compileJava --offline` 通过
- [ ] 三种炮塔分别能进入 / 瞄准 / 开火 / 左 Shift 退出，退出后玩家视角正常
- [ ] 落点括号位置与实际命中一致（方块与实体各试一次）
- [ ] 深海无光环境下粒子束仍可见，且不从镜头中心射出
- [ ] 单发 0.6 秒冷却 / 全自动 5 发每秒都能正常连发，弹道槽位不互相覆盖
- [ ] 炮塔内 Q 切换单发/全自动；HUD 模式显示正确，模式退出重进后保留
- [ ] 命中指针只在生物/玩家命中时出现；随机预设角、8 tick 淡出、新命中替换旧指针
- [ ] 打船壳/玻璃/结构/海床/打空不出现命中指针
- [ ] 水下能看到白蓝能量核心 + 气泡尾；空气/水下命中、破口、舷窗、海床分支可肉眼区分
- [ ] 右键瞄准：FOV 缩到 55%，准心收拢；跟随炮塔的鼠标十字 alpha 降到 35%，松开恢复
- [ ] 瞄准时 HUD 落点括号仍与实际命中一致（缩放后投影不能错位）
- [ ] 六面挂载：底座贴原生面、不浮空；拆支撑会掉落；三种挂载鼠标方向一致
- [ ] 相机贴近炮管/转盘时整组淡出，不出现“末端透明、前端挡视野”
- [ ] 绑定器：炮塔→站台、站台→炮塔两种顺序都能绑定；同类型两端被拒绝；跨维度被拒绝
- [ ] 瞄准站：未绑定时右键只提示；绑定后右键进入同一套炮塔视角，左 Shift 退出
- [ ] 潜望镜须有相邻的上置或下置底座。操作者距离按潜望镜校验，视点使用炮塔规格的镜头，船载炮塔的镜头随船平移和旋转。
- [ ] 站台绑定/解绑后手持绑定器，连线 10 tick 内刷新；拆站台/拆炮塔后占用者自动退出
- [ ] 超过 128 格远的站台仍能进入/退出（状态包直发占用者）
- [ ] 瞄准时本机玩家模型仍留在原地可见（第三人称），退出后恢复原来的第一/第三人称
- [ ] 炮塔能打中其他玩家和操作者本体；创造模式下目标也能看到病症/出血反馈
- [ ] 手持绑定器指向已绑定的瞄准站/炮塔：另一端 64 格内出现脉冲发光描边，隔墙可见

## Debug

- 准心抽搐：检查跟随/粒子推进是不是被放到了 `ClientTickEvent`，或 FOV 用了 `effectiveFov` 之类每帧变值。
- 弹道偏移：确认三处（服务端、预判、粒子）都用 `muzzlePosition()`，且用的是同一组 `aimYaw/aimPitch`。
- 粒子看不见：先查 `assets/barotrauma/particles/turret_tracer.json` 与贴图路径，再看
  `ClientSetup.onRegisterParticleProviders` 是否注册了 `registerSpriteSet`。
