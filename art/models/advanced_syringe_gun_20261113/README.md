# 高级注射器枪 AdvancedSyringeGun

制作来源：人工 + AI（TakaraMiyuki + GPT-6）。人工提供原作侧视参考并保存机匣高度微调；AI（Codex + Blockbench 5.2.2）完成初始几何、像素贴图、UV、脚本、预览和验证，发布前保留人工保存版本并重出几何与六向预览。

日期：2026-11-13。工具：本机 Blockbench 5.2.2 原生工程 / Bedrock codec / Preview、Python、Pillow。独立静态美术候选，未接入游戏。

![斜视预览](preview_oblique.png)

## 文件

- `advanced_syringe_gun.bbmodel`：可编辑 Bedrock Entity 工程，内嵌贴图，不依赖外部绝对路径。
- `advanced_syringe_gun.geo.json`：原生导出；`format_version=1.12.0`；`geometry.advanced_syringe_gun`。
- `advanced_syringe_gun_tex.png`：一张 64×64 RGBA 单帧像素图集。
- `preview_front/back/side/oblique.png`：1200×900 规范预览；另附顶视、背面斜视。
- `validation.json`、`structure_audit.json`、`audit_cost.json`：交付、结构及成本记录。
- `reference/`：用户参考、本机原作裁切、色板、构建与原生预览脚本、重开证据、保守清理试验。

## 原作查证

名称分别读取本机游戏的两份文本，不从英文直译：

| 内容 | 文件 / 条目 |
| --- | --- |
| 简中 | `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml`，`entityname.advancedsyringegun` = 高级注射器枪 |
| 英文 | `Content/Texts/English/EnglishVanilla.xml`，`entityname.advancedsyringegun` = Advanced Syringe Gun |
| 实体 | `Content/Items/Jobgear/Medic/medic_talent_items.xml`，`identifier=advancedsyringegun`，`variantof=syringegun` |
| 世界精灵 | `Content/Items/JobGear/TalentGear.png`，`sourcerect=354,429,145,78` |
| 缩放 | 继承 `Content/Items/Tools/tools.xml` 中基础 `syringegun` 的 `scale=0.5` |
| 高级版特点 | 实体包含 `StatusHUD`；本机 `ItemContainer capacity=2 maxstacksize=8` |

