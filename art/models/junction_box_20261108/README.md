# junction_box_20261108 — 潜渊症接线盒 JunctionBox Minecraft 模型交付

> 资产名：**接线盒 JunctionBox**（mod 标识 `junction_box`，类型：道具；译名查证自游戏 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `<entityname.junctionbox>` 条目）

> 制作日期：2026-11-08
> 基准流程：《潜渊症物件模型制作规范 V2》（`BAROTRAUMA_PROP_MODELING_GUIDE_V2.md`）
> 参考案例：navigation_terminal_20261108（沿用其工程约定与导出校验方法）

## 1. 目标与边界

**目标句**：制作一个关门态的潜渊症接线盒静态模型：圆角钢柜（框圈 + 内凹门板）+ 右侧警示立柱（黄黑警示三角 + 白红标签）+ 右缘铰链筒与双凸节 + 底部黄黑斜纹警示底座 + 背部安装板与线缆（推定）。

**本轮不做**：开门态、发光（通电绿光覆盖层）、动画、粒子、碰撞、游戏内接入与交互。发光在原作中为运行时覆盖层（见下），静态资产不以亮色贴图冒充发光（规范 §7.4）。

## 2. 原作依据（三类信息标记）

### 查证数据
- 物品标识：`junctionbox`；XML：`Content/Items/Electricity/poweritems.xml`
- 图集：`signalcomp.png`，主精灵裁切 `sourcerect = 264,1,110,176`（110×176 px）
- `scale = 0.5`，`canflipx = false`，精灵 `depth = 0.8`（渲染层级用，非物理厚度）
- 通电覆盖层：`sourcerect 264,186,110,176`（警示区发绿光）与过载闪烁覆盖层 `376,186`；损坏态精灵 `376,1` / `488,1`（本轮未使用，参考图已存档）
- 精灵分区实测（11 px = 1 BB）：柜体 y0–127、暗缝 y128–133、斜纹底座 y134–159、底缘 y160–169；列向：左缘 x0–9｜门板 x10–59｜暗色立柱 x60–92（三角 y39–70、标签 y80–100）｜右框 x93–103｜铰链筒 x104–109（凸节 y32–57 / y75–100）；斜纹 45°、带宽约 14 px、周期约 30 px、黄 `#e9b002`

### 换算假设
- `K = 0.5 × 28.8 / 242.25 = 0.05944 BB/px`（沿用导航终端案例的人物视觉高度法；本项目 scale 同为 0.5）
- 原作换算：宽 6.54 BB（0.41 格）× 高 10.46 BB（0.65 格）——挂墙小盒

### 美术推定
- **整体放大 ×1.529**（需求方选定"适当放大"）：宽 **10 BB（0.625 格）** × 高 **16 BB（1.0 格）**，比例与原作换算严格一致（恰为整数化）
- 深度约 **7.15 BB（0.45 格）**（柜体本体；精灵为纯正面无深度依据）
- 背部：安装背板（8.4×11.6 BB，双段横缝 + 四角螺栓）、底部线缆束端面、顶部导管——按规范 §8 以"可维护设备"逻辑补全，不新增原作没有的功能件
- 圆角以直角 + 贴图暗角示意（Minecraft 体块语言）；柜门内凹 0.4 BB 由框圈凸出实现

## 3. 工程与规格

| 项 | 值 |
|---|---|
| 格式 | Bedrock Entity（`format_version 1.12.0`） |
| 标识 | `geometry.junction_box` |
| 几何 | **12 cubes / 5 bones**（junction_box → housing 6、hinges 3、mounting 1、cables 2） |
| 面数 | 72 面（逐面 UV；`micro_dark` 与 `steel_strip` 两个共用补丁为有意的文档化共享） |
| UV 空间 | 128×128（1 UV px = 1 BB） |
| 贴图 | **512×512 RGBA PNG（4× 密度，1 UV px = 4 位图像素）**——需求方选定 |
| 朝向 | 正面 +Z（south），地面 Y=0，X 居中 |
| 包围盒 | X −5…5，Y 0…16，Z −4.35…4.4（含背部推定件；柜体本体 Z −2.75…3.75 居中） |
| 尺寸（格） | 0.625 × 1.0 × 0.448（含背部凸出） |
| 分组 | `junction_box / housing / hinges / mounting / cables`（功能命名，可单独隐藏） |

cube 明细：`frame_top / frame_bottom / frame_left / frame_right`（框圈，前面凸出 0.5 BB 形成内凹）、`door_panel`（内凹门板，左右 0.1 BB 阴影缝）、`base_body`（斜纹底座）、`hinge_barrel` + `hinge_knuckle_top / _bottom`（右缘铰链，前凸 0.35 + 0.3 BB）、`back_plate`（背板，后凸 0.8 BB）、`cable_bundle`（底部线缆端）、`conduit_top`（顶部导管）。

## 4. 美术执行

- 调色板：`reference/palette.json`（冷调旧钢 + 炭黑功能件 + 饱和琥珀警示色；全部取自精灵采样：钢 `#c7c6c7/#a4a6a8/#949596`、暗柱 `#232322` 系、警示黄 `#e9b002`、锈 `#49402f/#371e0c` 等）
- 确定性绘制：`reference/make_texture.py`（Pillow，固定坐标、固定色板、零随机、硬边、无渐变；磨损为固定位置的少量短划与锈点）
- 门板三分明度带（上暖中灰下暗）+ 固定锈斑簇；警示三角为黑描边黄芯 + 雷电折线 + 底部黑条（位图 7×11 px）；标签为红顶带白底贴纸
- 立柱上部保留精灵中的"通电残光"橄榄色过渡区（`#26251f` + 3 个 `#6b6630` 点缀），属于原作未通电精灵的既有特征，非发光实现
- 底座斜纹按精灵相位公式 `((sx − sy + 6) mod 30) < 14` 重建，方向与相位与原作一致

