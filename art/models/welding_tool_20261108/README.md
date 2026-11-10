# 潜渊症焊接工具 · Minecraft 物品模型交付

- 日期：2026-11-10（v4 统一像素密度重绘定稿）
- 目标：Minecraft Java Edition 1.20.1（Forge 47.x）
- 制作：Blockbench 5.2.2，Java Block/Item 格式
- 范围：仅美术资产。未修改 Mod 的 `src/main/resources`，未启动 Minecraft 客户端。

## 交付内容

| 文件 | 说明 |
| --- | --- |
| `welding_tool.bbmodel` | 可编辑 Blockbench 工程，内嵌 64×64 贴图，44 个立方体、5 个部件组（另有根组）、7 个 Display 槽位，无动画/骨骼 |
| `welding_tool_3d.png` | 64×64 统一密度像素图集（交付副本） |
| `assets/barotrauma/models/item/welding_tool_3d.json` | 干净的 1.20.1 物品模型，`parent` 为 `minecraft:item/handheld` |
| `assets/barotrauma/textures/item/welding_tool_3d.png` | 模型引用的运行时贴图 |
| `preview_hero_south.png`、`preview_north.png`、`preview_east.png`、`preview_top.png`、`preview_oblique.png` | 终版工程的南/北/东/顶/斜视预览 |
| `preview_display_gui.png`、`preview_display_thirdperson.png`、`preview_display_firstperson.png`、`preview_display_thirdperson_left.png`、`preview_display_firstperson_left.png`、`preview_display_ground.png`、`preview_display_fixed.png` | Display 槽位预览（含 Blockbench 中的参数面板）；左右手各一组 |
| `audit_bbmodel.json`、`audit_model.json` | 结构审计报告 |

## 设计与保留特征

保留并重排了潜渊症焊接工具的主要识别点：铜橙色机身、黑色后握把、浅色前握把、银色喷嘴与黄色警示环、镀铬弧形护手、侧面压力表、红色按钮、黄色警示条与标签板。细节按 Minecraft 像素语言重新组织，未照搬原画比例。

v2 按参考图重新调整了整体比例：

- 机身更纤细：主体截面约 2.4 × 3.4 单位（v1 约 2.8 × 4.7）。
- 后握把显著放大：从 y≈2.55 延伸到 y≈7.7，约占总高的 56%，并保留 22.5° 后倾。
- 铜橙机身缩短：止于前握把处（x≈8.95），前端改为裸露的钢制机匣。
- 喷口前补出一截细长枪管：依次为镀铬收口、黄色警示环、细长钢管与枪口环，延伸到 x≈17.98。

模型为静态资产，不含物品骨骼动画；Mod 现有的 `WeldingToolItem` 火花/烟雾粒子逻辑无需改动。

## 纹理规格（v4 重绘）

v4 重排 UV 并重绘整张贴图，几何不变，目标是全场统一的像素块尺寸。2026-11-10 定稿采用最终贴图，并把第三人称 Display 调整为实际握持姿态。

- 统一密度 64×64 @ 2 px/单位：1 个纹理像素对应 0.5 模型单位；每个面的像素尺寸 = round(世界尺寸 × 2)，最小 1 px。
- 同材质、同像素尺寸的面共用 UV 岛，不再按“正面/侧面区域”分配。共 77 个岛，其中 59 个被 2 个及以上面共用；岛间留 1 px 间隙，货架式排布，无重叠。
- 直面与斜面使用同一换算规则，像素块沿各自表面保持 0.5 单位，不会在喷嘴或护手处变密。
- 含薄轴（<0.5 单位）的面无法再降半个像素，按 1 px 最小值绘制；这类面共 126 个，集中在护手条、薄板与枪膛内壁，属于统一规格下的下限例外。
- 每个岛外扩 1 px 复制边缘像素，降低 mipmap 采样串色；外扩不写入 UV。
- 定稿贴图在生成图集上做了 10 个像素的手工修正（清理杂点、校正小指示色），修正已写入 `make_welding_texture.py` 的 `TOUCHUPS`；重新生成与交付 PNG 逐像素一致。

## 规格与成本

编辑工程：

