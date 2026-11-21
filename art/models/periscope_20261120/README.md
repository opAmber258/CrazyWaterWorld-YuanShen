# reference/

- `validate_deliverable.js` — 交付自检脚本（通用，java_block 口径）。在仓库根运行：`node art/models/periscope_20261120/reference/validate_deliverable.js periscope`。检查命名、格式、元素/面计数、贴图 2 的幂与真透明、`_tex.png` 与 bbmodel 内嵌贴图字节一致、预览尺寸；结果写入本目录 `validation_output.json`（运行产物，不入库）。
- `render_bbmodel.js` — 离线几何校验渲染器（无依赖 Node 脚本，ZYX 欧拉序）。用法：`node art/models/periscope_20261120/reference/render_bbmodel.js art/models/periscope_20261120/periscope.bbmodel <输出目录>`，输出 1200×900 的正/背/侧/顶/斜视图并打印逐 cube 世界包围盒，用于核对预览与几何一致、复算包围盒。
- 四张预览均由作者在 Blockbench 视口手动截图产出（约定 3.3 的交互环境例外）；以上脚本为交付后自建的校验工具，不用于产出预览。
