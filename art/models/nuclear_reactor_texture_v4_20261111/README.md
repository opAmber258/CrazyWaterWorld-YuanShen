# 核反应堆 NuclearReactor · 材质修订 v4

**制作来源：人工 + AI（TakaraMiyuki + GPT 6.1 Sol）**

- 人工：TakaraMiyuki —— 修改几何、确定美术方向与验收
- AI：GPT 6.1 Sol（Codex）—— Python/Pillow 重绘像素材质，Blockbench 5.2.2 导出与预览

本轮仅改贴图，113 个 cube、9 个分组以及全部 UV 均与保存的 v3 完全一致。

## 本轮方向

用户指出 v3 的斑驳像迷彩、材料和色彩层次难以辨认，并取消先前的选色限制。本版重新建立连续的大色块和材料对比：

- 灰白旧漆壳体：保留安静、完整的漆面，板缝、折边、紧固件与检修结构负责近看细节。
- 偏暖浅色护套：与灰白壳体区分，管箍下方有污流、下缘积垢和局部漆损。
- 深色钢铁与机械：铸造面、散热肋、端盖、法兰和焊缝有可读的明暗层级；局部油污从紧固与密封处向下延伸。
- 受热管道：暖暗铁色主体、冷灰热暗区、短露铁擦痕和接头积污，避免把腐蚀画成独立贴上的色块。
- 观察窗：低亮度烟灰玻璃，整块弱反射；没有新增显示信息或运行时发光。

不再使用大面积随机填充、迷彩状斑块或均匀撒点。随机种子仅决定小范围边缘擦伤和污流的位置，左右仍独立绘制。机械槽、铆钉和无磨损的内部金属表面允许规则或一致。

本轮验收重点是正常观看大小下的部件层次、材料区别和磨损位置。颜色数量、平均亮度、随机程度均不作为美术合格分数。最终视觉验收待用户确认。

## 保护范围与规格

输入保存于 `reference/user_baseline/nuclear_reactor_user.bbmodel`，源文件 SHA-256 见 `reference/baseline_provenance.json`。v2、v3 和最早的用户保存版均未覆盖。

| 项目 | 本版 |
| --- | --- |
| 几何 | 113 cubes / 9 bones / 678 声明面；尺寸、位置、旋转、轴心、层级不变 |
| UV | 全部面属性及 UV 与 v3 逐项完全一致 |
| 贴图 | 512×512 RGBA；内嵌 PNG 与外置 PNG 字节一致 |
| 像素密度 | 沿用 4 px/BB、整数像素岛、每岛 1 像素颜色外扩 |
| 纹理岛 | 585；584 个外露面分别独立，94 个完全内部面沿用共同内部材质区 |
| 格式 | Bedrock Entity；导出 `format_version=1.12.0`，`geometry.nuclear_reactor` |
| 预览 | 1200×900，透明底、无网格/UI；正/背/侧正交，斜视透视 |

小数尺寸的极薄侧边至少占一个 texel，仍有不可避免的整像素取整误差。本轮未更改 UV；此前棋盘格验证继承有效，并用面属性完全相等与独立导出比较再次核对。

真实旋转后包围盒（BB）：

```text
min  [-15.24706, -0.04367, -12.95000]
max  [ 15.24706, 19.90586,   6.15000]
size [ 30.49412, 19.94953,  19.10000]
约 1.906 × 1.247 × 1.194 格（宽、高、深）
```

正面 +Z；最低点与 Z 偏移原样保留。无动画；声明三角形上限 1356。技能预检不应用 cube 旋转的基础包围盒不能代替上述真实范围，独立角点计算已与实际 cube 网格矩阵核对。

## 来源与推定

原作反应堆裁切 `reference/original_reactor_crop.png` 用于判断材料关系、主要功能区和积污位置。已查阅的 `curated/engine_20261111_v2` 仅作为项目像素细节层级参考。旧版仅作为返修前后比较，不复制原版或其他模型的纹理像素。

沿用首轮独立查证的条目，原始记录在 `reference/source_info.json`：

- 简中：`Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml`，`entityname.reactor1` = `核反应堆`。
- 英文：`Content/Texts/English/EnglishVanilla.xml`，`entityname.reactor1` = `Nuclear Reactor`。
- 定义：`Content/Items/Reactor/reactor.xml`，`reactor1`、`scale=0.5`，图集矩形 `[0,0,528,336]`。
- 首轮换算假设 `K=0.5×28.8/242.25` BB/原作像素，不是官方米制尺寸。本轮完全服从用户保存后的几何；厚度、背部与遮挡区是美术推定和用户修改。

## 文件与复现

主文件为 `nuclear_reactor.bbmodel`、`nuclear_reactor.geo.json`、`nuclear_reactor_tex.png` 和 `preview_{front,back,side,oblique}.png`。`validation.json`、`structure_audit.json` 记录实际检查。

`reference/` 包含基线、原作裁切、色板、UV 布局、绘制脚本、磨损分布和检查报告。`readability_review.png` 将四张原生预览用最近邻缩至 360×270，便于检查正常观看大小；生成脚本 `make_readability_preview.py` 一并提供。

Python 3 + Pillow 可从本目录离线重绘，全部输入均包含，不依赖本机游戏路径：

```powershell
python reference/repaint_reactor_v4.py
```

随后使用运行中的 Blockbench 5.2.2 与本地 MCP `risky_eval` 进行原生导出和预览。任务实例端口 3015，可通过 `bb_rpc.py --port` 调整。以下步骤依赖编辑器会话，不能在纯离线仓库中运行；脚本均包含在交付内：

```powershell
python reference/bb_rpc.py --code reference/load_revision.js
python reference/bb_rpc.py --code reference/verify_revision.js
python reference/bb_rpc.py --code reference/preview_revision.js
python reference/audit_materials.py
python reference/validate_revision.py
python reference/make_readability_preview.py
```

`verify_revision.js` 重新打开工程、核对原生导出并统一外置与内嵌 PNG 的编码。四视图由 Blockbench 原生 MediaPreview 生成。

## 实际检查与边界

- 几何、分组层级、全部 UV 与面属性冻结：通过。
- 工程重新打开后的原生 Bedrock 导出精确复现：通过。
- 独立比较 113 个 cube、678 个面 UV：通过。
- PNG 一致、UV 整数/边界/间距、预览尺寸和裁切：通过。
- 左右材质比较：112 对；有磨损的对应面没有相同磨损掩码。三对完全相同的图案为无磨损的散热槽背板和护套端面，不是复用做旧。原始数据保留在 `material_audit.json`。
- 正面、背面、侧面、斜视、缩小预览及可见 Blockbench 窗口：已实际检查。
- 技能工程/Bedrock 预检：均 0 errors、0 warnings；原始报告及限制保留。

已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。未增加代码、碰撞、交互、动画、运行时发光，未提交或推送资产。
