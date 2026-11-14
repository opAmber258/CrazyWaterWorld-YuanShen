# 等离子切割器 PlasmaCutter · 候选 B（Java 物品模型）

- 日期：2026-11-15
- 作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图绘制、预览截图全部人工完成；AI（GLM-5.3-Flash，经 ZCode CLI）仅承担交付打包、离线一致性校验与文档起草，未参与几何、UV、贴图、预览的制作。
- 格式：Blockbench 5.x `java_block`（`java_block_version` 26.3），Java 物品模型路线；按约定不产出 `.geo.json`。

## 原作对应与命名

- 中文官方译名：等离子切割器；英文：Plasma Cutter；mod 标识 `plasma_cutter`。
- 译名口径沿用 Discussion #39 已登记的候选 A（原版条目 `entityname.plasmacutter`）。本机无游戏安装目录，未直接重查 `Content/Texts/` XML，如评选需要可由维护者复核。

## 交付内容

| 文件 | 说明 |
| --- | --- |
| `plasma_cutter.bbmodel` | 本体工程（Blockbench 5.x，java_block），贴图内嵌，可重开编辑 |
| `plasma_cutter_oxygen_tank.bbmodel` | 变体：加挂氧气瓶（瓶体 + 垫环两个 cube），其余与本体一致 |
| `plasma_cutter_tex.png` | 本体贴图（64×64 RGBA，自 bbmodel 内嵌逐字节提取） |
| `plasma_cutter_oxygen_tank_tex.png` | 变体贴图（64×64 RGBA，自变体 bbmodel 内嵌逐字节提取） |
| `preview_front.png` / `preview_back.png` / `preview_side.png` / `preview_oblique.png` | 本体四向预览，1200×900，纯黑底、无网格无 UI |
| `preview_oblique_oxygen_tank.png` | 变体斜视预览，1200×900 |
| `README.md` / `validation.json` / `structure_audit.json` | 说明与结构化验证 |
| `reference/` | 离线校验脚本，见 `reference/README.md` |

## 规格

本体（`plasma_cutter.bbmodel`）：

- 25 cubes / 150 声明面 / 6 分组（整个、主、枪口、琐之、握把、后握把）/ 0 bones / 0 动画；
- 旋转 cube：25/25（含多轴自由旋转，见下方"旋转与格式注意"）；
- 世界包围盒 6.000 × 13.289 × 23.561 模型单位（X 5..11，Y −0.289..13，Z −4.25..19.311），枪轴沿 Z，枪口朝 −Z；
- 贴图 64×64 RGBA（4096 像素，其中 2690 透明像素为真透明）。

变体（`plasma_cutter_oxygen_tank.bbmodel`）：

- 27 cubes / 162 声明面 / 7 分组（比本体多 `央企管子` 氧气瓶分组），其余同上；
- 世界包围盒 6.000 × 15.999 × 23.561 单位，瓶体下探至 Y −2.999；
- 贴图与本体同 UV 布局，两份贴图仅 16 像素不同（x33-58 / y31-46，氧气瓶 UV 区）。

## 尺寸依据与换算

按 16 模型单位 = 1 格换算：本体 0.375 × 0.830 × 1.473 格，变体 0.375 × 1.000 × 1.473 格。原作中等离子切割器为手持工具，本轮尺寸为作者在 Blockbench 内对照原作精灵图观感自定；物品模型无碰撞/占位需求，实际观感由 display 缩放决定。

## 保留的识别特征

- 去饱和绿灰机体 + 黑色后握把与斜向前握把；
- 顶部银黑相间散热/护条与浅青顶罩；
- 侧面亮蓝色状态屏、红色按钮细节；
- 前端切割枪管、青色切割尖端与锯齿状枪口；
- 变体在下挂位置增加白瓶身、青瓶盖的氧气瓶。

## 旋转与格式注意（接入前必读）

工程内 25 个 cube 全部带旋转，且多为多轴自由旋转（如 `[-90, 67.5, -90]`），超出 Minecraft 1.20.1 原版 Java 方块/物品模型 JSON 的表达范围（仅单轴 ±22.5°/±45°）。本仓库当前目标版本为 1.20.1 Forge，集成者在接入 `src/main/resources` 前需确认目标格式（升级 MC 版本 / 自定义渲染器 / 其他）后再用 Blockbench 原生导出；本轮因此未随附 Java 模型 JSON。

## 与候选 A / 上版差异

- 本候选为独立重制（作者、建模路线与候选 A 不同），非候选 A 的修改版；
- 作者内部迭代 1.3 → 1.5；交付前从基础工程中移除氧气瓶瓶体与垫环两个 cube（保留枪身接驳口），氧气瓶完整保留在变体工程中；
- 相对候选 A：单贴图 64×64 方案，非 emissive 双贴图。

## 预览与验证

- 五张预览均由作者在 Blockbench 视口手动截图导出（非脚本渲染）：正视（南向正交）/ 背视（北向正交，枪口方向）/ 侧视（东向正交）/ 斜视（透视），变体补一张斜视；
- 离线自建渲染器（`reference/render_bbmodel.js`，ZYX 欧拉序）逐 cube 复算世界包围盒，并渲染正/背/侧视图与五张预览轮廓逐一比对一致；删除氧气瓶后重渲染，确认基础版与预览一致；
- `reference/validate_deliverable.js` 在仓库根可重跑：命名、格式、计数、贴图 2 的幂与真透明、贴图与内嵌字节一致、预览尺寸等；
- bbmodel 已由作者在 Blockbench 5.x 中重开编辑并保存（2026-11-15）；
- 未接入 `src/main/resources`，未进入 Minecraft 客户端验证。
