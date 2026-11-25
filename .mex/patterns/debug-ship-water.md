---
name: debug-ship-water
description: Diagnose why a ship is dry, flooding incorrectly, or not showing water, using the scalar water simulation and water-state packet.
triggers:
  - "ship not flooding"
  - "no water visible"
  - "water level wrong"
  - "breach not filling"
  - "water diag"
edges:
  - target: context/architecture.md
    condition: when the water simulation's place in the ship loop is unclear
  - target: patterns/vs2-physical-ship.md
    condition: when the ship is a VS2 physical ship
grounds_to: []
last_updated: 2026-11-22
mex:
  id: mx_01M6FTV7HFY90PQMFBX26T2438
  type: pattern
  status: promoted
  revision: 3
  title: debug-ship-water
  relations:
    - type: related_to
      target: mx_01M6FTBTA32ESJGHB433FWZZZ3
      note: when the water simulation's place in the ship loop is unclear
    - type: related_to
      target: mx_01M6FTV7VPSPV6F87V82ZGWGX4
      note: when the ship is a VS2 physical ship
---

# Debug Ship Water

## Context

服务端 `ShipFluidField` 按舱内格维护水量（1 格 = 100 单位），同一水体归平。`ShipWaterStatePacket` 发送水盒（每盒 7 个 float：min/max + surface）、破口和实际过水产生的路径。`ShipWaterRenderer` 每帧插值水位。干盒的 surface 等于所在列的底部，也参与干区判定。

双层舱室先核对 `streams`。同一舱室的破口能发出从二层破口到甲板边缘再到下层水面的路径，落水由真实来水流量驱动。`ShipWaterStreams` 的每段应位于同一水体的实际开放格中，落点被下方水面或地板截断。详情见 [船内逐格流体场](ship-fluid-field.md)。

侧门打开后查 `sideFlows`。接收舱水面低时显示横向飞溅，水面淹没洞口后用定向气泡表现还在流动的水。关门或水头归平会停止发流。压载舱看着满时，先读实际逐格水量，`ballastNeutral` 是总目标，单泵要和自己所在舱的目标比较。旧版会先把一舱注满再注下一舱，合计水量仍可能刚好中性。

实船打开外门仍没有水时，核对门朝向、`DoorLink.seaSide`、实际水场容量和船坞里的 `OPEN` 状态。外海可能仍在整船包围盒内，纯外部空腔的容量为 0。存档快照和船坞状态也可能不同，应在船坞定位后验证实际门洞。

破口行有 9 个 float，包含中心位置、接水列在 `boxes` 里的绝对 float 偏移、朝内方向、舱上下界及开口类型。空气侧水花用 `ship_spray` 水滴，`ship_bubble` 从浸没的接收侧生成，离水退场。粒子按世界游戏刻生成并受数量上限约束。暂停期间没有发射刻，恢复时处理当前刻。物理船用装配变换决定粒子出生点和方向。船水面渲染开关是 `-Dbarotrauma.renderWater=false`。

外海背景、距离雾和舱内折射共享寒冷深穴的深蓝色带。镜头处于外海时，`ShipWaterMaterial` 按场景深度和水柱边界计算水程，人物视角在约 7.4–18.7 格融入雾色。炮台镜头使用远距观察范围。窗外背景和悬浮海雪由世界位置决定，气闸、哨站和干舱内近景使用常规场景颜色。海雪和舱门喷溅按客户端粒子级别限流。

“看不到水”时，核对实际水量、水面包和渲染开关。水面渲染默认开启。

## Steps

1. 服务端先查实际水量。客户端的 `[water]` 有包数据，开启 F9 后 `[water-diag]` 有 `wetColumns`、`triangles`、`flowTriangles`、`eyeInWater`、`eyeInSea` 和 `refracted`。
2. 如果 `waterUnits=0` 且 `surface=minY`，船是干的；用 `/barotrauma ship flood 1500` 或砸一格船壳制造破口。
3. 物理船找不到声纳核心时，`/barotrauma ship flood` 会回退到 `VsPilot.state()`；确认命令返回的是“物理船 1 号舱”而不是“没有船核心”。
4. 检查 `ShipWaterStateBuilder.build(...)` 的输出：`boxes.length` 应为 7 的倍数；`surface` 应随注水上升。有落差且开口实际过水时检查 `streams`。
5. 检查客户端是否收到包。用 `ShipWaterRenderer` 的 `packetCount` 和日志核对 ship id、physical 标志、水盒数量及水位。
6. 要查某片水面，确认 `surface > minY` 后看 F9 日志里的 `wetColumns` 和 `triangles`。有湿列但没画三角形时，检查相机视锥、船体矩阵和世界渲染阶段。
7. 破口没有水花/气泡：看客户端 `[water] 画水面` / `收到水面包` 日志里的 `breaches=N`。只有真正连到外海的洞才会被记录；内部隔墙的洞会被 `ShipState.addBreaches` 过滤掉。空气侧不再涨水时水花停止；灌满后水下气泡会持续冒，这是预期行为。

## Gotchas

