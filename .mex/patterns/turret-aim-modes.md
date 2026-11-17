---
name: turret-aim-modes
description: 炮塔原型的瞄准模式与弹道实现：三种方块共用 BE、mixin 接管输入与相机、以炮口为起点的 hitscan/落点预判、细粒子曳光与 HUD 括号。
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
last_updated: 2026-11-17
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
   - `TurretInteractionMixin` 拦 `startUseItem` / `startAttack`。
2. **每帧推进的逻辑放 `ViewportEvent.ComputeCameraAngles`**（`GameRenderer.renderLevel` 每帧一次）：
   自由瞄准的限速跟随 `stepFreeAim`、跟随模式的 `stepTracking`、粒子束 `tickBeam` 都在这里。
   `ClientTickEvent` 只有 20 Hz，放那儿会“一卡一卡”。
3. 弹道起点统一用 `TurretBlockEntity.muzzlePosition()`：`PIVOT_Y = 0.75` + `MUZZLE_LENGTH = 1.14`。
   服务端 `fire()`、客户端 `predictedImpact()`、粒子束三处共用，别各写一份。
4. 落点预判返回 `ImpactPrediction(point, halfWidth, halfHeight)`：实体取包围盒，方块按 1×1；
   `TurretHud` 按透视把半宽/半高投成像素，竖边限制 3…7 px、横边至少 3× 竖边、上限 34 px。
5. 曳光用自定义粒子 `ModParticles.TURRET_TRACER` + `TurretTracerParticle`，逐帧按
   `BEAM_SPEED * dt` 推进头部、沿新段落按 `BEAM_DOT_SPACING` 撒点；不要给初速和抖动。

## Gotchas

- 炮管伸出方块 1.14 格且要随 yaw/pitch 旋转，**不能**放进方块模型（超出 0…16 会被裁切），只能走 BER。
- 相机眼位 `EYE_HEIGHT`（0.5+0.55=1.05）比炮口高 0.3 格；从相机发射粒子会糊在镜头前挡视线。
- 原版没有又细又不带火焰的粒子：`END_ROD` 光晕偏粗，`CRIT` 受重力会下垂，所以用自定义粒子把
  `quadSize` 压到 0.03，并覆写 `getLightColor` 返回满亮度。
- 新增网络包必须 bump `ModNetwork.PROTOCOL`；合并上游时该行与包注册块最容易冲突，两侧都要保留。
- 跟随炮塔退出时不能 snap 玩家视角（`deactivate` 里只有 `snapPlayerRotation && !tracking`）。
- HUD 层级靠绘制顺序：拖动光标先画，炮塔准心、落点括号后画（后画的在上）。
- 自由瞄准光标按屏幕像素换算角度（用 `options.fov()` 的基础 FOV），套原版角度灵敏度会在宽屏上快 2~3 倍。
- 客户端粒子只有本机可见；要让其他玩家看到弹道，需要新增服务端广播包。
- `runClient` 运行期间不要跑 Gradle：会重写 `build/classes` 并触发 `NoClassDefFoundError`。

## Verify

- [ ] `./gradlew compileJava --offline` 通过
- [ ] 三种炮塔分别能进入 / 瞄准 / 开火 / 左 Shift 退出，退出后玩家视角正常
- [ ] 落点括号位置与实际命中一致（方块与实体各试一次）
- [ ] 深海无光环境下粒子束仍可见，且不从镜头中心射出

## Debug

- 准心抽搐：检查跟随/粒子推进是不是被放到了 `ClientTickEvent`，或 FOV 用了 `effectiveFov` 之类每帧变值。
- 弹道偏移：确认三处（服务端、预判、粒子）都用 `muzzlePosition()`，且用的是同一组 `aimYaw/aimPitch`。
- 粒子看不见：先查 `assets/barotrauma/particles/turret_tracer.json` 与贴图路径，再看
  `ClientSetup.onRegisterParticleProviders` 是否注册了 `registerSpriteSet`。