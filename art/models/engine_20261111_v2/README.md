# 引擎 Engine｜第二版手改增量优化候选（名义 4×2×3）

**制作来源：人工 + AI（说明分工）**

- 人工用户完成黑色风道、百叶组、底裙、风机旋转/位置，以及本轮新增的大型辅助设备舱和两块检修面板。
- AI 在本轮只重建 UV、材质和贴图，没有移动、缩放、旋转或回正任何手改 cube。
- 工具：Codex、Blockbench 5.2.2、Python 3.12、Pillow、Blockbench MCP Server 1.10.1。
- 验证边界：**已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。**

## 本轮修改

1. 为手动新增的大型辅助设备舱补充完整贴图：
   - 大块壳体改为橄榄灰 2×2 维护面板材质，带接缝、压边、角落紧固件、纵向污带和下缘成组磨损。
   - 两块内凹面板分别使用检修门和横向通风面材质，不再使用纯黑填充。
2. 整体降低纯黑线条和对比度：
   - 最深色从近黑抬到深绿灰，最低不透明纹理亮度从约 20 提升到约 51。
   - 结构线统一到深橄榄灰 #444B45 附近，主壳、黑风道框条和格栅不再依赖纯黑描边。
   - 最亮材质从约 193 压到约 175，并全局混入 7% 中性灰。
   - 不透明纹素亮度范围由 173 降到 124（约 -28%），标准差由 33.18 降到 20.6（约 -38%）。
3. 修复四向预览中的编辑器叠加物：
   - 使用 Canvas.withoutGizmos 渲染，axes、grid、选中框和背景网格均不进入预览。

## 冻结手改基线

- 几何清单：reference/user_saved_geometry_manifest_rev2.json
- 基线时间：2026-11-11 13:40:09 +08:00
- 冻结元素：119 cubes / 8 groups
- 聚合几何 SHA-256：068e49808b229672eefcdd1b40a04ef786c59312208fe9ad1e497a281beeae85
- 所有 from / to / rotation / origin 已逐项比对，本轮未改变。
- 用户复制出的新结构保留了重复 cube 名；UV 与材质映射改用 UUID 作为唯一键，不会把两个同名 cube 混在一起。

## 用户新增结构

新增结构由：

- 一个复制并改造的大体量壳体 cube；
- 上方的黑色风道侧检修门；
- 下方的侧通风面；

共同组成。当前为其分配 18 个独立图案面：

- aux_shell：橄榄灰壳体、2×2 面板分缝与接触磨损。
- aux_service_door：内凹门框、横向加强线、把手和底部掉漆。
- aux_vent：横向格栅、内侧阴影和边缘积尘。

检查图：

- reference/aux_structure_check.png
- reference/right_side_check.png
- reference/bevel_corner_check.png

## 尺寸与安装

- 名义设计仍为 4×2×3 格，正面 +Z，最低点 Y=0。
- Blockbench 最终世界边界：X -34.85…34.25，Y 0…32，Z -23…24。
- 世界尺寸：69.1 × 32 × 47 BB。
- 世界中心：X -0.3，Y 16，Z 0.5。
- 没有为了名义占位或居中而移动手改风机和新结构。
- 通用审计的 raw-cube 包络为 X -45.593…43.1，是大旋转 cube 的保守原始包络，不等同于 Blockbench 实际世界包围盒。

## UV 与贴图

- 贴图：engine_tex.png，512×512 RGBA。
- 真透明像素：132519；半透明像素：0。
- 内嵌 PNG 与外置 PNG SHA-256 一致。
- 714 个面全部有 UV 与纹理分配。
- 135 个主图案面使用独立、按当前手改尺寸计算的 UV 岛。
- 28 个隐藏/窄边共享纯色微岛。
- 图案岛重叠 0，图案与纯色岛交叉重叠 0。
- 所有图案面的长宽比误差 0.0%。
- 图集占用高度 354 px，岛间距 2 px。
- 不使用随机噪点、渐变、纯黑描边或零散扩散点。

