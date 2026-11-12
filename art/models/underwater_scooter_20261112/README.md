# 水下助推器 UnderwaterScooter

制作来源：AI。工具：Codex（GPT-6）、本机 Blockbench 5.2.2 / MCP 1.10.1、Python / Pillow。几何在 Blockbench 中使用原生立方体、分组及 UV 工具创建；像素图集由脚本绘制；预览来自 Blockbench 原生 WebGL 离屏截图。

日期：2026-11-12。标识：`underwater_scooter`。这是根据原作参考重新制作的低多边形美术候选，工程位于本地 production 素材库。

## 文件

| 内容 | 文件 |
| --- | --- |
| 可编辑工程，贴图内嵌 | `underwater_scooter.bbmodel` |
| Bedrock Entity 几何 | `underwater_scooter.geo.json` |
| 独立 RGBA 贴图 | `underwater_scooter_tex.png` |
| 标准四向预览，1200×900 | `preview_front.png`、`preview_back.png`、`preview_side.png`、`preview_oblique.png` |
| 补充灯头、上下、另一斜视 | `preview_nose.png`、`preview_top.png`、`preview_bottom.png`、`preview_oblique_nose.png` |
| 验证与预算 | `validation.json`、`structure_audit.json`、`cost_audit.json`、`hidden_face_audit.json` |

## 原作查证与参考范围

中文名分别查本机游戏 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `entityname.underwaterscooter`：**水下助推器**。英文另查 `Content/Texts/English/EnglishVanilla.xml` 同条目：**Underwater Scooter**。英文驼峰为 `UnderwaterScooter`。用户称“水下推进器”，交付名按本项目约定使用官方简中译名。

实体：`Content/Items/Diving/divinggear.xml`，identifier `underwaterscooter`，`Scale="0.5"`；精灵为 `Scooter.png` 的 `0,0,140,102`；物品栏图标为 `Content/Items/InventoryIconAtlas.png` 的 `256,128,67,62`。两个用户参考图与原作裁切保存在 `reference/`，出处、条目及文件哈希见 `source_evidence.json`。

