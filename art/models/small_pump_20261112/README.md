# 小型泵 SmallPump

| 项 | 内容 |
| --- | --- |
| 资产名 | 小型泵 SmallPump |
| mod 标识 | `small_pump` |
| 制作来源 | 人工（Han-xing-official） |
| 版本 | 4.0 |
| 交付日期 | 2026-11-12 |
| 类型 | 船内装置 / 道具（GeckoLib 静态几何，无动画） |
| 格式 | Blockbench 5.0 工程 + Bedrock Entity 几何（`format_version 1.12.0`） |

> **制作来源：人工（Han-xing-official）**
> 建模、贴图绘制、预览截图全部由人工完成，未使用 AI 生成或 AI 辅助生成。

---

## 1. 来历

本资产为 Barotrauma 原版「小型泵 Small Pump」的方块化重制，接在 mod 既有的 `small_pump` 装置上
（`SmallPumpBlock` / `SmallPumpBlockEntity` / `SmallPumpRenderer`，GeckoLib 渲染）。

工程由作者在 Blockbench 中完成，交付版为 `小型泵4.0`，是一份独立的完整工程，不依赖任何历史版本。

---

## 2. 原作查证

中英两名分别查证，来源均为游戏安装目录，不是直译。

### 2.1 名称条目

| 项 | 查证结果 | 来源 |
| --- | --- | --- |
| 中文名 | 小型泵 | `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml:2885` → `<entityname.smallpump>小型泵</entityname.smallpump>` |
| 英文名 | Small Pump | `Content/Texts/English/EnglishVanilla.xml:2885` → `<entityname.smallpump>Small Pump</entityname.smallpump>` |
| 条目标识 | `entityname.smallpump` | 上述两文件同标识条目 |

英文名按官方英文取驼峰：`Small Pump` → `SmallPump`。
mod 标识沿用仓库既有 `small_pump`，与 `SmallPumpBlock` / `SmallPumpBlockEntity` / `SmallPumpRenderer` 及资源文件名一致。

### 2.2 原作实体与图集坐标

| 项 | 查证结果 | 来源 |
| --- | --- | --- |
| 实体定义 | `identifier="smallpump"`，`category="Machine"`，`tags="pump"`，`linkable="true"`，`allowrotatingineditor="true"` | `Content/Items/Pump/pump.xml:68` |
| 图集文件 | `Pumps.png` | 同上 `<Sprite texture="Pumps.png" ...>` |
| 图集坐标 | `sourcerect="1,264,133,89"`，`depth="0.8"`，`origin="0.5,0.5"` | 同上 |
| scale | `0.5` | 同上 `<Item ... scale="0.5">` |
| 损坏态 sprite | `134,264,133,89`（maxcondition 80）、`267,264,133,89`（maxcondition 0） | 同上 `<BrokenSprite>` |

原作 sprite 为 133×89 像素、scale 0.5，即世界内占位约 66.5 × 44.5 像素，长宽比 1.49；
`origin 0.5,0.5` 说明原作以中心为锚点。本模型的居中处理与此一致（见 3 节）。

---

## 3. 尺寸依据

**换算基准：长边按 1 格（16 单位）对齐。**

整机长边（Z 向）取 16 单位，正好等于 1 个方块；横向与纵向按同一比例缩放，不做非等比拉伸。

| 轴 | 渲染包围盒（单位） | 折合方块（16 单位 = 1 格） |
| --- | --- | --- |
| X（宽） | −4.011 ~ 4.011，跨度 **8.021** | 0.501 格 |
| Y（高） | 0 ~ 8.500，跨度 **8.500** | 0.531 格 |
| Z（长） | −8.000 ~ 8.000，跨度 **16.000** | 1.000 格 |

- 底面落在 **Y = 0**，可直接坐在舱面/地板上，无需抬高。
- X / Z 以原点为中心对称（±4.011 / ±8.000），与原作 sprite `origin 0.5,0.5` 的中心锚点一致。
- 长宽比 16 : 8.021 ≈ 1.99，比原作 sprite 的 1.49 更修长——这是立体化后的取舍：
  原作是单个平面 sprite，重制为 3D 后按筒体 + 端盖的实际比例展开，未强压成 sprite 比例。

> ⚠️ 尺寸说明：工程内 27 个 cube 带旋转、20 个带 inflate，上表是**旋转后的渲染包围盒**。
> 未旋转的原始坐标范围是 X −4.000~5.836 / Y −1.471~8.586 / Z −8.000~14.550，
> 看着像"Z 向 22.55 超一格、Y 沉地 1.47"，那是未套用旋转的中间态，**不是实际尺寸，不可直接引用**。