补充阅读：[官方 Wiki · Advanced Syringe Gun](https://barotraumagame.com/wiki/Advanced_Syringe_Gun)。网页容量摘要与本机实体 XML 有差异，本候选以本机文件核实特征，不把网页摘要当作游戏逻辑实现。精确读取值保存在 `reference/source_evidence.json`。

形状参考为用户图片和本机精灵裁切；未将原作贴图直接用于模型。项目风格仅参考 `curated/syringe_gun_20261113` 和 `curated/welding_tool_20261108`：实际读取其工程、PNG 尺寸和 UV，查看斜视预览，学习低体块机械结构、灰绿漆面 / 金属 / 橡胶分层与约 2–3 px/模型单位的像素语言。没有复制它们的几何或图集，没有使用 deprecated。

## 造型与比例

这是从零新建的高级版，保留长方形琥珀药仓、青绿外围框架、扫描器与双浅色旋钮、后部管线、镂空枪托、倾斜握柄、开放扳机区、弯折拨杆、竖向前握把和短枪口。枪口有真实方形开孔；前后两侧均具完整厚度。背面、管线截面与装配连接属于原创三维推断，不代表官方三维设计。

药仓的 3×4 可见分格是视觉节奏与原作色块的低多边形转译，**不代表原作插槽或实际弹药数量**。琥珀材质以不透明硬边像素表达，未添加透明玻璃层、描边壳、发光 shader 或动画。扫描器的青绿色显示条仅为静态纹理。

原作精灵 145×78，继承 `scale=0.5` 后为约 72.5×39 游戏像素；此值没有官方米制换算。作者主动选择约一米长的双手医疗工具比例，将原创草图统一乘 0.8；16 模型单位按 1 格理解。计入旋转体块后的完整包围盒：

- 编辑坐标最小点：`[-7.826978, -0.418088, -1.288]`。
- 最大点：`[8.56, 7.387173, 1.288]`。
- 长 × 高 × 厚：`16.386978 × 7.805261 × 2.576` 模型单位。
- 约 `1.024186 × 0.487829 × 0.161` 格；若一格约一米，长度约 1.02 米。这是美术尺寸选择，不是原作现实尺寸。

编辑工程枪口朝 +X，Y 向上，Z 为厚度；Bedrock 原生 codec 镜像 X 后导出，枪口朝 −X。集成时须使用 codec 导出的枢轴和旋转，不能将编辑坐标直接逐数填入 geo。

## 材质和成本

最终 **69 cubes / 8 bones / 414 个声明面**，一张 64×64 图集，单材质、单帧，无动画副本。组名按 receiver、scanner、cassette_frame、amber_magazine、stock、grip_trigger、rear_hose、front_assembly 分工。

预算在批量生成前设为最多 90 cubes、10 bones、540 faces、单图集最多 128×128；最终图集仅需 64×64。大结构使用连续 cuboid，斜线使用旋转长条，管线只用五段。刻线、握柄纹、旋钮标记和药仓反光使用贴图，没有逐像素挤出或扫描线堆叠。

基础 UV 密度约 2 px/草图单位，整体缩放后约 2.5 px/最终模型单位；极小旋钮、扫描条和药仓反光至少分配 2 px 以保留标记。相同材料、尺寸与图案的面复用 UV，四边扩展 1 px。没有柔边渐变或随机噪点；未使用的透明图集区域 alpha 和 RGB 均为零，实际面采样区域全部不透明。基础 RGBA 字节估算为 16 KiB，不是显存或 FPS 测量。

## 验证

- 最终工程在 Blockbench 原生保存后关闭本任务页签、重新打开：元素、分组和内嵌 PNG 完全一致；纹理错误为 0。
- 重开后原生 `Codecs.bedrock.compile()` 文本经 JSON 解析，与交付 geo 严格相等；证据在 `reference/native_reopen_validation.json`。
- 本任务新建页签，已有注射器枪工程的 UUID 和保存状态保持不变。
- 正尺寸、无重复 cuboid、无空骨骼、UV 范围及不透明采样、内外 PNG 一致、2 的幂尺寸、旋转包围盒与原生 Three.js 包围盒一致：通过 `reference/validate_asset.py`。
- 通用工程审计无错误 / 警告；其 generic profile 只做有限结构检查，不认证 Bedrock 客户端兼容性。
- 六张 1200×900 预览来自 Blockbench 原生 Preview / Three.js，纯色底，无 UI / 网格。正、背、侧、顶为正交，两张斜视为透视；相机参数在 `reference/render_conditions.json`。
- 隐藏面审计发现 18 个单体遮挡候选。删除试验的严格 RGBA 对照在正、背、顶视一致，但侧视变化 9 像素，两张斜视分别变化 34 / 68 像素。因此**未通过严格保真比较，最终恢复全部 18 面**，未把平均误差小当作清理成功。相关候选和逐视角对照在 `reference/face_cleanup_*`。

游戏内渲染、手持 / GUI / 掉落姿态、健康扫描、射击、装填、动画与性能均未测试。本次仅美术归档，按用户授权直推上游 main，并在 Discussion #39 登记为待评审候选；本地按用户要求移入 curated，本地分类不代表上游评审通过。未修改游戏代码，后续经评选和集成后才能声称游戏可用。

发布采用人工保存的机匣修改：`breech_core.to.y` 从 `6.16` 调整为 `6.06`，其它数值差别为编辑器保存时的小数精度。最终工程和贴图完整保留，几何与六向预览已重新输出。隐藏面试验记录针对初始 AI 版本；最终版本同样没有应用任何隐藏面删除。

## 重现

Python 3 + Pillow：

```text
python reference/build_asset.py
python reference/validate_asset.py
```

第一条优先从 `reference/publish_input.bbmodel` 恢复人工保存的发布输入和 PNG，不覆盖最终 `.bbmodel` / `.geo.json`；该快照不存在时才运行最初的程序化建模部分。第二条检查现有交付并更新 validation。当前最终工程对应 publish_input / authored_input，未应用隐藏面删除候选。

原生编辑 / 预览另需 `websocket-client` 与启用了本机调试端口 9224 的桌面 Blockbench。`bb_native.py` 通过 DevTools 调用编辑器原生 API，不使用 MCP 凭据，不更改 MCP 配置。先保护现有工程，再运行：

```text
python reference/bb_native.py reference/load_native.js
python reference/bb_native.py reference/render_native.js
python reference/bb_native.py reference/verify_reopen.js
```

已有本任务页签时使用 `reload_authored.js` 代替 `load_native.js`；前者只重建已保存的本任务页签。渲染脚本在原生环境创建离屏 Preview，结束即释放，不是自制光栅渲染器。脚本以自身位置解析资产目录，外部游戏文件只用于已归档的查证，不是重建依赖。`apply_face_cleanup.js` 是未通过严格比较的试验，**不要用于最终工程**。
