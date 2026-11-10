# 预览复现说明

本目录的脚本用于在 Blockbench 5.2.2 中重新生成交付目录下的 12 张 1200×900 预览图。它们通过 Chrome DevTools Protocol 操作一个已经打开 `welding_tool.bbmodel` 的 Blockbench 实例，不是独立渲染器。

## 环境

- Blockbench 5.2.2（Java Block/Item 编辑模式）。
- Node.js 24（脚本使用内置 `WebSocket`，不需要额外 npm 包）。
- Blockbench 以远程调试端口启动，例如：
  `blockbench --remote-debugging-port=9231`
- 建议使用隔离的用户配置目录，避免影响正在编辑的实例。

## 步骤

在仓库根目录执行（PowerShell 用 `$env:CDP_PORT="9231"`，bash 用 `export CDP_PORT=9231`）：

```sh
node art/models/welding_tool_20261108/reference/load_bbmodel.js art/models/welding_tool_20261108/welding_tool.bbmodel
node art/models/welding_tool_20261108/reference/render_clean_views.js <输出目录> clean
node art/models/welding_tool_20261108/reference/render_display_framed.js <输出目录> all
```

`load_bbmodel.js` 会把 `.bbmodel` 载入当前实例，并显式应用工程内的 `display` 设置。`render_clean_views.js` 输出 `preview_front/back/side/top/oblique.png`；`render_display_framed.js` 可通过第三个参数只渲染部分槽位（如 `gui,fixed`），第四个参数为取景余量（默认 `1.06`）。

## 固定参数

本体视图（`render_clean_views.js`）：

- 画布 1200×900，背景 `#202731`（不透明），隐藏 `grid_group`。
- 正交相机：`OrthographicCamera(-10, 10, 7.5, -7.5)`，统一 15 单位画幅；`front` 沿 +Z、`back` 沿 −Z、`side` 沿 +X、`top` 沿 +Y，`top` 的上方向为 `(0, 0, -1)`。
- 斜视：透视相机 fov 38、4:3，方向 `(16.98, 10.84, 21.875)`，距离 33。

Display 槽位（`render_display_framed.js`）：

- 逐槽调用 `DisplayMode.loadThirdRight/loadThirdLeft/loadFirstRight/loadFirstLeft/loadGUI/loadGround/loadFixed`。
- 画布 1200×900，背景 `#202731`，隐藏网格和变换 Gizmo。
- 透视相机 fov 45、4:3，方向取 Blockbench Display 相机方向（−44, 40, −44 指向舞台），距离按可见包围球计算：`半径 / sin(22.5°) × 1.06`。
- 第三人称取景包含玩家与物品；地面槽位包含屏障方块与物品；第一人称、GUI、固定槽位只取物品（第一人称会隐藏 Blockbench 的参考平面）。

## 已知限制

- 脚本依赖 Blockbench 内部的 Three.js 场景对象（`Preview.selected`、`Canvas.scene`、`DisplayMode`）和全局对象，属于版本敏感的内部接口；Blockbench 升级后可能需要调整。
- GUI 与固定槽位的预览使用 Blockbench Display 的 3/4 相机，是槽位姿态检查，不是游戏内 GUI/物品展示框的正交实机画面。
- 预览只代表 Blockbench 中的几何、UV 和显示变换；没有进入 Minecraft 客户端验证。
