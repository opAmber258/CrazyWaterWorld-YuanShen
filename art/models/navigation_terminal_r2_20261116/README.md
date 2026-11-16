# 导航终端 NavigationTerminal — 第二版

制作来源：人工 + AI（TakaraMiyuki + GPT-6）。贡献者、所用工具与分工只记在 [`validation.json`](validation.json) 的 `contributors`；结构审计、实测规格与三项验收也在同一份记录里，本文不再复述计数。

这是独立修订目录，与上游 `navigation_terminal_20261108/`（三格宽版）并列，作为同一资产的第二候选。上一轮工作目录 `navigation_terminal_20261116` 保留不动；开工时从当时打开的工程另存了完整工程与内嵌位图，位于 `reference/baseline_live/`。

## 本轮（2026-11-16 手改后重出）

作者在 Blockbench 里手改工程后保存（11:32）。几何与贴图都变了，但目录里的 `geo.json`、`_tex.png` 和四向预览还是 10:45 的旧状态，与工程不一致。本轮据此重出全部导出物：

1. **顶壳抬高。** 顶壳芯体高度 3.0 → 3.6 BB、翼板 1.5 → 2.1 BB，两端 45° 角面整体上移 0.585 BB。整机高度由 31.875 变为 32.475 BB（2.5 × 2.0296875 格）。主机箱、屏框与底部护边未动。
2. **键盘中段收窄。** 控制板与白色前面板宽度 30.0 → 29.1 BB（X −14.5…+14.6），左右键井各 10.5 → 9.6、键组各 10.0 → 9.1，中央控制模块 5.0 → 4.1、读数窗 3.5 → 2.6、两枚按键 1.5 → 0.6，空格条与铭牌 7.5 → 6.6。键井与黑护端之间的缝因此加宽约 0.5 BB/侧。所有键区元素高度与 Y 不变。
3. **黑护端改为独立骨骼。** 两端护端从 `keyboard` 移到 `bone4` / `bone5`（父级为过渡骨骼 `bone`），不再继承键盘的 12° 倾斜，世界空间中改为水平；局部坐标抬高 0.5 BB，右侧护端另 +0.1 BB X。因为倾斜消失，护端的世界包围盒由倾斜外扩的形状回正：左护端 Z 跨距 9.43 → 9.00 BB，Y 范围 2.57–7.37 → 4.18–7.18 BB（倾斜时 AABB 被撑大，非实体变大或变小）。
4. **后侧增加交叉线缆。** `bone3` 下新增 5 根 `crossed_power_lead_1..5`，与前侧 `bone2` 下同名 5 根互为镜像；两组共用同一批贴图矩形（见「UV 与检查边界」）。
5. **信号线组右移。** `loose_signal_1..5` 与 `signal_grommet` 整体 +6.2 BB X，其余坐标不变。
6. **主声呐屏重绘。** 声呐屏南面覆盖的贴图区（UV 118.5–139 × 38.5–51）有 301 个像素被改，图集总色数仍为 50，其余贴图区逐像素相同。

四向预览、`navigation_terminal.geo.json` 与 `navigation_terminal_tex.png` 均已按手改后的工程重出；`.bbmodel`、`geo.json`、内嵌 PNG 三者内容一致。以上数值取自当前工程与上一轮已交付几何的逐块对比；工程内部的 Z 向 −0.1015 BB 集体偏移是上一轮交付前的居中归一化，不属于本次手改。

## 相对第一版的六项设计改动

1. 顶部外壳、底部护边、主／副屏框和键盘黑色护端加入 45° 角面，以低多边形八边形轮廓近似圆角。圆角组合面保留微小深度层次，避免共面闪烁。
2. 主屏外轮廓由 22.5×13 BB 调为 24×14 BB，可视包围矩形面积增加约 **14.87%**；新屏幕角部有小切角。屏框收窄，保留右侧仪表布局。
3. 操作板两端为黑色护端，中段为灰白外壳；中段明确划为左键盘、中央深色控制模块、右键盘。中央模块有小读数窗和两枚控制键。
4. 增加上下两层不同长度、不同前后深度的松弛线束和回环，保留固定点与应力保护接头。头梁和新增线缆的线段端点已连接检查。
5. 删除原先误认的旋钮，换为挂钩、弧形头梁、左右耳罩／软垫与下垂耳机线。
6. 补充细像素接缝、低对比漆面变化、短划痕、局部露底、螺钉污流、细通风孔和按键层次。仍使用硬边像素，无渐变、照片噪声或抗锯齿贴图。

## 原作与风格依据

主要造型依据为用户参考图 `reference/user_reference.png`。原作裁切为 `reference/original_navigation_terminal.png`：`Content/Items/Command/command.xml` 中的 `navterminal`，`scale=0.5`，贴图 `CommandDevices.png`，裁切 `0,0,384,304`。

