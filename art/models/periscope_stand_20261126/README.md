# reference/

- `render_bbmodel.js` — 离线几何渲染器（无依赖 Node 脚本，ZYX 欧拉序，支持 `inflate`）。用于本交付的**一致性核对**（确认工程内嵌贴图与作者预览一致，并排查外置 PNG 的 UV 不匹配）。可重跑：
  `node art/models/periscope_stand_20261126/reference/render_bbmodel.js art/models/periscope_stand_20261126/periscope_stand.bbmodel <输出目录>`
  输出 1200×900 正/背/侧/顶/斜视图并打印逐 cube 世界包围盒。
- `validate_deliverable.js` — 交付自检脚本。在仓库根运行：`node art/models/periscope_stand_20261126/reference/validate_deliverable.js periscope_stand`。检查命名、格式、计数、贴图 2 的幂与真透明、`_tex.png` 与 bbmodel 内嵌字节一致、预览尺寸；结果写入本目录 `validation_output.json`（运行产物，不入库）。
- 本交付的四张预览为**作者提供的 Blockbench 视口截图**（非本脚本产出）。
