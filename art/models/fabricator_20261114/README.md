# 加工台 Fabricator

**制作来源：人工 + AI（TakaraMiyuki + GPT-6 / Codex；Blockbench 5.2.2、Python / Pillow）。**

AI 完成最初几何、UV 和像素贴图；TakaraMiyuki 手动修改最终几何、UV 与像素贴图。发布时完整保留人工保存版本，AI 仅重出 Bedrock 几何、四视图及验证记录。当前会话只提供 GPT-6 基础模型标识，未提供更细版本号。

**已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。** 用户已指定以此人工修订版直推上游 main，并在 Discussion #39 登记为加工台候选 A；入库不代表上游评选或运行时验收通过。

## 交付文件

| 内容 | 规格 |
| --- | --- |
| `fabricator.bbmodel` | Bedrock Entity 可编辑工程，贴图内嵌，无绝对贴图路径依赖 |
| `fabricator.geo.json` | 原生 Bedrock 几何导出，1.12.0，`geometry.fabricator` |
| `fabricator_tex.png` | 512×512 RGBA，固定色板、硬边像素，21 色含透明 |
| `preview_front/back/side/oblique.png` | 四张 1200×900 透明底、无 UI / 网格；前三类正交，斜视透视 |
| `validation.json` / `structure_audit.json` | 检查结论、尺寸、UV、重开与导出验证 |
| `reference/` | 用户图、原作裁切、来源记录、色板、可复现制作与检查脚本 |

108 个 cubes，11 个 bones（一个根骨骼、10 个功能组），648 个声明面。静态轮组与刀头仅表达结构，没有动画、发光或交互。

## 原作查证

两种语言分别读取本机游戏原始文本：

- `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml`：`entityname.fabricator` = **加工台**。
- `Content/Texts/English/EnglishVanilla.xml`：`entityname.fabricator` = **Fabricator**。
- `Content/Items/Fabricators/fabricators.xml`：实体 `fabricator`，`scale="0.5"`；主 Sprite 图集为 `Content/Items/Fabricators/FabricatorsAndDeconstructors.png`，`sourcerect="0,624,336,400"`。
- 同一实体还有独立的传动机构 DecorativeSprite；本批将它们概括为下挂机构与静态轮组。

