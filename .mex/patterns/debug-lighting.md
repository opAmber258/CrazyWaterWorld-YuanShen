---
name: debug-lighting
description: Deep-sea lighting panel, flashing/vanilla mode, ocean fog toggle, night vision, flashlight rendering, fixture tint, and loading stalls.
triggers:
  - deep sea brightness panel or lighting mode (mod / vanilla) behaves wrong
  - ocean fog or night vision button in the brightness controller
  - hand flashlight lags or world stalls after raising its update rate
  - light beam penetrates a cabin wall or fails to illuminate a VS2 ship
  - fixture color is stale after changing or loading
edges:
  - target: context/architecture.md
    condition: when checking what the client and server are responsible for
  - target: patterns/debug-ship-water.md
    condition: when the beam or its water tint is missing in a flooded room
last_updated: 2026-11-26
---

# 手电筒与舱灯排查

## Context

- `FlashlightLightEvents.onPlayerTick` 和 `FlashlightItem.findEmitter` 决定手持或头戴发光、电池消耗和低电状态。实体数据通过 `PlayerMixin` 同步。玩家同时带多支时按主手、副手、头部顺序选第一支带电的灯。
- `FlashlightRenderer.onRenderLevel` 在水面之前取得场景颜色及深度。光束最远 12 格，8 格后逐渐收尾。第三人称和其他持灯玩家使用绘制实体之前的区块深度算光路，避免玩家本体形成假遮挡。实心船壳的区块深度仍可遮挡。只由方块实体渲染的模型不在这一阶段的阴影采样内。
- 主画面和手电副本保留 16 位颜色，`flashlight.fsh` 按同一倍率提亮场景 RGB，保留暗色贴图里的细微色差。滑动视角时，单次深度命中只会形成弱阴影。正常电量恒亮，最后一格以 0.1 Hz 平滑起伏。深海的 2.6 格近身微光只为本机正在控制的活角色绘制。近身增益只随距离变，逐像素随亮度急降的增益会压平高光并翻转暗色纹理的明暗。
- 电磁枪默认有跟随炮轴的方向灯，原作 `coilgun.xml` 的色值为 1/0.8/0.8、标称范围 2500 显示单位。灯在开火储能不足时照常工作。最多同时选两盏并优先保留当前操作的炮灯。每灯用 17×17 的世界射线深度记录船和地形，即使挡墙在相机画面外也参与遮挡。场景深度只用来定位接收光的表面。进潜望镜后舱外相机旁没有玩家近身微光。
- 炮灯掠过船壳时，邻格的命中深度差很大也可能在同一个平面。`coilgunVisibility` 先算受光面在每条采样射线上的深度，再做遮挡比较，最后按权重合成可见度。用同一斜面检查自阴影，并用斜面后方和开口检查漏光。[离屏复现](../../PLAN.md#2026-11-26-炮灯沿船壳出现放射状暗条)
- `LightTextureMixin` 仅在深海忽略亮度滑杆。相机位于船舱时使用 0.074 的中性光图混合值（名义值约 14/255），手电合成保留这部分材质亮度。相机在舱外时仍扣掉暗材质占位值。舱内亮度按相机所在环境统一应用，避免用水盒在不同设备和舱壁的表面画出蓝黑边线。
- 舱口或舱壁门在普通光下局部发红时，检查 `BlockLightSampler` 是否用 `LevelRenderer.getLightColor` 取样并保留 Colorful Lighting 的 RGB 光图标记。把带彩色标记的整数和原版光照值直接拆包取最大会产生假红光。
- `LightBlockEntity.serverTick` 根据电网决定固定舱灯的方块光照等级。`LightClientColors` 为可选的 Colorful Lighting 提供每盏灯的颜色，跳过默认或重复值，单个客户端刻最多通知 32 个已变色的位置。
- 深海亮度设置统一放在 `DeepBrightnessDebug`（客户端会话内状态）：模式 `MOD` / `VANILLA`、模组环境底光 0..100%、原版环境底光 0.00..0.20、原版模式是否还原雾与水体。F7（`VanillaLightingClient`，切换时打 `[light] 原版光照切换`）只做模式快捷切换；右键 `brightness_debugger` 打开 `BrightnessDebuggerScreen` 编辑全部参数，改动立即生效、不走服务端。面板还并入原 Ctrl+右键的“关闭海雾”开关，以及无限夜视按钮（`NightVisionPacket`，协议 40，服务端只认创造/权限 ≥ 2；已有无限夜视时再按移除）。Shift/Ctrl + 右键仍可直接切海雾（`BrightnessFogShortcut`，在方块/设备交互之前截住点击），与面板共用同一份状态。物品图标是 16×16 的 `textures/item/brightness_debugger.png`，由 `tools/gen_item_icons.py` 生成。
- `VANILLA` 模式打开后 `LightTextureMixin` 的三处改动（0.04 环境底光、材质底色、gamma 固定 0）全部放行原版值，并把 `getBrightness` 读到的环境底光换成 `vanillaAmbient`（默认 0.1，PR #83 把维度 JSON 从 0.1 改成了 0.0；不补这一项，无方块光的舱室在“原版模式”下仍然是纯黑），`FlashlightRenderer` 在深海整体跳过——否则它的 `RemoveDarkFloor` 会把刚恢复的原版底光再扣掉。只影响深海维度；该模式下深海不绘制手电光束/近身微光，但电池与服务端状态照常。排查“画面问题是不是模组光照引起”时先按 F7 对照。
- `VANILLA` 模式且“还原雾与水体”打开时，还跳过 `UnderwaterEffects` 的黑色雾色、20/72 格雾距和 `AFTER_SKY` 清黑，以及 `ShipWaterRenderer` 在深海的整段水体绘制：水面、水下蓝黑滤镜和 `ship_water` 吸收都不画，天空/雾/背景走原版。流体场与物理不受影响，只是看不到模组水面；关掉开关或切回 `MOD` 后下一帧恢复。

## Debug

1. 对照日志里的登录、区块水盒和 VS2 物理帧队列。11 月 23 日归档在玩家登录后一直没有接收水盒，并开始积压物理帧。缺少线程栈时，单靠这两个信号不能确认阻塞调用。
2. 查 `[light]` 日志、持灯者是否有电、当前场景是否使用可拷贝的深度缓冲。手持灯随相机帧更新，旧存档中的 `flashlight_light` 只在其区块已加载时清理一次。
3. 在干舱和进水舱分别转动视角，检查同一面墙的光斑、近处被门框挡住的区域、门打开后的通光和水下小范围散射。用普通方块船和装配后的 VS2 船各看一次。
4. 确认固定灯在更改颜色、熄灭和重新进入世界后仍能恢复设置。对照 `Powered` 状态、灯的实际方块亮度以及本世界的彩色光传播日志。
5. 在无灯的干舱、灌水舱和舱外，从最低亮度调到最高。舱内远离贴身微光时墙角和设备仅有极低的中性亮度。舱外无光场景仍黑。用固定灯和手电分别检查材质色相及开门时是否有边界痕迹。
6. 怀疑画面偏暗/漏光时按 F7 切原版光照对照——这只缩小范围，不等于排除光图：先确认亮度滑杆没有被拉到最低、面板里的“原版环境底光”仍是默认 0.10；若原版模式仍偏暗，再查水体/雾/粒子。
