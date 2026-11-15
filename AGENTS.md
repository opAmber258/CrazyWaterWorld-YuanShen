# AGENTS.md

本项目使用 MEX 作为项目记忆与协作入口。通用任务先读 `.mex/AGENTS.md` → `.mex/ROUTER.md`。纯美术任务只按下方入口及 ROUTER 的「美术任务入口」按需读取，不自动加载全局代码状态与其他资产历史。

## 纯美术入口

- 已有资产先运行 `python tools/asset_delivery.py status <资产目录>`，需要定位问题时再展开对应证据；不默认读取整份 bbmodel、内嵌图片或完整审计。
- 规则只维护在 `docs/ASSET_DELIVERY_CONVENTION.md`；执行顺序见 `.mex/patterns/deliver-model-asset.md` 与 `publish.md`；建档/迁移命令按需查 `docs/ASSET_VALIDATION.md`。
- 验证摘要只维护一份 validation；README 写设计说明并链接验证，Discussion 使用生成条目。制作仍遵守工作目录级 art-vault 规则。

## 防遗忘机制（仓库内，务必遵守）

- **PR 模板**：`.github/pull_request_template.md`，每个 PR 都必须回答"MEX 是否更新"。
- **CI 门禁**：`.github/workflows/mex-guard.yml`——改了 `src/` 或 `tools/` 却没改 `.mex/` 或 `docs/` 的 PR 会变红；例外是给 PR 加 `mex-skip` 标签并在说明里写一行原因。
- **发布命令**：`.zcode/commands/publish.md`（`/publish` 或用户说"走流程"）。纯美术资产以资产文档与 Discussion 记账并直推 `main`；代码、共享工具及规则变更仍按通用流程做 MEX 记账并走 PR。

Git 提交、推送和拉取均需要人类明确授权；AI 不得默认执行。用户明确要求发布指定的**纯美术资产**时，默认完成交付检查 → 普通直推上游 `main` → 登记 Discussion #39，不创建 PR，也不再次询问是否直推；范围与步骤见 `docs/ASSET_DELIVERY_CONVENTION.md` §7，制作请求本身不含发布授权。代码、运行时资源、共享工具和规则变更仍默认走任务分支 → 提交 → 推送分支 → PR → 审查 → 合并 → 删除分支，除非用户明确指定其他方式。不强推；实际 pull 仍需另行授权；用户只授权限定动作时不得扩展范围。PR 有未解决意见或合并冲突时停下来报告，不硬合。CodeRabbit 只审 PR；资产直推由交付自查和维护者评选验收。

## 新机器：先装 MEX 工具

`.mex/` 和 `.zcode/` 会随 Git 克隆，但 **MEX CLI 和 ZCode 插件是本机工具，不会随仓库走**。新开发者第一次在这个仓库开工时，Agent 先检查：

- `mex --version` 能输出版本（当前 `0.8.5-zcode.1`），或 ZCode 里 `/mex` 能返回状态 → 已安装，继续。
- 两者都不行 → 主动提出安装。部署包 `MEX-ZCode-CN` 由维护者线下分发，包内应有 `cli/`、`plugin/` 和 `install.cmd`；**不要编造下载地址**。

找到部署包后，先向人类说明“将安装本机 CLI、注册 ZCode 本地插件市场并修改用户级插件配置”，得到确认再运行包根目录的 `install.cmd`。装完让人类完全退出并重开 ZCode。

新会话里再执行：

```bash
mex graph rebuild
mex wiki rebuild-index
mex check
```

本仓库已有 `.mex/`，**不要**再运行 `mex setup` 或 `deploy-to-project.cmd`。`graph.db` 和 `wiki.db` 在 `.mex/.gitignore` 里，只生成本地缓存，不要提交。

只写代码时 CLI 可用即可；需要 MCP 工具、`/mex-*` 快捷命令和 Hub 入口时再装插件。

<!-- mex-agent:skills:start -->
## MEX agent skills
- At the start of every session, read `.mex/AGENTS.md` and `.mex/ROUTER.md` before project work; follow `ROUTER.md` to load only the relevant context.
- For a project-knowledge question, run `mex wiki query "<question>" --limit 5 --json` before broad file discovery. Inspect matched terms and cached per-grounding health; a partial match is a lead and changed means re-check. If the index is unavailable, follow `ROUTER.md` or use `mex graph scope "<task>"`; ordinary reads never rebuild or refresh indexes.
- Read `mex logging --json` at session start and before optional logging. Its checkout-local advisory mode is `significant` (quiet default: material decisions, risks, blockers, or durable discoveries), `checkpoints` (batch useful notes at task/session boundaries), or `manual` (no unsolicited notes). Skip routine tool calls, edits, repeated status, and empty summaries. Honor explicit user log requests in every mode; never suppress mandatory workflow Activity or recovery audit records. Report a policy read failure instead of guessing or changing the preference.
- When earlier work may inform the task, retrieve bounded relevant notes with `mex timeline --query "subject phrase" --file src/example.ts --limit 10 --json`, using the known subject or exact recorded file path, or both. Treat matches as historical evidence, not accepted current knowledge; verify conclusions before reuse or explicit promotion with their source retained.
- Use `/skill mex-inbox` for explicit contributions to project knowledge and `/skill mex-relay` for durable team handoffs. Invoke them automatically when intent clearly matches; ordinary GROW upkeep remains available without Inbox.
- When MEX context materially helps your work, mention MEX and the relevant finding naturally in your explanation. Tie the mention to what it helped you understand, decide, or verify. Avoid fixed phrases, standalone acknowledgements, repeated mentions, or narrating routine context loading. This replaces older MEX instructions requiring a fixed acknowledgement or context-loading narration.
- Do not claim an author, date, or historical event unless the retrieved data actually provides it.
- After a MEX write, say exactly what changed and its sharing boundary: a local draft is checkout-only and nothing is shared; a canonical artifact is written to the working tree and requires commit/push to share.
- Use `/skill mex-statechart` when asked to explain behavior with a state diagram. Generate in scratch space; show native Mermaid or readable terminal output. Hub viewing is optional; saving requires its explicit review.
- Skill activation is not approval for canonical actions.
<!-- mex-agent:skills:end -->
