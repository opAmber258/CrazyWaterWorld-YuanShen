# 加工台 Fabricator · 候选 B

**制作来源：人工 + AI（TakaraMiyuki + GPT-6 / Codex + ZCode Agent，Blockbench 5.2.2）。** Codex 制作初始几何和像素材质；TakaraMiyuki 手动调整最终工程和贴图；ZCode Agent 在人工保存的工程上把整体 X 偏移归零并重出导出、预览与验证记录。2026-11-16 修订版直接从人工保存的工程导出，未改动任何方块尺寸、UV 或像素。工程与外置 PNG 已分别比对 SHA-256，工程内嵌纹理与外置 PNG 逐字节一致。

![手调版斜视](preview_oblique.png)

## 造型与比例

原作名称来自 Barotrauma 安装目录的 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 和 `Content/Texts/English/EnglishVanilla.xml`，两处条目均为 `entityname.fabricator`。设备定义在 `Content/Items/Fabricators/fabricators.xml`，标识为 `fabricator`，绘制比例为 0.5。完好状态主精灵取自同目录 `FabricatorsAndDeconstructors.png` 的 `0,624,336,400` 区域，机头另有装饰层。原作裁切及组合参考图位于 `reference/`。

以原图宽高比和约 2.5 格的目标宽度起稿，高度约为 2.5 × 400 / 336 ≈ 2.98 格。手调后将整体缩至 2 格宽，高度按同一比值取 2.228 格。外轮廓含机头两侧的检修盖。实际宽、高、深为 **1.99875 × 2.228125 × 1.226062 格**（16 BB 单位/格，包围盒 X −15.6…16.38、Y 0…35.65、Z −10.725…8.892）。正面朝 +Z，底面 Y=0，柜体、台面与平台三组的 X 中心在 0；机头与接线骨按原作造型偏一侧，因此整体包围盒中心在 X +0.39。机头轮系、夹头和带夹具的台面分别建模，台面由斜撑和立柱承托，双门柜向内退进。进深和侧背检修结构为立体化设计。本候选和 2026-11-14 的候选 A 各自使用独立目录。

### 与上一版（2026-11-15 首次发布）的差异

- **尺寸改为 2 格宽**：上一版 2.5625 × 2.96875 × 1.571875 格；作者手改时整体等比缩至 0.78 倍，宽 41 → 31.98 BB，落在整 2 格。
- **X 归零**：手改稿缩放后整体沿 X 偏了 +7 BB（0.4375 格），本轮对全部方块与骨骼 pivot 施加 −7 BB，柜体组中心回到 0，与 `ballast_pump`、`hatch` 等现有资产的居中约定一致。相对关系不变。
- 方块、骨骼、UV 与贴图像素均未改动：151 cubes / 9 bones / 906 面对应不变，贴图 SHA-256 与手改稿相同。

## 打开与复核

- `fabricator.bbmodel` 是可编辑工程；`fabricator.geo.json` 为 Bedrock 1.12.0 原生几何，标识为 `geometry.fabricator`。151 个方块、9 个骨骼、906 个面。
- `fabricator_tex.png` 为 512 × 512 RGBA。工程和几何共同采用 256 × 256 的逻辑 UV 空间，每一逻辑单位覆盖 2 × 2 个实际像素。结构预检的唯一警告 `texture_export_size_mismatch` 对应这一设置。逐面核对了 UV 端点、纹理引用及映射区域的像素；结果见 `validation.json` 和 `structure_audit.json`。
- `preview_front.png`、`preview_back.png`、`preview_side.png` 为正交视图，`preview_oblique.png` 为透视视图。四张图均为 1200 × 900 透明底，来自当前工程的 Blockbench 场景。
- 可在副本中使用独立用户目录启动 Blockbench 5.2.2 并指定本机调试端口，例如 19479。安装 Python 的 `websocket-client` 后运行 `python reference/blockbench_views.py --port 19479`，可从工程重出几何、四视图和 `reference/native_check.json`。该脚本会更新这些导出文件。

已在 Blockbench 5.2.2 重新打开工程，用原生 `Codecs.bedrock.compile` 重出几何并与交付的 `fabricator.geo.json` 逐字段比对一致；四向预览从同一场景重出。未进入 Minecraft 验证。
