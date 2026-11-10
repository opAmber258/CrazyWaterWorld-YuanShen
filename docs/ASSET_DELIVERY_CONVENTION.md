# 模型资产交付约定

> 适用：**Bedrock 格式**的 Blockbench 模型资产（方块 / 道具 / 物品 / 生物），即交付 `.geo.json` 的那一类。
> 不适用：`java_block` 格式的 Java 方块模型 —— 那类继续遵循 `.mex/patterns/blockbench-modeling.md`（UV 归一化、贴图、物品图标），交付 Java 方块模型 JSON，不需要 `.geo.json`。
> 配套：[`art/models/BAROTRAUMA_PROP_MODELING_GUIDE_V2.md`](../art/models/BAROTRAUMA_PROP_MODELING_GUIDE_V2.md)（建模手法）。
> 登记处：[Discussion #39 · 模型资产统计](https://github.com/opAmber258/CrazyWaterWorld-YuanShen/discussions/39)。
> 运行手册：`.mex/patterns/deliver-model-asset.md`。

本文档是上述范围内资产交付的**唯一权威约定**。其它位置的说明若与本文冲突，以本文为准。

---

## 0. 角色与产出边界

| 角色 | 职责 |
| --- | --- |
| 美术作者 | 在 Blockbench 完成 `.bbmodel`，产出几何、贴图、预览与验证记录，按本文组织目录 |
| 集成者 | 把定案资产接入 `src/main/resources` 并在游戏内验证 |
| 维护者 | 在 Discussion #39 评选候选、决定升入正式资产、归档落选版本 |

美术侧交付**不含**游戏代码改动。资产从「待评选」升入「正式」之后，才由集成者做运行时接入。

---

## 1. 命名

### 1.1 资产名

**中文官方译名 + 官方英文名（驼峰）**，例如：焊接工具 WeldingTool、导航终端 NavigationTerminal、核反应堆 NuclearReactor。

中英两名**必须分别查证**，不能从同一份文件推出来：

| 需要 | 查证来源（均在游戏安装目录，不在本仓库内） |
| --- | --- |
| 中文译名 | `Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `<entityname.标识>` |
| 官方英文名 | 英文文本 `Content/Texts/English/EnglishVanilla.xml` 的同标识条目；或实体定义文件（`Content/Items/**/*.xml`）中的显示名 |

- 简中文件的条目值**只有中文**，部分条目只有中文无英文对应，不能据此推断英文名。
- **不得凭印象或从英文直译**。查证后在 README 中写明所用的两个条目标识。
- 英文名取官方英文的驼峰形式（`Nuclear Reactor` → `NuclearReactor`），**不是**文件标识的驼峰（`reactor1` → Reactor）。
- 原版没有对应实体时（自创生物、项目特有构件），使用项目定名，并在讨论条目里**注明"非原版实体，项目定名"**。已知例：噬海女妖 Charybdis、压载泵 BallastPump。

### 1.2 制作来源标注（必填）

每件资产必须标明制作来源，供评审判断可信度与复核力度。

| 来源 | 标注 | 要求 |
| --- | --- | --- |
| 人类制作 | `制作来源：人工` | 无额外要求 |
| AI 制作 | `制作来源：AI` | 见下方四条 |
| 人机协作 | `制作来源：人工 + AI（说明分工）` | 说明哪部分由 AI 生成 |

**AI 制作的资产额外要求：**

1. **README 首屏**写入 `制作来源：AI`，并写明使用的模型/工具（如"GLM-5.3-Flash + Blockbench 自动化脚本"）；
2. **Discussion #39 候选条目**在任务复选框**之后**加 `🤖` 标记（格式 `- [ ] 🤖 候选 A…`），与人工资产区分。**不要写成 `🤖 - [ ] …`** —— 那会让 GitHub 不再把该行识别为可勾选的任务列表项；
3. **PR 描述**注明 AI 参与范围（几何 / 贴图 / 预览 / 全部）；
4. **评审强度不降低** —— AI 产出必须逐项走第 6 节自查，且原作者需确认视觉结论；不得以"AI 生成"为由跳过评审。

已存在的资产在下次修改时补标，不追溯强制补齐。

### 1.3 标识与路径

| 项 | 规则 | 示例 |
| --- | --- | --- |
| mod 标识 | 小写 + 下划线，与资源文件名一致 | `navigation_terminal` |
| 资产目录 | `art/models/<标识>_<YYYYMMDD>/` | `navigation_terminal_20261108/` |
| 主工程 | `<标识>.bbmodel` | `navigation_terminal.bbmodel` |
| 几何导出 | `<标识>.geo.json` | `navigation_terminal.geo.json` |
| 贴图 | `<标识>_tex.png` | `navigation_terminal_tex.png` |
| 预览 | `preview_{front,back,side,oblique}.png` | `preview_oblique.png` |

同一资产的多件变体（如门的 3x1 / 4x）追加后缀：`bulkhead_door_3x1.bbmodel`。

---

## 2. 目录与版本

### 2.1 标准结构

```text
art/models/<标识>_<YYYYMMDD>/
├── <标识>.bbmodel
├── <标识>.geo.json
├── <标识>_tex.png
├── preview_front.png / preview_back.png / preview_side.png / preview_oblique.png
├── README.md
├── validation.json
├── structure_audit.json        （多骨骼资产建议提供）
└── reference/                  （原作裁切、调色板、取模/渲染脚本）
```

### 2.2 允许的例外

- **扁平命名**：早期资产直接放在 `art/models/` 根下（`engine.bbmodel`、`reactor.bbmodel`、`ballast_pump.bbmodel`、`small_pump.bbmodel`）。这些属于**历史豁免**，不强制迁移；但**新资产一律使用日期目录**。
- 历史豁免资产在补齐 README 与 validation 之前，只能留在「待评选」，不得升入「正式资产」。

### 2.3 不入库的内容

- `art/models/.work/` —— 过程工作区（截图、脚本、临时导出）。**不提交**。
- `art/models/_archive/` —— 落选版本与历史修订。**不提交**（需要保守留档时，单独走一次提交并说明）。
- `.blockbench_launch.log` 等工具日志。

---

## 3. 交付物规格

### 3.1 四件套（缺一不可）

| 文件 | 规格 |
| --- | --- |
| `.bbmodel` | Blockbench 5.2.2 可重新打开编辑；贴图内嵌；不依赖本机路径 |
| `.geo.json` | Bedrock Entity 几何，`format_version` 与项目现有资产一致；identifier `geometry.<标识>` |
| `_tex.png` | RGBA 像素图，2 的幂（32/64/128/256/512）；无渐变、无噪点；透明像素真透明 |
| 预览 ×4 | 1200×900、透明或纯色底、无网格；正/背/侧用正交，斜视用透视（fov 38° 左右）；受光约定：正面平光、背/侧略暗（约 0.9 / 0.55）、顶面接近满亮 |

### 3.2 说明性文件

- **README.md**：来历、原作查证（XML 条目、图集坐标、`scale`）、尺寸换算过程、保留的识别特征、本轮范围、与上版的差异。尺寸换算必须写出中间系数，便于复核。
- **validation.json**：结构化验证记录（尺寸、包围盒、cube/bone 计数、贴图尺寸、检查项与结论）。
- **structure_audit.json**：结构审计原始报告；有警告时在 README 里解释。

### 3.3 `reference/`

放原作裁切与局部放大、调色板 `palette.json`、取模/绘制/渲染脚本。

**脚本必须在仓库内可复现**：能在仓库根以相对路径重跑，且不依赖作者本机私有路径。这条同样约束预览渲染 —— 如果预览由自建脚本产出（而非 Blockbench 内置截图），该脚本必须一并放进 `reference/`，否则评审与后续维护者无法重现预览。

允许的例外：一次性探索脚本、需要交互式环境（如 Blockbench 内 MCP 会话）才能运行的片段，可以不入库。此时 README 必须写明**该脚本的用途**以及**为什么无法在仓库内复现**；如果这个脚本正是预览的产出工具，还要写清预览的实际生成方式（例如"斜视图由自建光线步进渲染器生成，脚本未入库，故仓库内不可重现"），不要让读者误以为预览来自 Blockbench 内置截图。

---

## 4. 几何规范

### 4.1 坐标约定

| 项 | 约定 | 说明 |
| --- | --- | --- |
| 原点 | 底座中心 `(0, 0, 0)` | 便于按方块格对齐 |
| Y | 贴地，最低点 `y = 0` | 不得悬空或陷入地面 |
| X | 居中（左右对称于 0） | 宽度按整数格取 |
| Z | 居中（前后对称于 0），除非资产本身有明确朝向 | 有朝向的资产（门、终端）把"正面"朝 +Z |
| 旋转 | 立方体绕自身 `origin` 旋转，角度写进 `rotation` | 不要用组变换补朝向 |
| 缩放 | **不缩放**导出的几何；尺寸靠建模阶段直接建对 | 后期等比放大必须同时改几何、轴心与可见框 |

### 4.2 尺寸

- 宽度按格取整：1 格 = 16 BB。常见：0.5 / 1 / 2 / 3 格。
- 高度不强制取整，但要写明实际格数与视觉高度依据。
- 参考尺度：人物视觉高度约 32 BB（2 格），用于判断道具是否过大/过小。

### 4.3 数量级

不作硬上限，但超出下列量级需在 README 说明理由：

| 类型 | cube 参考 | 骨骼参考 |
| --- | --- | --- |
| 小道具（接线盒级） | ≤ 30 | ≤ 8 |
| 中道具（钢柜、终端级） | ≤ 80 | ≤ 12 |
| 大件（引擎、反应堆级） | ≤ 150 | ≤ 12 |

静态道具**不要**为省 cube 而牺牲识别特征；也不要用大量 1 像素薄片堆细节。

### 4.4 UV

- 使用 per-face UV，不依赖 box UV 自动推导（便于跨工具一致）。
- 同类部件在贴图上分区，`reference/palette.json` 记录分区含义。
- 贴图更新后必须重新检查所有面，避免串图。

---

## 5. 运行时整合（升入正式资产后）

集成者负责，美术作者不直接改代码资源。

| 内容 | 目标位置 |
| --- | --- |
| 几何 | `src/main/resources/assets/barotrauma/geo/` 下的 `block/` 或 `entity/`，文件 `<标识>.geo.json` |
| 贴图 | `src/main/resources/assets/barotrauma/textures/` 下的 `block/`、`entity/` 或 `item/`，文件 `<标识>.png` |
| 动画 | `src/main/resources/assets/barotrauma/animations/` 下的 `block/` 或 `entity/`，文件 `<标识>.animation.json` |
| 物品图标 | `src/main/resources/assets/barotrauma/textures/item/<标识>.png` |

- 方块类资产还需 blockstate、block model、loot table、lang（中英）同步，命名与注册 id 完全一致，详见 `.mex/context/conventions.md`。
- GeckoLib 静态道具只需 geo + 贴图；有动画的资产补 animation 并在注册处声明。
- 整合后必须验证：渲染正确、UV 不串、轴心正确、暗环境下可读。

---

## 6. 验证

提交前自查（作者自证，写进 `validation.json`）：

- [ ] 四件套齐备且命名合规
- [ ] `geo.json` 能被解析，identifier 正确
- [ ] 包围盒与 README 记录一致
- [ ] 贴图尺寸为 2 的幂，透明像素真透明
- [ ] 四向预览与当前几何一致（**几何改过就必须重出预览**）
- [ ] 无未使用的 cube / 孤立骨骼警告，或已解释

集成后验证（集成者）：

- [ ] 游戏内可渲染，无缺贴图/紫黑面
- [ ] 尺寸与碰撞/占位相符
- [ ] 动画（若有）播放正常

---

## 7. 推送纪律

- 遵守根 `AGENTS.md`：**默认任务分支 → 提交 → 推送分支 → PR → 审核通过并处理意见 → 合并 → 删除分支**。直推 `main` 需维护者当次明确授权。
- 一个 PR 只包含一个资产（或一组同批资产），不与代码改动混提。
- 推送内容：`art/models/<标识>_<日期>/` 全量（含 `reference/`）。**不含 `.work/`、`_archive/`**。
- PR 描述里写：资产名、标识、类型、尺寸、cube/bone 计数、与原作的对应条目、以及"是否已重出预览"。

---

## 8. 登记与评选（Discussion #39）

三个分类，顺序固定，**章节标题与分类说明不得改写**。

| 分类 | 含义 | 条目要求 |
| --- | --- | --- |
| ✅ 正式资产 | 已入库 `main` 且定案 | 只放定位链接（工程/变体/动画/geo/贴图/预览），**不内嵌预览图** |
| 🔍 待评选资产 | 同一资产多候选并列 | 每候选一条任务列表项 + 定位链接 + 几何规格 + commit + **制作来源**（AI 制作的在复选框后加 `🤖`，写作 `- [ ] 🤖 候选 A…`）；**允许内嵌预览图** |
| 📌 待制作 / 待推送 | 需求池 | 三列表格（资产 / 类型 / 状态）；**不写本地路径** |

### 8.1 流转

1. **推送完成** → 从「待制作/推送」移除 → 在「待评选」建候选，补定位链接、几何规格、commit。
2. **评选通过** → 移入「正式资产」，去掉任务列表项，改为资产小节。
3. **落选** → 移入 `art/models/_archive/`，从讨论移除。

### 8.2 编辑纪律

- **只修改自己推送资产对应的条目**。章节标题、分类说明、命名规范、约定说明等文档性文字，非维护者不得改写。
- 新增需求用评论提出，由维护者定期合并进表格。

### 8.3 预览图缓存

GitHub 用 Camo 代理缓存外链图片，**同一 URL 的内容更新后页面仍显示旧图**。

- 内嵌预览的 URL 必须带版本参数：`?v=<数字>`（如 `?v=20261110`）。
- 该资产的图片每次更新后**递增该参数**。
- 「正式资产」只有链接，无此问题。

---

## 9. 评审标准

一次评审看三档阅读距离：

- **远**：缩略图下能否一眼认出是什么。
- **中**：轮廓、分区、主次是否清楚，是否一眼像原作。
- **近**：贴图是否干净（无渐变/噪点/串图）、甲片/面板是否分得清、有无穿插。

硬性红线：

- 朝向错误、贴地图集串位、透明区域实心 → 直接退回。
- 预览与几何不一致 → 退回重出预览。
- 搬运原作比例失真（明显过胖/过瘦/头身失衡）→ 退回。

---

## 10. 历史豁免资产的整改路径

以下资产在仓库中为扁平命名、缺 README / validation，按第 2.2 节保留在「待评选」：

`engine`、`reactor`、`ballast_pump`、`small_pump`。

补齐的最小路径（不强制立刻做）：

1. 新建 `art/models/<标识>_<日期>/`，移入资产文件；
2. 补 `README.md`（原作条目 + 尺寸换算）与 `validation.json`；
3. 补 `reference/palette.json`；
4. 提交时在 PR 说明"结构整改，无几何改动"。

`small_pump` 当前**只有** `.bbmodel` 与 `.geo.json`，缺贴图与预览，属于未完成交付；补齐前不得评选。

---

## 11. 快速清单

新增资产从零到入库：

1. 查官方译名 → 定资产名与 mod 标识
2. **标注制作来源**（人工 / AI / 人机协作，见 1.2）
3. 按第 4 节建模（原点、贴地、居中、按格取宽）
4. 出四件套 + README + validation + reference
5. 按第 6 节自查
6. 按第 7 节走 PR
7. 按第 8 节更新 Discussion #39（待评选 + `?v=` 递增 + AI 资产加 `🤖`）
8. 评选通过后由集成者按第 5 节接入运行时

---

## 维护

- 本文档随资产实践演进；修改需在 Discussion #39 公告。
- 修改后跑 `mex graph rebuild` / `mex wiki rebuild-index` 刷新索引（本机须先安装 MEX CLI）。
