# 引擎 Engine｜独立低多边形重制

**制作来源：AI（GPT 6.1 Sol）。** 工具：Codex、Blockbench 5.2.2、Blockbench MCP、Python / Pillow。几何与像素纹理由本批脚本独立构建，最终在可见的 Blockbench 中检查、保存和原生导出。

用户确认：以附图为主要依据，按原作与人物比例制作约 5～6 格宽的独立静态设备；已有引擎仅作风格参考。

**已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。**

## 文件与规格

| 项目 | 内容 |
| --- | --- |
| 可编辑工程 | `engine.bbmodel`，Bedrock Entity，内嵌贴图，无绝对贴图路径依赖 |
| 几何 | `engine.geo.json`，`format_version: 1.12.0`，`geometry.engine` |
| 外置贴图 | `engine_tex.png`，512×512 RGBA，硬边像素图 |
| 结构 | 143 cubes / 10 bones / 858 声明面，无动画 |
| 四视图 | `preview_front/back/side/oblique.png`，均为 1200×900、透明底、无 UI / 网格 |
| 记录 | `validation.json`、`structure_audit.json` 与 `reference/` |

正面、背面、左侧为正交预览；斜视为透视。四张图直接来自 Blockbench 的场景与渲染器，使用 `Canvas.withoutGizmos` 排除辅助线。渲染脚本已随附。

## 原作查证

本机游戏安装目录中的下列独立来源已读取：

- `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml`：`entityname.engine` = **引擎**。
- `Content/Texts/English/EnglishVanilla.xml`：`entityname.engine` = **Engine**。
- `Content/Items/Engine/engine.xml`：`identifier="engine"`，`Scale="0.5"`；主 Sprite 为 `Engines.png`，`sourcerect="0,0,659,317"`。
- `Content/Items/Engine/Engines.png`：原图裁切见 `reference/original_engine_crop.png`；用户附图见 `reference/user_reference.png`。

