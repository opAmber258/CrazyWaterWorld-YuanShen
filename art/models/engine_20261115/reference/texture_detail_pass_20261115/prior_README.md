# 引擎 Engine｜手调基线斜面与机械循环返修

**制作来源：人工 + AI（TakaraMiyuki + Codex / GPT-6）。** 人工负责本批输入工程的壳体加厚、前部组件位置等手动调整，以及四叶和双活塞交替方向选择；AI 在冻结的手调版上完成指定三处结构返修、增量像素贴图、骨骼与动画、原生预览和导出检查。

用户确认的方向：连续斜面取代右端阶梯；保留四叶并细化；双活塞一上一下交替运动。本批为独立的本地返修工程，等待视觉评审。

**已在 Blockbench 5.2.2 检查并完成导出，未进入 Minecraft 验证。**

## 交付

| 文件 | 内容 |
| --- | --- |
| `engine.bbmodel` | 可编辑 Bedrock Entity 工程，512² 内嵌贴图，3 条动画 |
| `engine.geo.json` | 原生 Bedrock 1.12.0 几何，`geometry.engine` |
| `engine.animation.json` | 原生 Bedrock 1.8.0 动画，循环参数和轨道可重新导入 |
| `engine_tex.png` | 512×512 RGBA；与内嵌 PNG 字节一致 |
| `preview_front/back/side/oblique.png` | 1200×900 透明底；正、背、侧为正交，斜视为透视 |
| `preview_running.gif` | 整体组合动作，960×720，2.4 秒 / 60 帧，循环 |
| `preview_rotor.gif` | 四叶转子近景，600×600，0.8 秒 / 20 帧，循环 |
| `preview_pistons.gif` | 交替活塞近景，600×600，1.2 秒 / 30 帧，循环 |
| `validation.json` / `structure_audit.json` | 实际执行的验证、警告、包围盒与运动范围记录 |
| `reference/` | 用户基线、来源、调色板、增量脚本、原生验证与渲染脚本 |

所有静态图、动图帧均来自 Blockbench 自身的场景和 WebGL 渲染器，通过 `Canvas.withoutGizmos` 排除辅助线；Pillow 仅负责将原生帧编码为 GIF。静态四视图展示中间位置的绑定姿态，动图展示运行循环。

## 本轮具体修改

1. **端部斜面**：去除两个右端阶梯块，改为一整段约 45° 的连续斜面；端盖高度配合接合。顶部前后两条既有倒角按人工加厚后的壳体重新拟合，连接顶盖与主壳，消除原倒角外挑。斜面端部与侧盖有 0.02 BB 层差，避免共面闪烁。
2. **四叶转子**：保留人工版的轴、开放推进罩和主体安装关系；重做轮毂、紧固件、叶根套、倾斜叶面与收窄叶尖。转子轴心为 `[-39.6,21,0]`，绕 X 轴旋转；静态叶形分骨骼，运动由统一 `rotor` 骨骼驱动。
3. **双活塞**：原两组简化接口改为缸体、安装座、导向柱、密封套、回路管、活动杆与顶头。缸体和导向柱固定，只有活动杆及顶头上下运动；最低位导向柱与顶头仍留 0.3 BB 间隙。

外壳总体比例、三联百叶、仪表、电控板、线缆、背部检修区及原有像素画法延续人工基线。

## 用户手改保护

- 输入是用户于 2026-11-15 保存的 `curated/engine_20261113/engine.bbmodel`，并非重新执行旧版生成器。
- 原件只读；原始完整副本为 `reference/user_baseline.bbmodel`，内嵌贴图另存为 `reference/user_baseline_tex.png`。
- 基线 SHA-256：`b3e4202bd8987c780bc77d7f043e88737109e8ef14ecc2e5a91ae8260cc36256`。
- 基线 143 cubes：**127 个 cube 的几何和各面 UV 完整保留**；3 个壳体 cube 只在倒角接合范围内修改；13 个旧阶梯 / 简化转子 / 接口 cube 在本轮指定范围内替换。新增 49 个 cube，最终 179 个。
- 原始所有不透明纹素逐像素保留。新纹理仅填入原 512² 图集的空白区；没有重排旧 UV、覆盖手绘像素或改变整体调色板。
- 逐项记录见 `reference/refinement_manifest.json` 与独立验证脚本。

## 动画用法

| 动画 | 时长 | 内容 |
| --- | --- | --- |
| `animation.engine.rotor_spin` | 0.8 s | 四叶转子匀速一整圈，450°/s，循环接缝速度一致 |
| `animation.engine.pistons` | 1.2 s | 双活塞总行程 3 BB，余弦往复，左右相差 180° |
| `animation.engine.running` | 2.4 s | 组合播放：转子 3 圈、活塞 2 次完整循环，首尾闭合 |

