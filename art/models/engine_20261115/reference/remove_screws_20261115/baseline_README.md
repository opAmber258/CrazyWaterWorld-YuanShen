# 引擎 Engine｜实体检修板与紧固件

**制作来源：人工 + AI（TakaraMiyuki + Codex / GPT-6）。** 人工完成当前模型微调、UV 重排和贴图手绘；本轮 AI 追加实体检修板、独立垫片与开槽螺钉，清除相应贴图框线和螺钉标记，刷新原生导出、预览与验证。

**保留输入的全部 180 个 cube、18 个原骨骼定义、原有 UV 与三条动画。** 当前共 428 cubes / 37 bones；继续使用人工设置的 256×256 逻辑 UV / 512×512 实际 PNG。

已在 Blockbench 5.2.2 原生渲染、重开及导入导出检查，未进入 Minecraft 验证。本轮为本地修订，未提交、推送或更新 Discussion。

## 本轮变化

- 顶盖两块、右端、两处斜面、推进罩外壳分段和侧面下部、底部维护盖，均有独立薄板体积。
- 新增 17 组检修盖，共 20 个板体 cube。电机轴承检修盖由四片围出 5.6 BB 方孔，避让原有旋转传动轴。背部原有两块实体检修门直接复用。
- 19 组盖板共 76 个独立紧固件；每个由一块垫片和两半螺钉头组成。螺钉头间有真实 0.12 BB 槽隙，垫片厚 0.12 BB，头部厚 0.28 BB。均可单独选中编辑。
- 斜面上的盖板和螺钉继承各自宿主的角度。新分组挂在原静态功能骨骼下，原 cube 不重挂父级。
- 对应的大检修板框线和大螺钉纹理清除，内部通风条、标识、用户既有手绘内容保留；小型口沿等非本轮实体化区域仍保留像素细节。

## 文件

| 文件 | 规格 |
| --- | --- |
| `engine.bbmodel` | 可编辑 Bedrock Entity，内嵌 PNG 与 3 条动画 |
| `engine.geo.json` | 原生 Bedrock 1.12.0，`geometry.engine` |
| `engine.animation.json` | 原生 Bedrock 1.8.0 动画 |
| `engine_tex.png` | 512×512 RGBA，与工程内嵌 PNG 字节一致 |
| `preview_front/back/side/oblique.png` | 1200×900 透明底，无 UI / 网格 |
| `preview_running.gif` | 整机组合，960×720，60 帧 / 2.4 秒循环 |
| `preview_rotor.gif` | 四叶转子，600×600，20 帧 / 0.8 秒循环 |
| `preview_pistons.gif` | 双活塞交替，600×600，30 帧 / 1.2 秒循环 |
| `validation.json` / `structure_audit.json` | 本轮验证与结构统计 |

`reference/physical_panels_20261115/preview_{right,top,bottom,detail}.png` 是额外检查图。所有预览帧均由 Blockbench 原生 WebGL 渲染；Pillow 仅用于精确纹素清理和 GIF 编码。

## 用户基线与保护

本轮冻结输入在 `reference/physical_panels_20261115/baseline_engine.bbmodel`、`baseline_engine_tex.png`，同时保留修订前的几何导出、动画和文档记录。模型 SHA-256 为 `438b36c931e0bbf4237721d97e7919af68530c7a31ab0581e4e172757a29dc9b`。

独立验证逐项对比原 180 个 cube 的完整数据、全部原骨骼定义、原 cube 父级和三条动画；新增内容只追加，不替换这些对象。仅纹理 source 更新，PNG 变化必须与紧固件清理清单逐像素一致。详细面与部件清单在该目录的 `physical_panel_manifest.json`。

新增板面 UV 有意复用对应宿主面的子区域，垫片和螺钉共用纯色材质小块，因此新增 UV 存在有意重叠；原有 1080 面 UV 不改变。保留用户高清贴图的 2 倍逻辑/实际关系。

更早的 `reference/texture_detail_pass_20261115/` 是上一轮纯贴图修订；`reference/user_baseline.bbmodel` 是更早输入，不作为当前验收基线。

## 尺寸与动画

- 当前原生边界约 X **-43.3…43.15**、Y **0…44**、Z **-17.55…18.6**。
- 宽×高×深约 **86.45×44×36.15 BB**，即 **5.403125×2.75×2.259375 格**。右端新增板体和螺钉增加约 0.95 BB 外伸。
- 正面 +Z，推进罩朝 -X，Y=0 贴地；人工版主体偏移保持。

| 动画 | 时长 | 内容 |
| --- | --- | --- |
| `animation.engine.rotor_spin` | 0.8 秒 | 四叶转子一圈 |
| `animation.engine.pistons` | 1.2 秒 | 双活塞总行程 3 BB、左右交替 |
| `animation.engine.running` | 2.4 秒 | 转子 3 圈、活塞 2 个完整周期 |

原动画轨道、关键帧、表达式和绑定完整保留。使用组合动画或分别使用前两条，避免重复叠加相同运动；游戏控制器由集成者处理。

## 来源与推定

标准引擎标识 `engine`。简中文本的 `entityname.engine=引擎` 与英文文本的 `entityname.engine=Engine` 分别查证。原作 XML 主图 `Engines.png`，裁切 `0,0,659,317`，`Scale=0.5`；来源摘要和原图裁切保留在 `reference/source_report.json`、`reference/original_engine_crop.png`。

原始比例假设为人物 120 px 对应 32 BB，`K=0.5×32/120`，不是官方米制尺寸。当前尺寸以用户手动微调后的工程为准，本轮没有重新缩放。内部推进罩、背部维护布局、双活塞和新增检修板属美术推定，不宣称原作存在对应的具体三维机构。

## 验证

- 原工程数据保护、内嵌/外置 PNG 一致、UV 范围、所有 cube 归属、实体螺钉头槽隙均已检查。
- 原生重开编译与交付几何/动画匹配，导入再导出也匹配。
- 2.4 秒组合动画以 25 Hz 抽查 60 帧，使用原生 mesh 的有向包围盒和分离轴测试；新增 248 个静态 cube 与检测到的原活动 cube 无相交。详见 `reference/physical_panels_20261115/motion_clearance.json`。这是离散帧检查，不是连续碰撞或游戏验证。
- 当前四视图、额外视图及三条 GIF 已按最终几何重新生成。

## 复现本轮

需要 Python / Pillow，以及已启用 MCP 的 Blockbench 5.2.2；本机 MCP 端口 3005。以下路径相对于资产目录：

```text
python reference/physical_panels_20261115/build_physical_panels.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/physical_panels_20261115/inspect_motion.js
python reference/bb_work.py eval reference/texture_detail_pass_20261115/export_only.js
python reference/render_previews.py
python reference/render_motion.py
python reference/physical_panels_20261115/render_extra_checks.py
python reference/physical_panels_20261115/render_closeup.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/native_roundtrip.js
python reference/physical_panels_20261115/validate_physical_panels.py
```

生成脚本从本轮冻结快照重建，会覆盖工程与 PNG；后续手改前应创建新检查点。旧 `detail_surfaces.py`、`validate_texture_detail.py`、`refine_engine.py`、`finalize_delivery.py` 等仅作历史溯源，不能用于生成或验收当前版。

## 边界

本轮仅本地美术修订，未改变上游、curated 或 Discussion。未验证 Minecraft 渲染、动画控制器、碰撞/放置、推进交互、暗环境和性能。无运行时代码变化，Gradle / L1 / GameTest 不适用于本次美术修订。
