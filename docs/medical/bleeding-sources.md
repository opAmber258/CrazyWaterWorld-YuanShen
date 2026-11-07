# 出血/失血来源设计（方案 A · 已拍板并实现）

| 项 | 内容 |
|---|---|
| 状态 | **已实施**（2026-11-08 用户拍板方案 A，三个默认值照单全收） |
| 日期 | 2026-11-08 |
| 数据依据 | 本机 Barotrauma 1.13.5.0 `Content/`（行号见文末）；源码 `AfflictionBleeding.cs`；符合 REQUIREMENTS §10 与设计指令"三方对照"要求 |
| 关联 | `vanilla-baseline.md`（M0 底表）、`MedicalCommand`（命令注入保留，作调试兜底） |

---

## 0. 结论

出血不必等武器模块：**原作出血的第一来源就是生物咬击**，而生物近战伤害我们已经在接（`DamageConversions.fromGeneric` 过渡期折算）。按原作规律给"近战/投射物/爆炸/重度摔落"接上出血后，绷带/血袋/盐水三药立即生效，"包扎第一优先级"的原作救援节奏成立。**失血无需独立来源**——原作失血从来不是直接注入的，是出血按 `强度/60 每秒` 推出来的（`AfflictionBleeding.cs:17`，`AfflictionContainer.tickSecond` 已实现同款推进）。

## 1. 原作出血来源全景（1.13.5.0 本机 XML 逐条核对）

### 1.1 生物攻击（出血的第一来源）

| 生物 | 攻击 | 主伤害 | 出血 | 出血概率 | 来源行 |
|---|---|---|---|---|---|
| Crawler（船内威胁候选） | 咬（地） | bitewounds 3~12 | 5 | 50% | CrawlerDefaultRagdoll.xml:58 |
| Crawler | 咬（水） | bitewounds 12 | 12 | 50% | :76 |
| Mudraptor（船外威胁候选） | 舱内咬 | bitewounds 10 | 10 | **75%** | MudraptorDefaultRagdoll.xml:16 |
| Mudraptor | 船外咬 | bitewounds 30 | 20 | 50% | :37 |
| Mudraptor | 撞角（钝击） | blunttrauma 90 | **无出血** | — | :26 |
| Tigerthresher | 咬 | bitewounds 20~30 | 20~30 | **100%** | TigerthresherDefaultRagdoll.xml:12 |
| Hammerhead | 撕咬 | lacerations 50 | 50 | **100%** | HammerheadDefaultRagdoll.xml:43 |
| Hammerhead | 撞角（钝击） | blunttrauma 120 | **无出血** | — | :108 |

**规律 1：锐器咬击带出血（强度≈主伤害同级、概率 50~100%），纯钝击（撞角/拍击）无出血。**

### 1.2 手持武器（武器模块接入后逐武器对表）

| 武器 | 主伤害 | 出血 | 概率 | 来源行 |
|---|---|---|---|---|
| 手枪弹 | 4 | 12.5 | 100% | weapons.xml:338 |
| 步枪弹 | 10 | 16 | 100% | :217 |
| 潜水刀 | 2（+lacerations 10） | 10 | 100% | :896 |
| 斧类 | 15（+lacerations 35） | 35 | 100% | :38 |
| 棍棒/板球拍 | blunttrauma 3 | **无出血** | — | :1199 |

**规律 2：穿刺/切割武器出血 100%（XML 无概率字段即必中），出血强度 ≈ 1.25~5× 主伤害；钝器武器无出血。**

### 1.3 爆炸

```
Explosion structuredamage 90~300 →
  explosiondamage 75~250（主伤）
  burn 10~15 × 20%
  bleeding 20~50 × **5%**        ← 小概率大强度、固定值不随伤害缩放（explosives.xml:33/78/124/170/403）
```

### 1.4 明确**无**出血的来源

摔落（纯 blunttrauma）、深海压力（pressure 专属通道）、缺氧/失血自身、火焰。

### 1.5 v1 没有的原作病症如何折算

`bitewounds / lacerations / gunshotwound / explosiondamage` 全部是 `InternalDamage`，vitality 模型与钝器伤**逐字节一致**（maxstrength 200、multiplybymaxvitality、maxvitalitydecrease 2 → 全强度线性 0→2%/s，Afflictions.xml:105-240）。**1 强度 = 1 vitality，1:1 折进钝器伤不损失任何数值**（损失的是"哪种药能治"的区分度——v1 绷带治钝器伤+出血，可接受）。

### 1.6 出血 → 失血 → 死亡链

