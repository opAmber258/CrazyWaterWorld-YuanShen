# 舰载炮塔 Turret v2（三段式 Java 方块模型）

- 日期：2026-11-19
- 制作来源：AI（DeepSeek-V4.1-Flash，经 ZCode CLI + Blockbench MCP）；需求与验收：boingsetta
- 格式：Blockbench 5.x `java_block`（`java_block_version` 26.3）
- 目录：`art/models/turret_20261119/`
- 验证记录：[validation.json](validation.json)

## 设计意图

把原来“底座 + 一根炮管”的两段式原型改成可读的三段结构：

| 部分 | 行为 | 渲染方式 |
| --- | --- | --- |
| 固定底座 | 不随瞄准转动 | Java 方块模型（`models/block/turret*.json`） |
| 水平旋转基座 | 只跟炮塔 yaw 旋转 | `TurretRenderer` BER，第二个 draw |
| 俯仰炮管 | yaw + pitch，开火时沿炮轴后坐 | `TurretRenderer` BER，第三个 draw |

轴心保持原版的 `PIVOT_Y = 0.75`（模型 y=12），炮口长度 `MUZZLE_LENGTH = 1.14`
（模型 z=26.24），这样服务端弹道、客户端落点预判和炮口粒子都不需要改数值。

## 几何概览

- 28 cubes / 168 面 / 4 分组（root + base + turntable + barrel）。
- 包围盒：15.0 × 15.0 × 25.44 模型单位（X 0.5..15.5，Y 0..15，Z 0.8..26.24），
  约 0.9375 × 0.9375 × 1.59 格；炮管超出单格是 BER 的正常情况。
- 底座 11 cubes（装甲板、前后台阶、固定环、轴环、四角螺栓、两侧电缆盒）。
- 旋转基座 10 cubes（转盘、轮毂、左右叉臂、左右耳轴、供弹箱、两根液压杆、瞄具）。
- 炮管 7 cubes（炮尾、尾盖、套筒、主管、两道加强环、炮口制退器）。
- 无旋转 cube、无零厚度贴片；所有 cube 都参与几何。

## 贴图与 UV

单张 512×256 RGBA 图集（2 的幂），8×4 共 32 块 64×64 瓦片；**20 种零件各占一块瓦片**
（同型号的螺栓/电缆/叉臂/耳轴/液压杆/加强环共用），瓦片内按面分三个 32×32 象限：

| 象限 | UV 区域（瓦片内） | 用于 |
| --- | --- | --- |
| 左上 | `x0..x0+32, y0..y0+32` | up / down |
| 右上 | `x0+32..x0+64, y0..y0+32` | north / south（Z 面） |
| 左下 | `x0..x0+32, y0+32..y0+64` | east / west（X 面） |
| 右下 | 预留 | 当前未使用（保持透明） |

因此不是“一张图重复贴所有面”：底座、台阶、叉臂、液压杆、炮管、炮口各有自己的瓦片和
不同的顶/侧面版式。三种炮塔共用几何，只换整张图集：`turret_atlas` /
`turret_atlas_freeaim` / `turret_atlas_tracking`。

## 交付内容

| 文件 | 说明 |
| --- | --- |
| `turret.bbmodel` | Blockbench 5.x 工程，贴图内嵌，可重开编辑 |
| `turret_tex.png` | 512×256 RGBA 图集（普通涂装） |
| `preview_front.png` / `preview_back.png` / `preview_side.png` / `preview_oblique.png` | 1200×900，Blockbench 视口渲染，纯深色底、无网格无 UI |
| `README.md` / `validation.json` / `structure_audit.json` | 说明与结构化验证 |
| `reference/palette.json` | 色板与图集区域定义 |

## 运行时接入说明（集成侧）

- 方块模型只包含 `base` 分组；`turntable` 和 `barrel` 由 BER 绘制。
- 方块模型和 BER 都按 cube 的瓦片号取 UV：64px 瓦片 = 2 个模型 UV 单位（u）/ 4 个（v），
  每个面的象限再各占 1×2 个 UV 单位。
- 旋转顺序：先 yaw（世界 Y），再 pitch（局部 X）；局部 +Z 为炮口。
- 后坐位移沿局部 -Z（向后），峰值 0.10 格。
- `item/turret.json` 为三部分静态预览模型，自由/跟随炮塔通过父模型覆盖图集。

## 自查

- [x] `turret.bbmodel` 在 Blockbench 5.2.2 中重新打开通过
- [x] 贴图 512×256，2 的幂；留白区真透明
- [x] 四向预览 1200×900，与当前几何一致
- [x] 无隐藏元素、无未使用 cube、无旋转/零厚度异常
- [x] Blockbench `Codecs.java_block.compile()` 的 base-only 导出与仓库内方块模型几何/UV 一致
- [ ] 游戏内集成验证（由集成提交完成）
