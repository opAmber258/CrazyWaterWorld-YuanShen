# 接线盒 JunctionBox · 候选 C（1.1 修订版）

- 日期：2026-11-26｜作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图绘制人工完成；AI 承担交付打包、预览渲染与离线校验。分工见 [validation.json](validation.json) 的 `contributors`。
- 格式：Blockbench 5.x `java_block`（Java 方块模型路线；不产出 `.geo.json`）。
- 类型：道具（承候选 A/B 小节；壁挂电力设施）。

## 来历与版本

- 资产：原版接线盒（定名出处与中英文标识见 `validation.json.sources`）。
- 候选谱系：候选 A = AI 生成的 Bedrock 方案（12 cubes / 5 bones）；候选 B = 人工 java_block 重制 1.0；**本候选 C = 1.1 修订版**。
- 与候选 B（1.0）的差异：**几何完全一致**（22 cubes/132 面）；贴图约 5% 像素修订；分组结构/元素归属有调整（分组名不变：接线盒/门板/表面/底座/罐/电线×3）。

## 设计意图与识别特征

- 壁挂电箱：正面门板可开启，开门可见内部腔体、内罐、白色接线铭牌与铰链。
- 底部黄黑警戒条、面板边框与门锁件。
- 尺寸与比例由作者按原作观感自定；实测规格、包围盒与验收证据统一记录在 [validation.json](validation.json)。

## 使用限制

- 10/22 cube 带旋转，其中 9 个为超限单轴角度，超出 1.20.1 原版 Java 模型 JSON 角度限制；接入按 GeckoLib Bedrock geometry 路线处理。
- 含 2 个零厚度面片、3 处 `inflate` 细节。
- 待完成验收：作者在 Blockbench 重开确认、原生导出同名 Java 模型 JSON、预览复核——当前状态见 `validation.json` 的 `unresolved`。
- 未接入 `src/main/resources`，未进入游戏。

## 预览

- 四张预览为**作者提供的 Blockbench 视口截图**（正/背/侧正交 + 斜视透视；"正面"为开门侧）。
