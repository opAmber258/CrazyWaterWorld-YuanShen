---
name: deliver-model-asset
description: Deliver a Bedrock-format Blockbench asset end-to-end (ships a geo.json) — naming, four-piece package, delivery checks, direct main push, and Discussion #39 registration. Not for java_block modeling; use blockbench-modeling.md for that format.
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
last_updated: 2026-11-15
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

1. **定位并读短状态**：已有资产先运行 `python tools/asset_delivery.py status <资产目录>`。只在处理相应问题时展开 `--full`、详细审计或原文件；新资产按交付约定 §1 查证定名并建立人工元数据。
2. **完成制作**：几何与像素以实际文件为准，格式/四件套按交付约定 §3。风格入口遵守工作目录的 art-vault 规则；不要加载无关资产与历史报告。
3. **更新唯一验证记录**：运行 `refresh`，将人工说明留在 README，把计数、规格、原生测量和验收记在 validation。首次使用与历史迁移命令按需查 [ASSET_VALIDATION.md](../../docs/ASSET_VALIDATION.md)，不复制校验器到每件资产。
4. **补必要验收**：按 status 中的 `unverified` / `stale` 定位实际需检查的文件，实际操作后再 `attest`。原生截图优先；不为核对截图另造渲染器。工具不能自动完成原生重导出或视觉验收。
5. **发布**：按 [publish.md 的纯美术分流](publish.md#纯美术资产直接推送-main) 直推 main；交付检查由约定 §6 定义，不在此重复规则。
6. **登记**：确认远端后，用 `card --commit <已推送commit>` 生成候选条目，仅更新 Discussion #39 对应资产；使用同一提交的验证与预览链接，不另手写规格。登记细则按交付约定 §8。
7. **后续集成**：维护者评选后由集成者按约定 §5 接入运行时。美术发布不自动启动游戏或宣称运行时验证通过。

## 已知操作问题

- 总包围盒通过不能替代封片局部检查；有 alpha 裁形时核对实际不透明区域和厚度方向。
- 脚本解析或 cube 数相同不能代替原生导出比对。
- status 为 legacy 时先查记录格式与迁移命令，不直接覆盖旧报告，也不全量迁移历史资产。
