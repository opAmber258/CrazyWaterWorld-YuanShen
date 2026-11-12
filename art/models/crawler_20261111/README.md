# 藻鬃爬行者 Crawler

制作来源：AI（Codex + Blockbench 5.2.2 / MCP 1.10.1；Python/Pillow 重新绘制像素图集）。

《潜渊症》普通 Crawler 的 Minecraft 方块化模型。仅交付爬行者，含可编辑 `.bbmodel`、Bedrock Entity 1.12.0 `.geo.json`、内嵌及独立 PNG、五向预览和验证记录。未接入模组代码，未进入 Minecraft 验证。

## 原作查证

主要外形依据是本机游戏的 `Content/Characters/Crawler/`，未混用 Legacycrawler、Crawlerhusk 或 Crawlerbroodmother 的素材。

- `Crawler.xml`：speciesname = Crawler。
- `Ragdolls/CrawlerDefaultRagdoll.xml`：limbscale=0.65、jointscale=0.65、texturescale=0.5，图集为 `Content/Characters/Crawler/crawler.png`（512×512）。
- 头部 sourcerect=`306,4,204,164`；裁切内实际 alpha bbox=`6,8,202,161`，可见 196×153 px。
- 躯干 sourcerect=`72,0,230,226`；裁切内 alpha bbox=`0,6,225,206`，可见 225×200 px。
- 原作有头、躯干、独立颚、六节尾和两套前肢分段，共 15 个可见肢体。隐藏的攻击判定肢体没有作为美术模型制作。
- 原作中英名称分别核对简中与英文文本的 `character.crawler`：藻鬃爬行者 / Crawler。
- [官方维基 Crawler](https://barotraumagame.com/wiki/Crawler) 用于交叉核对四对眼、背棘、前肢与长尾。该页面标有资料可能过时的提示；尺寸使用本机实际 XML 和 PNG。
- [官方角色编辑器说明](https://regalis11.github.io/BaroModDoc/Editors/CharacterEditor.html) 的爬行者截图用于理解爬伏姿态与关节关系；截图属于较早版本，不用它量当前版本的绝对尺寸。

`reference/original_parts.json` 记录全部 15 个可见肢体的裁切、alpha 边界和原点；原始图集与头/躯干裁切仅供核对。本模型实际使用的 `crawler_tex.png` 为本次新绘制的像素图。

## 实际参考过的 Minecraft 三维模型

1. [Crocodile - minecraft](https://sketchfab.com/3d-models/crocodile-minecraft-21e46c7059d845ff8978a5cb2595bab3)，作者 **!RadHer**。参考公开预览中的长尾方块递减、背部剪影，以及少量像素色块组织。
2. [Crocodile - Minecraft animal #BlockBench](https://sketchfab.com/3d-models/crocodile-minecraft-animal-blockbench-073c04c844654a8cbbc9fff138715cab)，作者 **W’Projects**。参考公开预览中的体块衔接、方块齿口和橄榄色皮肤的像素表现。

参考范围为公开模型预览；几何、骨骼、UV 和贴图由本次重新制作。包内仅有爬行者资产，另两件模型文件和预览图未包含。作者、原链接及参考范围也记录于 `reference/references.json`。

## 比例与补全

使用项目的近似换算：16 BB 单位=1 格；Barotrauma 默认人物约 176 显示单位对照 MC 的约 1.8 格。爬行者肢体精灵的换算因子为：

`0.65 × 0.5 × 16 × 1.8 / 176 ≈ 0.05318 BB/px`。

因此，头部可见侧视约 10.42×8.14 BB，躯干约 11.97×10.64 BB。以这两个大形、六节尾的递减尺寸及连接关系校准整体，保持前高后低的爬伏展示姿态。此换算是相对人物的近似，不是原作厘米或米制测量。

| 最终尺寸 | BB 单位 | Minecraft 格 | 依据 |
| --- | ---: | ---: | --- |
| 前后长 | 45.75 | 2.8594 | 原作侧视比例 + 三维姿态补全 |
| 左右宽 | 20.1802 | 1.2613 | 躯干厚度、双臂展开为三维推定 |
| 总高 | 17.9810 | 1.1238 | 原作肢体大小 + 展示姿态 |

准确旋转后边界见 `validation.json`。+Y 向上，正面朝 -Z，尾部朝 +Z；root 位于 `(0,0,0)`。最下方爪尖约 Y=-0.12 BB，为约 0.0075 格的轻微低于基准面；没有设定游戏碰撞箱或落地偏移。

保留特征：隆起的粗糙头部、每侧四只橙红眼、上下齿颚、两条屈曲支撑前肢、蹼状手、六节递细长尾、背棘和少量尾侧退化附肢。皮肤用灰绿、橄榄褐和浅色结块表现。二维侧视无法证明的厚度、背面、双侧对称与蹼指细节属于三维化补全。

## 结构与成本

- 91 个正尺寸 cuboid；16 个骨骼；538 个导出面；单张 256×256 RGBA 图集，标称 3 px/BB。
- 骨骼为 1 个层级 root、body、head、jaw、tail_1..tail_6，以及左右各 upper_arm / forearm / hand。root 作为父级容器有明确用途。
- 下颚与各节尾有独立枢轴；本轮为静态展示模型，未添加动画文件、动作副本、发光层或粒子。
- 8 个确定被同骨骼不透明几何完整遮挡的内部面已禁用。跨骨骼、旋转不易证明或未来动作可能露出的面保留。
- 建模预算 120 个 cuboid，本次为 91；没有用逐像素挤出皮肤花纹。游戏 draw call / FPS 未测量。

通过原生 MCP add_group / place_cube 分阶段搭建，modify_cube 修正头部角度，set_cube_uv 设置显式逐面 UV；脚本规划坐标和绘制图集。没有将整件成品 geo.json 一次导入冒充窗口内逐块搭建。

## 验证

1. 已实际查看 Blockbench 正面、侧面、背面、斜前方、俯视。五张预览均为原生离屏截图，1200×900、透明底、无网格或选中框。
2. 8 面清理前后，四个固定视角的 RGBA 逐像素相同；记录见 `reference/cleanup_comparison.json`。
3. 实际重开磁盘 `.bbmodel`，91 个 cuboid、16 骨骼和内嵌图集正常。最终 `.geo.json` 来自重开后的工程，再次导出结果一致。首次重开时两个前臂长度出现 0.00001 BB 的序列化末位差，已由最终重新导出对齐。
4. `audit.geo.json`：errors=0、warnings=0。`audit.bbmodel.json`：errors=0、1 类警告；该警告把刻意禁用的 8 个 null 面计为“未指定贴图”，其余 538 面均引用贴图 0，实际导出恰为 538 面。
5. 通用预检的包围盒只基于未旋转坐标，不能作为最终尺寸。本包另外计算所有旋转后的顶点，并与原生 OBJ 导出的实际顶点边界核对。
6. 离屏视口删除时 MCP 1.10.1 报 `onContextMenu is not defined`；工具列表确认仅余主视口。未修改插件，监听器释放不能由此确认。

本目录运行 `python reference/validate_asset.py` 可复算结构、层级、贴图、UV、实际尺寸和预览边距；需要 Python 3 与 Pillow。这些检查不认证运行时、动作、AI、攻击或碰撞。

截图重拍参数：统一 target=`(0,8,9)`；front=`(0,8,-70)`、back=`(0,8,80)`、side=`(70,8,9)`，正交 zoom=0.5；oblique=`(50,29,-45)`，透视 FOV=28。top 单独 target=`(0,8,9.725)`、position=`(0,90,9.725)`、正交 zoom=0.4。使用原生 create_offscreen_view + set_camera_angle，关闭抗锯齿。

已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。未修改模组代码或旧资产，未执行构建、启动游戏、Git 拉取/提交/推送或发布。
