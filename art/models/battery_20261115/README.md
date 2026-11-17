# 电池 Battery · 候选 A（Java 方块模型）

- 日期：2026-11-15
- 作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图绘制、预览截图全部人工完成；AI（GLM-5.3-Flash，经 ZCode CLI）仅承担交付打包、离线一致性校验与文档起草，未参与几何、UV、贴图、预览的制作。
- 格式：Blockbench 5.x `java_block`（`java_block_version` 26.3），Java 方块模型路线；按约定不产出 `.geo.json`。
- 登记类型：方块（墙体安装的电力设施）。

## 原作对应与命名

- 资产名：电池 Battery；mod 标识 `battery`；目录 `battery_20261115/`。
- 原型为原版墙体蓄电设备「**电池（设备）**」Battery —— 官方简中以"（设备）"与手持电池消歧义（原版描述用语："可在电池（设备）或充电坞站处充电"）；条目标识推断为原版 `entityname.battery`（identifier `battery`）。
- **与两种相邻实体明确区分**：不是手电筒里装的手持电池（battery cell），也不是给电池充电的充电坞站（Charging Dock）。
- 查证方式说明：本机无游戏安装目录，未直接核验 `Content/Texts/SimplifiedChinese/` XML；官方中文名取自潜渊症中文维基（灰机 wiki）对官方描述的引用。评选前可由维护者对照游戏文件复核。

## 交付内容

| 文件 | 说明 |
| --- | --- |
| `battery.bbmodel` | 工程文件（Blockbench 5.x，java_block），贴图内嵌，可重开编辑 |
| `battery_tex.png` | 贴图（64×64 RGBA，自 bbmodel 内嵌逐字节提取，满电状态） |
| `preview_front.png` / `preview_back.png` / `preview_side.png` / `preview_oblique.png` | 四向预览，1200×900，纯黑底、无网格无 UI |
| `README.md` / `validation.json` / `structure_audit.json` | 说明与结构化验证 |
| `reference/screen_states/` | 电量屏四个状态（满/半/低/空）的完整 64×64 贴图，见下文 |
| `reference/` | 离线校验脚本，见 `reference/README.md` |

## 规格

结构化校验（cube/面/分组计数、世界包围盒、贴图统计）统一维护在 [validation.json](validation.json)；
接入限制（67.5° 端子、零厚度屏面）见下文“旋转与格式注意”。

## 电量屏与贴图状态

正面屏幕为**三段电量指示条**，工程内以贴图图层表现四个状态（作者备注："贴图有三个图层，只在这里有变化"）：

| 状态 | 屏面（2×3 px） | 文件 |
| --- | --- | --- |
| 满电（3 段绿） | 主贴图 `battery_tex.png`（与 bbmodel 内嵌一致） | 同左 + `reference/screen_states/battery_tex_full.png` |
| 半电（2 段橙） | — | `reference/screen_states/battery_tex_mid.png` |
| 低电（1 段红） | — | `reference/screen_states/battery_tex_low.png` |
| 空电（全暗） | — | `reference/screen_states/battery_tex_empty.png` |

四个状态均为完整 64×64 贴图，像素比对确认实际差异仅在屏幕正面 UV 区（6 px），其余 ≤8 px 为图层合成舍入噪声（Δ≤7，不可见）。集成侧可用 blockstate 按方块内电量切换贴图；主贴图为满电状态，与预览一致。

## 尺寸依据与换算

按 16 模型单位 = 1 格换算：0.625（宽）× 0.969（高）× 0.375（厚）格。原版中该设备为贴墙安装的蓄电设施，本轮厚度与比例由作者对照原作精灵图自定；未随附 blockstate / loot table / lang（属集成者接入范围）。

## 旋转与格式注意（接入前必读）

- 3 个插口端子为 **67.5° 单轴旋转**，超出 Minecraft 1.20.1 原版 Java 模型 JSON 的角度限制（仅 ±22.5°/±45°）；本仓库当前目标版本为 1.20.1 Forge，集成侧接入前需确认目标格式（升级 MC 版本 / 自定义渲染 / 端子改为静态角），再用 Blockbench 原生导出；本轮因此未随附 Java 方块模型 JSON。
- 电量屏为零厚度面片（2×3×0，悬浮于主体前表面 0.001 单位）。
- 工程无 display 设置（方块渲染不需要）。

## 保留的识别特征

- 米白/浅棕两段式电池壳体与顶部三个插口端子（对应原版可插入电池充电的造型语义）；
- 正面电量指示屏（绿色三段条）、通风栅格；
- 底部黑黄警戒条纹。

## 预览与验证

- 四张预览由作者在 Blockbench 视口手动截图导出（非脚本渲染）：正视（屏幕面，−Z 北向）/ 背视 / 侧视 / 斜视（透视），1200×900；
- 离线渲染器（`reference/render_bbmodel.js`，ZYX 欧拉序）复算世界包围盒并渲染比对预览轮廓；
- `reference/validate_deliverable.js` 在仓库根可重跑：命名、格式、计数、贴图 2 的幂与真透明、贴图与内嵌字节一致、预览尺寸等；
- bbmodel 已由作者在 Blockbench 5.x 中重开编辑并保存（2026-11-15）；
- 已接入 `src/main/resources`（`battery.geo.json` + 方块注册，按电量切换四段贴图）；游戏内验收状态见 `validation.json` 的 `runtime.in_game_verified`。
