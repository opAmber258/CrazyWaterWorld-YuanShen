---
name: deliver-model-asset
description: Deliver a Bedrock-format Blockbench asset end-to-end (ships a geo.json) — naming, four-piece package, geometry rules, PR, and Discussion #39 registration. Not for java_block models; use blockbench-modeling.md for those.
triggers:
  - "new model"
  - "new asset"
  - "blockbench"
  - "bbmodel"
  - "geo.json"
  - "deliver asset"
  - "资产"
  - "模型"
  - "推资产"
edges:
  - target: context/conventions.md
    condition: when the asset must be wired into Java resources or registered blocks/items
  - target: patterns/run-tests.md
    condition: when the asset needs integration verification beyond art review
last_updated: 2026-11-12
mex:
  id: mx_01M71QR5WFYRJP79BPJRV1HS3F
  type: pattern
  status: promoted
  revision: 3
  title: deliver-model-asset
  relations:
    - type: related_to
      target: mx_01M6FTBTB77P4PT8TV3HT78NGZ
      note: when the asset must be wired into Java resources or registered blocks/items
    - type: related_to
      target: mx_01M6FTV7PNBD1ZG84B1K5GCG1N
      note: when the asset needs integration verification beyond art review
---

# Deliver a Model Asset

## Context

模型资产的完整规范在 [`docs/ASSET_DELIVERY_CONVENTION.md`](../../docs/ASSET_DELIVERY_CONVENTION.md) —— **本文不重复规则，只给执行顺序和每步的判定点**。规则冲突时以该文档为准。

资产分两侧：**美术侧**（Blockbench 交付四件套）与**运行时侧**（进 `src/main/resources` 并注册）。美术作者只做前者；后者由集成者在资产升入「正式资产」后进行。

资产的登记处是 [Discussion #39](https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39)，分「正式 / 待评选 / 待制作推送」三类。

## Steps

### 1. 定名（不可跳过）

1. 中英两名**分别查证**：中文译名查游戏安装目录里 `Content/Texts/SimplifiedChinese/` 下的简中文件 `<entityname.标识>`；官方英文名另查同目录 `Content/Texts/English/` 的英文文本同标识条目，或 `Content/Items/` 下的实体定义 XML。**这两处都在游戏安装目录，不在本仓库内**；简中文件的条目值只有中文，不能据此推断英文名。
2. 资产名 = 中文译名 + 英文驼峰，例如 `核反应堆 NuclearReactor`；mod 标识用小写下划线。
3. 查不到条目 → 是项目定名，必须在讨论条目里注明"非原版实体"。

**判定点**：译名有出处才能继续。凭印象命名是本流程最常见的返工原因。

### 1b. 标注制作来源

- 人工制作 → `制作来源：人工（<贡献者>）`，必须写贡献者名称
- AI 制作 → `制作来源：AI（<模型名>）`，写完整模型名与工具；Discussion 候选在复选框后加 `🤖`（写作 `- [ ] 🤖 候选 A…`；写在行首会让 GitHub 不再识别为任务项）；PR 描述注明 AI 参与范围
- 人机协作 → `制作来源：人工 + AI（<贡献者> + <模型名>）`，另起一段说明分工
- **`validation.json` 必须含 `contributors`**：`{"human": [...], "ai": [{"name","tool","role"}], "division"}`，并写 `creation_source` 摘要与中英显示名

示例：`制作来源：人工 + AI（TakaraMiyuki + GPT 6.1 Sol）`

**判定点**：贡献者名称与 AI 模型名缺一即不合规；标注不降低评审强度。

### 2. 建模

按作者自定的建模手法与尺寸完成几何；约定不设建模规范（见约定第 4 节）。

**判定点**：导出后用脚本复算包围盒，与作者 README 里记录的尺寸一致再往下走。

### 3. 出四件套 + 说明文件

```text
art/models/<标识>_<YYYYMMDD>/
├── <标识>.bbmodel
├── <标识>.geo.json
├── <标识>_tex.png
├── preview_{front,back,side,oblique}.png
├── README.md            (原作查证 + 尺寸换算过程)
├── validation.json      (尺寸/计数/检查结论)
└── reference/           (原作裁切、palette.json、脚本)
```

**判定点**：四件套任一缺失不要开 PR。`small_pump` 就是反面例子（只有 bbmodel + geo，至今无法评选）。

### 4. 自查

按约定第 6 节逐项过。**几何改过就必须重出四向预览** —— 预览与几何不一致会被评审直接退回。

预览渲染注意两个已知坑：

- 正交（正/背/侧）与透视（斜视）的深度方向必须统一为"越近越小"；
- 朝上的面（up）角点顺序决定法线朝向，写错会让顶面全部被背面剔除。

**预览怎么出**：优先用 Blockbench 内置预览截图（无需额外脚本）。若改用自建脚本渲染，按约定 3.3 该脚本**必须放进 `reference/` 并能在仓库内重跑**，否则评审无法复现预览。确实无法入库的脚本（一次性探索、交互式会话片段），README 要写明**它的用途**和**为什么无法复现**；若它正是预览的产出工具，再补一句预览的实际生成方式，别让读者以为是 Blockbench 截的。

### 5. 推送

默认走任务分支 + PR，不与代码改动混提。PR 描述写：资产名、标识、类型、尺寸、cube/bone 计数、原作对应条目、预览是否已重出。

`.work/` 与 `_archive/` **不入库**。

### 6. 登记 Discussion #39

1. 「待制作/推送」表格移除该行（若在）；
2. 「待评选」建小节：几何规格 + commit + 定位链接 + 内嵌预览（URL 带 `?v=`）;
3. 只改自己资产对应的条目，章节标题与规范说明不得动。

**判定点**：图片文件更新后必须递增 `?v=`，否则 GitHub Camo 缓存会让页面一直显示旧图。

### 7. 评选与整合

维护者在候选条目勾选通过 → 移入「正式资产」→ 集成者按约定第 5 节接入 `src/main/resources` 并做游戏内验证。落选版本进 `art/models/_archive/`（不入库）。

## 常见错误

| 现象 | 原因 |
| --- | --- |
| 讨论里图片不更新 | 缺 `?v=` 或未递增 |
| 总包围盒正常但封片局部外翻 | 对照相邻斜壁检查可见表面与厚度方向；带 alpha 裁形时检查实际不透明像素范围，不能只看整个模型的包围盒 |
| 顶面/细节消失 | 预览渲染器 up 面法线反了，或正交深度方向写反 |
| 资产无法评选 | 缺贴图/预览（四件套不全） |
| PR 被要求返工 | 命名无出处、预览与几何不一致、混入代码改动 |
| 复制的资产尺寸异常 | 后期等比放大只改了部分文件（几何/轴心/可见框要一起改） |
