# 潜水服 DivingSuit — 全身穿戴模型

制作来源：AI（Codex；Blockbench 5.2.2 / MCP 1.10.1，Python/Pillow 绘制像素图集）。

按《潜渊症》普通潜水服制作的 Minecraft 方块化美术候选。可编辑工程为 `diving_suit.bbmodel`，几何为 Bedrock Entity 1.12.0，标识 `geometry.diving_suit`。这是美术交付，尚未接入 Forge/GeckoLib 渲染器，未进入 Minecraft 验证。

## 原作依据

- 官方维基：<https://barotraumagame.com/wiki/Diving_Suit>，2026-11-11 查阅。维基提示部分数据可能过时，本次外观主要依照本机游戏素材。
- 游戏 `Content/Items/Diving/divinggear.xml`，`Item identifier="divingsuit"`。
- 普通服穿戴图集：`Content/Items/Diving/reg_DivingSuit.png`（512×256）。不使用战斗、深渊或损坏版潜水服。
- 头盔：`Content/Items/Jobgear/headgears.png`，裁切 `5,4,92,94`；XML 中 scale=0.65，贴图继承规则见原 XML。
- 柜内全身侧视：`Content/Items/Diving/DivingSuit_Item.png`，裁切 `181,0,75,193`，可见 alpha 边界在裁切内为 `0,0,70,188`。
- 原作中/英文名称分别来自 `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 与 `Content/Texts/English/EnglishVanilla.xml` 的 `entityname.divingsuit`：潜水服 / Diving Suit。
- 人体部件位置参照 `Content/Characters/Human/Ragdolls/HumanDefaultRagdoll.xml`；穿戴图集通过 `inheritsourcerect` 引用人体分肢区域，不能把整个 512×256 图集当作一件服装的宽高。

`reference/` 中的原作图片只供核对；交付模型的 PNG 是重新绘制的像素图集，没有把原画裁切直接贴到模型上。

## 忠实部分与推定部分

保留暗青灰硬壳、深色头盔视窗、顶部灯、下巴通气栅、肩侧关节、棕灰色褶皱接头、护腕、封闭手套、护腿和厚底靴。所有身体部位都有不透明几何覆盖；视窗以不透明深色反光表现。

二维素材没有完整三维背面与正面。后背分段板、前胸细节位置、厚度、左右对称关系和灯的正面形状为克制补全。头盔圆形轮廓被转成阶梯状方块体，不是原作曲面的一比一复制。没有添加外置氧气背罐、发光特效或动画。

## 比例与骨骼

用途是贴合 Minecraft 经典宽臂玩家，不使用地面物品精灵的 item.scale=0.5 去缩放全身服。站立玩家以 32 BB 单位高为适配基线；16 BB 单位=1 格。头部原点 `(0,24,0)`，躯干 `(0,24,0)`，双臂 `±5,22,0`，双腿 `±1.9,12,0`。正面朝 -Z，+Y 向上，靴底最低点 Y=-0.25。

最终尺寸（包含旋转后的视窗）以 `validation.json` 的 `transformed_bounds` / `size_bb` 为准；宽约 17.52、高 33.30、深约 9.99 BB 单位，即约 1.095×2.081×0.624 格。这是玩家穿戴尺寸，不是原作米制尺寸测量。

骨骼：`armorHead`、`armorBody`、`armorRightArm`、`armorLeftArm`、`armorRightLeg`、`armorLeftLeg`。这些名称便于后续护甲映射，但文件本身不会自动绑定现有模组。Slim 玩家、行走/游泳/蹲伏、帽子外层及与其他装备叠穿尚未实机验证。

## 制作与成本

几何通过本机 MCP 的原生 add_group / place_cube / modify_cube 在 Blockbench 中分组搭建；UV 用原生 set_cube_uv 设置。脚本负责坐标计划、像素绘图与批次调用，没有把整件成品 geo.json 一次导入充当逐块建模。原生导出器生成最终 `.bbmodel` / `.geo.json`。

64 个 cuboid，6 个骨骼，331 个导出面，单张 256×256 RGBA 图集；标称 2 px/BB，少量外壳适配修正使局部密度略有变化。无 mesh、无动画、无透明特效层。最终禁用 53 个被同骨骼结构完整遮挡的内部面，保留的内层几何用于封闭接头。游戏端 draw call 和 FPS 未测量。

## 验证记录

1. 已在 Blockbench 检查正面、侧面、背面、斜前方；预览均为原生离屏截图，1200×900、透明底、无网格/选中框。正/侧/背用正交，斜前方用透视。
2. 实际打开已保存 `.bbmodel`，6 骨骼、64 cuboid、内嵌贴图均正常；重开后原生几何导出与重开前一致。随后对裤腿内侧接缝做了裁齐并保存，避免站立时前表面共面闪烁。
3. `reference/validate_asset.py` 对最终文件执行可复算检查：六个经典玩家部位的轴对齐体积被对应骨骼的几何完整覆盖；PNG 与内嵌图片逐像素相同；导出数量一致；UV 在图集内。
4. `audit.geo.json` 为有限结构预检，errors=0 / warnings=0。`audit.bbmodel.json` 保留原始警告：检查器将 53 个刻意禁用的 null 内部面计为“未指定贴图”。最终工程的其余 331 个面都引用贴图 0，导出也恰为 331 面。不是紫黑贴图缺失。
5. 53 面清理的初始斜前方截图在 RGB / alpha 上与清理前相同；最终保留该内部面清理，并确认扩大的手套六面都有贴图。
6. 离屏视图清理时 MCP 1.10.1 报 `onContextMenu is not defined`。工具视图列表确认只剩主视口；未修改插件全局代码，无法确认监听器完全释放。此问题不影响已导出的模型。

复算：在本目录运行 `python reference/validate_asset.py`，需要 Python 3 和 Pillow。

截图相机均瞄准 `(0,16,0)`，正面 `(0,16,-70)`、背面 `(0,16,70)`、侧面 `(70,16,0)`，正交 zoom=0.5；斜前方 `(-45,30,-65)`，透视 FOV=35，关闭抗锯齿。通过 MCP create_offscreen_view + set_camera_angle 可重拍。

未修改项目注册、装备功能、渲染代码或资源目录；未构建、启动 Minecraft、执行 Git pull/commit/push、发布资产或发送消息。仓库 MEX CLI 在本会话的 PATH 中不可用，已直接读取项目交接和相关交付约定；没有改动仓库记忆文件。
