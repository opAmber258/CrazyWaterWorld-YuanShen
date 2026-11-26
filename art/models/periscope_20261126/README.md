# 潜望镜 Periscope · 候选 B（1.3 吊装组合体）

- 日期：2026-11-26｜作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图、预览截图人工完成；AI 仅承担交付打包与离线校验。分工见 [validation.json](validation.json) 的 `contributors`。
- 格式：Blockbench 5.x `java_block`（Java 方块模型路线；不产出 `.geo.json`）。

## 来历与版本

- 资产：原版潜望镜（定名出处与中英文标识见 `validation.json.sources`）。
- 本版为候选 B 的 **UV 修正重制版**，替代 2026-11-25 撤回版（后者贴图/UV 与模型不匹配）。
- 完整吊装组合体：镜体 + 天花板吊架（含配套底座的全部几何）；吊架贴顶、镜体悬垂，与原版吊装形态一致。

## 设计意图与识别特征

- 银灰吊装架、黄黑警戒带、竖井立柱的条纹台阶、底部目镜轮盘与把手。
- 尺寸与比例由作者按原作观感自定；实测规格、包围盒与验收证据统一记录在 [validation.json](validation.json)（共用工具提取）。

## 使用限制

- 旋转 cube 占比高且含多轴/超限角度，超出 1.20.1 原版 Java 模型 JSON 表达范围；运行时接入按已落地的 GeckoLib Bedrock geometry 路线（参照电池（设备）先例）。
- 待完成验收：作者在 Blockbench 重开确认、原生导出同名 Java 模型 JSON、预览复核——当前状态见 `validation.json` 的 `unresolved`。
- 未接入 `src/main/resources`，未进入游戏。

## 预览

- 四张预览为作者提供的 Blockbench 视口截图（正/背/侧正交 + 斜视透视）。