辅助查阅：[官方引擎组件文档](https://regalis11.github.io/BaroModDoc/ItemComponents/Engine.html)。组件文档用于确认原作题材，不作为本模型的功能实现说明。

查证字段与本机源文件摘要记录在 `reference/source_report.json`。原图仅作参考，交付贴图由脚本重新绘制，没有把原作图集贴到模型上。

## 比例与坐标

原作没有为该 2D 图提供可直接移植的三维米制尺寸。本批采用以下**换算假设**，不是官方物理尺寸：

```text
人物视觉高度参考 = 120 px（工作流假设）
Minecraft 人物视觉高度 = 32 BB
K = 0.5 × 32 / 120 = 0.133333 BB/px
参考矩形宽 = 659 × K = 87.87 BB = 5.49 格
参考矩形高 = 317 × K = 42.27 BB = 2.64 格
```

为让倒角、框架和像素节奏落在易编辑的尺寸上，最终宽高取 **86.6 × 42 BB**，相对以上建议分别约 -1.44% / -0.63%。深度由三维造型推定，主体壳深 24 BB；含推进罩最终深 35.1 BB。

- 包围盒：X **-43.3…43.3**；Y **0…42**；Z **-17.55…17.55**。
- 最终尺寸（宽 × 高 × 深）：**86.6 × 42 × 35.1 BB**，约 **5.4125 × 2.625 × 2.19375 格**。
- 地面 Y=0；正面 +Z；推进罩朝 -X；X/Z 居中。
- Minecraft 的方块占位、碰撞形状与安装代码不包含在本批交付中。

## 造型与风格

保留原作识别特征：左侧外扩推进罩、三联百叶检修门、三联仪表、右上电控板、右下接口区、顶部线缆与红色底梁。百叶有实体间隙，推进罩为开放八边形结构，罩内设置静态四叶转子。

以下为**美术推定**：推进罩截面与内部转子、整机厚度、背面双检修盖、维护接头、线槽、顶盖、安装脚和线缆在顶面的走向。原作图未证明这些区域的三维结构。本模型不声明已实现推力、转动、亮灯或电路交互。

项目风格参考只来自 `art-vault/curated/engine_20261111_v2`：借鉴其低对比灰绿材质、成组功能区、少量定位磨损。没有复制其几何、UV 或贴图，也没有改动现有引擎。未使用外部优秀模型资产。

与 curated 旧版相比，本批为从零重制：采用开放八边形左推进罩，正面三联百叶与电控区按附图重新组织，背部采用克制维护分区。它是独立候选，不代表替换或定案。

## UV 与验证

主面目标密度为 **2 px/BB（32 px/格）**；图集包含 156 个独立岛，同尺寸、同材料及同图案的零件可有意共享。岛之间无交叉重叠，所有 858 个声明面均有有效 UV 和纹理分配。相同图案共享属于设计，不是缺贴图。

- 独立脚本核对全部 cube 的尺寸、位置、旋转、轴心、骨骼层级与 UV；已规范化 Bedrock 的 X 镜像、X/Y 旋转符号以及上下表面的 UV 方向，全部匹配。
- 最终 `.bbmodel` 在 Blockbench 重开后，重新编译的几何与交付 `.geo.json` 完全一致。
- `.geo.json` 经 Blockbench 原生导入并再次导出，结果完全一致。
- 内嵌与外置 PNG 的字节一致；RGBA；alpha 仅 0 / 255；19 个颜色（含透明）。
- 四向检查完成，已修正首轮右上角突出的倒角结构；最终四图均已重新生成，没有触及画布边缘。
- 通用结构预检：工程 0 errors / 0 warnings，几何 0 errors / 0 warnings，原始报告保留在 `reference/`。

**保留警告：** 个别不足 1 BB 的窄边向上取整到完整纹素，实际密度为 2～4 px/BB，不能宣称全模型像素密度完全一致。主要可见面维持目标密度；窄边取整避免零面积 UV。本批的独立结构审计为 0 errors / 1 warning。

根骨骼 `engine` 不直接带 cube，用于统一父层级；其余 9 个功能骨骼都包含实际几何，不是孤立骨骼。

## 复现

从仓库根运行时，在下列脚本路径前加 `art/models/engine_20261113/`；脚本通过自身位置解析资产目录。

需要 Python、Pillow、已安装 MCP 插件并运行的 Blockbench（本地 `/bb-mcp`，端口 3000）。从本目录执行：

```text
python reference/build_engine.py
python reference/bb_ops.py load
python reference/bb_ops.py export
python reference/render_previews.py
python reference/native_roundtrip.py
python reference/bb_ops.py load
python reference/bb_ops.py eval reference/check_reopened.js
python reference/validate_delivery.py
python reference/finalize_validation.py
```

`build_engine.py` 会按本批规格重建工程与贴图；后续有人工编辑时，先另存检查点，再决定是否重跑。加载工具遇到同路径未保存工程会停止，不会覆盖未保存编辑。原生导出会同步保存工程、几何和内嵌 PNG 的外置副本。

`reference/engine_gray.bbmodel` 是灰模检查点，`build_engine.py --gray` 可重建；它不是最终交付工程。工具脚本使用自身位置定位资产，不依赖本机私有路径；Blockbench 的交互式环境是原生检查与渲染的必要依赖。

## 未验证事项与边界

未运行 Minecraft；未验证 GeckoLib / 客户端实际渲染、暗环境可读性、占位碰撞、放置朝向、资源注册、性能、交互、动画、发光或推进逻辑。Gradle、L1 与 GameTest 属运行时代码检查，本批没有运行时代码变更，不适用。

2026-11-13，用户明确授权将本资产直推上游 `main`，按 `art/models/engine_20261113/` 归档，并在 Discussion #39 登记为引擎**候选 C（第三候选）**。本地按用户要求归入 `art-vault/curated/engine_20261113/`；此本地分类不代表上游评选通过。制作来源为 AI（GPT 6.1 Sol），用户提供需求、参考与方向选择，未记录人工几何或贴图编辑。旧候选 A、B 保留；本批未接入运行时资源。