[官方 Wiki](https://barotraumagame.com/wiki/Underwater_Scooter) 和本机实体定义均说明这是电池驱动、双手使用且带照明的水下装备。这些功能用于确定电池盖、握把和灯头的美术结构；本次没有接入功能代码。

主要保留：黄橙色筒体、两道灰钢箍、深色前罩、浅绿灯片、尾部机械舱、侧盖、穿孔金属护板、下方回环及握把。尾部八角护圈、四叶螺旋桨、双侧握把、另一侧电量显示是三维补完与适度自由发挥，不声称来自原作未展示的背面。

项目风格基线仅使用 `curated/plasma_cutter_20261110` 与 `curated/welding_tool_20261108`：参考其成组金属色块、硬边像素、少量警示色和手持设备的细节层级。另核对 `curated/navigation_terminal_20261108` 的 Bedrock 工程/贴图结构。没有复制其几何或贴图，没有读取 deprecated 素材。

## 比例与方向

原作精灵 140×102，乘 `Scale=0.5` 得 70×51 原作屏幕单位。二维精灵没有给出三维深度或可靠的米制尺寸，因此没有把屏幕像素冒充精确物理测量。

本候选按双手设备约 **0.93 格长**的美术比例制作：草模全长 18.52 单位，统一乘 0.8 后为 **14.816 单位**；Minecraft 每格按 16 模型单位计算。含握把和线缆的最终三维尺寸为：

| 方向 | 模型单位 | 格 |
| --- | --- | --- |
| 长 X | 14.816 | 0.926 |
| 高 Y | 8.703197 | 0.543950 |
| 深 Z | 6.608 | 0.413 |

包围盒：`[-6.96, 0.000004, -3.304]` → `[7.856, 8.703201, 3.304]`。底部对齐 Y≈0。+X 为灯头，−X 为螺旋桨/操作者一端，+Y 向上。Bedrock 编码器会翻转 X 坐标并转换旋转符号，导出包围盒已转换回 Blockbench 坐标复核。

`front` 为 +Z 主识别侧，`back` 为 −Z，`side` 为 −X 尾轴方向；`nose` 补充 +X 灯头。正、背、侧、上下、灯头均为正交相机，两张 oblique 为透视。

## 几何、贴图与成本

- 83 个可编辑立方体，10 个骨骼（含 `scooter` 根容器）。`rotor` 独立分组，当前为静态，无动画副本。
- 498 张原始声明面中，24 张经单个不透明立方体完整遮挡证明后关闭；最终导出 **474 张面**。保留部分遮挡面与八角截面必要的交叠，不做未经证明的剔除。精确证明见 `hidden_face_audit.json`。
- 单张 **64×64 RGBA** 图集，4096 像素，2304 不透明像素，0 半透明像素。透明区域 RGB 为零。图案采用离散色块，没有渐变、随机噪声、模糊或材质透明叠层。
- 图集从制作阶段的 128×128 重新装箱为 64×64，保留图块原像素并重新分配 UV；不是将整图缩小。分辨率、图集占用和项目 UV 元数据一致。
- 声明预算：≤100 组件、≤600 面、1 张贴图、≤4096 贴图像素；均通过。预览 draw calls、Minecraft 帧率和显存没有测量。

## 验证状态

1. 四件套、命名和 1200×900 无 UI / 无网格预览齐备：通过。预览保留透明背景。
2. JSON、identifier、正尺寸、UV 范围、骨骼父级、无完全重复 cube：通过。
3. `.bbmodel` 与 `.geo.json` 的组件数、转换后包围盒、原生重导出一致：通过。
4. 内嵌 PNG 与独立贴图像素一致，无本机绝对贴图路径：通过。
5. 使用 Blockbench 原生 codec 从磁盘工程内容重新打开，几何导出 JSON 与贴图像素均完全一致：通过；其它已打开的工程已保留。
6. 完成八视角复查。重开前后六个正交图逐像素相同；两张透视图分别有 168 与 8 个像素不同，因此**不宣称全图严格像素一致**。详细测量保存在 `reference/reopen_verification.json`。
7. Minecraft 客户端、第一/第三人称握持、推进与灯光逻辑：**未接入、未实测**。Bedrock 工程没有 Java Display 槽；运行时集成与握持校准另行进行。

Blockbench 5.2.2 的离屏视图删除在 `controls.dispose` 报过已知 `onContextMenu is not defined` 警告。每次均在工具及 Preview 注册表中复核该视图已经移除；未修改共享插件，也不宣称证明了所有 listener 的释放。

## 重现

脚本均按 `__file__` 定位资产目录，不需要作者私有路径。依赖 Python 3.10+、Pillow、本机 Blockbench 5.2.2 与 MCP 插件。MCP 默认端点 `http://localhost:3015/bb-mcp`，可用 `--url` 指定实际端点；不处理认证连接。

在本资产目录执行：

```text
python reference/prepare_model.py
python reference/blockbench_task.py gray
python reference/blockbench_task.py gray-render
python reference/blockbench_task.py texture
python reference/cleanup_hidden_faces.py --apply
python reference/blockbench_task.py render
python reference/reopen_verify.py
python reference/validate_delivery.py
```

`gray` 为防止覆盖已有工程，会在 `blockbench_session.json` 存在时拒绝重新创建。完整重建应在新的候选目录复制这些脚本后执行；检查现有工程使用 `inspect`、`export` 或 `render`。纹理/几何参数在 `prepare_model.py`，预览相机在 `blockbench_task.py`。原作参考的重取用 `capture_sources.py --game-root <游戏目录>`。

`reference/work/` 保存灰模、清理前基线与截图对照，属于本地审计过程文件，**不入库**（同 `__pycache__`）。入库的脚本本身不引用 `work/`，只有 `validate_delivery.py` 的重新打包复核一项会读 `reference/work/before_cleanup.bbmodel`；该文件缺失时此项自动跳过，其余检查照常执行。

`blockbench_session.json` 只含工程 UUID，用于阻止 `gray` 误覆盖已有工程，随 `reference/` 一并入库。

当前是首次新建候选，没有覆盖旧版资产。本资产已按交付约定推送至上游 `art/models/underwater_scooter_20261112/`，尚未接入运行时、未在 Discussion #39 完成评选。
