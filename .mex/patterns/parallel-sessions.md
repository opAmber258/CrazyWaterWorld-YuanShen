---
name: parallel-sessions
description: 多个 Agent 会话并发工作时的协作规范 — 工作区隔离、目录占用登记、提交边界、基线新鲜度。Load when another session may be editing the repo, when picking up work mid-stream, or before touching shared files.
triggers:
  - "并发"
  - "另一个会话"
  - "别的 agent"
  - "worktree"
  - "工作区脏"
  - "冲突"
  - "handoff"
  - "交接"
edges:
  - target: patterns/publish.md
    condition: when the work is ready to commit or push
last_updated: 2026-11-10
mex:
  id: mx_01M71WCP35T4RTQKT1W0KJF7YV
  type: pattern
  status: promoted
  revision: 2
  title: parallel-sessions
  relations:
    - type: related_to
      target: mx_01M6Y1VF5VEPY23G7RHKEJ5PDJ
      note: when the work is ready to commit or push
---

# 多会话并发协作

## Context

本仓库常有多个 Agent 会话同时工作：一个在改代码，另一个在做美术资产，第三个在写文档。它们共享**同一个工作区目录**（除非显式建 worktree），因此会出现三类问题：

1. **工作区混放**：分支上只有导航代码，工作区里却堆着美术资产和文档，`git add -A` 会把无关内容提交进去。
2. **基线落后**：会话 A 基于旧的工作区副本改文件，覆盖掉会话 B 刚推送的新内容（2026-11-10 就这么丢了 4 个 pattern 索引条目）。
3. **互相踩脚**：两个会话同时改同一个文件，后者覆盖前者。

工作区是 Git 的单一实例，**分支 ≠ 工作区**。停在 `feat/xxx` 分支上，不代表工作区里只有该分支的内容。

## 开工前

1. 读 [`.mex/relays/concurrent-work-ledger.md`](../relays/concurrent-work-ledger.md)，确认你要动的目录有没有被别人占用。
2. `git status --short` 看当前工作区状态。**如果出现不是你产出的未提交文件，不要碰、不要提交**，在回复里点出来。
3. 判断手上工作属于哪一类，走对应通道：

| 工作类型 | 通道 | 说明 |
| --- | --- | --- |
| 代码功能（分支已在开发） | 当前分支 | 正常提交，注意只 `git add` 自己的文件 |
| 独立交付（美术、文档、约定） | **独立 worktree**，基于 `origin/main` | 不与开发分支的工作区混放 |
| 小修补丁 | 独立 worktree + 短分支 + PR | 同上 |

## 动手前

在占用台账里登记一行（占用者 / 目录 / 分支或工作区 / 开始时间 / 说明）。收工时删掉或标记完成。

## 修改共享文件时

`.mex/ROUTER.md`、`.mex/patterns/INDEX.md`、`.mex/context/*`、`docs/*`、根 `AGENTS.md` 属于共享结构，规则更严：

1. **先 `git fetch origin`**，确认基线。
2. **在基于 `origin/main` 的 worktree 里改**，不要拿本地工作区的副本当基准 —— 本地可能就是三天的旧版。
3. **只追加自己的条目**，不要整文件替换。整文件覆盖是丢内容的头号原因。
4. 改完跑 `mex check`（本机已装 CLI）确认没有 MISSING_PATH / KNOWLEDGE_NOT_ADOPTED。

## 建 worktree 的推荐做法

```bash
# 从最新远端基线拉一个独立目录，不动主工作区
git fetch origin
git worktree add ../<task-name> -b <type>/<short-name> origin/main
# 在那里做完、提交、推送、开 PR
git worktree remove ../<task-name>
```

好处：主工作区里别人的未提交改动完全不受影响，你的改动也不会混进别人的提交。

## 提交边界

- **只 `git add` 明确列出的路径**，不要 `git add -A` / `git add .`。
- 提交前再跑一次 `git diff --cached --name-only`，确认列表里每一项都是自己的产出。
- 发现自己的改动里混入了别人的文件 → 停下，`git reset <file>` 移除，不要直接提交。

## 收工

- 台账里自己的行：删除或标记完成。
- 主工作区恢复到「只剩别人的东西」的状态。
- 如果有跨会话必须交接的结论，用 MEX relay（`/skill mex-relay`）写持久交接，而不是只在对话里说 —— 对话不会传给下一个会话。

## 已知坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 提交里出现别人的美术资产 | 用了 `git add -A` | `git reset` 掉，改成精确路径 |
| 改共享文档后别人的条目消失 | 拿本地旧副本整文件覆盖 | 从 `origin/main` 重新取基准，只追加 |
| `mex check` 大量 MISSING_PATH | 本地工作区落后，缺新文件 | 在基于 `origin/main` 的 worktree 里检查 |
| 分支 ahead/behind 数字很大但工作区没动过 | 长期停在旧分支，远端持续推进 | 属正常，计划性落后不算故障；重点是别让工作区内容误入该分支 |
