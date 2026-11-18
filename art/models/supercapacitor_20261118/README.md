# 超级电容器 Supercapacitor · 候选 A（Java 方块模型）

- 日期：2026-11-18
- 作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图绘制、预览截图全部人工完成；AI（GLM-5.3-Flash，经 ZCode CLI）仅承担交付打包、离线一致性校验与文档起草，未参与几何、UV、贴图、预览的制作。
- 格式：Blockbench 5.x `java_block`（`java_block_version` 26.3），Java 方块模型路线；按约定不产出 `.geo.json`。
- 登记类型：方块（电力设施）。

## 原作对应与命名

- 资产名：超级电容器 Supercapacitor；mod 标识 `supercapacitor`；目录 `supercapacitor_20261118/`。
- 官方简中名「超级电容器」查证自游戏加载提示原文（潜渊症中文维基收录）：
  > "超级电容器不能像电池那样储存很多电能，但能在瞬间释放高压脉冲电流，一般用于驱动轨道炮或其他需要高压脉冲电流的设备。"
- 英文名 Supercapacitor；条目标识推断为原版 `entityname.supercapacitor`（identifier `supercapacitor`，按原版电气组件命名惯例）。本机无游戏安装目录，未直接核验 `Content/Texts/` XML，评选前可由维护者对照游戏文件复核。

## 交付内容

| 文件 | 说明 |
| --- | --- |
| `supercapacitor.bbmodel` | 工程文件（Blockbench 5.x，java_block），贴图内嵌，可重开编辑 |
| `supercapacitor_tex.png` | 贴图（128×128 RGBA，自 bbmodel 内嵌逐字节提取，没电状态） |
| `preview_front.png` / `preview_back.png` / `preview_side.png` / `preview_oblique.png` | 四向预览，1200×900，纯黑底、无网格无 UI |
| `README.md` / `validation.json` / `structure_audit.json` | 说明与结构化验证 |
| `reference/screen_states/` | 电量屏六个状态（没电 / 1–4 格 / 满格）的完整 128×128 贴图，见下文 |
| `reference/` | 离线校验脚本，见 `reference/README.md` |

## 规格

- 37 cubes / 222 声明面 / 20 分组 / 0 bones / 0 动画 / 无隐藏元素；
- 旋转 cube：30/37 —— 含 `±22.5°` / `±45°` 合法单轴旋转，以及 `56.31°` / `±35°` / `±12.5°` / `±17.5°` 等超限角度与多轴自由旋转（如 `[-27.16, 51.43, -33.27]`，超出 1.20.1 原版限制，见下文）；
- 世界包围盒 24.000 × 22.400 × 13.000 模型单位（X −4..20，Y −0.399..22.001，Z 3..16），按 16 单位/格换算为 1.500 × 1.400 × 0.8125 格；
- 贴图 128×128 RGBA（16384 像素，其中 13626 透明像素为真透明）；
- 零厚度贴片 ×4（左前/左后贴片，1×0×3.60555）。

## 电量屏与贴图状态

正面电压/电量指示为**五段进度条**，工程内以贴图图层表现六个状态：

| 状态 | 图层名 | 文件（reference/screen_states/） |
| --- | --- | --- |
| 没电（0/5，主贴图同款） | layer-没电 | `supercapacitor_tex_empty.png` |
| 1 格（1/5） | layer-一格电 | `supercapacitor_tex_1of5.png` |
| 2 格（2/5） | layer-两格电 | `supercapacitor_tex_2of5.png` |
| 3 格（3/5） | layer-三格电 | `supercapacitor_tex_3of5.png` |
| 4 格（4/5） | layer-四格电 | `supercapacitor_tex_4of5.png` |
| 满格（5/5） | layer-满格电 | `supercapacitor_tex_5of5.png` |

状态差异集中在屏条 UV 区（x49-53 / y14-15，逐格递增 6→13→18→24→30 px）。主贴图为没电状态，与四张预览一致。集成侧可用 blockstate 按方块内电量切换贴图。

## 尺寸依据与换算

按 16 模型单位 = 1 格换算：1.500（宽）× 1.400（高）× 0.8125（厚）格。原版中超级电容器为船内电力组件（储能 + 脉冲放电），模型体量约为 1.5×1.4 格、略高于单格，属作者按原作精灵图观感自定；实际占位/碰撞由集成侧 blockstate 与方块属性决定。

## 旋转与格式注意（接入前必读）

- 30 个 cube 带旋转，其中相当数量为**超限角度**（56.31°/±35°/±12.5°/±17.5°）或**多轴自由旋转**，超出 Minecraft 1.20.1 原版 Java 模型 JSON 的表达范围（单轴 ±22.5°/±45°）。集成侧接入 `src/main/resources` 前需确认目标格式（升级 MC 版本 / 自定义渲染 / 旋转烘焙为几何），再用 Blockbench 原生导出；本轮因此未随附 Java 方块模型 JSON。
- 工程无 display 设置（方块渲染不需要）。

## 保留的识别特征

- 银灰双层柜体与顶部凹槽面板；
- 背面/侧面黑色 V 形支撑柱与粗电缆、底部伸出电缆头；
- 正面深色指示窗（五段电量条）、白底铭牌、通风格栅与暗红标签；
- 底部黑黄警戒条纹。

## 预览与验证

- 四张预览由作者在 Blockbench 视口手动截图导出（非脚本渲染）：正/背/侧为正交、斜视为透视，1200×900；
- 离线渲染器（`reference/render_bbmodel.js`，ZYX 欧拉序）复算世界包围盒并渲染比对预览特征（柜体轮廓、V 形支撑柱、警戒条、电缆脚一致）；
- `reference/validate_deliverable.js` 在仓库根可重跑：命名、格式、计数、贴图 2 的幂与真透明、贴图与内嵌字节一致、预览尺寸等；
- bbmodel 已由作者在 Blockbench 5.x 中编辑并保存（2026-11-18）；
- 未接入 `src/main/resources`，未进入 Minecraft 客户端验证。
