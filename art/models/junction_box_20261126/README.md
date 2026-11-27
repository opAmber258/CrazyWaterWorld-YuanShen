# 接线盒 JunctionBox · 候选 B（1.0 人工重制）

- 日期：2026-11-26｜作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图、预览截图人工完成；AI 仅承担交付打包与离线校验。分工见 [validation.json](validation.json) 的 `contributors`。
- 格式：Blockbench 5.x `java_block`（Java 方块模型路线；不产出 `.geo.json`）。
- 类型：道具（承候选 A 小节；壁挂电力设施）。

## 来历与版本

- 资产：原版接线盒（定名出处与中英文标识见 `validation.json.sources`，沿候选 A 的查证记录）。
- 本版为**人工重制**的候选 B：候选 A 为由 AI 生成的 Bedrock 方案（12 cubes / 5 bones，带功能性分组），本版为人工建模的 java_block 独立方案，门板、内罐与走线为可见结构。
- 与候选 A 无派生关系，可并列评选。

## 设计意图与识别特征

- 壁挂电箱体：正面为可开启门板，开门可见内部腔体、内罐、白色接线铭牌与铰链结构。
- 底部黄黑警戒条、面板边框与门锁件。
- 尺寸与比例由作者按原作观感自定；实测规格、包围盒与验收证据统一记录在 [validation.json](validation.json)（共用工具提取）。

## 使用限制

- 10/22 cube 带旋转，其中 9 个为超出 1.20.1 原版限制的单轴角度；接入时按 GeckoLib Bedrock geometry 路线处理（参照电池（设备）先例）。
- 含 2 个零厚度面片、3 处 `inflate` 细节。
- 待完成验收：作者在 Blockbench 重开确认、原生导出同名 Java 模型 JSON、预览复核——当前状态见 `validation.json` 的 `unresolved`。
- 未接入 `src/main/resources`，未进入游戏。

## 预览

- 四张预览为作者提供的 Blockbench 视口截图（正/背/侧正交 + 斜视透视；"正面"为开门侧）。
