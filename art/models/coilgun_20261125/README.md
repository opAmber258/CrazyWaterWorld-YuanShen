# 电磁枪 Coilgun

> 资产名：**电磁枪 Coilgun**（mod 标识 `coilgun`，类型：炮塔方块）
> 制作日期：2026-11-25
> **制作来源：人工 + AI**（用户在 Blockbench 完成 0.8 倍缩放和主贴图 UV 重排，Codex 对接后坐与火光、拆出斜槽侧壁 UV、绘制挡板加深与局部锈蚀、导出并复核预览）

## 1. 本轮范围

R9 相对 R8 只动两处：把 10 片斜块南北共 20 个侧壁面从共用一条 UV 拆成各自独立的 1 像素宽 UV 岛；在此基础上把两个朝向的槽壁再压深一档。几何、其余面的 UV、动画时序与后坐曲线都不变，`reference/split_uv.json` 逐条记录了 20 个面改动前后的矩形。

## 2. 原作查证

- 简中：`Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` 的 `<entityname.coilgun>` →「电磁枪」。
- 英文：`Content/Texts/English/EnglishVanilla.xml` 同标识条目 →「Coilgun」。
- 实体定义：`Content/Items/Weapons/coilgun.xml`，`Scale = 0.5`。
- 图集分层（`Content/Items/Weapons/Turrets.png`，坐标 x, y, w, h）：底座 `772, 8, 248, 248`、护罩 `901, 256, 120, 287`、炮管 `776, 256, 125, 333`。
- 裁切对照：`reference/sprite_comparison.png`（左两行为 Coilgun / Railgun 的原作分层，右侧为用户参考图），只作视觉取证，模型几何与贴图均为自制。

## 3. 尺寸

沿用用户定的 0.8 倍：包围盒 x ±29.7521、y 0–54.2164、z −56.9347–48.72 Blockbench 单位，按每格 16 单位换算为 **宽 3.719 × 高 3.3885 × 长 6.6034 格**。

几何账目：**286 个 cube / 13 根骨骼 / 1670 个 UV 面**。`gun_pitch` 没有 cube，只作俯仰动画枢轴，导出时保留为空骨骼；`flash_*_volume` 四个网格 `export=false`，仅在主工程里预览枪口火光。

## 4. 贴图

512 × 512 RGBA，透明像素 `alpha = 0`（无半透明），2 的幂。

20 个侧壁 UV 岛排在图集原空白区第 319 行附近，每条宽 1 像素、高等于对应斜块，四周各留 1 像素填充，避免采样串色。用户在本轮脚本产出后又手工加深了锈蚀，改动落在图集 `(113, 8)–(222, 197)` 区间共 408 个 texel，已记入 `validation.json` 的 `human_retouch_bbox`。

## 5. 动画

`coilgun.animation.json` 四条：

| 动画 | 时长（秒） | 驱动骨骼 |
| --- | --- | --- |
| `animation.coilgun.yaw_test` | 8 | `turret_yaw` |
| `animation.coilgun.aim_demo` | 11 | `turret_yaw`、`gun_pitch`、`pitch_wheel_left/right` |
| `animation.coilgun.fire_single` | 1.16 | `barrel_recoil` |
| `animation.coilgun.fire_burst` | 1.75 | `barrel_recoil` |

后坐关键帧按 0.8 缩放同步：峰值后坐 7.2 个 Blockbench 单位、发间回弹 2.2 个单位。火光锚点 `muzzle_flash_origin` 与火光网格的世界坐标误差 7.1e-15 单位。主工程 `coilgun.bbmodel` 保留四条火光网格与发光材质，可直接回放特效；`coilgun_runtime.bbmodel` 是 Bedrock 导出的对应工程。

## 6. 交付清单

| 文件 | 说明 |
| --- | --- |
| `coilgun.bbmodel` | 主工程（free 格式，贴图内嵌），含火光预览网格与 locator |
| `coilgun_runtime.bbmodel` | 运行时工程（bedrock 格式），`coilgun.geo.json` 与 `coilgun.animation.json` 的导出源 |
| `coilgun.geo.json` | Bedrock Entity 几何，`geometry.coilgun` |
| `coilgun.animation.json` | Bedrock 动画 |
| `coilgun_tex.png` | 交付图集 |
| `preview_{front,back,side,oblique}.png` | 四向预览，1200 × 900，正交正/背/侧 + 透视斜视 |
| `preview_armor_{left,right}.png` | 挡板近景，1200 × 900 |
| `preview_single.gif` / `preview_burst.gif` | 单发 / 连发回放，由 `reference/frames/` 的中间帧合成 |
| `validation.json`、`structure_audit.json` | 验证记录、结构审计 |
| `reference/` | 原作裁切、调色板、UV 映射、渲染与校验脚本 |

## 7. 复现

- 重出几何、动画与预览：先以隔离配置启动 Blockbench 5.2.2
  `Blockbench.exe --user-data-dir=%TEMP%/coilgun-scaled-aged-r9-20261125-qa --remote-debugging-port=0`，
  再在本目录执行 `node reference/blockbench_local.mjs reference/capture.js`。脚本会重写 `coilgun.geo.json`、`coilgun.animation.json`、六张 PNG 预览和 `reference/native_check.json`。
- `python reference/verify.py`：与上一版 `coilgun_scaled_aged_r8_20261125/` 逐项对比，复核 20 条侧壁、重算预览差值并重建两个 GIF。需要同级存在 R8 目录，属于一次性生产校验。
- `python reference/split_uv.py`：从 R8 工程生成 R9 工程与图集，同样依赖同级 R8 目录。
- `python reference/verify_delivery.py`：仓库内可重跑的交付自查，不依赖 Blockbench 与 R8，覆盖命名、geo 标识与计数、贴图规格与内嵌一致性、预览尺寸、动画骨骼引用与 UV 岛分布。
- `reference/frames/` 是 GIF 合成用的中间帧（13 MB），不随交付保存；GIF 本身已入库。

## 8. 已知边界

- Minecraft 客户端未实测；游戏内的火光触发与炮塔转向接入由运行时侧完成。
- 预览由 Blockbench 5.2.2 内置预览渲染（脚本见 `reference/capture.js`），不是外部渲染器。