- `AfflictionBleeding.cs:17`：`失血 += 出血强度/60 × deltaTime`（每 60 强度出血每秒推 1 失血）
- 出血自身活力消耗：60~100 强度段 6~10%/s（vanilla-baseline §2.2）——**出血每强度点杀伤是内伤 10 倍**（wiki 交叉验证）
- 失血强度 s → 持续 2s%/s 活力消耗（activationthreshold 5 以下无害）
- 结论：失血永远不需要独立来源，接通出血 = "钝+出+失"三件套自动成立

## 2. 已实施的 MC 侧映射（方案 A）

转化基准沿用现行：**1 HP = 5 强度**（`DamageConversions.BLUNT_PER_HP`）。

| MC 伤害来源 | 实现（2026-11-08） | 原作依据 |
|---|---|---|
| 近战（生物/玩家，`fromGeneric`） | 钝器伤 3/HP **+ 出血 5×HP × 50% 概率**（挂伤害部位） | 规律 1（咬击出血≈主伤同级×0.5 概率） |
| 投射物（箭/三叉戟） | 钝器伤 5/HP **+ 出血 2.5×HP × 100%**（挂高度鉴定部位） | 规律 2（穿刺武器 100% 出血，强度取手枪/步枪折中） |
| 爆炸 | 钝 5/HP×0.8 + 烧 2.5/HP **+ 单次 ≥8 HP 时 5% 概率附加出血 40 强度（固定值）** | explosives.xml 精确值 |
| 摔落 ≥6 HP 单次 | 双腿钝器伤 + **双腿各附加出血 10 强度**（≥6 HP=原作断腿级，用户指令"重度才附加"） | MC 适配决策 |
| 火焰 / 压力 / 溺水 / 饥饿冻伤 | 不变（无出血） | §1.4 |

- 概率掷骰：`player.getRandom().nextDouble()`（服务端），与 `fromFire` 同款。
- 出血部位跟随主伤部位：近战=躯干、投射物=命中高度鉴定、摔=双腿——与部位鉴定指令一致。
- 生物模块/武器模块接入时只需按 §1 表逐只/逐武器替换数值（概率与强度都是常量），结构不变。

## 3. 同轮拍板的倒地流失修正（原版语义）

原版源码复核（`CharacterHealth.cs`）：`Update → RecalculateVitality → KillIfOutOfVitality`，死亡只判 `Vitality ≤ MinVitality`（人类 −100）；**0 以下没有任何额外流失机制**——vitality 是纯函数（`CalculateVitality:1065`），跌破 0 只因病症消耗总和超过 100，继续下坠全靠伤口 dynamics（出血推失血、强度自然变化）。

据此（用户指令"以接近潜渊症原版数值的为准"）：

- **移除 v1 自加的 `downedDrain` 附加流失**（顺带消灭其 `/100` 单位错误——原实现把每秒点数又除以 100，实际流失率比设计值慢两个数量级）。
- 倒地（raw ∈ (−100, 0]）后的活力变化完全由病症强度决定，与原版一致：
  - 带伤倒地且伤口在恶化（如出血 60 → 失血 +1/s → 活力 −2/s）→ 约 50 秒内滑向 −100 真死（原版数值自然涌现，非外加系数）；
  - 伤口稳定/已治住 → 活力停在原值，等待队友治疗救援（原版昏迷语义）。
- `AfflictionContainer` 的 `downedDrain` 字段、`addDownedDrain/resetDownedDrain/downedDrainValue/restoreDownedDrain`、治疗抵消流失分支与 NBT 键一并移除；旧存档残留的 `downedDrain` NBT 键读取时忽略。

## 4. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-08 | 拍板方案 A（三个默认值照单全收）；实现出血四来源；按原版语义移除 downedDrain |

---

## 附：数据行号索引

- 出血→失血推进：`AfflictionBleeding.cs:17`（源码缓存 D:\圆神\drafts\barotrauma-src\）
- vitality 纯函数与死亡线：`CharacterHealth.cs:1065`（CalculateVitality）、`:666`（KillIfOutOfVitality）、`:290`（MinVitality 默认 −100）、`:143`（Vitality≤0=昏迷非死亡）
- bitewounds/lacerations/explosiondamage vitality 模型：`Afflictions.xml:105-240`（lacerations 112-135、bitewounds 138-160、gunshotwound 162-187、explosiondamage 217-240）
- 生物攻击：`CrawlerDefaultRagdoll.xml:55-76`、`MudraptorDefaultRagdoll.xml:14-77`、`TigerthresherDefaultRagdoll.xml:10-32`、`HammerheadDefaultRagdoll.xml:41-118`
- 武器：`Items/Weapons/weapons.xml`（手枪 336-338、步枪 215-217、刀 894-896、斧 36-38、棍棒 1198-1199）
- 爆炸：`Items/Weapons/explosives.xml:30-34、75-80、400-404`
