---
name: cthulhu-post-processing
description: 「克苏鲁的注视」系列视觉预设：一套后处理 + MobEffect 档位 + 淡入淡出的接入方式，以及生产环境反射坑。
triggers:
  - "克苏鲁"
  - "cthulhu"
  - "注视"
  - "shader"
  - "后处理"
  - "post chain"
  - "鱼眼"
  - "画面撕裂"
edges:
  - target: patterns/developer-tools.md
    condition: when adding the trigger item / creative-tab entry for a new preset
  - target: context/conventions.md
    condition: when the visual effect touches networking or client-only code
grounds_to: []
last_updated: 2026-11-09
mex:
  id: mx_01M6YVNY85SVEBSM22WCKKFSNJ
  type: pattern
  status: promoted
  revision: 3
  title: cthulhu-post-processing
  relations:
    - type: related_to
      target: mx_01M6YVNYH6918E8JA3Z0MHSHW9
      note: when adding the trigger item / creative-tab entry for a new preset
    - type: related_to
      target: mx_01M6FTBTB77P4PT8TV3HT78NGZ
      note: when the visual effect touches networking or client-only code
---

# 克苏鲁的注视（后处理）

## Context

六个预设共用**一套**后处理链：`assets/barotrauma/shaders/post/cthulhu_gaze.json`（main → swap 跑 program，再用 `minecraft:blit` 回 main）+ `program/cthulhu_gaze.json` + `cthulhu_gaze.fsh`。着色器用 `Mode`（0–5）分支、`Strength`（0–1）控制强度，所以加预设不需要新的 post chain。

服务端只挂 MobEffect：`CthulhuGazePacket(preset, tier, seconds)` 校验 `创造模式 || 权限 ≥ 2`，先移除其它注视效果再 `addEffect(durationSeconds * 20, tier, false, false)`。amplifier 0/1/2 就是弱/中/强（`CthulhuPreset.tierStrength` = 0.35 / 0.65 / 1.0）。

客户端 `CthulhuGazeClient` 每 tick 从玩家身上找激活的预设，算目标强度，按 0.05/tick 淡入、0.028/tick 淡出；效果结束后仍继续淡出，所以 `activePreset` 要保留到强度归零再清。换算成时间：满档（目标 1.0）约 1.0s 进 / 1.8s 出，中档（0.65）约 0.7s / 1.2s——写文档别只写一个数。镜头侧：`ViewportEvent.ComputeFov` 乘 `fovFactor` 与 1.2% 呼吸脉动，`ComputeCameraAngles` 加 `1.8° × rollFactor × strength` 滚转。

面板 `CthulhuGazeScreen`（440×300 基准，等比例 `uiScale`）只负责选预设/档位/时长，一个触发器物品搞定全部组合，**不要**给每个档位造物品。

## Steps

1. 新预设：在 `registry/ModEffects` 注册一个 `MobEffect(MobEffectCategory.HARMFUL, color)`（纯视觉，不 tick），在 `CthulhuPreset` 枚举里加 id / effect / mode / fovFactor / rollFactor / 显示名 / 描述。
2. 着色器：在 `cthulhu_gaze.fsh` 里加分支。**最后一档梦境目前是无条件 `else`（Mode ≥ 4.5 全落进去）**，新增模式前先把它改成 `else if (Mode < 5.5)`，再把新分支追加在后面；否则新 Mode 会被梦境截走。所有位移量都乘 `s`（Strength），保证弱档不糊、强档明显。
3. 图标：`assets/barotrauma/textures/mob_effect/<effect_id>.png`，18×18；`mob_effects` 图集目录源已覆盖所有命名空间，不需要额外 atlas 配置。面板里用 `Minecraft.getInstance().getMobEffectTextures().get(effect)` 取图标。
4. lang：效果名称用 `effect.barotrauma.<id>`（1.20.1 的 `MobEffect` 名称键前缀是 `effect.`，不是 `mob_effect.`），描述用 `effect.barotrauma.<id>.desc`；zh_cn + en_us 都要。
5. 实机：`/effect give @s barotrauma:cthulhu_<id> 10 <0|1|2>` 可直接验证，不必开面板。

## Gotchas

- **生产环境 `PostChain.passes` 是 SRG 名 `f_110009_`**：必须 `ObfuscationReflectionHelper.findField(PostChain.class, "f_110009_")`；开发环境 `getDeclaredField("passes")` 能跑，正式包会失败，异常被吞后 `Strength` 恒 0——表现为"效果完全不出现"。反射失败只警告一次，看到 `[cthulhu] 无法访问 PostChain.passes` 就是这个坑。
- **只关自己的 PostChain**：卸载时判断 `gameRenderer.currentEffect() == ourChain`（或为 null）再 `shutdownEffect()`，否则会把倒地模糊等其它后处理一起关掉。
- **不要在别的效果占用时抢链**：当前 effect 非空且不是我们的时候不要 `loadEffect`，等它结束。
- **GLSL 不要写反向 `smoothstep(edge0 > edge1)`**：结果未定义；要"边缘 1 中心 0"用 `1.0 - smoothstep(lo, hi, x)`。这一条在泛光/边缘压暗里反复踩。
- **撕裂要做成真的位移**：只加噪点看起来"没有撕裂"。静电模式用 `band = floor(uv.y * InSize.y / 3.0)`（3 像素细行带）、`tick = floor(Time * 16.0)`（1/16 秒重掷）、`step(0.97, hash(...))`（约 3% 概率）位移 ±2%，少数 ±8%；单带持续时间短、带宽细才像信号故障。
- **档位不要做成物品**：amplifier 已经编码强度；加物品会让创意栏爆炸、还得分叉同步。
- **面板必须等比例缩放**：`uiScale = min(1, min((width-8)/PANEL_W, (height-8)/PANEL_H))`，再用 `pose.translate/scale` 画、鼠标坐标 `(mouseX - originX) / uiScale` 换算；否则高 GUI scale 下底部按钮被裁掉（2026-11-08 实机截图确认）。
- 玩家身上的效果要 `ambient=false, visible=false`，否则会多出一个原版效果图标，和自定义图标重复。

## Verify

- [ ] `./gradlew build --offline` 通过
- [ ] `/effect give` 三档都能看到强度差异，且退出后约 1.2s 内平滑消失
- [ ] 背包/效果栏显示 18×18 图标（不是紫黑缺省框）
- [ ] 与倒地模糊同时存在时，退出注视不会把模糊一起关掉
- [ ] 日志无 `[cthulhu] 无法访问 PostChain.passes`

## Debug

效果完全不动：先看日志有没有反射警告，再确认 `Strength` uniform 是否非 0（可在 fsh 里临时 `fragColor = vec4(s, s, s, 1.0)` 验证）。画面只在边缘异常：检查是否被 `barrel()` 的越界分支涂黑。撕裂不明显：确认 `InSize` 是屏幕像素尺寸、band 用的是 `uv.y * InSize.y`。