完整字段、文件 SHA-256 与美术推定边界见 `reference/source_report.json`。原作裁切仅用于研究；交付贴图重新绘制，没有直接贴用原作图集。在线补充查阅：[官方 Wiki 的加工台与解构台条目](https://barotraumagame.com/wiki/Fabricator_%26_Deconstructor)。

## 尺寸换算与朝向

用户选择按原作比例还原。原作是 2D 精灵；下列人物比例是**换算假设**，不是官方米制尺寸：

```text
人物参考视觉高度 = 120 px
Minecraft 人物参考高度 = 32 BB
K = 原作 scale × 32 / 120 = 0.5 × 32 / 120 = 0.133333 BB/px
建议宽 = 336 × K = 44.8 BB ≈ 2.80 格
建议高 = 400 × K = 53.333 BB ≈ 3.33 格
最终宽高 = 44 × 52.5 BB，分别约 -1.79% / -1.56%，便于编辑与像素分区
初稿深度 = 18.7 BB（美术推定）；人工调整后深度 = 23.375 BB
```

- 最终宽 × 高 × 深：**44 × 52.5 × 23.375 BB**，约 **2.75 × 3.28125 × 1.4609375 格**。
- 旋转后实际包围盒：X -22…22；Y 0…52.5；Z -11.6875…11.6875。旋转计算在 Y 上有约 0.0000013 BB 的浮点余差，验证保留实测值。
- 地面 Y=0，正面 +Z，X/Z 居中；最终方块占位与碰撞尺寸由后续集成确定。

## 造型与原创补全

保留原图的偏左上罩、低多边形折面顶盖、左上深腔、右上双层百叶、中央长工作槽、下部双检修门、告示贴纸和右侧小控制盒。百叶、检修门扣、工作腔和传动轮有真实体积，浅接缝、紧固件、铭牌与磨损由像素贴图表达。

原图没有给出三维深度、背面或完整的加工腔内部。本批按维护逻辑原创补全：加工腔床轨、夹具、龙门与刀头；上罩后支架、背部检修盖和线槽；机器厚度、支脚、轮组截面与电缆走向。这些结构属于美术推定，不声称是原作官方三维结构。

初稿风格参考**只使用** `art-vault/curated/engine_20261113`：借鉴低对比灰绿金属色、按功能分组、实体百叶与克制磨损；没有复制其模型、UV 或贴图。未使用外部优秀模型，也未参考 deprecated。本次发布人工修订版，相比 AI 初稿增加前后厚度并调整细部几何、UV 与贴图；108 cubes / 11 bones 保持不变。没有覆盖上游其它资产。

## 检查与限制

- 已在可见 Blockbench 中核对工程、内嵌贴图与最终造型；四张最终预览由同一 Blockbench 场景、原生 THREE 渲染器输出，并逐向检查。
- 四向 1200×900 预览已按当前人工修订工程全部重出，逐向检查；发布整理没有修改人工几何、UV 或像素。
- 工程重开后原生导出与交付几何完全一致；几何原生导入、再次导出也完全一致。
- 独立逐 cube 比对尺寸、旋转、轴心、骨骼层级与 UV；已处理 Bedrock 的 X 镜像和上下表面 UV 方向。
- 当前 **256×256 逻辑 UV 配合 512×512 PNG**；648 个声明面都有有效贴图与 UV，648 个实际图岛无互相覆盖。内嵌与外置 PNG 字节一致，alpha 仅为 0 / 255。
- `reference/human_preservation.json` 逐项核对人工快照：元素名称、位置、尺寸、轴心、旋转、UV、骨骼语义、层级与逻辑 UV 分辨率均保持一致；内嵌与外置 PNG 字节均保持一致。
- 通用工程、几何预检分别为 0 errors / **1 warning**：实际 PNG 512² 大于逻辑 UV / 几何描述 256²。这是当前工程的设计口径，已按 2 倍纹理采样比例核对原生显示与导出，没有改动人工 UV。
- 独立检查为 0 errors / **1 warning**：物理纹素密度约 1.43～2.67 px/BB，前后厚度与 UV 分配造成密度差异；保留人工版本，不宣称所有面密度统一。
- 根骨骼 `fabricator` 用于统一层级，不直接带 cube；10 个功能骨骼均含实际几何，没有孤立组。

未检查 Minecraft / GeckoLib 实机渲染、占位与碰撞、配方、电力、灯光、动效、交互和性能。发布路径为 `art/models/fabricator_20261114/`，仅包含本资产及 MEX 状态记录，没有修改游戏代码或 `src/main/resources`。

## 复现

脚本通过自身位置定位资产目录，可从任意工作目录运行。Python 3、Pillow、websocket-client 以及已安装的 Blockbench 5.2.2 是依赖。

```text
python reference/capture_sources.py <Barotrauma安装目录>
```

本机现有 MCP 插件文件不完整，未加载成功。本批未安装或修改插件，也未修改原 Blockbench 配置；使用独立用户数据目录启动已有 Blockbench，再通过其本机 Electron 调试 API 调用原生工程与几何编解码器。连接器默认端口 9224；启动时传入 `--remote-debugging-port=9224 --user-data-dir=<独立临时用户数据目录>`。`bb_api.py` 会校验只有一个 Blockbench 页面；连接器不依赖作者私有文件路径。

```text
python reference/bb_native.py load
python reference/bb_native.py export
python reference/render_previews.py
python reference/native_roundtrip.py
python reference/check_human_preservation.py
python reference/validate_delivery.py
python reference/finalize_validation.py
```

预览使用 Blockbench 已加载的场景、材质与原生渲染器，通过 `Canvas.withoutGizmos` 排除辅助线。脚本会恢复场景与视口设置。

**最终版本以人工修订 `.bbmodel` 为来源。** `reference/build_fabricator.py` 仅保留初稿生成逻辑；重跑时写入 `reference/regenerated_initial_draft/`，不会覆盖人工交付。`reference/fabricator_gray.bbmodel` 是早期轮廓检查点，不代表最终几何。`reference/manual_checkpoint_20261114/` 保存本次发布前的人工工程与 PNG，用于保留核对。
