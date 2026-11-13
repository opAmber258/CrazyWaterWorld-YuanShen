# 大型钢柜 LargeSteelCabinet

制作来源：**人工 + AI（用户手动修订几何与贴图；GPT-6 Codex + Blockbench 5.2.2、Blockbench MCP、Python/Pillow 完成初稿、导出、预览与校验）**。用户确定尺寸与柜门表现，并在初稿完成后保存了 103-cube 修订版；本次发布完整保留用户保存的几何、UV 与贴图，未用生成脚本覆盖。上游评选尚待进行。

《Barotrauma／潜渊症》大型钢柜的 Minecraft 低多边形静态美术模型。按用户要求制作为 **3 格宽 × 2.5 格高 × 约 0.875 格深**；关闭展示，五个柜门独立分组并配置铰链轴心，便于以后继续制作开门动画。

已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。

## 文件

| 文件 | 内容 |
|---|---|
| `large_steel_cabinet.bbmodel` | 可编辑的 Bedrock Entity 工程，贴图内嵌，无绝对贴图路径依赖 |
| `large_steel_cabinet.geo.json` | Blockbench 原生 Bedrock 1.12.0 导出，`geometry.large_steel_cabinet` |
| `large_steel_cabinet_tex.png` | 512×512 RGBA 固定色板像素纹理 |
| `preview_front.png` / `preview_back.png` / `preview_side.png` | 1200×900 正交预览，透明底，无网格、无 UI |
| `preview_oblique.png` | 1200×900 透视预览，透明底，无网格、无 UI |
| `validation.json` / `structure_audit.json` | 独立几何、UV、贴图与尺寸核对记录 |
| `reference/` | 原作裁切、用户图片、色板、制作脚本、原生重开和预览记录 |

## 原作查证

当前安装文件逐项核实（2026-11-13）：

