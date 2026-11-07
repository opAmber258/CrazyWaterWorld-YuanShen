# 原作医疗数值底表（M0 · 潜渊症 v1.13.5.0）

| 项 | 内容 |
|---|---|
| 来源版本 | Barotrauma **1.13.5.0**（ProductVersion 1.13.5.0+0a9af9a7f0efeef46d63a4fe80103b4446a21dee） |
| 数据文件 | `Content/Afflictions.xml`（3337 行）、`Content/Items/Medical/medical.xml`（1544 行）、`Content/Texts/SimplifiedChinese/SimplifiedChineseVanilla.xml` |
| 本机路径 | `D:\Steam\steamapps\common\Barotrauma\` |
| 提取日期 | 2026-11-07（tenbaku） |
| 用途 | 医疗系统实现（REQUIREMENTS §5.5 / ROADMAP §5）的唯一数值依据；实现前逐项对照，冲突明确记录 |
| 关联 | `docs/REQUIREMENTS.md` §10 引用的"PLAN.md §10.57 医疗数值"**不存在**，本文档即补此缺口 |

> 说明（**2026-11-07 源码核对后修正**）：原作 vitality 满值 100；`multiplybymaxvitality="true"` 时 XML 的 `min/maxvitalitydecrease` 是**最大生命的分数**（1.0 = 整条血条），vitality 消耗点数 = Lerp(min,max,强度位置) × MaxVitality，**瞬时计算、无时间积分**。`strengthchange` 是病症强度的每秒自然变化（负=自行消退）。详见文末 §8。

---

## 1. 六病种 ↔ 原作病症对照（含官方中文）

| REQUIREMENTS 用名 | 原作 identifier | 原作官方中文名 | 类型 | 部位归属 | 强度上限 |
|---|---|---|---|---|---|
| 内伤 | `blunttrauma` | 钝器伤 | damage | 是 | 200 |
| 流血 | `bleeding` | 出血 | bleeding | 是 | 100 |
| 烧伤 | `burn` | 烧伤 | burn | 是 | 200 |
| 失血 | `bloodloss` | 失血 | bloodloss | **否**（显示于躯干） | 100 |
| 缺氧 | `oxygenlow` | 缺氧 | oxygenlow | **否**（显示于躯干） | 200 |
| 眩晕 | `stun` | 眩晕 | stun | **否**（显示于头部） | 30 |

v1 参考但排除：`pressure`（恶性水压创伤，10%/s 掉血，归潜艇环境模块后期做）；`cardiacarrest` 在 1.13.5.0 的 Afflictions.xml 中**已不存在**（CPR 设置仍在文件头，v1 按大纲跳过 CPR）。

## 2. 病症机制参数（逐条自 XML）

### 2.1 钝器伤 blunttrauma（内伤）

| 参数 | 值 |
|---|---|
| maxstrength | 200 |
| 部位 | limbspecific=true |
| vitality 消耗 | 全强度段 0 → **2%/s**（multiplybymaxvitality，随强度线性） |
| 自然变化 | 无（不自愈不自衰，必须治疗或等医疗手段） |
| 描述分段 | 0–40 低（"afflictiondescription.blunttrauma.low"），40–200 正常 |

### 2.2 出血 bleeding（流血）

| 参数 | 值 |
|---|---|
| maxstrength | 100 |
| 部位 | limbspecific=true |
| healcostmultiplier | 2.5 |
| **分段效果** | 见下 |

| 强度段 | vitality 消耗 | strengthchange | 附加 |
|---|---|---|---|
| 0–20 | 0–2%/s | **-0.1/s**（轻微自愈） | — |
| 20–40 | 2–4%/s | -0.1/s | 感染风险（高敌意世界，v1 不做感染→忽略） |
| 40–60 | 4–6%/s | -0.1/s | 感染风险 |
| 60–100 | 6–10%/s | -0.1/s | 感染风险 |

**关键事实：流血会自己缓慢消退（0.1/s），但高强度的掉血速度远超自愈速度——不包扎就是失血死。**

### 2.3 烧伤 burn

| 参数 | 值 |
|---|---|
| maxstrength | 200 |
| 部位 | limbspecific=true |
| healcostmultiplier | 2.25 |

| 强度段 | vitality 消耗 | 附加 |
|---|---|---|
| 0–30 | 0–0.3%/s | — |
| 30–60 | 0.3–0.6%/s | 感染风险（v1 忽略） |
| 60–200 | 0.6–2%/s | 感染风险 |

自然变化：无。

### 2.4 失血 bloodloss

| 参数 | 值 |
|---|---|
| maxstrength | 100 |
| 部位 | **全身**（indicatorlimb=Torso） |
| activationthreshold | **5**（低于 5 完全无效果） |
| healcostmultiplier | 1.5 |

| 强度段 | vitality 消耗 | strengthchange | 附加 |
|---|---|---|---|
| 0–5 | 无 | **-0.1/s** | 无害区，自行消退 |
| 5–45 | 0–0.75%/s | -0.1/s | — |
| 45–100 | 0.75–2%/s | -0.1/s | 屏幕模糊/色差/变红（v1 用 MC 静态效果近似） |

**关键事实：失血自身会缓慢消退（0.1/s），止血（降流血）才是根治——血袋/生理盐水只是争取时间。**

### 2.5 缺氧 oxygenlow

| 参数 | 值 |
|---|---|
| maxstrength | 200 |
| 部位 | **全身**（indicatorlimb=Torso） |
| activationthreshold | 1 |
| **treatmentthreshold** | **100**（强度>100 后普通治疗失效？——治疗类物品对它的 SuitableTreatment 多为负，见 2.6/2.8 吗啡） |
| healcostmultiplier | 0.5 |

| 强度段 | vitality 消耗 | 附加 |
|---|---|---|
| 1–50 | **无** | 脸色发紫（facetint） |
| 50–200 | 0–2%/s | 面部+躯体发紫 |

**关键事实：缺氧 50 以下是"预警区"不扣血；来源是环境（溺水/无氧），原作伤害源注入量为 12/次左右。治疗靠恢复供氧（氧气模块的职责），药品基本无效甚至有害（吗啡 -20 suitability）。**

### 2.6 眩晕 stun

| 参数 | 值 |
|---|---|
| maxstrength | **30** |
| 部位 | 全身（indicatorlimb=Head） |
| showiconthreshold | 1000（**永不显示图标**——它是瞬时控场不是病症） |
| vitality 消耗 | **0** |
| strengthchange | **-1.0/s**（一秒掉一点，来得快去得快） |

**关键事实：原作眩晕不扣血、不入健康面板、秒级消退。REQUIREMENTS 把"眩晕"列为六病种之一是 MC 适配的重新定义——建议 v1 落地为：原版伤害附带的短暂控制（N 秒缓慢+视野晃动）+ 吗啡等药品的副作用载体，不做成需要"治疗"的持久病症。**

## 3. 五药品 ↔ 原作物品对照（含官方中文）

| REQUIREMENTS 用名 | 原作 identifier | 原作官方中文名 | 英文名 | 价格 | 使用次数（Condition 模型） |
|---|---|---|---|---|---|
| 绷带 | `antibleeding1` | 绷带 | Bandage | 30 | 一次性（Condition -100） |
| 止血密封胶 | `antibleeding3` | **抗生素凝胶** | **Antibiotic Glue** | 220 | **8 次**（每次 -12.5） |
| 血浆包 | `antibloodloss2` | **血袋** | Blood Pack | 240 | 一次性（-100） |
| 生理盐水 | `antibloodloss1` | 生理盐水 | Saline | 50 | 瓶装分次（-100 总量） |
| 吗啡 | `antidama1` | 吗啡 | Morphine | 100 | 一次性注射器（-100） |

> **命名澄清**：REQUIREMENTS 的"止血密封胶"= 原作 **Antibiotic Glue/抗生素凝胶**（"An antibiotic glue with hemostatic properties. Seals bleeding wounds almost immediately"，密封止血语义吻合）；"血浆包"= 原作 **Blood Pack/血袋**。建议 v1 文档统一用官方中文：抗生素凝胶、血袋。
> 备选：原作还有 `antibleeding2` 可塑人造皮肤（Plastiseal，50 元，bleeding 40/burn 50 双修）——v1 五件套若嫌凝胶太贵可换它，【默认】按 REQUIREMENTS 取凝胶。

## 4. 药品效果明细（OnSuccess 路径；v1 无技能→恒定走 Success，见 §5）

| 药品 | 治疗量（每秒 × 持续秒） | 适用性 SuitableTreatment | 副作用 |
|---|---|---|---|
| 绷带 | 出血 **-1.5/s × 20s**；烧伤 -0.75/s × 20s | 出血 30 / 烧伤 15 | 无 |
| 抗生素凝胶 | 出血 **-30/s × 2s**；烧伤 -7.5/s × 2s；感染 -0.75/s × 30s | 出血 60 / 烧伤 15 / 感染 22.5 / **损伤 -3** | **器官损伤 +0.6/s × 5s**（失败时 +1.8） |
| 血袋 | 失血 **-8/s × 15s**（合计 -120） | 失血 **120** | 无 |
| 生理盐水 | 失血 **-2.5/s × 10s**（合计 -25）；感染 -0.5/s × 10s | 失血 30 | 无 |
| 吗啡 | **损伤类 -2/s × 20s**；烧伤 -0.1/s × 20s；戒断 -3.75/s × 20s | 损伤 40 / 戒断 5 / **过量 -20 / 成瘾 -20 / 缺氧 -20** | **缺氧 +1/s × 20s、成瘾 +1/s、过量 +1/s**（失败时更多） |

失败（OnFailure）路径为技能不足时的打折效果（约 Success 的 62–75%）——**v1 无技能系统，【默认】恒走 OnSuccess 数值**，失败分支与 RequiredSkill 字段不入实现。

**v1 取舍**：`infection`（感染）、`opiateaddiction/opiateoverdose/opiatewithdrawal`（阿片三件套）、`organdamage`（作为独立可显示病症）——这些超出六病种范围。【默认】v1 全部省略，副作用简化为：凝胶→附加少量钝器伤；吗啡→附加缺氧强度（保留"药不对症会伤人"的原作精神，但不新增病症种类）。

## 5. 原作机制规则总结（对实现有直接约束）

1. **治疗是"每秒治疗量 × 持续时间"的持续过程**，不是瞬间结算（血袋=8/s×15s）。MC 适配：使用后挂一个持续治疗 buffer（服务端 tick 推进），死亡/换人中断。
2. **multiplybymaxvitality 语义**：vitalitydecrease 按最大生命百分比计算 → MC 侧映射到 20 HP 时保持"百分比"语义（2%/s of 20HP = 0.4 HP/s）。
3. **activationthreshold**：低于阈值完全无效果（失血<5 无害）。实现必须做，否则轻微失血会莫名扣血。
4. **treatmentthreshold**（缺氧=100）：超过阈值强度后治疗效果受限。
5. **strengthchange 自然变化**：出血/失血 -0.1/s 自衰；眩晕 -1/s 快衰；钝器伤/烧伤 0（不自愈）。**vitality 下降与强度变化相互独立**。
6. **目标模型**：绷带/凝胶作用于**部位**（target="This, Limb"——作用于被治疗的肢体）；注射类（吗啡/血袋/盐水）作用于**全身**（target="UseTarget"）。
7. **Condition 耐久模型**：物品有使用总量（凝胶 8 次），用尽 OnBroken Remove；同类可合并（canBeCombined）。
8. **SuitableTreatment 的 suitability 值**：用于健康界面"推荐用药"排序（血袋 120 > 盐水 30），治疗量本身由 ReduceAffliction 决定。v1 的 H 面板"对症提示"按此排序。
9. **缺氧的来源注入**：环境原因注入量约 10–12/次（Afflictions.xml 各处 `<Affliction identifier="oxygenlow" amount="10~12" />`）——氧气模块的写入量级参考。
10. **伤害源同时施加多病症**：如子弹=撕裂伤+出血+失血；烧伤/烫伤源=burn + 可能 bleeding（amount 0.5–1.0，dividebylimbcount）。武器模块将来按此发伤害。

## 6. 对实现大纲的修订（基于以上事实）

| 大纲原【默认】 | 现在的结论 |
|---|---|
| 失血"满值直接致死" | 保留；原作失血 45–100 段最高 2%/s，映射 MC 后即正常死亡流程 |
| 眩晕改为限时 debuff | **XML 证实**：原作眩晕 0 伤害、-1/s 衰减、无图标——按瞬时控制适配合理，REQUIREMENTS 的"眩晕"定位需在 M0 评审时向团队提出澄清 |
| 缺氧"唯一写入方=氧气模块" | 注入量级参考 10–12/次；50 以下无害预警区必须实现 |
| 凝胶/吗啡副作用 | 用"附加钝器伤/附加缺氧"近似，不新增病症种类 |
| 治疗为持续过程 | 新增设计点：治疗 buffer（服务端 tick），原作即如此 |
| 技能成功/失败分支 | v1 恒 OnSuccess，字段保留不实现 |

## 7. 待确认问题（请 opAmber258 / 团队拍板）

1. "止血密封胶"按官方中文定名**抗生素凝胶**、"血浆包"定名**血袋**——REQUIREMENTS 是否修订用词？
2. 眩晕的定位澄清（见 2.6）。
3. 副作用的简化近似（§4 v1 取舍）是否接受。
4. REQUIREMENTS §10 的错误引用（PLAN §10.57）由本文档取代——文档修正随 M0 PR 一起提交。

## 7.5 头部不做外伤（用户指令 · 2026-11-07）

**v1 头部不参与受伤判定**（命中头部并入胸部计算）。原作里头部的"异常状态"不是外伤，
而是精神/药物/感官/控场类特殊状态——本机 XML `indicatorlimb="Head"` 全名单（后续逐步添加）：

| 类别 | identifier | 说明 |
|---|---|---|
| 控场 | `stun` | 眩晕（v1 已有，见 §2.6） |
| 伤情 | `concussion` | **脑震荡**（max 10；屏幕扭曲+醉酒步态+呕吐；-0.02/s 自愈）——头部系第一个候选 |
| 药物 | `drunk` / `drunknodebuffs` | 醉酒 |
| 药物 | `opiatewithdrawal` / `opiateoverdose` / `opiateaddiction` | 阿片戒断/过量/成瘾 |
| 药物 | `chemaddiction` / `chemwithdrawal` | 化学成瘾/戒断 |
| 精神 | `psychosis` / `psychosisresistance` / `psychosisimmunity` | 精神错乱/抗性/免疫 |
| 精神 | `hallucinating` / `hallucinatingshortduration` / `hallucinating_mindsunraveled` | 幻觉系 |
| 精神 | `watchersgaze` | 深潜者凝视 |
| 伪装 | `disguised` / `disguisedashusk` | 伪装/伪装成 husk |
| 感官 | `blind` / `blind_hard` | 失明 |

原作物理性外伤（blunttrauma 等）虽可命中头部，但按本指令 v1 不做头部外伤。

## 8. 原作 vitality 语义（源码核对 · 2026-11-07）

> 数据源：官方源码仓库 **FakeFishGames/Barotrauma**（master）`CharacterHealth.cs` / `Affliction.cs` / `AfflictionPrefab.cs` / `AfflictionBleeding.cs`，交叉验证 wiki Afflictions 页（fandom api.php）。本机游戏版本 1.13.5.0，源码 master 与之可能有小版本差异，冲突时以本地 XML 反编译为准（ROADMAP §2）。

### 8.1 vitality 是"随算值"，不是积分池

```csharp
// CharacterHealth.CalculateVitality()（每帧经 RecalculateVitality 调用）
vitality = MaxVitality;                                   // 100 起步
foreach (affliction in afflictions)
    vitality -= affliction.GetVitalityDecrease(this);     // 按当前强度直接扣
