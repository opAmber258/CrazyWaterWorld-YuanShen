---
name: parallel-sessions
description: 多人协作仓库里同时使用多个工作区（worktree）时的规范 — 工作区卫生、worktree 占用登记、提交边界、基线新鲜度。Load when another worktree may be active in this checkout, when the working tree contains files you did not create, or before touching shared scaffold files.
triggers:
  - "worktree"
  - "工作区脏"
  - "并发"
  - "冲突"
  - "交接"
  - "handoff"
  - "不是我的改动"
edges:
  - target: patterns/publish.md
    condition: when the work is ready to commit or push
last_updated: 2026-11-10
mex:
  id: mx_01M71WCP35T4RTQKT1W0KJF7YV
  type: pattern
  status: promoted
  revision: 4
  title: parallel-sessions
  relations:
    - type: related_to
      target: mx_01M6Y1VF5VEPY23G7RHKEJ5PDJ
      note: when the work is ready to commit or push
---

# 多工作区协作

## Context

本仓库是多人协作项目：上游 `main` 由多位协作者通过 PR 推进（截至 2026-11-10，分叉点之后的 117 个提交来自 4 位作者），本地也常常同时存在多个 `git worktree`。

这带来两类**互相独立**的问题，不要混为一谈：

### 问题一：工作区卫生（单人也会犯）

Git 的工作区是**仓库级单一实例**。在一个 checkout 里：

- 停在某个分支上，不代表工作区里只有该分支的内容；
- 未提交的文件不会因为切换分支而消失，也不「归属」某个分支；
- 长期停在功能分支上（例如等上游稳定后再同步），期间做的其它工作会全部堆进这个工作区。

后果：`git add -A` 会把美术资产、文档、其它子系统的改动一起提交进功能分支。

**判据**：`git log <branch> --name-only` 显示该分支只碰过若干个源码文件，而 `git status` 里却有一大堆无关文件 —— 这就是工作区卫生问题，与并发无关。

### 问题二：多 worktree 并行

同一个仓库可以有多个 `git worktree`，各自检出不同分支、位于不同目录，**互不共享文件**，但**共享同一个 `.git` 目录和远端**。

真实观测（2026-11-10）：主工作区之外另有一个 `welding_tool_main_push_20261110`（detached HEAD），正在准备推送另一个资产。

这类并行本身是**推荐做法**，风险只在于两件事：

- 两个 worktree 修改相同文件 → 后者覆盖前者；
- 一个 worktree 基于过期的 `origin/main` 提交 → 覆盖掉别人已推送的内容。

## 独立交付放独立 worktree

与功能分支无关的产出（美术资产、文档、约定、一次性补丁）一律走独立 worktree：

```bash
git fetch origin
git worktree add ../<task-name> -b <type>/<short-name> origin/main
# 在 ../<task-name> 里工作、提交、推送
git worktree remove ../<task-name>
```

好处：主工作区的未提交改动完全不受影响，你的改动也不会混进别人的提交。

## 动手前

1. `git worktree list` —— 看清本机有几个工作区，各自检出什么。
2. `git status --short` —— **如果出现不是你产出的未提交文件，不要碰、不要提交**，在回复里点出来。
3. 读 [`.mex/relays/concurrent-work-ledger.md`](../relays/concurrent-work-ledger.md)，确认你要动的目录没被别的 worktree 占用。
4. 在台账里登记自己这次要占用的目录。

## 修改共享文件时

`.mex/ROUTER.md`、`.mex/patterns/INDEX.md`、`.mex/context/*`、`docs/*`、根 `AGENTS.md` 属于共享结构，规则更严：

1. **先 `git fetch origin`**，确认基线是新的。
2. **在基于 `origin/main` 的 worktree 里改**，不要拿本地工作区的副本当基准 —— 本地可能就是隔了几天的旧版。
3. **只追加自己的条目，不要整文件替换。** 整文件覆盖是丢内容的头号原因。
4. 改完跑 `mex check`，确认没有 `MISSING_PATH` / `KNOWLEDGE_NOT_ADOPTED` / `DEAD_EDGE`。

## 提交边界

- **只 `git add` 明确列出的路径**，不要 `git add -A` / `git add .`。
- 提交前 `git diff --cached --name-only` 复核，每一项都应是自己的产出。
- 发现混入别人的文件 → `git reset <file>` 移除，不要直接提交。

## 收工

- 台账里自己的行删掉或标记完成。
- `git worktree remove` 清理临时工作区。
- 有跨会话/跨协作者的结论要传递，用 MEX relay（`/skill mex-relay`）写持久记录 —— 对话不会传给下一个人。

## 已知坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 功能分支的提交里出现美术资产 | 用了 `git add -A` | `git reset` 掉，改成精确路径 |
| 改共享文档后别人的条目消失 | 拿本地旧副本整文件覆盖 | 从 `origin/main` 重新取基准，只追加 |
| `mex check` 大量 MISSING_PATH | 本地工作区落后，缺上游新文件 | 在基于 `origin/main` 的 worktree 里检查 |
| 分支 ahead/behind 数字很大但工作区没动过 | 长期停在旧分支，上游持续推进 | 计划性落后不算故障；重点是别让工作区内容误入该分支 |
| 台账里的占用早已失效 | 对方 worktree 已清理但没更新台账 | 备注「疑似过期」，不直接删别人的行 |