---

## 4. 几何构成

| 项 | 数值 |
| --- | --- |
| 骨骼组（bone） | 18 |
| cube | 38 |
| 面 | 228（三角形上界 456） |
| 带旋转的 cube | 27 |
| 带 inflate 的 cube | 20 |
| 零厚度平面贴片 | 12（某轴 size=0，inflate 0.0002 → 实际厚度 0.0004） |
| 动画 | 0 |
| 贴图 | 1 张，64×64，RGBA8 |
| 贴图实际用色 | 225 种 |
| 半透明像素 | 0 |
| UV 范围 | u 0–63，v 0–57（未越界） |
| 可见范围 | width 2 / height 2.5 / offset [0, 0.75, 0] |

骨骼分组（保留作者原始命名）：

```text
水泵
├── 主罐
│   ├── 斜边2
│   └── 贴片
│       ├── 主管后贴片 → 斜边
│       └── 主管前贴片 → 斜边5
├── 李
│   ├── 左李
│   └── 右李
├── 末
└── 前端
    ├── 接口
    └── 大黑头
        ├── 斜边3
        └── 大黑头前贴片 → 斜边4
```

其中 `水泵` / `贴片` / `主管前贴片` / `主管后贴片` / `大黑头前贴片` 为纯父级分组，本身不含 cube，
是结构分层而非孤立骨骼。

**关于 12 个零厚度贴片**：这 12 个 cube 的 Z 轴 size 为 0（45° 斜边贴片），厚度完全由 `inflate 0.0002`
撑出，实际只有 0.0004 单位。这是 Blockbench 里做平面贴片的常见做法，Bedrock/GeckoLib 可以正常渲染；
但厚度极薄，若在游戏内出现闪烁（z-fighting），把 inflate 提到 0.01 量级即可，不影响形状。

---

## 5. 保留的识别特征

对照原作 sprite（`Pumps.png` 1,264,133,89）与 3D 重制，刻意保留了以下特征：

1. **深色圆盘状端盖**（大黑头 + 斜边3/4）：对应原作左端的深色圆盘，重制为带倒角的多段圆形端面。
2. **八角硬边轮廓**：端面由多段斜切（斜边 / 斜边2 / 斜边3 / 斜边4 / 斜边5）拼出八角形，保住原作 low-poly 的硬边味道。
3. **侧面条纹大面板**（主管前/后贴片）：原作机身侧面的横向纹路，重制为独立贴片层。
4. **左右支耳**（左李 / 右李）与**底部安装座**（末）：原位对应的安装结构。
5. **前端接口**（接口）：对应原作的进出水管口位置。
6. **贴图尺寸沿用 64×64**：与原作的低分辨率像素风格保持一致，不使用高分辨率渐变。

---

## 6. 本轮范围

- 只交 **4.0** 这一版，新建 `art/models/small_pump_20261112/` 日期目录。
- **不动**仓库根下既有的扁平资产 `small_pump.bbmodel` / `small_pump.geo.json`（属约定 2.2 的历史豁免，本次不做覆盖）。
- 无动画：本轮为静态几何交付，`animation.json` 不在范围内。
- 不改代码资源：geo / 贴图的运行时接入由集成者按约定第 5 节处理。

---

## 7. 预览