```

全文件只有这一处 `vitality -=`。**没有"每秒扣血"的时间积分**——血量是病症的纯函数：强度变，血量立刻变；强度不变，血量不动。`GetVitalityDecrease`（Affliction.cs L184-203）= 分段 Lerp × (multiplybymaxvitality ? MaxVitality : 100)。

### 8.2 XML 数值是"最大生命的分数"

`min/maxvitalitydecrease` 在 multiplybymaxvitality=true 时是分数（1.0 = 100% 血条，反序列化**无除以 100**）。交叉验证：

- wiki 内伤行："**−1 vitality per strength**"（0–200 线性）→ `Lerp(0,2,s/200)×100 = 1.0×s` ✓ 精确吻合
- wiki 出血行："−0 → −10（随强度）" → 出血每强度点的杀伤是钝器伤的 **10 倍** ✓（所以包扎是第一优先级）
- 满强度病症 = 死亡：钝器伤 200 → 200 点 → 必死 ✓；失血 100 → 200 点 ✓

### 8.3 死亡线与复活缓冲

```csharp
minVitality = character.IsHuman ? -100.0f : 0.0f;    // L290
KillIfOutOfVitality(): if (Vitality <= MinVitality) Kill();
```

人类死亡线是 **vitality ≤ −100**（多出的 100 点缓冲留给 CPR 复原）；vitality ≤ 0 即视为死亡状态。v1 不做 CPR → MC 侧按 ≤0 结算死亡。

### 8.4 流血 → 失血的推动在代码里（不在 XML）

```csharp
// AfflictionBleeding.Update()：流血以 强度/60 每秒 推高全身失血强度
characterHealth.BloodlossAmount += Strength * (1 - resistance) / 60.0f * deltaTime;
```

XML 的流血 Effect 里只有感染风险（v1 忽略）——**"流血慢慢失血而死"是 AfflictionBleeding 类的专属逻辑**。v1 移植：容器 tick 里对每个出血实例按 `强度/60 每秒` 加到全身失血（含双侧肢体求和后统一推）。

### 8.5 攻击只施加病症，没有独立立即扣血

`CharacterHealth.ApplyDamage(hitLimb, attackResult)` 只遍历 `attackResult.Afflictions` 逐个 AddLimbAffliction——立即掉血来自新病症加入后的重算。**未发现任何"伤害直接减 vitality"的旁路**。MC 侧取消原版伤害、全量转病症后，立即掉血同样由公式即时体现 ✓。

### 8.6 记录在案的冲突

wiki 出血行写 "vitality **per second**"，但源码 CalculateVitality 无 deltaTime——wiki 措辞描述的是"随强度演化的血量变化体验"，非字面每秒积分。以源码为准（每帧重算的纯函数），MC 侧实现同为随算值。

### 8.7 对 MC 实现的最终换算

| 原作事实 | MC 侧落地 |
|---|---|
| vitality = 100 − Σ(分数×100) | 纯函数实现，心数按 vitality/100 镜像 |
| 攻击 = 施加病症 | 取消原版 MC 伤害，全部转病症（未映射→躯干钝器伤过渡） |
| 流血/60 每秒推失血 | 容器 tick：每秒对每个出血实例执行 |
| 死亡线 −100（CPR 缓冲） | v1 无 CPR → vitality ≤ 0 即死亡（自定义 DamageType 结算） |
| 治疗是 ReduceAffliction | M2 的治疗物品按此实现 |

