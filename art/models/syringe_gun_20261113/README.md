# 注射器枪 SyringeGun

制作来源：AI。Codex（GPT-6）制作几何、原创像素图集、UV、原生 Blockbench 预览与离线检查；用户提供两张原作参考图。

日期：2026-11-13。工具：本机 Blockbench 5.2.2、原生 Bedrock codec / Preview、Python + Pillow。交付格式由用户明确选择为 `.bbmodel + Bedrock 几何`。

![斜视](preview_oblique.png)

## 文件

- `syringe_gun.bbmodel`：可编辑 Bedrock Entity 工程，内嵌 64×64 贴图，无外部绝对路径依赖。
- `syringe_gun.geo.json`：Blockbench 原生导出，`format_version: 1.12.0`，identifier `geometry.syringe_gun`。
- `syringe_gun_tex.png`：单张 RGBA、64×64、单帧像素图集。
- `preview_front/back/side/oblique.png`：规范四件套预览；另有 `preview_top.png` 和 `preview_rear_oblique.png`。
- `validation.json`、`structure_audit.json`：离线交付、结构和成本证据。
- `reference/`：用户参考、游戏裁切、原创色板、制作脚本、原生重开证据与审计。

## 原作查证与参考范围

本机原作安装目录 `F:/SteamLibrary/steamapps/common/Barotrauma/`，分别核实：

| 内容 | 原作条目 |
| --- | --- |
| 简中名称 | `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml`，`entityname.syringegun` = 注射器枪 |
| 英文名称 | `Content/Texts/English/EnglishVanilla.xml`，`entityname.syringegun` = Syringe Gun |
| 实体 | `Content/Items/Tools/tools.xml`，`Item identifier="syringegun"`，`scale="0.5"` |
| 世界精灵 | `Content/Items/Tools/tools.png`，`sourcerect="0,93,149,71"` |
| 物品栏精灵 | `Content/Items/InventoryIconAtlas.png`，`sourcerect="0,64,64,64"` |
| 容量 | 原作 `ItemContainer capacity="1" maxstacksize="4"`；本模型以两侧各两支药筒表达四支容量，是视觉重排 |

