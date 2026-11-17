# reference —— 证据与本轮重出方式

本目录放原作裁切、调色板、中间工程与本轮使用的脚本。交付物以资产根目录的 `.bbmodel`、`geo.json`、`_tex.png` 与四向预览为准。

## 独立证据

| 文件 | 用途 |
| --- | --- |
| `original_navigation_terminal.png` | 原作 `CommandDevices.png` 的 `0,0,384,304` 裁切 |
| `user_reference.png` | 用户提供的正面参考图 |
| `source_report.json` | 简中／英文条目、实体 XML 与 `scale` 查证原文 |
| `palette.json` | 贴图色板 |
| `audit_bbmodel.json` / `audit_geo.json` | 结构审计原始报告（当前 130 cubes / 13 bones 版本） |
| `uv_density_audit.json` / `uv_pack.json` / `uv_pixel_layout.json` | 上一轮的 UV 打包与密度审计，记录的是手改前基线 |
| `native_density_sample.*` / `editability_check.json` / `reopened_check.json` | 上一轮的原生小样、逐面编辑与重开检查 |
| `baseline_live/` | 开工时从编辑器另存的工程与内嵌位图快照（手改前） |
| `gray_views/` | 上一轮的结构检查用灰度视图 |
| `revision_spec.json` | 六项结构改动的机器可读规格 |

## 本轮（手改后重出）怎么做的

作者的改动是在 Blockbench 里手工完成的，只保存了 `.bbmodel`。目录里的 `geo.json`、`_tex.png` 与预览当时仍是旧状态，因此本轮没有沿用原脚本链路，而是用 Blockbench 自身的导出与渲染补出交付物：

1. 在工作区另起一个 Blockbench 5.2.2 实例（独立 `--user-data-dir`，不触碰作者正在用的窗口），用 DevTools 协议连接，而不是插件自带的 MCP 端口——本机 `127.0.0.1:3000` 已被其他服务占用，插件未能监听。
2. 用 `loadModelFile` 载入交付目录内的 `.bbmodel`，确认 130 cubes / 13 groups / 1 张 512² 内嵌贴图。
3. 用原生 `Codecs.bedrock.compile()` 导出 `navigation_terminal.geo.json`；用同一会话的内嵌贴图字节写出 `navigation_terminal_tex.png`。
4. 用编辑器自身的 Three.js 场景与材质渲染四向 1200×900 预览，`Canvas.withoutGizmos` 排除辅助线与网格，透明底、无 UI。
5. 关闭会话，用 `tools/asset_delivery.py` 记录三项验收。

**为什么不能在仓库内复现：** 上述第 1、3、4 步都要求一个活动的、装有 Blockbench 的图形会话；`Codecs.bedrock.compile` 与 `Preview.selected.renderer` 是编辑器内部对象，没有无界面的等价实现。本轮使用的 DevTools 桥接脚本是一次性的，只放在工作区、未入库——它依赖本机的 Blockbench 安装路径与临时端口，写进仓库也无法在别处直接运行。

替代做法：用 Blockbench 打开该 `.bbmodel`，"导出 → Bedrock 几何"得到 `geo.json`；四向预览用编辑器的截图功能按正／背／侧正交、斜视透视出图，尺寸设为 1200×900。验收标准见上游 `docs/ASSET_DELIVERY_CONVENTION.md`。

## 上一轮的脚本（手改前基线）

`revise_geometry.py`、`allocate_uv.py`、`paint_texture.py`、`save_native.py`、`render_previews.py`、`verify_native_sample.py`、`verify_editing.py` 由 `bb.py` / `bb_client.py` 经 Blockbench MCP 端点执行，端点由 `BLOCKBENCH_MCP_URL` 指定。

这些脚本产出的是本轮手改之前的工程。**直接重跑会覆盖作者的手改结果**，重跑前必须先另存当前 `.bbmodel`。它们保留在原处，是为了让结构改动与 UV 分配可追溯，不代表当前几何由它们生成。
