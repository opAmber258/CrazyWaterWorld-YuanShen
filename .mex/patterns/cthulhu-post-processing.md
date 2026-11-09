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

八个预设共用**一套**后处理链：`assets/barotrauma/shaders/post/cthulhu_gaze.json`（main → swap 跑 program，再用 `minecraft:blit` 回 main）+ `program/cthulhu_gaze.json` + `cthulhu_gaze.fsh`。着色器用 `Mode`（0–7）分支、`Strength`（0–1）控制强度，所以加预设不需要新的 post chain。几何类预设（平面凝视、万花筒）另走投影矩阵/核心顶点着色器，见下文。

服务端只挂 MobEffect：`CthulhuGazePacket(preset, tier, seconds)` 校验 `创造模式 || 权限 ≥ 2`，先移除其它注视效果再 `addEffect(durationSeconds * 20, tier, false, false)`。amplifier 0/1/2 是弱/中/强（`CthulhuPreset.tierStrength` = 0.35 / 0.65 / 1.0）；平面凝视额外允许 amplifier 3「满」，走 `tierStrength` 的 default 分支（同样是 1.0，但投影混合量由 `projectionBlend()` 单独处理成完全正交）。

客户端 `CthulhuGazeClient` 每 tick 从玩家身上找激活的预设，算目标强度，按 0.05/tick 淡入、0.028/tick 淡出；效果结束后仍继续淡出，所以 `activePreset` 要保留到强度归零再清。换算成时间：满档（目标 1.0）约 1.0s 进 / 1.8s 出，中档（0.65）约 0.7s / 1.2s——写文档别只写一个数。镜头侧：`ViewportEvent.ComputeFov` 乘 `fovFactor` 与 1.2% 呼吸脉动，`ComputeCameraAngles` 加 `1.8° × rollFactor × strength` 滚转。

面板 `CthulhuGazeScreen`（440×272 基准，等比例 `uiScale`）只负责选预设/档位/时长，一个触发器物品搞定全部组合，**不要**给每个档位造物品。每行按钮数由 `CthulhuPreset.maxTier() + 1` 决定：默认三档（弱/中/强），平面凝视多一个「满」档（amplifier 3），`tierX(tier, count)` 按行内档位数右对齐排布。

### 投影类预设（平面凝视）

后处理只能在成品画面上做像素位移，做不出真正的正交（几何视差、遮挡、深度都不会变）。「平面凝视」（`cthulhu_flat`，Mode 6）因此改的是**投影矩阵**：`mixin/CameraProjectionMixin` 注入 `GameRenderer#getProjectionMatrix(double)`，在返回值上与 `Matrix4f.setOrtho(...)` 的结果按混合量做 `lerp`，矩阵每帧重建，混合量归零就自然回到透视。

混合量来自 `CthulhuGazeClient#projectionBlend()`：三档 `strength × (FLAT_BASE 0.28 + FLAT_WOBBLE 0.09 × 双正弦)`，即只压到透视与正交之间偏透视的一侧，并在 0.19–0.37（满强度）的小范围里缓慢晃动；**第四档「满」（amplifier 3，仅平面凝视有）直接返回 `strength`，即完全正交、不再晃动**，强度只随淡入淡出走。淡出时混合量随 strength 一起归零。正交半高按「玩家前方 12 格的视野高度」算，压平过程中画面中心比例基本不变。

投影类预设的代价：雾/天空/云按视空间距离计算，正交下观感会变；第一人称手臂共用世界投影会一起变形；视锥剔除虽然数学上仍成立，但近远平面必须显式给对（near 0.05、far 用 `GameRenderer#getDepthFar()`）。新增投影类效果前先在实机里确认这四项，再调混合量。

### 几何类预设（万花筒）

