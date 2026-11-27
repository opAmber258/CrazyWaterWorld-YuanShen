# 造船厂（Shipyard）

状态：M1 世界底座、M2 编辑器（640×360 大字号悬浮 HUD + 12 种建模/连线工具：单块/Alt连绘/填充/方壳/椭球流线艇壳/舱壁/直线/材质替换/框删/复制/剪切/旋转翻转微调粘贴/编辑器内连线绑定 + 舱室预制件拼装 + 分类方块库与快捷热键栏 + 多方块完整虚影与朝向箭头 + 标尺包络框 + Y剖面视图 + 舱室分区着色 + 潜艇指标预算）、M3 校验清单（问题优先折叠 + 可点击定位/下个问题跳转 + 3D 未连通/破口/连线高亮）、M4 船库后端与 UI（owner 隔离与实船校验徽章）、M5 试航后端（分批清场）已落地；试航海洋环境与实机手感仍在迭代。当前协议 **48**。

## 目标

进入独立造船世界 → 轨道相机编辑 → 自动检查船体连通/水密 → 最低运行清单 → 跨存档船库保存/加载 → Tab 切第一人称继续放方块 → 在独立试航维度直接试航。

## 已定决策

- `barotrauma:shipyard`：独立编辑维度，虚空 + 每玩家固定地块（`SHIPYARD_ORIGIN_X=1_000_480`，间距 1024，避免 DockingBay 坐标误判；origin 已写入地块 NBT）。
- `barotrauma:shipyard_test`：独立试航维度；生成器现在填充真实海水（水面 y=-1，石底 y=-128），船从 y=-8 起始；同服一次只允许一艘测试船。旧存档测试区会在试航前补一圈海水。
- 编辑器：轨道相机 + 透视/六向正交视图（1-4 与 Gizmo 轴切换）、WASD/Space/Shift 移动、F 聚焦悬停方块、Home 重置视角、右下角按相机深度排序并投影的 XYZ 方向 gizmo（拖动旋转视角、单击吸附与切换正反视图）、工作平面升降（`[`/`]` 或 `PageUp`/`PageDown`）、空域平面光线求交回退、单线单 Quad 局部/全局网格（G）+ 原点中心轴 + 青色镜像辅助面、多方块完整占用格与冲突虚影、未连通/破口三维诊断高亮（K 开关）、分类方块库（全部/船壳/舱门/动力/设备）、H 隐藏面板、工具栏（单块/填充/空心船壳/舱壁/直线/框选拆除/复制 C/粘贴 Ctrl+V）、对称镜像（M：关/X轴/Z轴）、方块与剪贴板旋转（R / Shift+R / 中键取块继承朝向）、Ctrl+Z/Ctrl+Y/Ctrl+Shift+Z；Tab 退出编辑器并传送到焦点附近继续第一人称。
- 校验：结构连通硬性；水密仅警告；固定基础运行清单（核心/船体/反应堆/引擎/泵/压载/氧气/门/电力/驾驶）。
- 船库：`<gameDir>/barotrauma/ships/<id>/structure.nbt + meta.json`，原版 `StructureTemplate` 格式，外部工具可直接编辑；编辑器右侧“船库”面板支持保存/加载/导出/删除。
- 试航中的损伤/进水/耗材全部丢弃；建造地块不被试航修改。

## 实现结构

- 世界：`ModDimensions`、`ShipyardChunkGenerator`（虚空/可选石底）、`data/barotrauma/dimension*/shipyard*.json`、`data/valkyrienskies/vs_dimension_parameters/shipyard*.json`。
- 地块：`ShipyardSavedData`（地块/返回点/船库 id/动态 bounds）、`ShipyardPlot`（平台/核心骨架）、`ShipyardManager`（进入/离开）、`ShipyardCommand`（`/barotrauma shipyard`）。
- 核心扫描：`ShipCoreBlockEntity` 新增 NBT `ScanMin/Max*`；`ShipGeometry.scanShip(Bounds,...)`；`ShipState.collectBlocks(level, translate, bounds)`。旧船没有 bounds 时仍走 ±24。
- 编辑：`ShipyardServer` 服务端权限/地块/白名单校验，`ShipyardShapes` 共享几何与对称算法，`ShipyardEditPacket` / `ShipyardFillPacket` / 撤销重做（协议 45）；支持单块/区域操作原子镜像对称、空心船壳/四壁舱壁/3D直线/框选拆除、按编辑点扩展核心扫描范围；一次 fill/clear/镜像/一台多方块设备是一个撤销单元，副段一并校验/回滚。
- 校验：`ShipyardValidator` 结构 BFS + `ShipGeometry` 水密 + 设备清单（附目标坐标可在右侧清单点击定位）；尺寸按实际船体方块统计；`ShipyardStatusPacket` 回传 UI。
- 客户端：`ShipyardClient`（轨道相机/目标方块）、`ShipyardEditorScreen`（透明 ScaledScreen + 方块库 + 清单）、`ShipyardCameraMixin`、`ShipyardPalette`。世界交互覆盖全屏，只排除左右面板矩形。
- 船库：`ShipLibrary`（目录/meta/路径安全/名称截断）、`ShipyardStructureIO`（`StructureTemplate` 读写/导出/加载）；外部 NBT 限流读取、palette/坐标/地块白名单校验、忽略实体、失败回滚；`ShipyardLibraryScreen`（保存/加载/导出/删除，删除与未保存覆盖二次确认）。

## 操作

- `/barotrauma shipyard`：进入（权限 2）。
- `/barotrauma shipyard leave|plot`：离开/查看地块。
- 船厂内 Tab：打开编辑器；编辑器内 Tab/Esc：回第一人称/关闭。
- 编辑器：左键拆、右键放、中键拖拽旋转、Shift 平移、滚轮缩放；右侧为实时校验清单。

## 尚未完成 / 已知限制

- 试航返回会按起点区域 + 船最后位置/尺寸清理，并移除测试维度全部非玩家实体；超过 16384 格的清理区域已改为每 tick 分批（8192 格）异步清场，玩家断线无实船时也会自动触发测试区清理。
- 试航维度已接入 `OceanEnvironment` 海域判定，但雾/浮力/压力手感仍需实机调；试航船崩溃清理未自动化。
- 船库已做中文名 CRC id、同名冲突追加序号、删除/未保存覆盖二次确认；`meta.json`（schema 2）已记录 `ownerUuid` / `ownerName` / `verified`，非 owner（且非 OP）无法覆盖或删除他人船只。
- 大型地块（>2500 格底面积）的自动校验防抖延长至 16 tick，但单次扫描本身仍为同步重扫；`ShipGeometry` 安全上限为单轴 512、体积 1600 万格。
- Tab 与原版玩家列表冲突未完全抑制；多人地块/编辑锁未做。
- 多方块设备的放置/拆除/撤销与虚影预览已按整组处理（含副段冲突标红）；导航终端/反应堆/引擎的朝向与分段仍需实机验证。
