# 引擎 Engine · R15 泵动节奏修订

制作来源：人工 + AI（用户手调 R14 工程与材质；AI/Codex 按用户要求调整运作动画节奏，并重出动画与动态预览）。使用 Blockbench 5.2.2 原生导出。mod 标识 `engine_6block`，几何 identifier `geometry.engine_6block`。

## 原作查证

- 中文名：`Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `<entityname.engine>` = 引擎。
- 英文名：`Content/Texts/English/EnglishVanilla.xml` 的 `<entityname.engine>` = Engine。
- 定义：普通版《Barotrauma》`Content/Items/Engine/engine.xml`；使用 `Engines.png` 的 `0,0,659,317` 裁切，`scale="0.5"`。

## 尺寸依据

按用户手调工程实测，静止外廓为 97.90 × 48.20 × 35.62 Blockbench 单位（长、高、深），每格 16 单位，约 6.12 × 3.01 × 2.23 格。推进端朝 `-X`，仪表面朝 `+Z`。本轮未重新缩放，尺寸与 R14 一致。

## 播放与改动

- 在 Blockbench 的 Animate 模式播放 `animation.engine_6block.running`。泵动每 1.2 秒一拍。上壳、顶线和接头的动作幅度约缩小四分之一。三联百叶保持固定。
- 内外扇叶维持每 0.8 秒各转一圈。整段动画每 2.4 秒无缝循环，正好容纳两拍泵动和三圈扇叶。独立的 `animation.engine_6block.rotor_spin` 动画与 R14 相同。
- 工程几何、轴心、层级、UV、贴图像素和静止预览均与 R14 一致。保留原作识别特征：三联百叶、右侧仪表组、开放八边形推进罩、红色底座与舱侧线缆。185 个立方体、39 个骨骼、512 × 512 RGBA 贴图。

## 文件

- `engine_6block.bbmodel`：Blockbench 5.2.2 可编辑 Bedrock Entity 工程，内嵌贴图与两条动画。
- `engine_6block.geo.json`：原生 Bedrock 1.12.0 几何，`geometry.engine_6block`。
- `engine_6block.animation.json`：原生 Bedrock 1.8.0 动画，`animation.engine_6block.running` 与 `animation.engine_6block.rotor_spin` 两条 clip。
- `engine_6block_tex.png`：512 × 512 RGBA 像素贴图，2 的幂，透明像素 alpha 为 0。
- `preview_{front,back,side,oblique}.png`：1200 × 900；前三者正交、斜视透视，纯色底、无网格无 UI。
- `preview_{guard_joint,tail_joint,tail_oblique}.png`：1200 × 900 局部与背侧补充视图。
- `preview_running.gif`：1282 × 540、48 帧双机位动态预览（整机斜视 + 屋顶线缆俯斜视）。
- `structure_audit.json`、`validation.json`：结构审计与交付验证记录（含文件 sha256）。
- `reference/`：R14 快照 `user_baseline.bbmodel`、动画重定时/导出/预览脚本、共面审计脚本。

## 验证与复现

`engine_6block.animation.json` 和 `engine_6block.geo.json` 通过 Blockbench 原生重导入，动画骨骼均存在于几何中，关键帧时间不超出片段长度。抽检 129 个时间点，百叶位置不变，两根线缆的鼓包依次到达护罩，28 处接合未检出分离；静止的 1098 个可见声明面未检出有面积的共面重叠。Minecraft 内尚未实测；本轮不改 `src/main/resources`。

`reference/user_baseline.bbmodel` 是 R14 的完整快照。运行 `python -B reference/retime_pump.py --adapter <Forge3D目录>/scripts/forge3d/bb/app.py` 可重建工程、动画、几何、贴图与动态预览。

**预览生成方式**：静态预览与 GIF 均由 Blockbench 5.2.2 自身的 WebGL 视口渲染（`reference/blockbench_delivery.py` 中的 `Preview` 脚本、`reference/preview_running.js`），不是另写的渲染器；Pillow 仅做 GIF 编码。这些脚本通过 Forge3D 的 Blockbench 适配器（`scripts/forge3d/bb/app.py`）驱动，该适配器与本机 Blockbench 会话不在仓库内，因此 `reference/` 脚本在仓库内不能直接重跑，保留用于说明视角、灯光与编码参数。
