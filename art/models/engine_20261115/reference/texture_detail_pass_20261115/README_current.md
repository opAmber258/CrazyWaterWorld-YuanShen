# 引擎 Engine｜用户手改版表面纹理细化

**制作来源：人工 + AI（TakaraMiyuki + Codex / GPT-6）。** 人工完成当前模型微调、UV 重排和贴图手绘；本轮 AI 在底色区域增补检修板、接缝、通风和紧固件纹理，并刷新原生预览、导出与验证。

**完整保留当前 180 个 cube、18 个骨骼、全部 1080 面 UV 与三条动画。** 继续使用人工设置的 256×256 逻辑 UV / 512×512 实际 PNG。

已在 Blockbench 5.2.2 检查并完成导出，未进入 Minecraft 验证。

## 本轮变化

- 顶盖两块检修口，其中一块带窄通风条，保留原有分缝和线缆走向。
- 右端大检修盖、两处斜面小盖板，沿用人工调整后的斜面角度。
- 推进罩外壳分段检修板、口沿紧固件、侧面下部检查盖；罩内固定背板增加克制的维护分区。
- 底部两块维护板，以及前裙边、双活塞背板的少量接缝和螺钉。

共细化 **25 个面、2446 个实际纹素**。所有修改均限定在登记面的原主底色中，原有非底色笔触及周围保护区不覆盖，未登记区域逐像素保持原样。仪表、电控内容、百叶、叶片、活塞活动件、背部双检修门和用户已有细节保留；新细节只用既有调色板、硬边像素，不加入随机噪点或满表面磨损。

## 文件

| 文件 | 规格 |
| --- | --- |
| `engine.bbmodel` | Bedrock Entity，可编辑；完整内嵌 PNG 与 3 条动画 |
| `engine.geo.json` | 原生 Bedrock 1.12.0，`geometry.engine` |
| `engine.animation.json` | 原生 Bedrock 1.8.0 动画 |
| `engine_tex.png` | 512×512 RGBA；与内嵌 PNG 字节一致 |
| `preview_front/back/side/oblique.png` | 1200×900 透明底，无 UI / 网格；正、背、侧正交，斜视透视 |
| `preview_running.gif` | 整机组合循环，960×720、60 帧 / 2.4 秒 |
| `preview_rotor.gif` | 四叶转子，600×600、20 帧 / 0.8 秒 |
| `preview_pistons.gif` | 双活塞交替，600×600、30 帧 / 1.2 秒 |
| `validation.json` / `structure_audit.json` | 按当前手改输入重新核对的记录 |

右侧、顶视和底视检查图在 `reference/texture_detail_pass_20261115/preview_{right,top,bottom}.png`。全部图像帧均由 Blockbench 自身渲染并以 `Canvas.withoutGizmos` 去除辅助线，Pillow 只负责像素绘制与 GIF 编码。

## 用户基线与保护

本轮完整输入：`reference/texture_detail_pass_20261115/user_baseline.bbmodel`、`user_baseline_tex.png`。

- 工程 SHA-256：`91b20227ba0f237807695c62c316653d2ee51bf90bdde78b8acdff58edb792ea`
- 贴图 SHA-256：`8d9240d1fb008533cc11b4f7b96ccf2eee0fa25fa6b4fe3866a823800274fd57`

`surface_analysis.json` 记录 UV 到实际像素的 2 倍换算；`texture_detail_report.json` 按 UUID 与面登记改动，避免重复 cube 名误选。脚本只替换主纹理的 source，不修改任何非贴图字段；独立检查再次确认 180 个 cube、所有 UV、分组、骨骼和动画与本轮快照一致。

此前说明和检查保留为该目录的 `prior_README.md`、`prior_validation.json`、`prior_structure_audit.json`。更早的 `reference/user_baseline.bbmodel` 是上一轮 143-cube 输入，不是当前基线。

## 尺寸与动画

- 当前原生边界约 X **-43.3…42.2**、Y **0…44**、Z **-17.55…18.6**。
- 宽×高×深约 **85.5×44×36.15 BB**，即 **5.34375×2.75×2.259375 格**。
- 正面 +Z，推进罩朝 -X，Y=0 贴地；人工版 X/Z 偏移保留。
- 180 cubes / 18 bones / 1080 声明面；UV 保持人工布局。

| 动画 | 时长 | 内容 |
| --- | --- | --- |
| `animation.engine.rotor_spin` | 0.8 秒 | 四叶转子一圈 |
| `animation.engine.pistons` | 1.2 秒 | 双活塞总行程 3 BB、左右交替 |
| `animation.engine.running` | 2.4 秒 | 转子 3 圈与活塞 2 次完整循环 |

轨道、关键帧、表达式与骨骼绑定均保持原样。可播放组合动画或分别使用前两条，避免重复叠加同一运动。游戏中的控制器与启停绑定由集成者处理。

## 来源与推定

标准引擎标识 `engine`。简中文本的 `entityname.engine=引擎` 与英文文本的 `entityname.engine=Engine` 分别查证。原作 XML 主图 `Engines.png`，裁切 `0,0,659,317`，`Scale=0.5`；来源摘要和原图裁切保留在 `reference/source_report.json`、`reference/original_engine_crop.png`。

原始比例假设为人物 120 px 对应 32 BB，`K=0.5×32/120`，不是官方米制尺寸。当前尺寸以用户手动微调后的工程为准，本轮没有重新缩放。内部推进罩、背部维护布局、双活塞和新增检修板属美术推定，不宣称原作存在对应的具体三维机构。

## 验证

- 非贴图工程数据与用户快照一致，只有贴图 source 发生变化，其余纹理元数据保留。
- 修改没有超出登记区域，既有非底色笔触保持原样；内嵌 / 外置 PNG 一致，RGBA，alpha 只有 0 / 255。
- 所有 UV 按 256² 逻辑空间检查。512² PNG 是用户的高清贴图配置，其 2 倍关系明确保留。
- 最终工程重开后，几何与动画重新编译匹配交付文件；原生重新导入导出也匹配。
- 四视图、额外三向检查图和三条动图都按当前几何与纹理重出。

本轮结果见 `validation.json` / `structure_audit.json`。这些检查不等于 Minecraft 或 GeckoLib 客户端验证。

## 复现本轮

需要 Python / Pillow 和已启用 MCP 的 Blockbench 5.2.2，本机既有配置为 localhost:3005。脚本按自身位置解析资产路径；从资产目录执行：

```text
python reference/texture_detail_pass_20261115/detail_surfaces.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/texture_detail_pass_20261115/export_only.js
python reference/render_previews.py
python reference/render_motion.py
python reference/texture_detail_pass_20261115/render_extra_checks.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/native_roundtrip.js
python reference/texture_detail_pass_20261115/validate_texture_detail.py
```

本轮绘制脚本从冻结快照重现纹理，会覆盖本批工程与 PNG，后续人工编辑应先留新检查点。`refine_engine.py`、`validate_refinement.py` 和旧 `finalize_delivery.py` 只供前轮溯源，不是当前版本的再生成或验收入口，不要用它们覆盖这次手改版。

## 边界

本轮仅本地美术交付，没有改动上游、curated 分类或 Discussion。未运行 Minecraft；未验证运行时渲染、动画控制器、碰撞、推进、交互、暗环境与性能。没有运行时代码变化，Gradle / L1 / GameTest 不适用于本次纹理细化。
