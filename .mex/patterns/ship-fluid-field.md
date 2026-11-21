# 船内逐格流体场（ShipFluidField）

## 何时用

- 改船内进水/排水、水面高度、舱室之间串水、破口进水。
- 排查“干舱被当海里”“舱壁/地板里出现水”“水泵 0%/100% 反复切”“货舱没水却能游泳”。
- 改 `ShipWaterStateBuilder` 生成的水盒（`ShipWaterBoxes`）或客户端水面渲染。

## 结构

- `ship/water/ShipFluidField.java`：逐格流体场。每格一个水量，按“水体（body）”分组：
  **密封舱室优先**，连通海水的空腔单独成 body；同一 body 内整舱水面归平。
- 门格：算舱内，但不直接透水；开门走 `DoorLink` 限速搬运，所以门两侧不会瞬间拉平。
- 破口格：按 tick 往所属 body 注入海水（速率在 `ShipWater`/`ShipSimulation` 一侧调）。
- 水泵：只抽自己所在 body；自动模式用滞回阈值（`DrainPumpBlockEntity.autoWet`：
  ≥0.1 起抽、≤0.02 停），不要改回单阈值，否则会 0%/100% 抖。

## 水盒生成（客户端渲染 / 干湿判定共用）

`ShipWaterStateBuilder.build(...)`：

1. 密封舱室 → 拆矩形棱柱；连海空腔 → 1×1 列。
2. `appendSolidHoles` 跳过**整格方块**（`isCollisionShapeFullBlock`），否则水盒会画进墙/地板里，
   旁观者也能钻进墙里“游泳”。调用方必须传真实 level 的整格判定，物理船要先把船坞坐标
   `VsPilot.toYard/toAssembly` 换回来再查。
3. `appendMissingDryCoverage` 给“属于舱内但没有任何水盒覆盖”的格子补一个干燥 bbox：
   干舱（货舱等）没有水盒时，客户端才不会把它当海里。
4. 最后才是粗包围盒兜底。

## 客户端/服务端分工

- 服务端权威：水量、进水、泵、`VsPilot.isPlayerInDryCompartment` / `isInsideInterior`。
- 客户端只用 `ShipWaterStatePacket` 下发的盒子做渲染和浸没查询
  （`ShipWaterRenderer`、`VsClientCompartments`、`UnderwaterEffects`）。
- 物理船的方块坐标是船坞坐标：任何“世界里的位置”都要换算，否则镜头/粒子会跑到
  x≈-2800 万的船坞虚空里。

## 渲染注意

- 薄水层（贴地 1/32 格）的波浪幅度要同时受**天花板净空**和**水深**限制，
  否则波谷穿到地板下面，看起来一会干一会湿。
- 水面必须逐帧连续（10 Hz 包 + 插值），不要退回按 tick 台阶更新。

## 验证

- `ShipFluidFieldTest`、`ShipWaterBoxesTest`、`ShipWaterNbtTest`、
  `ShipWaterStateBuilderTest`、`ShipFluidDoorTest`、`ShipPocketAirTest`。
- 改这块至少跑 `./gradlew test --offline`（纯 L1，不需要客户端）。
- 实机复测点：舱壁内不能游泳、货舱保持干、泵不抖、贴地水面不闪、破口进水有下压气泡。
