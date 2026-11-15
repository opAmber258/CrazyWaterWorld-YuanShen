---
name: publish
description: 发布分流：纯美术资产自查后直推 main；代码、共享工具与规则变更做 MEX 记账并走 PR。
triggers:
  - "走流程"
  - "发布"
  - "推到仓库"
  - "publish"
edges:
  - target: context/conventions.md
    condition: when the change touches code conventions or the verify checklist
grounds_to: []
last_updated: 2026-11-15
mex:
  id: mx_01M6Y1VF5VEPY23G7RHKEJ5PDJ
  type: pattern
  status: promoted
  revision: 2
  title: publish
  relations:
    - type: related_to
      target: mx_01M6FTBTB77P4PT8TV3HT78NGZ
      note: when the change touches code conventions or the verify checklist
---

# 发布（`/publish`）

## 何时用

用户说「走流程 / 发布 / 推到仓库」，或调用 `/publish`。先判断发布范围；授权只涵盖用户指定的内容，**实际 pull 仍需单独授权**。

## 纯美术资产：直接推送 main

仅发布 `art/models/<标识>_<日期>/` 内的资产及其配套文档、验证和 reference 时，按 `docs/ASSET_DELIVERY_CONVENTION.md` §7 执行，不创建 PR、不等待 CodeRabbit 或 PR 门禁，也不再次询问是否直推；此分流适用于所有模型格式。

1. 确认资产、目标路径与用户发布授权；制作或预览请求不含发布授权。
2. 先读共用工具的短 status，按交付约定 §6 补必要自查；记录格式或迁移命令只在需要时查 `docs/ASSET_VALIDATION.md`。不要重复读取/手写多份规格与通过清单。
3. fetch 最新 `origin/main`，在干净、隔离的发布工作区准备仅含本资产的提交；可基于远端跟踪分支，不要求拉取其他工作区的本地 `main`。
4. 普通推送上游 `main` 并核实远端 commit 与文件；不强推。远端前进时重新 fetch 并保留其变更；缺上游写权限时报告并交维护者处理，不自动开 PR。
5. 用共用工具的 card 生成 Discussion #39 待评选条目；报告 commit、验证与登记链接，规格与来源不另行手填。纯资产文档与登记即本轮记账，不额外强制更新 ROUTER 或申请 `mex-skip`。

涉及 `src/`、共享 `tools/`、发布规则或 CI 配置时拆分后走下面的通用流程；共享约定与项目能力变化仍需 MEX 记账。

## 通用发布：任务分支与 PR

1. `git status` + `git fetch`；比较**本地 `main`** 与 `origin/main`（不能用 `HEAD`，任务分支可能已经领先），落后就停下，提醒需要 pull 授权。
2. **MEX 记账（必做）**：更新 `ROUTER.md` 的 Current Project State；可复用的踩坑写进 `patterns/` 并登记 `INDEX.md`；长期约定写进 `context/`；设计依据写进 `docs/`。确实无需文档时，发布时给 PR 加 `mex-skip` 标签并写明原因。
3. 从最新 `main` 建 `<type>/<slug>` 分支；若当前已在任务分支且有未发布提交，**复用当前分支**，不要重开分支丢提交。代码与文档改动一起提交（中文信息，写动机与影响面）。
4. 推送分支 → 创建或复用 PR：先 `gh pr list --head "<branch>" --state open --json number,headRepository` 并核对源仓库（`--head` 不支持 `owner:branch`，会跨 fork 命中同名分支），有则复用更新，为空才 `gh pr create`；正文按模板填写，明确 MEX 结论。
5. 等 CodeRabbit 与 `mex-guard` 检查；每修完意见推送新提交后，重新等最新提交上的检查结果；未解决线程清零前不合并。
6. 合并 PR，删除本地和远端任务分支。
7. 报告：合并 hash、L1/实机验证、文档改动清单、遗留风险。

## 门禁与豁免

- `.github/workflows/mex-guard.yml`：只统计 `src/` 与 `tools/`——这两处有改动、但 `.mex/` 与 `docs/` 都没有 → 检查变红。其他路径（如 `build.gradle`）不会触发。
- 通用 PR 的文档门禁豁免方式是 `mex-skip` 标签；纯资产直推不创建 PR，无需此标签。门禁只校验标签本身，不校验原因；原因靠 reviewer 把关。
- 仓库尚未开启分支保护，所以红叉**不会**阻止合并；看到红叉必须人工确认，不能当没看见。

## 已知坑

- 含 `.github/workflows/` 的提交需要 token 的 `workflow` scope（`gh auth refresh -s workflow`），否则 push 被 GitHub 拒绝。
- 合并后要切回 `main` 并 pull，否则本地工作区还是旧代码。2026-11-08 的克苏鲁调参就因此"没生效"：提交推送合并都在云端完成，本地 `main` 没快进，`runClient` 编译的是旧 shader。