## 5. 视觉质检（Step 7）

四视图 1200×900 **透明底**预览：`preview_front / preview_side / preview_oblique / preview_back.png`（符合 `ASSET_DISCUSSION_CONVENTION.md` §2：正面平光、侧/背 0.82 略暗、斜视 fov 38°）。
- 正面：三分带、警示立柱、标签、斜纹、铰链条、锈蚀与磨损分布——与原作精灵逐区比对通过
- 侧面：大面为右框侧面（进深 6.5 BB）、门板经 0.1 BB 缝隙透出为暗条、铰链凸节在前缘、背板/导管/线缆在后缘——体块关系成立
- 背面：背板双段 + 四角螺栓 + 顶部导管端 + 底部线缆端——维护逻辑完整且比正面克制
- 斜视：体积、内凹、铰链、底座一体成型

预览渲染说明：由 `reference/render_preview.py`（读取 geo.json + 贴图的确定性软件光栅化器）产出，不依赖 Blockbench 运行时；预览用于造型检查，不代表 Minecraft 内渲染效果。

## 6. 交付文件

```
junction_box_20261108/
├─ junction_box.bbmodel      Blockbench 工程（层级 + 嵌入贴图 + 面级贴图绑定）
├─ junction_box.geo.json     Bedrock 几何（reference/make_geo.py 生成）
├─ junction_box_tex.png      512×512 RGBA 贴图
├─ preview_front.png         正面预览（透明底）
├─ preview_side.png          侧面预览（透明底）
├─ preview_oblique.png       斜侧预览（透明底，fov 38°）
├─ preview_back.png          背面预览（透明底）
├─ validation.json           独立校验记录（全部 PASS）
├─ structure_audit.json      结构审计（0 错误 / 0 警告）
├─ README.md                 本文件
└─ reference/
   ├─ jb_main_264_1.png(+3x) 等   原作精灵裁切与放大（含通电/损坏态）
   ├─ palette.json                调色板与 UV 分区表
   ├─ make_texture.py             确定性贴图绘制脚本
   ├─ make_geo.py                 bbmodel→geo.json 独立生成器（含导航终端对照自检 --verify-nav）
   ├─ render_preview.py           约定规格四视图软件渲染器（透明底）
   ├─ structure_audit.py          结构审计生成脚本
   ├─ validate.py                 交付校验脚本
   └─ chk_*.png / tex_preview_256.png   贴图区 QC 放大图
```

## 7. 校验结果与事故记录（透明交接）

### 校验（reference/validate.py → validation.json，全部 PASS）
- bbmodel ↔ geo.json：12/12 cube 原点、尺寸、逐面 UV 完全等价
- 地面 Y=0 ✓；X 居中（±5）✓；Z −4.35…4.4（背部推定件凸出，记录在案）✓
- 全部 UV 矩形在 128×128 内 ✓；贴图实测 512×512 RGBA ✓；5 bones 与分组一致 ✓

### 事故记录（本次制作会话，Blockbench 5.2.2 + MCP 插件 1.10.1，窗口最小化期间）
1. **会话级损坏**：Blockbench 窗口被最小化后渲染循环暂停；期间打开/切换工程会触发其网格重建路径异常——产物为空索引数组（count=0）、NaN 包围球、工程序列化丢失分组 children、纹理对象丢失图像。**工程数据层（cube 坐标/UV/分组）自始至终未受损**，上述均为渲染网格与序列化层问题。
2. **native 导出崩溃**：Bedrock 编码器在导出时触发同一异常（`reading 'array'`）。故 geo.json 改由 `reference/make_geo.py` 从 bbmodel 数据独立生成；其映射约定（N/S/E/W 恒等、UP/DOWN 取 (u2,v2) 负尺寸）已用 navigation_terminal 的原生配对文件自检验证（17/17 同步 cube 完全一致；仅有的 2 处 UV 差异经核实为该交付导出后又编辑过贴图的键位牌方块，与约定无关）。
3. **bbmodel 回填**：保存的 .bbmodel 曾缺分组 children 与嵌入贴图，已从实时工程数据回填（本次工具链内完成，非手工编辑）。
4. **engine_r2 波及说明**：会话中途活动工程被切至 engine_r2，其间一次网格重建作用到其 17 个 cube 的**渲染网格**上（未触碰任何工程数据；该工程当时的网格本已处于隐形损坏态）。重新打开/重启 Blockbench 后一切从数据重建即恢复。如 engine_r2 出现显示异常，重启 Blockbench 即可。
5. **面→贴图绑定缺失（已修复 2026-11-09）**：制作会话中贴图指派在工程级，保存时 72 张面的 `texture` 字段被写成 `null`，导致重新打开工程后面片不渲染（仅线框）。已将全部面绑定到贴图索引 0（与导航终端工程写法一致）。`geo.json` 不受影响（Bedrock 导出不读取该字段）。

### 未验证事项（not_verified）
- 未进入 Minecraft/GeckoLib 游戏内验证（渲染、资源包接入、碰撞、缩放、性能）
- 未实现/未验证：发光、动画、粒子、交互
- 预览为离屏渲染产物，与游戏内光照表现存在差异
- 提示：在受影响的会话中重新打开本工程时，建议先重启 Blockbench 再操作，以避开上述网格重建异常

## 8. 一句话交接

接线盒按"原作换算比例 ×1.529、4× 贴图密度、关门单形态"交付：12 cube 撑起框圈/内凹门板/铰链/背板体块，512 确定性贴图承担全部表面细节，四视图与独立校验记录完整；下一轮如需开门态或发光，请作为新版本目录立项。
