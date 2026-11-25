# reference/

- `render_bbmodel.js` — 离线几何渲染器（无依赖 Node 脚本，ZYX 欧拉序，支持 `inflate`）。**本次四张预览即由该脚本生成**（作者未提供视口截图），工具链已入库、可从仓库根重跑：
  `node art/models/periscope_stand_20261125/reference/render_bbmodel.js art/models/periscope_stand_20261125/periscope_stand.bbmodel <输出目录>`
  输出 1200×900 的正/背/侧/顶/斜视图并打印逐 cube 世界包围盒。渲染与工程几何/内嵌贴图严格同源；光照为平面着色，风格与 Blockbench 视口略有差异。
- `validate_deliverable.js` — 交付自检脚本。在仓库根运行：`node art/models/periscope_stand_20261125/reference/validate_deliverable.js periscope_stand`。检查命名、格式、计数、贴图 2 的幂与真透明、`_tex.png` 与 bbmodel 内嵌字节一致、预览尺寸；结果写入本目录 `validation_output.json`（运行产物，不入库）。
