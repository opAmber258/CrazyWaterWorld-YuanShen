---
description: "发布入口：纯美术资产直推 main，通用改动走 PR；执行顺序只维护在 publish pattern"
argument-hint: "[一句话变更说明]"
---

仅当用户明确调用本命令，或要求「走流程 / 发布 / 推到仓库」时执行；制作、预览请求本身不含发布授权。授权仅涵盖指定内容，实际 pull 仍需单独授权。

```text
$ARGUMENTS
```

读取 `.mex/patterns/publish.md`，先按范围分流，再执行对应步骤。该 pattern 是操作顺序的唯一维护位置；本入口不复制步骤。纯美术交付规则以 `docs/ASSET_DELIVERY_CONVENTION.md` 为准，初次建档或迁移时才按需读 `docs/ASSET_VALIDATION.md`。不跳过失败、不强推、不扩大发布范围。
