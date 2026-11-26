# 潜望镜底座 PeriscopeStand · 候选 B（1.1）

- 日期：2026-11-26｜作者：Han-xing-official
- 制作来源：人工（Han-xing-official）—— 建模、贴图、预览截图人工完成；AI 仅承担交付打包与离线校验。分工见 [validation.json](validation.json) 的 `contributors`。
- 格式：Blockbench 5.x `java_block`（Java 方块模型路线；不产出 `.geo.json`）。

## 来历与版本

- 资产：**非原版实体，项目定名**——原版无独立"潜望镜底座"条目，本资产为依据原版吊装式潜望镜安装机构拆分的独立方块（定名出处见 `validation.json.sources`）。
- 本版为候选 B 的修正版，替代 2026-11-25 撤回版（贴图/UV 与模型不匹配）。交付采用工程内嵌贴图（与作者预览一致）；工作区外置 `潜望镜基座1.1.png` 经渲染核对与模型 UV 不匹配，未采用。
- 配套：潜望镜 Periscope（其 1.3 组合体已包含本底座全部几何）。

## 设计意图与识别特征

- 方形吊装架、四角插件与线头、中央承托环（插槽）、黄黑警戒带。
- 尺寸与比例由作者按原作观感自定；实测规格、包围盒与验收证据统一记录在 [validation.json](validation.json)。

## 使用限制

- 旋转 cube 占比高且含多轴/超限角度，超出 1.20.1 原版 Java 模型 JSON 表达范围；接入按 GeckoLib Bedrock geometry 路线。
- 待完成验收：作者在 Blockbench 重开确认、原生导出同名 Java 模型 JSON、预览复核——当前状态见 `validation.json` 的 `unresolved`。
- 与潜望镜本体的连接逻辑由集成侧方块实体实现；未接入 `src/main/resources`。

## 预览

- 四张预览为作者提供的 Blockbench 视口截图（正/背/侧正交 + 斜视透视）。