补充查询：[官方 Wiki · Syringe Gun](https://barotraumagame.com/wiki/Syringe_Gun)。外观以用户两张图片和本机精灵为主，不混用高级注射器枪。

项目风格参考只来自 `curated/welding_tool_20261108` 与 `curated/plasma_cutter_20261110`：学习少量连续结构、灰绿金属与橡胶的材料区分，以及约 2 px/单位的像素语言。读取了这两件参考的实际工程、64×64 PNG、UV 和预览。未使用 deprecated，也未复制参考几何或贴图。

## 设计和尺寸

这是新建的低多边形候选，没有覆盖既有版本。保留长药仓、灰绿控制盒、两颗浅色侧面紧固件、银色尾端、镂空肩托、倾斜握把、开放扳机护圈、向下弯折拨杆，以及前端竖向套筒与短枪口。枪口具有真实开孔；药仓两侧的药筒具有厚度。不可见背面、截面与装配关系属于原创推断，并非从二维图中提取的官方三维结构。

原作精灵长宽比为 149/71≈2.10，`scale=0.5` 后约 74.5×35.5 游戏像素。该值不能直接换算现实米制尺寸。本候选按双手紧凑工具的轮廓，主动选择总长约 0.99 m；不宣称这是原作官方尺寸。先用约 20.95 单位长的结构图建模，再统一乘 0.75，得到约 15.71 Minecraft 模型单位；16 单位=1 格，按 1 格≈1 m 理解约 0.98 m；计入倾斜枪托的完整包围盒为 0.9923×0.4391×0.1275 格。精确含旋转包围盒、三轴尺寸与导出镜像关系见 `validation.json`。

轴向：Blockbench 工程枪口朝 +X、Y 向上、Z 为厚度；原生 Bedrock codec 镜像 X，导出枪口朝 −X。文件和骨骼枢轴采用 codec 的原生转换，不能直接把编辑坐标逐数复制进 geo。

## 材质与成本

62 cubes、7 bones、372 个声明面；一材质、一张 64×64 单帧图集，无描边壳、透明覆盖层或动画副本。上部药仓、药筒、握柄、枪托、护圈和前端组件按用途分组。直线与斜线结构使用连续长方体和旋转体，没有逐像素挤出或逐扫描线堆叠。

色板采用灰绿旧漆、深色橡胶、浅灰金属、米白药筒、少量青绿药液窗与低饱和黄铜。贴图为硬边色块，不使用渐变和随机噪点。图集按建模草图约 2 px/单位分配，整体缩放后约 2.67 px/最终模型单位；不足一像素的细轴按至少 1 px 处理。相同材料、像素尺寸、纹理语义的面共享 UV，岛外留 1 px 挤出边。

已审计重复体、零体积和隐藏面。17 个几何遮挡候选在原生透视严格像素对照中未达到完全不变，故保守保留全部候选；没有把失败的严格比较称为优化通过。局部接合仍含内部面，成本仅 372 面。相关算法、候选和像素证据在 `reference/face_cleanup_audit.json`、`verified_face_selection.json` 及比较报告中。基础 RGBA 内存计算为 16 KiB，不代表显存或 FPS 实测。

## 验证与边界

- 四件套、文件名、geometry identifier、正尺寸、无重复体：离线校验。
- PNG 为 RGBA、2 的幂；仅 alpha 0/255，透明像素 RGB 为零；所有实际 UV 采样区域不透明，内嵌 PNG 与外置 PNG 完全一致。
- 在本机 Blockbench 5.2.2 保存、关闭本任务工程、重新打开；元素、分组和贴图来源完全一致；重新执行原生 **文本导出** 后 geo 结构严格相等。证据为 `reference/native_reopen_validation.json`，不是拿 raw 浮点对象与经过 codec 数字序列化的 JSON 直接比较。
- 六张预览均由 Blockbench 原生 Preview / Three.js 生成，1200×900、纯色底、无 UI/网格。正、背、侧、顶为正交；两张斜视为透视。相机和投影见 `reference/render_conditions.json`。历史清理对照有少量透视抗锯齿差异，未声称所有历史截图严格一致；最终预览均由当前交付工程生成。
- 游戏端未实测；未接入物品 renderer/attachable、手持姿态、射击逻辑或动画。当前交付为静态美术模型。未修改仓库代码、未提交或推送。

## 重现

`reference/build_asset.py` 用自身位置寻找资产根目录，从相对文件重新生成原创贴图和原始工程，不依赖游戏安装路径或用户参考图片。需要 Python、Pillow。运行：

```text
python reference/build_asset.py
```

原生导出与预览需要已安装 Blockbench 的交互式运行环境。`bb_native.py` 通过本机 9224 调试端口连接**隔离实例**，只使用 Blockbench 原生 API，不使用 MCP token。该端口是本轮隔离实例的启动参数，不修改用户原实例或认证配置。打开隔离实例后，在资产目录运行：

```text
python reference/bb_native.py reference/load_native.js
python reference/bb_native.py reference/render_native.js
python reference/bb_native.py reference/verify_reopen.js
python reference/validate_asset.py
```

首次加载要求隔离实例没有工程；重建已打开的本任务工程用 `reload_authored.js`。Python 桥接脚本需 `websocket-client`。脚本自动把自身所在资产目录传给 JavaScript，不依赖作者本机绝对资产路径。正式归档到仓库后可从仓库根使用对应相对脚本路径运行。

2026-11-13 用户明确指定将本资产升入本地 `curated/syringe_gun_20261113/`，并按交付约定发布到上游 `main` 的 `art/models/syringe_gun_20261113/`。本轮范围是资产归档与登记，不含运行时接入。