在 Blockbench 中进入动画模式，选择任意一条播放即可。组合动画用于整机展示；前两条方便分别查看和调整。运行时可选组合动画，或由集成者并行驱动前两条；**组合动画不应与前两条重复叠加播放**。

活塞 Y 位移为 `±1.5 × math.cos(query.anim_time × 300)`，单位 BB。最低位和最高位自然减速转向，首尾位置与速度连续。旋转采用明确的连续角度关键帧，避免每圈首尾减速或走最短角度导致反转。导出时 X 旋转为负值是 Bedrock 坐标转换，原生导入导出一致。

## 尺寸与来源

- 最终世界包围盒：X **-43.3…43.3**；Y **0…42**；Z **-17.55…18.6**。
- 宽 × 高 × 深：**86.6 × 42 × 36.15 BB**，约 **5.4125 × 2.625 × 2.259375 格**。
- 179 cubes / 18 bones / 1074 声明面；根骨骼用于统一父层级。
- 正面 +Z，推进罩朝 -X，Y=0 贴地，X 居中。正面顶头使 Z 中心偏至 0.525 BB，保留人工版的安装关系，没有为重新居中平移整机。
- 参考标准引擎 `engine`，原作 `engine.xml` 的 `Scale=0.5`，图集主裁切 `Engines.png: 0,0,659,317`。
- 中文来源：简中文本 `entityname.engine=引擎`；英文来源：英文文本 `entityname.engine=Engine`。来源文件摘要见 `reference/source_report.json`，原图裁切只作参考。
- 原始比例假设：人物 120 px 对应 32 BB，`K=0.5×32/120`，建议宽高约 87.87×42.27 BB；人工版已选用 86.6×42 BB，本轮沿用，主壳深度保留人工加厚后的 28.8 BB。最终深度含新活塞顶头。
- 推进罩内部三维结构、背部维护布局和新双活塞均为美术推定；动画是用户明确要求的视觉机械动作，不宣称原作已有该机构或当前工程已实现推进功能。

## 验证与保留警告

已执行：

- 最终工程重新打开，几何与动画分别重新编译，与交付文件一致。
- `.geo.json` 与 `.animation.json` 原生重新导入并导出，均一致。
- 独立核对 cube / bone 位置、旋转、层级、包围盒、全部 UV 和贴图，已处理 Bedrock X 镜像及上下表面的 UV 方向差异。
- 确认 127 个未涉及部件保留人工几何与各面 UV，原不透明纹素均未变。
- 原生姿态采样核对转速、活塞振幅、交替相位与循环端点；叶片最大径向半径约 12.912 BB，旋转所在罩段内切半径 14.95 BB，整圈保守径向余量约 **2.038 BB**，且叶片没有进入更窄的后段。
- 活塞在整个行程中有导向间隙，活动杆持续插入固定密封套；固定缸体不参与上下动画。
- 四张最终静态视图和三条最终动态预览已复查，静态整机图没有裁切。

**保留 1 条警告**：人工基线的深度缩放及窄边整纹素取整使实际像素密度存在差异。为保护手改，本轮没有全局重排 UV；不能宣称全模型密度完全一致。185 个不同 UV 岛之间无交叉重叠，同材质同尺寸部件的共享是有意设计。

本轮审计 **0 errors / 1 warning**。这是本地静态结构与动画资产验证，不是 Minecraft 或 GeckoLib 客户端兼容性验收。

## 复现

需要 Python / Pillow 和 Blockbench 5.2.2。辅助客户端使用当前本机既有 MCP 配置（localhost，端口 3005）；换机器可调整 `bb_mcp_client.py` 的默认端口，脚本本身按相对位置定位资产。

从本资产目录执行；从仓库根执行时，在脚本路径前加资产目录路径：

```text
python reference/refine_engine.py
python reference/bb_work.py load
python reference/bb_work.py export
python reference/render_previews.py
python reference/render_motion.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/native_roundtrip.js
python reference/bb_work.py eval reference/probe_animation.js
python reference/validate_refinement.py
python reference/finalize_delivery.py
```

生成器只读 `reference/user_baseline.bbmodel`；它会覆盖本批输出，未来有人手动编辑新工程时应先另存检查点。加载工具遇到同路径未保存编辑会停止。原生预览所需的 Blockbench 会话不能由纯离线 Python 替代。

## 交付边界

本次交付仅在本地 `art-vault/production/engine_20261115/`。没有修改 curated 原件或上游已发布候选，没有提交、推送或更新 Discussion；发布需另行指示。

未验证：Minecraft / GeckoLib 实际播放、动画控制器与启停绑定、碰撞和放置方向、暗环境、运行时性能、推力与交互逻辑。没有运行时代码修改，Gradle / L1 / GameTest 不适用于本次美术返修。
