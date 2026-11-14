# reference/

- `validate_deliverable.js` — 交付自检脚本（通用，java_block 口径）。在仓库根运行：`node art/models/battery_20261115/reference/validate_deliverable.js battery`。检查命名、格式、元素/面计数、贴图 2 的幂与真透明、`_tex.png` 与 bbmodel 内嵌贴图字节一致、预览尺寸；结果写入本目录 `validation_output.json`（运行产物，不入库）。
- `render_bbmodel.js` — 离线几何校验渲染器（无依赖 Node 脚本，ZYX 欧拉序）。用法：`node art/models/battery_20261115/reference/render_bbmodel.js art/models/battery_20261115/battery.bbmodel <输出目录>`，输出 1200×900 的正/背/侧/顶/斜视图并打印逐 cube 世界包围盒，用于核对预览与几何一致、复算包围盒。
- `screen_states/` — 电量屏四个状态的完整 64×64 贴图（自 bbmodel 贴图图层逐字节提取）：`battery_tex_full.png`（满电，3 段绿；与主贴图 `battery_tex.png` 一致）、`battery_tex_mid.png`（半电，2 段橙）、`battery_tex_low.png`（低电，1 段红）、`battery_tex_empty.png`（空电，全暗）。集成侧可用 blockstate 按方块电量切换贴图。
- 四张预览均由作者在 Blockbench 视口手动截图产出（约定 3.3 的交互环境例外）；以上脚本为交付后自建的校验工具，不用于产出预览。
