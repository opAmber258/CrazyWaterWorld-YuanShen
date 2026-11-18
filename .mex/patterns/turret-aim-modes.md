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
edges:
  - target: patterns/blockbench-modeling.md
    condition: when changing the turret block model or barrel rendering geometry
  - target: patterns/run-tests.md
    condition: when verifying the turret change beyond a compile
last_updated: 2026-11-18
mex:
  id: mx_01M7M7HRZYXWEE1W4VEB95FRVX
  type: pattern
  status: promoted
  revision: 3
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
  `fire()` 射程 48、冷却 10 tick、hitscan 命中方块或最近实体（`inflate(0.25)`）、12 点伤害。
- 四个网络包：`TurretAimPacket` / `TurretFirePacket` / `TurretExitPacket` / `TurretStatePacket`（协议号见 `ModNetwork.PROTOCOL`）。
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
3. 弹道起点统一用 `TurretBlockEntity.muzzlePosition()`：`PIVOT_Y = 0.75` + `MUZZLE_LENGTH = 1.14`。
   服务端 `fire()`、客户端 `predictedImpact()`、粒子束三处共用，别各写一份。
4. 落点预判返回 `ImpactPrediction(point, halfWidth, halfHeight, kind)`：实体取包围盒，方块按 1×1；
   `TurretHud` 按透视把半宽/半高投成像素，竖边限制 3…7 px、横边至少 3× 竖边、上限 34 px。
5. 曳光用自定义粒子 `ModParticles.TURRET_TRACER` + `TurretTracerParticle`，逐帧按
   `BEAM_SPEED * dt` 推进头部、沿新段落按间距撒点；不要给初速和抖动。
6. **每发炮弹一个 `TurretShotFx`**：`TurretClient` 固定 8 个槽位（正常帧率射程 48 / 24 格每秒 = 2 秒，
   本地冷却 0.6 秒最多 4 发并发；4–20 FPS 区间按帧推进变慢，8 个槽位足够兜底；低于约 1.7 FPS
   仍可能占满，属于极端情况）。
   弹速固定 24 格/秒，但**服务端 fire() 是瞬时 hitscan**：最远 48 格时客户端命中特效/标记会比真实伤害
   晚约 2 秒（已知限制，弹道表现优先）。
   空中间距 0.12，水下间距 0.24 且同时撒 `TURRET_TRACER` 能量核心 + `TURRET_BUBBLE` 气泡尾。
   介质按每帧弹道段中点查 `OceanEnvironment.isSubmerged`，跨水面时二分找水线再补一圈气泡。
7. **命中先分类再出特效**：`ImpactPrediction` 带 `HitKind`（CREATURE / HULL_INTACT /
   HULL_BREACHED / GLASS / STRUCTURE / SEABED / MISS）。破口方块碰撞为空，COLLIDER 射线会穿过去，
   必须再用 `ClipContext.Block.OUTLINE` 扫一次（`HullBlock#getShape` 在 breached 时仍返回整格）。
   各分支的特效、声音和 hitmarker 见 `TurretClient#spawnImpactEffects`。
8. **右键瞄准（ADS）**：按住 `keyUse` 进入，FOV ×0.55、普通炮塔鼠标增益 ×0.35，
   `aimBlend` 每帧插值；左键开火（`startAttack`）。跟随炮塔的视角中心是鼠标十字，
   瞄准时把它的 alpha 压到 35%，把画面让给炮塔落点括号。
   HUD 的世界点投影和自由光标换算必须用 `effectiveFov()`，否则缩放后括号会错位。

## Gotchas

- 炮管伸出方块 1.14 格且要随 yaw/pitch 旋转，**不能**放进方块模型（超出 0…16 会被裁切），只能走 BER。
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
- 命中特效、hitmarker、右键瞄准都只是客户端预测/表现；服务端仍按 hitscan 结算，且没有瞄准散布。
  要做“腰射散、瞄准不散”必须让服务端知道瞄准状态并改 `fire()`，同时 bump 协议。
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
- `runClient` 运行期间不要跑 Gradle：会重写 `build/classes` 并触发 `NoClassDefFoundError`。

## Verify

- [ ] `./gradlew compileJava --offline` 通过
- [ ] 三种炮塔分别能进入 / 瞄准 / 开火 / 左 Shift 退出，退出后玩家视角正常
- [ ] 落点括号位置与实际命中一致（方块与实体各试一次）
- [ ] 深海无光环境下粒子束仍可见，且不从镜头中心射出
- [ ] 连发 0.6 秒冷却下两发弹道同时在飞，第一发尾迹不被覆盖
- [ ] 水下能看到白蓝能量核心 + 气泡尾；空气/水下命中、破口、舷窗、海床分支可肉眼区分
- [ ] 右键瞄准：FOV 缩到 55%，准心收拢；跟随炮塔的鼠标十字 alpha 降到 35%，松开恢复
- [ ] 瞄准时 HUD 落点括号仍与实际命中一致（缩放后投影不能错位）

## Debug

- 准心抽搐：检查跟随/粒子推进是不是被放到了 `ClientTickEvent`，或 FOV 用了 `effectiveFov` 之类每帧变值。
- 弹道偏移：确认三处（服务端、预判、粒子）都用 `muzzlePosition()`，且用的是同一组 `aimYaw/aimPitch`。
- 粒子看不见：先查 `assets/barotrauma/particles/turret_tracer.json` 与贴图路径，再看
  `ClientSetup.onRegisterParticleProviders` 是否注册了 `registerSpriteSet`。