- 44 个立方体，264 个面；无零厚度 plane，无负尺寸壳
- 5 个部件组：shell 6、mechanism 10、grips 9、nozzle 11、guard 8（另有根组）
- 2 个旋转立方体：`grip_rear`、`grip_rear_heel`，均为 Z 轴 -22.5°，原点 `(3.05, 7.7, 8.0)`
- 包围盒约 17.92 × 9.28 × 2.75（X × Y × Z，模型单位；x 0.06–17.98，y 2.52–11.80，z 6.75–9.50）
- 单张 64×64 贴图（4096 像素，全不透明，单帧，60 种颜色），单一材质；77 个 UV 岛、894 个有效像素，覆盖率 21.8%
- 无动画、无骨骼、无独立挂点

导出 Java 模型：

- 44 个 elements、264 个声明面、2 个旋转 element，UV 换算到 0–16（实际范围 0.25–15.75）
- 贴图引用：`barotrauma:item/welding_tool_3d`；`particle` 使用同一贴图
- 纹理成本：64×64、4096 像素、0 个透明像素、0 个半透明像素、1 帧
- 像素密度：138 个双轴 ≥0.5 单位的面严格按 2 px/单位分配（整数取整后 x 中位数 1.94、y 中位数 1.92 px/单位，范围 1.43–2.5；89.9% 落在 1.5–2.5）；126 个含 <0.5 单位薄轴的面使用 1 px 下限，最极端为 0.08 单位枪膛壁（等效 12.5 px/单位）
- 编辑器/导出/游戏端成本分开评估；未进行游戏内渲染和 FPS 测量

## Display 参数

| 槽位 | rotation | translation | scale |
| --- | --- | --- | --- |
| thirdperson_righthand | `[0, 90, 40]` | `[0, 5, 0.25]` | `[0.85, 0.85, 0.85]` |
| thirdperson_lefthand | `[0, -90, -40]` | `[0, 5, 0.25]` | `[0.85, 0.85, 0.85]` |
| firstperson_righthand | `[5, 95, 0]` | `[0.84, 2.99, -0.89]` | `[0.68, 0.68, 0.68]` |
| firstperson_lefthand | `[5, -95, 0]` | `[0.84, 2.99, -0.89]` | `[0.68, 0.68, 0.68]` |
| gui | `[15, -30, 0]` | `[0, 0, 0]` | `[0.8, 0.8, 0.8]` |
| ground | `[0, 0, 0]` | `[0, 2, 0]` | `[0.5, 0.5, 0.5]` |
| fixed | `[0, -180, 0]` | `[0, 0, 0]` | `[1, 1, 1]` |

v3 修正了四个手持槽位的朝向。原数值沿用原版 `item/handheld`（如第三人称 `[0,-90,55]`），那是针对 +Z 朝向、贴图面向的平面物品写的；本模型枪管沿 +X、顶面朝 +Y，直接套用会让枪口朝后、机腹朝天，表现为“拿反”。新数值按握把锚点重新推导：

- 第三人称：握把落在拳心（与同场景原版剑的握点对齐），枪管朝前；游戏内手臂自然下垂时模型随臂前倾 22.5°，即枪口向前下方、枪顶朝上、侧面朝外，左右手镜像对称。
- 第一人称：握把落在原版剑的握点，枪口朝前并上抬 5°、内收 5°，与手臂前伸姿态一致；左右手同理。
- 左手槽位按 Minecraft `ItemTransform.apply` 的左手臂规则取镜像值（Y/Z 旋转与 X 平移取反），实际游戏中的有效姿态与右手一致。
- 推导以 Blockbench 预览中的原版剑握持姿态为参照；预览复刻了游戏的手臂坐标系，但未做游戏内实机截图。
- `fixed` 的 180° 与原版 `item/generated` 的继承值一致，保证 Blockbench 的 Fixed 预览与游戏一致；`head` 未覆盖，沿用父模型。
- `.bbmodel` 工程本身未填写 Parent Model；导出的 Java JSON 已显式加入 `minecraft:item/handheld`。若从 Blockbench 重新导出，请把 Parent Model 设为 `minecraft:item/handheld`，或保留本交付 JSON 的 `parent` 字段。

2026-11-10 定稿把第三人称两个槽位的 Z 旋转从 ±90° 收到 ±40°，平移从 `[0, 6.26, 5.75]` 收到 `[0, 5, 0.25]`：Blockbench 预览中握把落入拳心，枪身随手臂前倾，枪口朝前下方。第一人称、GUI、地面、固定槽位不变。

## 验证结果

