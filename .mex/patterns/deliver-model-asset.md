---
name: deliver-model-asset
description: Deliver a Blockbench model asset end-to-end — naming, four-piece package, geometry rules, PR, and Discussion #39 registration. Load when adding or updating a model, prop, block, item, or creature asset.
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
last_updated: 2026-11-10
---

# Deliver a Model Asset

## Context

模型资产的完整规范在 [`docs/ASSET_DELIVERY_CONVENTION.md`](../../docs/ASSET_DELIVERY_CONVENTION.md) —— **本文不重复规则，只给执行顺序和每步的判定点**。规则冲突时以该文档为准。

资产分两侧：**美术侧**（Blockbench 交付四件套）与**运行时侧**（进 `src/main/resources` 并注册）。美术作者只做前者；后者由集成者在资产升入「正式资产」后进行。

资产的登记处是 [Discussion #39](https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39)，分「正式 / 待评选 / 待制作推送」三类。

## Steps

### 1. 定名（不可跳过）

1. 在 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 查 `<entityname.标识>`，拿到中文官方译名与英文官方名。
2. 资产名 = 中文译名 + 英文驼峰，例如 `核反应堆 NuclearReactor`；mod 标识用小写下划线。
3. 查不到条目 → 是项目定名，必须在讨论条目里注明"非原版实体"。

**判定点**：译名有出处才能继续。凭印象命名是本流程最常见的返工原因。

### 2. 建模

按约定第 4 节：原点在底座中心、Y 贴地 `0`、X/Z 居中、宽度按格取整（1 格 = 16 BB）、per-face UV、不加多余缩放。

**判定点**：导出后用脚本复算包围盒，与预期格宽一致再往下走。

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

项目内可复用的渲染脚本：`art/models/.work/render_nav_ray.py`（光线步进，遮挡天然正确，参数 `az el fov target_y fill` 命令行传入）。`.work/` 不入库，脚本需要时从历史提交或本地取回。

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
| 顶面/细节消失 | 预览渲染器 up 面法线反了，或正交深度方向写反 |
| 资产无法评选 | 缺贴图/预览（四件套不全） |
| PR 被要求返工 | 命名无出处、预览与几何不一致、混入代码改动 |
| 复制的资产尺寸异常 | 后期等比放大只改了部分文件（几何/轴心/可见框要一起改） |
