---
name: publish
description: 发布流程（任务分支 → PR → 合并 → 删分支）；MEX 记账是固定第 2 步，代码与文档一起进 PR。
triggers:
  - "走流程"
  - "发布"
  - "推到仓库"
  - "publish"
edges:
  - target: context/conventions.md
    condition: when the change touches code conventions or the verify checklist
grounds_to: []
last_updated: 2026-11-09
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

用户说「走流程 / 发布 / 推到仓库」，或调用 `/publish`。授权范围是完整发布链路（分支 → 提交 → 推送 → PR → 审查 → 合并 → 删分支）；**pull 仍需单独授权**。

## 步骤

1. `git status` + `git fetch`；本地 `main` 落后 `origin/main` 就停下，提醒需要 pull 授权。
2. **MEX 记账（必做）**：更新 `ROUTER.md` 的 Current Project State；可复用的踩坑写进 `patterns/` 并登记 `INDEX.md`；长期约定写进 `context/`；设计依据写进 `docs/`。确实无需文档时，发布时给 PR 加 `mex-skip` 标签并写明原因。
3. 从最新 `main` 建 `<type>/<slug>` 分支；代码与文档改动一起提交（中文信息，写动机与影响面）。
4. 推送分支 → `gh pr create`，正文按 `.github/pull_request_template.md` 填写，明确 MEX 结论。
5. 等 CodeRabbit 与 `mex-guard` 检查；未解决线程清零前不合并。
6. 合并 PR，删除本地和远端任务分支。
7. 报告：合并 hash、L1/实机验证、文档改动清单、遗留风险。

## 门禁与豁免

- `.github/workflows/mex-guard.yml`：`src/` 或 `tools/` 有改动、但 `.mex/` 与 `docs/` 都没有 → 检查变红。
- 唯一豁免：给 PR 加 `mex-skip` 标签，并在说明里写一行原因。
- 仓库尚未开启分支保护，所以红叉**不会**阻止合并；看到红叉必须人工确认，不能当没看见。

## 已知坑

- 含 `.github/workflows/` 的提交需要 token 的 `workflow` scope（`gh auth refresh -s workflow`），否则 push 被 GitHub 拒绝。
- 合并后要切回 `main` 并 pull，否则本地工作区还是旧代码。2026-11-08 的克苏鲁调参就因此"没生效"：提交推送合并都在云端完成，本地 `main` 没快进，`runClient` 编译的是旧 shader。