- v4 `.bbmodel` 在隔离 Blockbench 实例中重新载入并渲染：44 个立方体、6 个组（含根组）、内嵌 64×64 贴图、7 个 Display 槽位均可读取。
- v4 与 v3 对比：44 个立方体的 `from`/`to`、旋转角度/轴/原点逐项一致；导出 JSON 的第三人称两个槽位按 2026-11-10 定稿调整，其余 5 个 Display 槽位与 v3 完全一致。工程内 `fixed` 写作 `[0,-180,0]`。
- 定稿工程与原 v4 交付逐立方体核对：几何与逐面 UV 完全一致，差异仅为第三人称两个 Display 槽位和贴图字节（10 个像素修正）。
- 工程与 Java 导出逐立方体核对：`from`/`to`、旋转与逐面 UV 一致，0 处不匹配；264 面全部满足 `像素 = max(1, round(尺寸 × 2))`。
- 图集布局核对：77 个岛均在 0–64 内、相互无重叠；894 个有效像素，覆盖率 21.8%；59 个岛被多个面共用。
- `.bbmodel` 内嵌贴图与 `welding_tool_3d.png` 为同一份 PNG 字节（base64 直接内嵌，无重编码），2586 字节完全一致。
- 定稿已在隔离 Blockbench 重新截取 5 个本体视图和全部 7 个 Display 槽位；第三人称左右手按新数值复核，握把落在拳心、枪口朝前下方；第一人称、GUI、地面、固定槽位沿用 v3 的朝向结论。
- 当前 sha1：`welding_tool.bbmodel` `4941a67141c9995ef3a05d0d287bd4703ae05c79`，`welding_tool_3d.png` `ca94461dea9d6ea1f38886770c0be4e0935df981`，`welding_tool_3d.json` `acee0251ec3940a84daa965c958dcdf6fe3217c3`。
- `audit_model.py`（`java-legacy`）：
  - `.bbmodel`：结构错误 0，依赖完整，无警告。
  - Java 模型：结构错误 0；警告 `parent_unresolved`，仅因审计时资源根目录没有原版 `minecraft:item/handheld`，游戏内由原版资源提供。
- 未启动 Minecraft 客户端，因此没有游戏内实机渲染、第一/第三人称实测或 FPS 数据；Blockbench 预览不能替代实机确认。
- 未做隐藏面剔除：44 个立方体均保留完整六面，共 264 面。这是保守交付，便于后续编辑；面数与材质批次已在上面单列，未用删面或压缩声明替代性能证据。

审计脚本的通用限制同样适用：不做完整 schema 校验、不证明任意遮挡关系、不展开自定义动画、不校验 display 槽位的最终实机效果。

## 接入说明（由你决定是否接入）

本次只交付美术资产，以下现有文件保持原样，未被修改：

- `src/main/resources/assets/barotrauma/models/item/welding_tool.json`
- `src/main/resources/assets/barotrauma/textures/item/welding_tool.png`

接入时可将 `assets/barotrauma/...` 下的模型与贴图复制进 Mod 资源，并把物品模型指向 `barotrauma:item/welding_tool_3d`。若希望直接替换旧模型，可在备份原文件后将 `welding_tool_3d.json` 改名为 `welding_tool.json`，并同步贴图文件名；两种方式都不需要改动物品注册代码。保留 `parent: minecraft:item/handheld` 即可沿用原版手持姿态。

模型是静态的，现有火花/烟雾粒子继续由 Mod 逻辑生成，不需要为模型添加动画。

## 复现路径

生成与导出脚本保留在 `art/models/.work/welding_tool/build/`：

- `build_welding_tool.py`：几何与部件组。
- `uniform_atlas.py`：统一密度 UV 布局（`DENSITY=2.0`、77 个岛、货架式排布）。
- `make_welding_texture.py`：按布局逐面重绘图集并应用 `TOUCHUPS` 像素修正，输出 `welding_tool_atlas.png` 与 8× 预览（与交付 PNG 逐像素一致，仅编码字节不同）。
- `make_bbmodel.py`、`export_java.py`：生成工程与 Java 模型；`export_java.py` 在存在 `candidate/welding_tool_3d.png` 时优先用它作为运行时与交付贴图；`load_bbmodel.js`、`capture_view.js` 及 `cdp_*.js` 为隔离 Blockbench 的载入与截图辅助脚本。

这些文件不属于运行时资源。v3 交付完整留档在 `art/models/.work/welding_tool/baseline_v3_delivery/`，v4 初始交付与最终贴图留档在 `art/models/.work/welding_tool/baseline_v4_delivery_20261110/`；交付的 Java 模型已去除工程私有字段，并完成上述一致性核对。
