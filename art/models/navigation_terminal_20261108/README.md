# Navigation Terminal｜潜渊症导航终端

以《Barotrauma》原作 `navterminal` 为参考制作的 Minecraft 静态美术模型。保留低饱和灰绿旧金属外壳（主色接近 `#5F6058`）、深色功能部件、主声呐屏、右侧辅助显示与开关区、双仪表、宽键盘、侧控制台、线缆和安装支架；暖色仅用于少量仪表点缀，背面与厚度采用克制补全。

> **2026-11-08 尺寸变更（终态：三格宽）**：整体等比放大至三格宽（X = 48.0 BB，相对原始版 ×2.102863），X 居中于 -24..24，Z 居中于 ±8.096，Y 贴地 0 → 38.001；几何、旋转轴心与可见框同步缩放，UV、128×128 贴图与全部细节未改动。期间检测到 bbmodel 曾被外部在 Z 向移动 -2.0（疑为 Blockbench 内查看时移动），已随本次「居中」要求修正回 Z=0，与 geo.json 完全对齐。存档链：原始 1.427 格宽 → `../_archive/navigation_terminal_x1_pre_scale_20261108/`；两格宽 → `../_archive/navigation_terminal_x2_pre_scale_20261108/`。下方「原作依据与尺寸换算」为原始版查证记录。`preview_*.png` 已于 2026-11-09 按三格宽几何重制（Z-buffer 软件渲染，风格对齐原 Blockbench 出图：透明底、正面平光、侧面偏暗、fov 38° 斜视）；渲染脚本见 `../.work/render_nav_previews.py`。

## 交付文件

- `navigation_terminal.bbmodel`：可编辑 Blockbench 主工程。
- `navigation_terminal.geo.json`：Bedrock Entity 几何导出。
- `navigation_terminal_tex.png`：128×128 RGBA 像素贴图。
- `preview_front.png` / `preview_side.png` / `preview_oblique.png` / `preview_back.png`：无网格四方向检查图，1200×900。
- `reference/`：原作裁切、放大参考、贴图绘制脚本与调色板。
- `validation.json`：结构化验证记录。
- `structure_audit.json`：通用结构审计原始报告；警告解释见下文。

## 原作依据与尺寸换算

查证来源：

- XML：`F:/SteamLibrary/steamapps/common/Barotrauma/Content/Items/Command/command.xml`
- 物品标识：`navterminal`
- 原作图集：`CommandDevices.png`
- `sourcerect = 0,0,384,304`
- `scale = 0.5`

采用工作流文档的人物视觉高度换算，不将其表述为原作直接提供的米制尺寸：

```text
K = 0.5 × 28.8 / 242.25 = 0.0594427 BB/px
宽 = 384 × K = 22.826 BB = 1.427 格
高 = 304 × K = 18.071 BB = 1.129 格
```

最终包围盒：X `-11.413…11.413`，Y `0…18.071`，Z `-3.85…3.85` BB。正面为 +Z，最低点 Y=0，X/Z 居中。

**美术推定：**7.7 BB（0.48125 格，约 0.481 格）深度、侧面层次、背部盖板、遮挡区域及支架/线缆的背面连接。原作没有提供这些方向的完整依据，未新增功能性装置。

## 模型结构

- 格式：Bedrock Entity，`format_version 1.12.0`
- 标识：`geometry.navigation_terminal`
- 40 个轴对齐 cubes，240 个声明面
- 8 个骨骼组：根组 `navigation_terminal`，以及 `housing`、`main_display`、`auxiliary_panel`、`keyboard`、`side_controls`、`cable_harness`、`mounting_brackets`
- 逐面 UV，128×128 贴图
- 无旋转、动画、粒子或发光设置

## 本次修订

- 前部操控台与键盘已沿 +Z 方向加长，宽度保持不变；整体沿 Z 轴居中。
- 背部采用双大型检修盖板、右侧窄通风格、紧固件/锁扣与下部走线槽，保持低复杂度方块结构。
- 本轮色彩修订将外壳统一为低饱和灰绿旧金属，主色接近 `#5F6058`；保留深色屏幕、线缆、侧控制台与接缝，暖色仅用于少量仪表点缀。
- 收敛高对比噪点与过强磨损，维持克制、清晰的像素细节；没有恢复早期粗糙噪点。
- **本轮仅改变贴图，未改动几何、UV、操控台长度或背部结构。**

## 已完成检查

- 在可见 Blockbench 中检查正面、侧面、斜前方与背面；未见明显漏面、异常遮挡或接地问题。
- 导出文件确认只有 `geometry.navigation_terminal`，无 `geometry.geometry.*` 误命名。
- 导出几何为 40 cubes / 8 bones；240 个逐面 UV 矩形均在 0–128 范围内。
- `.bbmodel` 与 `.geo.json` 已保存，贴图文件存在且为 128×128。
- 通用结构审计报告 `structural_errors_absent = true`、`dependencies_complete = true`，无 errors。

### 审计警告说明

通用审计读取到 `.bbmodel` 贴图记录中的 `uv_width/uv_height = 16`，因此保留 240 条 `uv_outside_texture` 警告。实际工程分辨率与 Bedrock 导出描述均为 128×128；导出后的全部逐面 UV 已另行按 128×128 逐项检查。原始警告未删除，不能将该通用报告描述为“零警告”。

## 验证边界

**已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。** 未修改游戏代码、注册、碰撞、交互或渲染；未验证 GeckoLib 游戏内显示、资源包接入、性能或碰撞行为。
