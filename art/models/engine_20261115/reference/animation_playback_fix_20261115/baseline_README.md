# 引擎 Engine｜保留检修板，移除新增螺钉

**制作来源：人工 + AI（TakaraMiyuki + Codex / GPT-6）。** 人工完成主体模型、UV 重排与贴图微调；AI 完成此前表面细化和实体检修板。本轮按用户要求，删除此前 AI 新增的全部 76 组螺钉及垫片，共 228 个 cube。

检修板保留；保留下来的 200 个 cube、UV、贴图与三条动画逐项保持原样。删除两个仅用于背板紧固件的空分组，当前为 **200 cubes / 35 bones**。

## 文件与规格

- `engine.bbmodel`：可编辑 Bedrock Entity，内嵌 PNG 和三条动画。
- `engine.geo.json`：原生 Bedrock 1.12.0，`geometry.engine`。
- `engine.animation.json`：原生 Bedrock 1.8.0。
- `engine_tex.png`：512×512 RGBA；逻辑 UV 256×256，用户设置的 2 倍关系保留。
- `preview_front/back/side/oblique.png`：1200×900，无 UI / 网格，前三者为正交，斜视为透视。
- `preview_running/rotor/pistons.gif`：按最终工程更新的原生动画预览。
- `validation.json` 和 `structure_audit.json`：当前删除修订的检查记录。

四视图及三条动图均由 Blockbench 自身 WebGL 渲染；Pillow 只负责 GIF 编码。额外的右侧、顶视、底视和近景已更新至 `reference/physical_panels_20261115/preview_{right,top,bottom,detail}.png`。

## 本轮保留与删除

新增实体检修板仍为 17 组、20 个 cube；电机背板的四片结构保留传动轴孔。原有背部检修门保留。此前新增的垫片及两半开槽螺钉头全部删除，未重新绘制贴图螺钉。

冻结输入与删除 UUID 清单位于 `reference/remove_screws_20261115/`。独立检查确保当前元素数组等于该快照剔除指定 228 个 UUID 后的结果；其余工程字段保持一致，只有相应 outliner 子项与两个空分组删除。历史生成脚本仍保留溯源，不应用来覆盖当前文件。

## 尺寸与动画

- 边界约 X **-43.3…42.735**、Y **0…44**、Z **-17.55…18.6**。
- 宽×高×深约 **86.035×44×36.15 BB**，即 **5.3771875×2.75×2.259375 格**。
- 正面 +Z，推进罩朝 -X，Y=0 贴地。
- `animation.engine.rotor_spin`：0.8 秒一圈。
- `animation.engine.pistons`：1.2 秒，双活塞交替，总行程 3 BB。
- `animation.engine.running`：2.4 秒，转子三圈及活塞两个周期。

三条动画的数据均未修改。使用组合动画或分别使用前两条，避免叠加相同轨道。游戏控制器由集成者处理。

## 来源与推定

标准引擎标识 `engine`。简中文本的 `entityname.engine=引擎` 与英文文本的 `entityname.engine=Engine` 分别查证。原作 XML 主图 `Engines.png`，裁切 `0,0,659,317`，`Scale=0.5`；来源摘要和原图裁切保留在 `reference/source_report.json`、`reference/original_engine_crop.png`。

原始比例假设为人物 120 px 对应 32 BB，`K=0.5×32/120`，不是官方米制尺寸。当前尺寸以用户手动微调后的工程为准，本轮没有重新缩放。内部推进罩、背部维护布局、双活塞和新增检修板属美术推定，不宣称原作存在对应的具体三维机构。

## 验证与复查

工程在 Blockbench 5.2.2 重开后，与原生几何、动画导出匹配；重新导入导出也匹配。对保留几何、UV、纹理、动画做了冻结快照的精确对比。

```text
python reference/bb_work.py load
python reference/bb_work.py eval reference/texture_detail_pass_20261115/export_only.js
python reference/render_previews.py
python reference/render_motion.py
python reference/physical_panels_20261115/render_extra_checks.py
python reference/physical_panels_20261115/render_closeup.py
python reference/bb_work.py load
python reference/bb_work.py eval reference/native_roundtrip.js
python reference/remove_screws_20261115/validate_removal.py
```

以上从当前工程刷新导出并复查；需要 Python / Pillow 与已启用 MCP 的 Blockbench，本机端口 3005。旧 `build_physical_panels.py` 会重新生成螺钉，不是当前版本入口；其它旧生成及验收脚本仅作历史溯源。

已在 Blockbench 检查并完成导出，未进入 Minecraft 验证。本轮仅本地美术修订，未提交、推送或更新 Discussion。未验证游戏渲染、控制器、碰撞、交互及性能。
