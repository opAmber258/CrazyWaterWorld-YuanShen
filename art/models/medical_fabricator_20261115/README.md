# 医药加工台 MedicalFabricator

制作来源：AI（OpenAI Codex，Blockbench 5.2.2 与 Pillow）。按用户所附正面图制作的独立、静态、可编辑模型。本资产另有 AI 复核会话（ZCode）在发布前重出交付物，范围见「发布前重出」一节。

## 原作和比例依据

- 简中与英文分别查自游戏安装目录的 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 和 `Content/Texts/English/EnglishVanilla.xml`。两个文件的 `entityname.medicalfabricator` 条目分别为「医药加工台」和「Medical Fabricator」。
- 实体定义位于 `Content/Items/Fabricators/fabricators.xml`，标识为 `medicalfabricator`，缩放为 0.5。完好状态的静态图来自 `Content/Items/Fabricators/FabricatorsAndDeconstructors.png` 的矩形 `0,0,336,288`。原始裁切保存在 `reference/original_crop.png`，模型使用独立绘制的像素纹理。
- 裁切以 alpha > 100 统计的可见范围为 332 × 279 px。采用项目中已有的人物视觉尺度估算，120 px 对应 32 BB 单位。每个原图像素折合 0.5 × 32 / 120 BB，参考宽高为 44.27 × 37.20 BB。
- 成品宽 **44.00**、高 **37.095**、深 **17.00 BB**，约 **2.75 × 2.32 × 1.06 格**。正面朝 +Z，X/Z 居中，含旋转的世界坐标边界为 (-22, -0.452, -8.5) 至 (22, 37.15, 8.5)。按壁挂使用，最低点是回水管弯头。背面挂带在 Z = -8.5 平面与舱壁贴合。
- 原图确定了深色非对称双舱门、左门检修卡、右门双蛇杖、左侧加工头、较上柜宽的台面、右侧沉槽和底部回水管的位置。柜体纵深、水槽俯视结构、背部检修盖与托臂是美术推定。项目风格参考了 curated 导航终端以像素区分玻璃、门框与结构件的做法。

## 工程与贴图

| 文件 | 内容 |
| --- | --- |
| `medical_fabricator.bbmodel` | Blockbench Bedrock Entity 工程，已内嵌贴图 |
| `medical_fabricator.geo.json` | 原生 Bedrock 1.12.0 几何，标识 `geometry.medical_fabricator`，86 cubes |
| `medical_fabricator_tex.png` | 512 × 512 RGBA 像素图集，每格目标 32 px |
| `preview_{front,back,side,oblique}.png` | 1200 × 900 透明底四视图，前三张为正交，斜视为透视 |
| `validation.json` / `structure_audit.json` | 交付核对、文件摘要与原始结构审计 |

85 个方块划分为壁挂件、上柜、舱门、加工头、台面、水槽、排水管路共 7 个分组。舱门图案、印刷标贴与沥水槽纹在贴图上绘制。水槽为开口结构，底部排水口与下方尾管对齐。金属丝束和波纹管用旋转方块连接。每个面按实际边长分配最接近目标的整数纹素，极薄边至少占一个纹素，每个 UV 岛有一像素颜色外扩。

## 发布前重出（交付口径以本节为准）

工程在初次验证后再被改动过一次（12:12），改动集中在加工头的导丝（`loom_1_*` / `loom_2_*`）与排水弯头（`basin_elbow_0..4`）的位置和长度，并新增一个 `loom_1_1`。原交付的 `.geo.json`、贴图与四张预览均产于改动之前（00:25–00:26），与工程已有 1 个 cube 的出入，不满足交付约定第 9 节。本轮以**改动后的工程为准重出全套交付物**：

- 由 Blockbench 5.2.2 原生工程编码器重导出 `.geo.json`，并以原生 Bedrock 编解码器重渲四向预览；
- 重新生成 `structure_audit.json`、`reference/uv_density.json` 与 `validation.json`；
- 贴图未改，`medical_fabricator_tex.png` 与工程内嵌图仍逐字节一致。

当前计数为 **86 个 cube / 7 bones / 516 个面**，与 `.geo.json`、`.bbmodel` 完全一致。相对原交付：bounds 底部由 0.055 变为 **-0.452**（弯头下移），其余三个方向不变；宽高深仍为 44 × 37.095 × 17 BB。

已知问题（不影响导出，未处理）：加工头内有两个元素同名 `loom_1_1`，几何与朝向不同（Z 旋转 -71.10° 与 -150.10°），属于两块独立的导丝。Blockbench 允许重名，导出的 86 个 cube 与工程元素一一对应，但重名会让后续按名字定位该组元素时产生歧义。

`reference/audit_structure.py` 与 `reference/audit_uv_density.py` 是本轮新增的可复现审计脚本，对改动前的工程重跑时能精确复现原交付记录的 85 cube / 510 面结论。

## 验证与复现

在 Blockbench 5.2.2 中打开工程、用原生工程编码器保存、重新打开并导出。两次 Bedrock 导出内容一致。独立核对 86 个方块的位置与尺寸、旋转件的轴心与方向，以及全部 516 个面 UV。外部贴图与最终工程内嵌贴图逐字节一致。结构、密度审计均为 0 错误、0 警告。通用结构审计的 bounds 字段采用旋转前坐标，成品尺寸取自编辑器变换后的网格。已目检正、背、侧和斜视，修正了水槽右侧托臂的穿插。未进入 Minecraft 验证，交付范围为静态美术资产。

`reference/build_model.py` 在本目录重建工程和手绘图集，会覆盖同名交付文件；修改过的工程请先另存。脚本只依赖 Python 和 Pillow。`reference/check_in_blockbench.py` 使用 websocket-client 连接已单独启动的本机 Blockbench 调试窗口（用 `--port` 指定端口），可执行原生重开、导出与预览。脚本按自身位置定位资产，预览来自 Blockbench 场景及其渲染器。`reference/verify_delivery.py` 核对导出和现有审计并生成验证记录；`reference/audit_structure.py`、`reference/audit_uv_density.py` 重新生成两份审计原始报告。三者均按自身位置定位资产，可从任意工作目录重跑。

