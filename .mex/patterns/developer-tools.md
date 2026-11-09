---
name: developer-tools
description: 创造模式「开发者工具」页签与调试物品/面板的接入方式：服务端校验、创造模式清理豁免、面板缩放与网络包约定。
triggers:
  - "开发者工具"
  - "dev tab"
  - "调试面板"
  - "医疗调试器"
  - "debug item"
  - "创造模式页签"
edges:
  - target: patterns/cthulhu-post-processing.md
    condition: when the debug tool triggers a client-side visual effect
  - target: context/conventions.md
    condition: when adding the packet or screen for a new debug tool
grounds_to: []
last_updated: 2026-11-09
mex:
  id: mx_01M6YVNYH6918E8JA3Z0MHSHW9
  type: pattern
  status: promoted
  revision: 3
  title: developer-tools
  relations:
    - type: related_to
      target: mx_01M6YVNY85SVEBSM22WCKKFSNJ
      note: when the debug tool triggers a client-side visual effect
    - type: related_to
      target: mx_01M6FTBTB77P4PT8TV3HT78NGZ
      note: when adding the packet or screen for a new debug tool
---

# 开发者工具（调试物品 / 面板）

## Context

创造模式有独立的 `itemGroup.barotrauma.dev` 页签（`ModCreativeTabs` 里的 `DEV`，图标是医疗调试器），只放调试/彩蛋物品，不污染主页签。当前内容：`medical_debugger`、`67`、`cpu_6700x3d`、`cthulhu_trigger`。

调试物品的模式固定为「客户端开面板，编辑走服务端包」：

- `MedicalDebuggerItem.use()`：`level.isClientSide` 时 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MedicalDebugScreen.open())`，返回 `InteractionResultHolder.sidedSuccess(stack, level.isClientSide)`。
- `MedicalDebugPacket` 动作 0–6：SET/ADD 病症、清部位、清全部、SET/ADD/CLEAR 活力覆盖。服务端校验 `isCreative() || hasPermissions(2)`，每次编辑回发一次 `MedicalSyncPacket` 快照，面板不用等每秒同步。
- 活力覆盖存在 `AfflictionContainer.vitalityOverride`，以 `v|<值>` 行进 NBT/同步（`rows()`/`loadRow`），可以设负数测倒地、−100 测真死。
- `CthulhuTriggerItem` + `CthulhuGazePacket` 用同一套校验（`canUse`），证明这套模式可以复用。

## Steps

1. 物品注册到 `ModItems`，在 `ModCreativeTabs.DEV` 里 `output.accept(...)`；主页签不要加。
2. `use()` 只在客户端开 Screen，服务端返回成功即可；屏幕类必须经 `DistExecutor` 引用，避免服务端类加载。
3. 编辑动作走新 packet：append 到 `ModNetwork.register()` **末尾**（id 按注册顺序分配），校验权限，改完回发快照。
4. 面板布局：所有按钮坐标抽成常量，绘制和点击共用，别写两份；长名字用 `font.plainSubstrByWidth` 截断。
5. 面板尺寸大于屏幕逻辑分辨率时用等比例缩放（见 `CthulhuGazeScreen`）：`uiScale = min(1, min((width-8)/PANEL_W, (height-8)/PANEL_H))`，`pose.translate + scale`，鼠标坐标反算。
6. 给调试行为补 L1 单测（参考 `AfflictionContainerDebugTest` 的 7 条：设值/夹取、增量饱和、limb 为空忽略、覆盖优先级、行往返、copy/clear）。

## Gotchas

- **创造模式每秒清空医疗状态**：`MedicalEvents` 的上帝模式会对创造/旁观玩家 `clear()` 病况，调试器改完立刻被抹掉。修复方式是按**携带物**豁免：`carriesMedicalDebugger(player)` 时跳过清理。新增调试物品如果依赖持久状态，也要检查这条清理链。
- **生存态残留覆盖要清**：创造模式设置的 `vitalityOverride` 如果被玩家带回生存，会把血条钉死；`MedicalEvents` 在生存且无权限时自动清掉（上游 `4d122d7`）。
- **packet id 与协议版本**：新包插在中间会让之后所有包 id 移位，旧端会把新包解码成错误类型；即使只追加到末尾（旧 id 不变），旧端也不认识新 id。两种情况都要**升 `PROTOCOL`**（`ModNetwork` 握手只比较这个字符串）；除非实现了能力协商、确保只向支持该包的对端发送，才可不升。
- **`use()` 两边都会调**：忘记 `level.isClientSide` 判断会在服务端尝试加载客户端 Screen 类；忘记 `setPacketHandled(true)` 会丢包。
- 面板不要用固定像素硬编码到 `width/height` 之外；窗口 resize 后坐标会错位。

## Verify

- [ ] 创造模式能看到「开发者工具」页签，物品齐全（en_us 也要有 `itemGroup.barotrauma.dev`）
- [ ] 生存玩家拿到物品也无法编辑（服务端拒绝并提示）
- [ ] 面板改完立即生效，不被创造模式清理覆盖
- [ ] 高 GUI scale（3x/4x）下面板完整可见
- [ ] 退出重进后调试状态按预期保留/清理

## Debug

面板数值不动：先确认 `MedicalSyncPacket` 是否回发，再看服务端是否拒绝（聊天栏提示"仅限创造模式/管理员"）。改完被重置：检查 `carriesMedicalDebugger` 豁免是否命中（要在背包任意槽位，不只主手）。
