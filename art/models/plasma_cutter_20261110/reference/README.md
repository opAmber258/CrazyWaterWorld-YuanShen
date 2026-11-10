# 预览与导出复现说明

这些脚本操作一个已经启动、且专门用于本资产的 Blockbench 5.2.2 实例。它们不是独立渲染器。

## 环境

- Blockbench 5.2.2，Java Block/Item 编辑模式；
- Node.js 24，脚本使用内置 `WebSocket`；
- 建议使用独立用户配置目录和调试端口，避免影响正在编辑的其他项目。

Windows 示例（在仓库根目录执行）：

```powershell
$profile = "art/models/.work/plasma_cutter/bb_profile"
Start-Process "C:\Users\wegene Jo\AppData\Local\Programs\Blockbench\Blockbench.exe" `
  -ArgumentList @("--user-data-dir=$profile", "--remote-debugging-port=9237") `
  -WindowStyle Hidden
$env:CDP_PORT = "9237"
```

## 重新载入与导出

```powershell
node art/models/plasma_cutter_20261110/reference/load_bbmodel.js `
  art/models/plasma_cutter_20261110/plasma_cutter.bbmodel

node art/models/plasma_cutter_20261110/reference/compile_java.js `
  art/models/.work/plasma_cutter/candidate/plasma_cutter.java_raw.json
```

`load_bbmodel.js` 会载入 `.bbmodel` 并显式应用工程内 Display 设置。使用全新的隔离实例，或在载入前关闭同名项目；Blockbench 对同路径项目不一定会自动替换当前内存工程。

## 预览

```powershell
node art/models/plasma_cutter_20261110/reference/render_clean_views.js `
  art/models/.work/plasma_cutter/previews clean

node art/models/plasma_cutter_20261110/reference/render_display_framed.js `
  art/models/.work/plasma_cutter/previews all 1.06
```

固定参数：

- 本体：1200×900，正交正面/背面/侧面/顶面 + fov 38 斜视；
- Display：1200×900，fov 45，按槽位单独取景；
- 背景统一为 `#202731`，隐藏网格和编辑 Gizmo；
- 第三人称包含玩家，地面槽位包含屏障方块，第一人称/GUI/Fixed 只保留物品。

## 已知限制

- 脚本依赖 Blockbench 内部 Three.js 场景对象和 DisplayMode 接口，升级 Blockbench 后可能需要调整。
- 地面预览为了展示落地关系会包含屏障方块，因此物品在画面中较小。
- 预览不等于 Java 客户端实机渲染；GUI、第一/第三人称和 emissive 的最终表现仍需在游戏中验证。
