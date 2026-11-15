# 解构仪 Deconstructor · 双轴工作动画

制作来源：人工 + AI。用户完成最终外形与材质调整，Codex（GPT-6）用 Blockbench 5.2.2 制作双刀轴工作循环。本工程以 2026-11-15 11:26 保存的手调工程为基线。170 个方块、10 个分组、轴心、UV 和内嵌材质已与基线逐项核对。

![刀轴工作循环](preview_work.gif)

## 使用

打开 [可编辑工程](deconstructor.bbmodel)，切到 Blockbench 的动画页，选择 `animation.deconstructor.work` 并播放。游戏侧可使用 [Bedrock 动画](deconstructor.animation.json)、[几何](deconstructor.geo.json) 和 [贴图](deconstructor_tex.png)。工作状态由游戏侧驱动，此次验证在编辑器内完成。

每根刀轴每秒一圈，1 秒回到起始姿态。正面朝 +Z，左刀轴顺时针、右刀轴逆时针。两组刀齿在中央向下卷入，每根轴的三片错齿刀盘随同一根骨骼转动。五个线性关键帧分别位于 0、0.25、0.5、0.75、1 秒，左轴绕 Z 从 0° 到 −360°，右轴从 0° 到 +360°。

## 来源与尺寸

原作中、英文名分别见简中和英文游戏文本的 `entityname.deconstructor`。游戏文件 `Content/Items/Fabricators/fabricators.xml` 的六片装饰刀盘分成转速符号相反的两组。可对照 [原作外壳](reference/game_housing.png) 和 [单片刀盘](reference/game_cutter.png)。

每格 16 个建模单位。最终手调工程在静止姿态实测宽 2.000 格、高 2.519 格、深 1.613 格。工程以 256 × 256 的逻辑 UV 映射 512 × 512 像素材质。本目录的外部 PNG 取自最终工程内嵌的图像。四张 1200 × 900 静态预览和 50 帧、1 秒的实速 GIF 均来自本轮工程的 Blockbench 视口。

## 检查结果

已重开工程，复核几何与动画的原生导出，并采样 49 个原生姿态，确认方向、角速度及其余八组的静止姿态。循环的起止画面逐像素相同。已检查正面和斜上方慢放，未进入 Minecraft 测试。

对旋转中的两个刀组每隔 1° 进行一次定向包围盒检测。两组刀盘的旋转外缘之间约有 1.05 个模型单位的间隙。部分角度刀组和同侧料斗侧板的几何有最多约 0.71 个单位的重叠，齿尖还会略微进入底板（约 0.25）和侧唇（约 0.09）。轴端与挡板有相交。上述数值为几何检测结果，详见 [验证记录](validation.json)。

## 本轮范围与上版差异

本轮**只新增动画**：以 2026-11-15 11:26 保存的无动画手调工程为基线，添加 `animation.deconstructor.work`，并重出四向预览与循环 GIF。几何、骨骼层级、UV 与贴图相对基线零改动，`validation.json` 的 `all_source_cube_group_outliner_uv_values_identical` 与 `identical_embedded_texture` 两项即为该结论的核对记录。基线工程本身相对更早的解构仪候选做了外形与材质手调，不属于本轮改动。

`fabricators.xml` 的图集坐标与 `scale` 未在本轮重新取模，几何沿用基线；README 与验证记录不声称逐像素还原原作图集。

仅美术资产：未修改 `src/main/resources`，未注册方块，未启动 Minecraft 客户端；动画的工作状态需由游戏侧驱动，本轮验证在 Blockbench 编辑器内完成。

## 复现

[旋转轨道脚本](reference/rotation.js) 使用 Blockbench 动画 API 在无动画的手调工程副本上添加工作循环。原生 Bedrock 动画导出包含两个同名骨骼的旋转轨道。[预览脚本](reference/render_previews.py) 可连接打开此动画工程的独立 Blockbench 本机调试端口，导出原生视图并构建循环 GIF。[验证脚本](reference/validate.py) 使用同一端口及最终手调工程路径，复核运动、源工程保真和几何间隙。脚本使用 Pillow、NumPy 和 websocket-client。

无动画的手调基线已随交付入库为 [`reference/manual_baseline_20261115.bbmodel`](reference/manual_baseline_20261115.bbmodel)（sha256 `f58c7ab5…`，与 `validation.json` 的 `source_sha256` 一致），因此 `--source` 指向仓库内路径即可复现。

```text
python reference/render_previews.py --port <端口>
python reference/validate.py --port <端口> --source reference/manual_baseline_20261115.bbmodel
```

两个脚本都要求 Blockbench 以 `--remote-debugging-port=<端口>` 启动，且已在该实例中打开本目录的 `deconstructor.bbmodel`（预览脚本会校验当前工程名与动画名，不符即中止）。这一步需要交互式 Blockbench 会话，无法在纯命令行下自动完成。