- **第一次装配的船通常是干的**：没有破口、没有注水时 `surface == minY`，渲染器按“无水”跳过是正确的。
- `flood` 命令只加模拟水量；物理船不会写世界水方块，世界里看不到水块，水面由客户端自绘。
- 水面高度使用装配坐标；物理船移动后客户端要用 `VsState` 的偏移换算，偏移不同步会表现为水面错位。
- `ShipWater.applyWater()` 仍能给非物理船铺真实水方块；空气海域下不要用它来“验证”水面。
- 水面网格按水盒覆盖的格列生成。如果隔离舱涨水，先看服务端 `[vs] 船体破损：… → 重扫 N 个舱室 / M 处连海`。隔离舱应归在 N 个舱室里，且水量为 0。
- “隔墙顶上留孔”属于同一个连海空腔，水量在同一水体归平。跨气密门时查开口高度和两边水头；双层舱的开放路径查 `ShipWaterStreams`。
- **现在船壳有逐格耐久**：左键/爆炸只扣耐久；耐久 ≤90% 就开始小漏、≤30% 大漏，0% 时方块切成 `breached`（保留方块数据、无碰撞、不可见），几何扫描把它当空气，才进入连海空腔灌水。砸了几下没进水不是水系统坏了，先看 `HullDamage` 的剩余比例；详见 `patterns/debug-hull-damage.md`。
- **2026-11-22 水面接缝**：旧路径即使平均角点，顶面与侧面的边线仍有坐标偏移，邻接一部分的盒子也会裁掉整段边。新路径按一格边长求侧面的出水范围，上下相接的湿区间合并，侧面复用同一个水面边。实机仍见缝时，开 F9 核对 `refracted` 和矩阵，再用 `-Dbarotrauma.waterFix=false` 对照同一张网格的透明材质，便于定位材质采样或几何覆盖。
- **船水面需要水包**。当 `ship=-1 boxes=0` 时核对船有没有成功扫描。外海着色可以独立运行，由镜头水柱高度、哨站干区、船舱覆盖及碰撞决定。
- **停靠/未装配的方块船整船都是水下滤镜、`画水面 ship=-1 boxes=0`**：先看客户端有没有收到 `physical=false` 水包。方块船的水包由 `ShipCoreBlockEntity.serverTick` 发，那个 tick 被节流成每 2 tick 且**奇偶固定**；周期任务一律用“距上次的差值”判断，不能用 `gameTime % N == 0`（奇数会话会整局不发）。`ShipState` 又不存档，世界重开后要主动补扫，否则客户端一直没有干区。
- **世界重开后第一次进船没有干区/水盒**：`ShipCoreBlockEntity.serverTick` 在 `state == null` 时会自己 `rescan`；若仍为空，查 `[ship] 方块船船体破损，已重扫` 与核心是否在 VS2 船坞里（物理船直接跳过）。
- **干地板或舱口出现折射**时检查水包是否仍有湿格，再检查水界面与实心面沿法线的距离。薄膜或共面水界面停用折射，镜头在水里直接看到地面时按水程着色。稳定积水取开启门洞两侧共同的水深。
- **诊断日志**：`[water] 画水面`（每 5 秒，boxes/breaches/streams/sideFlows/delta）、`[water] 水盒快照`（每艘船第一包，前 12 盒坐标和水面）、`[hull-bar]`（每格血条首次渲染的数值）。
- **泳姿与浸没**记录为服务端 `[vs] 疾游=… 可游=… 疾跑=…`。舱内躯干入水之后还要检查站姿水深和净空。客户端在 `LocalPlayer.aiStep` 读取 `Options.keySprint.isDown()`。切换模式下松手保持疾游，再按一次回到直立划水。按住模式下松手回到直立划水。镜头浸没日志独立取相机位置。
- **舱门来水和贴地观感**先核对包中的 `streams` 和 `sideFlows`，再看 F9 的 `flowTriangles` 和 `refracted`。落水路径遇到连续舱壁时贴近该面。强流横向采样共享边点，沿进水方向连续落下。弱流可以只见水珠，淹没的侧流会冒气泡。
- **设备模型旁出现一道干湿分界**时检查这一层的相邻水盒。连接的非整格设备空隙共用本舱同层水线，模型真正在屏幕上的形状通过场景深度遮挡水体。
- **靠泊的船顶外出现干区**时，核对镜头位置和船水包占用格。`DockingBay.isDockCapRow` 只给实际站侧中层的 3×1 舱口封盖。通道下端须存在两层对接舱口。干区查询以船水盒和站侧通道为准，船顶和长方形边角仍可属于外海。
- 物理船的船壳方块在世界里是空气，不能用 `level.getBlockState(worldPos)` 判断破口/耐久；物理船走 `snapshot` + `VsPilot.breakSnapshotBlock`，耐久包 `ShipHullDamagePacket` 的坐标空间和 `ShipWaterStatePacket` 相同。

## Verify

- [ ] `flood` 后日志里的 `waterUnits` 增加
- [ ] 对应的 `surface` 从 `minY` 上升
- [ ] 水面包的 `boxes` 覆盖舱内列，双层来水包的 `streams` 接到下层水面
- [ ] 客户端 `packetCount` 增长且 ship id 匹配
- [ ] 明确区分“渲染关闭”和“数据错误”

## Debug

服务端问题看 `VsPilot` 的水量同步计时和 `ShipWaterStateBuilder`；客户端问题看 `ShipWaterRenderer.onRenderStage` 和 `ClientShip` 缓存；干湿判断问题看 `OceanEnvironment.isSubmerged` 与 `VsPilot.isInsideDryZone`。