| 文件 | 视角 | 投影 | 尺寸 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_front.png` | 正视（沿长轴看端盖） | 正交 | 1200×900 | ✅ |
| `preview_back.png` | 背视（沿长轴看另一端） | 正交 | 1200×900 | ✅ |
| `preview_side.png` | 侧视 | 正交 | 1200×900 | ✅ |
| `preview_oblique.png` | 斜视 | 透视 | 1200×900 | ✅ |

四张均为 Blockbench 内置截图产出，**已按约定 3.1 重出为 1200×900**：RGBA，四角 alpha 为 0（背景真透明），
无棋盘格、无网格、无 UI、无坐标轴 gizmo，与当前几何一致。
正视前景框 161×171、背视 161×171，宽高比 0.941，与模型端面 8.021 : 8.500 ≈ 0.944 吻合，
说明两张取自同一长轴的两端，不是同一视角重复。

> 俯视图为附加视角、非四件套要求，本轮不入库：重出的四张已达标，避免混入非 1200×900 的图。

---

## 8. 参考文件（reference/）

| 文件 | 内容 |
| --- | --- |
| `official_sprite_smallpump.png` | 原版 sprite 原样裁切，133×89。取自游戏 `Content/Items/Pump/Pumps.png` 的 `sourcerect 1,264,133,89`，未做任何重绘或调色 |
| `official_source.json` | 原作出处结构化记录：图集文件名与尺寸、sourcerect、origin / depth / scale、损坏态 sprite 坐标、实体定义所在文件与行号、中英名称条目 |
| `palette.json` | 本资产贴图的用色采样，按钢材明度三档 + 纯色区分色分列，每档取分位代表色 |
| `palette_meta.json` | 调色板统计：唯一色 225、不透明像素 1756、UV 覆盖率 1.0、半透明像素 0 |

贴图用色构成（按不透明像素计的占比）：

| 分组 | 唯一色数 | 像素占比 | 说明 |
| --- | --- | --- | --- |
| `steel_dark` | 28 | 22.0% | 暗部钢材，V < 0.20 |
| `steel_mid` | 52 | 12.3% | 中间调，0.20 ≤ V < 0.35 |
| `steel_light` | 139 | 25.6% | 亮部，V ≥ 0.35，整体偏冷 |
| `saturated_primaries` | 6 | **40.0%** | 6 种纯色：`#00ffff` `#ff00ff` `#ff0000` `#00ff00` `#ffff00` `#0000ff`，用于部件区分 |

> 说明：`saturated_primaries` 这 6 种纯色合计 703 像素，用途是在编辑时区分相邻部件。
> 实测 67 个使用该类颜色的面中，**47 个被其他 cube 包覆**，其余多为与相邻 cube 共面贴合的内侧面，
> 装配后不参与外观呈现。UV 覆盖全部 1756 个不透明像素，无废像素。

`reference/` 内**不含渲染脚本**：四向预览均由 Blockbench 内置视口截图产出，未使用自建渲染器，
故无脚本需要入库，预览可在 Blockbench 中直接重现。

---

## 9. 自查结论

| 检查项 | 结果 |
| --- | --- |
| 四件套齐备且命名合规 | ✅ bbmodel / geo.json / _tex.png / 四向预览齐，命名符合约定 3.1 |
| `geo.json` 可解析，identifier 正确 | ✅ `geometry.small_pump`，`format_version 1.12.0` |
| 包围盒与本文记录一致 | ✅ 见第 3 节，旋转后实测值 |
| 贴图 2 的幂，透明像素真透明 | ✅ 64×64，半透明像素 0 |
| 四向预览与当前几何一致 | ✅ 正/背/侧/斜四向齐全且比例自洽 |
| 预览尺寸达 1200×900 | ✅ 四张均为 1200×900，透明底 |
| 无未使用 cube / 孤立骨骼 | ✅ 38 cube 全部在骨骼下，无游离元素；5 个空父级骨骼已说明 |
| cube 尺寸为正 | ✅ 计入 inflate 后全部为正；12 个零厚度贴片已在第 4 节说明 |
| geo 与 bbmodel 一致 | ✅ 反算回工程坐标逐 cube 比对，38/38 完全相等 |
| 工程可见包围盒与导出一致 | ✅ 工程 `visible_box` 已设为 `[2, 2.5, 0.75]`，按 `calculateVisibleBox()` 复算导出同为 width 2 / height 2.5 / offset 0.75，与 `geo.json` 描述完全相同 |
| `reference/` 齐备 | ✅ 原作裁切 + 出处记录 + 调色板 + 调色板元信息，无待入库脚本 |
| 工程可在 Blockbench 5.2.2 重开并原生重导出 | ⏳ **待验收**：GUI 重开确认与原生重导出比对均未做，`bbmodel_reopens`、`native_reexport_matches_committed_geo` 记为 `pending-manual` |

结构化记录见 `validation.json`，结构审计原始报告见 `structure_audit.json`。

> **当前交付状态：`incomplete`（待验收）。** 24 项检查通过，剩两项人工验收：
> ① 在 Blockbench 5.2.2 中重开本工程，确认内嵌贴图、骨骼与几何正常；
> ② 在该版本内用原生 `Export Bedrock Geometry` 重导出，与仓库内 `small_pump.geo.json` 逐字段比对一致（证明「重出即可复现」，而非只靠离线复算）。
> 两项完成后 `validation.json` 的 `status` 改回 `complete`，`bbmodel_reopens` 与 `native_reexport_matches_committed_geo` 改为 `true`；只做 GUI 重开、未做重导出比对，不视为验收完成。