- 中文：`Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml:3953`，`entityname.steelcabinet = 大型钢柜`。
- 英文：`Content/Texts/English/EnglishVanilla.xml:3953`，`entityname.steelcabinet = Large Steel Cabinet`。
- 实体：`Content/Items/Containers/containers.xml:53`，`identifier="steelcabinet"`，`scale="0.5"`。
- 图集：同目录 `containers.png`；`sourcerect="348,2,364,373"`，`origin="0.5,0.5"`。
- 原作容器：35 个普通格 + 5 个子容器格；此处仅查证物件身份，不代表已实现存储功能。
- 补充原作身份来源：[官方 Wiki 容器页](https://barotraumagame.com/wiki/Containers)。具体外形以本机原始贴图和用户图片为准。

原作数据、用户摆放要求与美术推定分别记录，不将推定写成官方数据。

## 尺寸换算与用户指定

仅作为对照，沿用 curated 导航终端 README 中的人物视觉换算假设：人物视觉高度 28.8 BB、人物参考像素高度 242.25 px。

```text
K = scale 0.5 × 28.8 / 242.25 = 0.0594427 BB/px
原作比例参考宽 = 364 × K = 21.637 BB ≈ 1.352 格
原作比例参考高 = 373 × K = 22.172 BB ≈ 1.386 格
```

用户明确选定约 **3×2.5 格**，因此最终按 48×40 BB 设计，宽高比 1.2，较原作更横向舒展；观察窗按高度等比构造，保持近圆的八边形，不随外壳横向拉伸。

最终旋转后包围盒（BB）：X `-24…24`，Y `0…40`，Z `-7.1025…6.9025`。尺寸 XYZ = `48×40×14.005` BB，即 `3×2.5×0.8753125` 格。正面 +Z，Y=0 贴地，X 居中。保留用户延伸后的背部安装条，因此 Z 中点为 -0.1 BB，未自动重居中。

**美术推定：**柜体深度、折边、背部维护板及安装横梁、门后结构、铰链轴向、窗框与锁件的三维厚度。原作是二维侧视贴图，未提供这些方向的完整证据。

## 保留特征与风格参考

- 三列柜体：左侧两个小柜格和一个下柜门，中间观察窗长门，右侧全高长门。
- 保留上部横梁、百叶通风槽、独立门锁、下部三段踢脚凹板。
- 冷暖克制的灰绿钢板、深色密封门缝、八边形钢质窗框；少量短磨边，不使用随机噪点和照片纹理。
- 项目风格仅参考 `curated/navigation_terminal_20261108` 的灰绿金属、有限明暗层次与规则像素细节。没有读取 deprecated；没有复制其它模型的几何或 UV。
- 没有使用外部优秀模型；此物件以原作布局和项目已有材质语言为主要依据。

## 编辑分组

103 cubes，10 bones，618 个声明面。根组 `large_steel_cabinet`，固定结构为 `housing`、`crown`、`plinth`、`rear_panels`；五个柜门为 `door_left_top`、`door_left_mid`、`door_left_lower`、`door_center`、`door_right`。各门的门板、铰链、锁件和观察窗从属于对应门组。

几何负责壳体厚度、门板层次、铰链、窗框和门锁；百叶、浅折边、紧固件及磨损由纹理表现。名义纹理密度 2 px/BB（32 px/格），微小零件向上取整到整数纹素。相同尺寸的普通金属面有意共用 UV 区域，以维持材质一致；修改共用区域会影响对应的所有面。

观察窗为关闭柜门表面上的不透明风格化暗玻璃。未制作透明玻璃材质、柜内陈列、开门动画、游戏碰撞、交互、存储功能、粒子或发光。

## 验证与边界

- 在可见 Blockbench 中制作，检查正面、背面、侧面和斜视；四张交付预览由 Blockbench 原生 offscreen viewport 输出。
- 工程由 Blockbench 原生 project codec 保存，再从已保存 `.bbmodel` 重新打开；103 cubes、10 bones、贴图与分组均保留。
- 最终已保存工程重新打开后，再次原生 Bedrock 导出与交付几何的 JSON **完全一致**；数值最大差异为 0。
- 独立校验核对了逐 cube 坐标、旋转与轴心、UV 矩形、纹理内嵌一致性、包围盒及四视图尺寸，详见 `validation.json`。
- 初次脚本构造到工程保存会将坐标量化到五位小数，因此最初一次重开的 JSON 并非逐字相同；保留该记录。离线坐标比对采用 0.0001 BB 容差，最终保存文件的再次重开已达到完全一致。
- 发布前保留用户保存的 103-cube 工程与手绘贴图快照（`reference/user_saved_before_publish/`）；逐 UUID 核对几何和 UV 与快照一致，内嵌贴图与外部 PNG 按 RGBA 像素核对一致。用户调整涉及观察窗、门锁座、底部凹板、横梁与背板/安装条；这份保存版是本批最终依据。
- 根骨骼自身不放 cube，承担骨架父组职责；不是孤立无用骨骼。UV 共用为有意设计；警告保留在审计记录中。
- 未启动 Minecraft；未验证运行时渲染、GeckoLib 集成、性能、碰撞与开门行为。

这是本资产的首个模型版本；同目录原先只有原作参考资料，没有覆盖既有模型。2026-11-13 按用户明确授权，以独立发布 worktree 将本批归档到上游 `main` 的 `art/models/large_steel_cabinet_20261113/`，并在 [Discussion #39](https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39) 登记为大型钢柜候选 A。

用户另行指定本地归类到 `curated/large_steel_cabinet_20261113/`；这是本地风格参考分类，**不代表上游评选通过**。production 保留制作检查点。美术工程与导出未接入运行时，不包含代码改动。

## 可复现脚本

需要 Python 3 与 Pillow。从当前资产目录或仓库根均可使用相对路径调用，脚本按自身路径定位输入输出，不依赖作者私有绝对目录：

1. `reference/build_cabinet.py`：复现 AI 初稿的 102-cube 规格与像素贴图；属于制作过程记录，**不要用于覆盖当前人工修订交付**。最终用户几何和手绘贴图以主工程及 `reference/user_saved_before_publish/` 快照为准。
2. `reference/import_blockbench.py`：将历史初稿规格建立为可编辑工程；只替换活动的同名钢柜项目，其他活动项目会新建独立标签。
3. `reference/export_native.py`：调用原生 project / bedrock codec 保存工程和几何。
4. `reference/reopen_native.py`：原生重开已保存工程，建立独立标签供核查。
5. `reference/render_previews.py`：原生 1200×900 预览；正/背/侧正交，斜视透视。
6. `reference/validate_asset.py`：脱离 Blockbench 的独立文件校验。

上述 2–5 需要可交互的 Blockbench MCP 会话。`mcp_client.py` 默认连接 IPv6 loopback `http://[::1]:3000/bb-mcp`；端口或端点不同需调整。会话 JSON 是本地临时连接状态，缺失时自动初始化；无需携带旧会话交付。四视图由原生视口实际生成，不是另写渲染器模拟。
