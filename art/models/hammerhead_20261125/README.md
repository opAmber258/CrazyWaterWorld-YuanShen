# 锤头煞 Hammerhead · 外扩锤面

**制作来源：人工 + AI。** 以用户微调过的双巡航工程为底稿，Codex 使用 Forge3D 和 Blockbench 修整铜色锤面。

锤面外沿由颊部向上下、左右和斜向逐渐展开，短斜面接到宽而浅的撞击面。取沿头部轴线的两个横截面，宽度从 44.7 增至 51.4，高度从 31.0 增至 35.8 Blockbench 单位。八个方向的半径均增加，最小增幅 15.0%。撞击面中央深度从 76.10 变为 76.29，都在前鼓版之前的锤面位置附近。

## 原作查证

| 项 | 内容 |
| --- | --- |
| 本地化条目 | `character.hammerhead` —— 简中「锤头煞」、英文 `Hammerhead`（游戏安装目录 `Content/Texts/SimplifiedChinese/` 与 `Content/Texts/English/` 同标识条目，记录见 reference 检索包 `manifest.json`） |
| 骨骼定义 | `Content/Characters/Hammerhead/Ragdolls/HammerheadDefaultRagdoll.xml` |
| 贴图 | `Content/Characters/Hammerhead/Hammerhead.png` |
| 缩放 | `limbscale` / `jointscale` / `texturescale` 均为 1 |
| 头部图块 | 侧视 `Head`，`sourcerect [803,1,221,236]`，`alpha_bounds [0,34,206,197]`；参考图块靠近端面处的外沿走势 |

完整坐标、锚点与图块清单见本地检索包 `reference_materials/hammerhead_materials_20261120/manifest.json`（取证用，不入库）；原作命名与图集定位见[精修版](../hammerhead_refinement_20261124/README.md)。

## 本轮范围

整模 149 个体块、19 根骨骼，256×256 RGBA 内嵌贴图，Bedrock Entity 几何 identifier 为 `geometry.hammerhead`。按 16 个 Blockbench 单位为一格，整模约 **6.31 × 5.99 × 17.19 格**（宽 × 高 × 长）。

改动只在锤面外沿：30 个体块重绘或新加、12 个端面体块替换（清单见 `validation.json`），贴图改动 5236 像素且不影响其余面；躯干、两对足、鳍与尾部沿用底稿。动画保留两条循环：`animation.hammerhead.swim_cruise`（3.0 s，18 骨骼）与 `animation.hammerhead.swim_fast`（1.5 s，18 骨骼），导出后与原工程逐帧矩阵差为 0。

## 验证

Blockbench 5.2.2 重开工程、回导几何并复核两段巡航共 182 个采样姿势；重出预览与工程渲染逐像素一致（侧视 37 像素差异，单通道最大 13）。`structure_audit.json` 为 0 error / 0 warning。Blender 文件为带同一贴图的静态检查几何。Minecraft 与 GeckoLib 实机尚未测试。

## 预览

[前后对比](preview_comparison.png) · [整模正视](preview_front.png) · [整模斜视](preview_oblique.png) · [锤面正视](preview_head_front.png) · [锤面俯视](preview_head_top.png) · [锤面侧视](preview_head_profile.png) · [锤面下视](preview_head_below.png) · [锤面转向](preview_head_turn.png) · [慢速巡航](preview_swim_cruise_head.webp) · [高速巡航](preview_swim_fast_head.webp) · [验证记录](validation.json)

## 复现

需要 Python、Pillow、NumPy、websocket-client，以及一份独立的 Blockbench 5.2.2 调试会话（`--remote-debugging-port`）。从本目录运行，脚本按参数接收工程路径，本目录 `.bbmodel` 即成品，可直接作为输入：

```powershell
python reference/render_blockbench.py hammerhead.bbmodel --port 9430 --output scratch --mesh-snapshot --check-reimport --views front back side oblique head_top head_profile head_front head_turn head_turn_right head_below
python reference/verify_flare.py hammerhead.bbmodel scratch
python reference/check_motion.py hammerhead.bbmodel scratch --port 9430
```

`reference/assemble.py` 与 `reference/parts/head/patch.py` 记录本轮如何从上一版本地底稿生成成品；底稿未随包，对成品重跑会重复施加局部补丁，仅供追溯。`reference/compare_head.py` 生成前后对比图，默认字体取本机 `C:/Windows/Fonts/msyh.ttc`，可用 `--font` 指向其他字体。

预览与两条巡航的 WebP 由上述 Blockbench 调试会话经 `reference/render_blockbench.py` 和 `reference/check_motion.py` 导出：没有可连接的 Blockbench 5.2.2 调试会话时，仓库内无法重现这批图。