中文名称来自 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `entityname.navterminal`＝“导航终端”；英文另查 `Content/Texts/English/EnglishVanilla.xml` 的同条目＝“Navigation Terminal”。查证原文见 `reference/source_report.json`；两个条目标识另记入 `validation.json.sources`。功能背景参见[官方 Wiki](https://www.barotraumagame.com/wiki/Navigation_Terminal)。

项目风格沿用第一版只读参考过的 curated 医疗加工台与引擎：学习浅色壳体、深色功能区、材料分层和装配逻辑。本轮不读取 deprecated 或 human_review 素材。背部、深度、耳机立体厚度与线束走向仍为美术推定。

## 尺寸与规格

| 项目 | 规格 |
| --- | --- |
| 编辑工程 | `navigation_terminal.bbmodel`，Bedrock Entity，贴图内嵌 |
| 原生几何 | `navigation_terminal.geo.json`，`geometry.navigation_terminal` |
| 真实尺寸（宽×高×深） | 40 × 32.475 × 15.431259 BB |
| Minecraft 格 | 2.5 × 2.0296875 × 0.964454 |
| 朝向与安装 | 正面 +Z，背部 -Z 朝墙；X/Z 居中，最低线缆 Y≈0 |
| 贴图 | 单张 512×512 RGBA，逻辑 UV 256×256，32 px/格 |
| 预览 | 四张 1200×900；正／背／侧正交，斜视透视，透明背景、无 UI 网格 |

计数、包围盒与逐项验收状态请读 [`validation.json`](validation.json)；上表的尺寸取自同一次原生测量。

尺寸由 Blockbench 中变换后的网格顶点计算，不含辅助物。按用户的 2.5 格宽确定 40 BB，再保留原版约 384:304 的宽高关系。原作 `scale=0.5` 只作查证，不被当作现实米制尺寸。

## UV 与检查边界

一般表面密度为 2 px/BB＝32 px/格；细线、薄边与角部侧面按至少一像素取整。每个面各有独立 UV 记录，但**不是全部互不重叠**：本轮新增的后侧 5 根线缆（`bone3`）与前侧 5 根（`bone2`）刻意共用同一批贴图矩形，共 30 个矩形完全重合、覆盖 60 个面。重合是精确同矩形，没有部分交叠；后果是改这 5 处线缆的任一像素会同时改变前后两组。若日后需要分别改色，须先给后侧那组重新分配 UV。

工程里另有一个不含 cube 的过渡骨骼 `bone`（`bone4` / `bone5` 的父级），用于承载键盘护端的独立变换；Bedrock 导出会保留这个空 bone，不影响渲染。工程内还有 5 组同名元素（`crossed_power_lead_1..5` 前后各一套），仅影响编辑器里的名称查找，geo 导出不含元素名。

上一轮的「750 个逐面 UV 无重叠」只适用于上一轮那批 cube；加上后侧线缆后不再成立，见上段。

结构审计（`reference/audit_bbmodel.json`、`reference/audit_geo.json`）对工程和 geo 各保留一个警告：实际 PNG 为 512²，逻辑 UV／几何描述为 256²。这是像素尺寸与 UV 空间的区别，密度按真实 PNG 单独检查；除此之外 0 错误。

**未进入 Minecraft 验证**：未检查游戏放置、碰撞、交互、性能或暗处效果。屏幕为静态不透明画面，未配置实际发光／动态声呐。未改仓库代码和运行资源；运行时接入由集成者在评选通过后进行。

## 复现

`reference/` 的脚本以自身目录定位文件，MCP 端点通过 `BLOCKBENCH_MCP_URL` 环境变量设置。需要 Python/Pillow 与装有 MCP 插件的 Blockbench。

- `reference/revise_geometry.py`：读取受保护基线，在独立项目中按 UUID 建立六项结构；不覆盖旧版。
- `reference/allocate_uv.py`：按原生面像素尺寸打包独立 UV，留一像素间距。
- `reference/paint_texture.py`：按布局绘制像素材质，色板为 `reference/palette.json`。
- `reference/save_native.py`：保存重开、居中与原生导出。
- `reference/render_previews.py`：在运行中的 Blockbench 内用其 Three.js 场景／材质渲染并恢复预览状态；`--gray` 用于结构检查。
- `reference/verify_native_sample.py`、`reference/verify_editing.py`：原生密度小样、逐面独立编辑及撤销恢复验证。

这些脚本产出的是手改前的基线。**当前几何以作者在 Blockbench 内的手改为准**：直接重跑 `revise_geometry.py` / `allocate_uv.py` / `paint_texture.py` 会覆盖手改结果，重跑前须先另存当前工程。本轮重出导出与预览的方法见 `reference/README.md`；预览由 Blockbench 自身渲染器出图，需要活动的编辑器会话，不能在无界面环境复现。

运行顺序为结构 → UV → 绘制 → 保存导出 → 预览／验证。参考中的中间工程只用于复核，正式交付以根目录主工程为准。