「万花筒」（`cthulhu_kaleido`，Mode 7）也是真几何：覆盖 13 个原版核心顶点着色器（`assets/minecraft/shaders/core/*.vsh`，地形 4 个 + 实体 7 个 + 粒子 + 掉落物），在视空间对顶点做形变。文件由 `tools/gen_kaleido_shaders.py` 从原版客户端 jar 提取并注入，**不要手改生成结果**；重新生成：

```bash
python tools/gen_kaleido_shaders.py "<gradle 缓存里的 downloadClient/client.jar>"
```

uniform `Kaleido` 不在原版 shader JSON 的 uniforms 列表里，所以 `ShaderUniformMixin` 在每次 `ShaderInstance#apply()` 末尾按 program id 缓存 `glGetUniformLocation`，直接用 `GL20.glUniform1f` 上传；**值为 0 也上传**，否则效果结束后会残留上一次的折叠量。没被覆盖的 shader 查不到这个 uniform，location = -1 自动跳过。

形变函数是**连续**的：径向正弦折返（`r + A*sin(2πr/P)`，A 随强度增大，超过 `P/2π` 后映射非单调，远处几何会折回来重复）+ 六瓣角向调制（`sin(6θ)`）+ 轻微深度压缩。**不要用交替扇区镜像**：镜像本身若不随强度衰减，最弱档也会是完整六瓣，几何被撕开、画面大面积黑洞，完全不可玩（2026-11-09 实机确认）。强度曲线：`strength × 0.5 × 呼吸`，三档等效约 0.17 / 0.33 / 0.49。

几何类预设的边界：视锥剔除仍按未形变坐标做，只有正前方的区块会被画进来（不会出现背后的几何折进画面）；雾和法线用形变前的位置计算；天空/GUI/文字不走这些 shader，不会被折；其它模组自己的自定义 RenderType/着色器也不会被折。

## Steps

1. 新预设：在 `registry/ModEffects` 注册一个 `MobEffect(MobEffectCategory.HARMFUL, color)`（纯视觉，不 tick），在 `CthulhuPreset` 枚举里加 id / effect / mode / fovFactor / rollFactor / 显示名 / 描述。
2. 着色器：在 `cthulhu_gaze.fsh` 里加分支。**最后一档（目前是万花筒）是无条件 `else`（Mode ≥ 6.5 全落进去）**，新增模式前先把它改成 `else if (Mode < 7.5)`，再把新分支追加在后面；否则新 Mode 会被最后一档截走。所有位移量都乘 `s`（Strength），保证弱档不糊、强档明显。
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
- **覆盖核心 shader 的两条硬约束**：文件必须是纯 ASCII（注释也别写中文，MC 的 GLSL 预处理器/驱动对非 ASCII 不保证兼容）；`Kaleido <= 0.001` 时必须直接 `return view`，保证未触发效果时与原版逐字节等价，否则会影响所有模组的世界渲染。

## Verify

- [ ] `./gradlew build --offline` 通过
- [ ] `/effect give` 三档都能看到强度差异，且退出后约 1.2s 内平滑消失
- [ ] 背包/效果栏显示 18×18 图标（不是紫黑缺省框）
- [ ] 与倒地模糊同时存在时，退出注视不会把模糊一起关掉
- [ ] 平面凝视：三档能看出压平程度差异，退出后投影平滑回弹，天空/雾/手部无明显破面
- [ ] 万花筒：弱档可正常行动，强档能看到径向折叠/重复，折痕处无裂缝；退出后世界完全恢复原状
- [ ] 日志无 `[cthulhu] 无法访问 PostChain.passes`

## Debug

效果完全不动：先看日志有没有反射警告，再确认 `Strength` uniform 是否非 0（可在 fsh 里临时 `fragColor = vec4(s, s, s, 1.0)` 验证）。画面只在边缘异常：检查是否被 `barrel()` 的越界分支涂黑。撕裂不明显：确认 `InSize` 是屏幕像素尺寸、band 用的是 `uv.y * InSize.y`。