UV 布局：reference/uv_layout.json
调色板：reference/palette.json
本轮审计：reference/texture_refinement_v3_audit.json

## 模型结构

- Bedrock Entity，format_version 1.12.0
- 标识：geometry.engine
- 119 cubes / 8 bones / 714 个声明面 / 0 动画
- 原手改 119 cubes 全部保留
- 8 个功能骨骼保持不变：
  engine、housing、front_vents、control_panel、black_duct、base_skirt、top_rigging、rear_rotor

## 已完成检查

- [x] Blockbench 5.2.2 重新打开并保存最终 .bbmodel
- [x] 原生 Bedrock 导出 geometry.engine
- [x] .geo.json 重新打开后为 119 cubes / 8 bones
- [x] 正面、背面、左侧、右侧、斜前方与新增结构近距离检查
- [x] 新结构的大面和两块检修面板均有可读贴图
- [x] 右侧风机仍在新增侧板前方正常可见
- [x] 四向预览 1200×900 RGBA，透明底、无网格、无 UI、无裁剪
- [x] audit_static_model.py：.bbmodel 0 errors / 0 warnings
- [x] audit_static_model.py：.geo.json 0 errors / 0 warnings
- [x] 714 面均有 UV 和纹理
- [x] 135 图案岛 + 28 纯色岛，重叠 0
- [x] 图案面纵横比误差 0.0%
- [x] 内嵌纹理与外置 PNG 一致
- [x] .bbmodel / .geo.json 无绝对路径依赖
- [x] 无动画、粒子、发光、碰撞或交互

## 交付文件

- engine.bbmodel
- engine.geo.json
- engine_tex.png
- preview_front.png
- preview_back.png
- preview_side.png
- preview_oblique.png
- README.md
- validation.json
- structure_audit.json
- reference/：原作裁切、调色板、UV 布局、两轮手改几何清单、修订脚本、补充检查图和旧版构建资料

## 复现方式

本轮材质修订只处理 UV 和贴图，不重建几何：

    python reference/refine_texture_contrast_v3.py
    python reference/render_previews_via_blockbench_mcp.py
    python reference/render_refinement_checks_via_blockbench_mcp.py

第一条脚本对 reference/user_saved_geometry_manifest_rev2.json 逐项校验 from / to / rotation / origin；任何一个手改 cube 被改动都会停止，不会静默覆盖。后两条脚本使用 Blockbench 自身渲染器输出透明预览，并通过 Canvas.withoutGizmos 排除编辑器和网格叠加物。

reference/revise_user_saved_v2.py、engine_spec.py、build_engine_model.py、make_texture.py 属于上一轮构建资料，不要直接重跑覆盖当前手改版。

## 验证边界

**已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。**

尚未验证：

- Minecraft / GeckoLib 游戏内渲染
- 资源包路径绑定与贴图加载
- 碰撞、交互、放置方向、运行时性能
- 非对称世界包围盒在游戏中的最终视觉占位

本轮只修改本地第二版美术工程，未修改仓库代码、注册文件或 src/main/resources。

## 交付记录（2026-11-11）

- **预览重出**：原四向预览生成于 13:52，材质手改完成于 18:31，预览未反映新材质。本轮按当前材质用 Blockbench 5.2.2（经 MCP，`Canvas.withoutGizmos`）重出全部四向预览，`validation.json` 的 `previews.alpha_bbox` / `margins` 已同步。
- **几何未变**：逐项比对 `reference/user_saved_geometry_manifest_rev2.json`，119 个 cube 的 from / to / rotation / origin 零差异（仅 `rotation` 的 `null` 与 `[0,0,0]` 写法不同）。
- **与主分支现有 engine 的关系**：主分支 `art/models/engine.*` 为另一版本（122 cubes / 6 bones / 3.00 格宽、X 居中）；本版为 119 cubes / 8 bones / 5.54 格宽、X 不居中（保留用户手改变换，`centered_xz: false`）。两版并存，待评选决定采用哪一版。
- **交付位置**：`art/models/engine_20261111_v2/`，与原扁平资产 `engine.*` 并存，不覆盖。
