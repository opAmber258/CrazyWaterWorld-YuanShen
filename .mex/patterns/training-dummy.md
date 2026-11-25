---
name: training-dummy
description: 训练人偶/气球充气台：方块实体生成/绑定/2 秒重生；人偶 1/200 血（2.5 格高），气球六向吊挂（沿朝向 2 格），200 血版无受击间隔。
triggers:
  - "训练人偶"
  - "充气台"
  - "高级训练人偶"
  - "训练气球"
  - "气球台"
  - "training dummy"
  - "training balloon"
  - "靶子"
edges:
  - target: patterns/add-ship-block.md
    condition: when the station must be treated as a ship block
last_updated: 2026-11-25
mex:
  id: mx_01M844RY52JXJTBH1FHERC1SSE
  type: pattern
  status: promoted
  revision: 5
  title: training-dummy
---

# 训练人偶充气台

## Context

用于测试炮塔/近战命中的开发设施：`barotrauma:training_dummy_station` 放下后，
在上方生成一只 `barotrauma:training_dummy`。人偶 1 血、不会移动/被击退，
与台座方块绑定；死亡 2 秒后由台座方块实体重新召唤。人偶本体 1.5 格高，
加 1 格台座总高 **2.5 格**（立柱/绳子从 12 px 缩到 4 px，头和身体没动）。

**高级版**（`barotrauma:advanced_training_dummy_station`）走同一套台座/绑定/重生逻辑，
差异只有两点：

- 人偶换成 `AdvancedTrainingDummyEntity`：默认 50 血（上限属性仍是 200，面板可调 1..200），
  并在 `hurt()` 里把 `invulnerableTime` 清零，**没有原版 10 tick 受击间隔**——炮塔
  5 发/秒每一发都扣血，适合测连发 DPS。
- 外观：台座换成金块/抛光黑石/发光红石灯；人偶换深灰装甲贴图 + 红色发光眼，
  渲染时 X/Z 放大 1.12 倍（Y 不缩放，避免底座压进台面）。

**高级版设置面板**（空手右键高级人偶台 / 高级气球台）：

- 血量滑条 1..200，默认 **50**；改血量时**不重生**当前人偶，直接把它的最大血量改成新值并回满
  （现存实体继续用，只是属性变了）；只改重生间隔/自发光时当前血量不动，仅当上限变小时截断。
  重生/区块重载也不会再“顺手奶满”，血量按存档保留。
- 自发光开关：实体 `DATA_SELF_ILLUMINATED` 同步到客户端，渲染时方块光/天空光都按 15，
  不再吃环境明暗（人偶走 `getBlockLightLevel/getSkyLightLevel`，气球走 `LightTexture.FULL_BRIGHT`）。
- 重生间隔滑条 0..200 tick（0..10 秒），默认 40 tick（2 秒）；死亡后按该值计时。
  倒计时已经在跑时改间隔，会按“实际死亡时刻 + 新间隔”重排（`respawnAt - 旧 respawnTicks`
  反推死亡时刻），不是等旧计时走完。
- 设置存 BE NBT（`MaxHealth` / `RespawnTicks` / `SelfIlluminated`），重载后第一次 tick
  重新套到已有实体上。

面板交互按“表单”习惯来（2026-11-25 重做，旧版“应用”不关界面且无反馈，实机反馈很差）：

- 右下“保存”= 发 `DummySettingsPacket` + 播 `UI_BUTTON_CLICK` + **立刻关界面**；
  服务端应用成功后回动作栏“已应用：血量 … · 重生 … · 自发光 …”，失败（>8 格 / 方块已拆）
  回“设置未应用：原因”。反馈走 `ServerPlayer.displayClientMessage(..., true)`。
- 左下“取消”和 Esc 同效：直接丢弃草稿（滑条只改屏幕本地字段，不发包）。
- 回车 / 小键盘回车 = 保存；指针在滑条行上时滚轮 ±1，按住 Shift ±10。
- 标题右侧显示“未保存 / 已同步”，标题本身按方块类型显示“高级人偶充气台 / 高级气球充气台”。
- 窗口缩放会重走 `init()`：草稿值只在第一次 `init()` 从菜单读，之后保留，别在 `init()` 里
  无条件覆盖。

**气球版**（两个独立方块，`balloon_dummy_station` / `advanced_balloon_dummy_station`）
把目标换成气球，沿台座朝向共占 **2 格**：

- 六向 `FACING` 取 `context.getClickedFace()`：地面朝上、贴墙朝外、吊顶朝下，
  也就是朝向放置者的一侧；台座模型复用普通/高级充气台模型，靠 blockstate 的
  `x`/`y` 旋转摆到六个方向（x=90 让模型 +Y 朝北，再 y=90/180/270 转东/南/西）。
- 气球中心 = 台座中心 + FACING × 1.1875 格（19 px），正好落在相邻格靠外的一半；
  实体碰撞箱 0.625 格，实体原点（碰撞箱底面）在球心下方 0.3125 格。
- 渲染用自定义 `EntityRenderer`（不走生物模型的翻转换算）：把模型 +Y（绳子方向）
  用四元数旋到 `-FACING`，绳子始终指回台座；上/下/四向都能正确吊挂。
- 动画：空闲时绕绳子末端（台座接口）用两个不同频率小幅摆动；死亡时 20 tick 内
  先胀到 1.3 倍再缩没并转半圈，`die()` 同时发 POOF 粒子和破裂声。
- 血量同样分 1 血 / 默认 50 血（高级气球面板可调 1..200）；高级气球在 `hurt()` 里清 `invulnerableTime`。

建模/动画是**临时占位**，不进入正式资产交付流程：

- 台座：普通 JSON 方块模型，普通版铁块/红石，高级版金块/黑石/红石灯，不依赖 GeckoLib。
- 人偶：`TrainingDummyModel<T>`（`EntityModel` + `ModelPart`，泛型以同时服务两个实体）
  + `TrainingDummyRenderer` / `AdvancedTrainingDummyRenderer`。
- 气球：`BalloonDummyModel<T>` 四层方盒拼球 + 结 + 绳，`BalloonDummyRenderer<T>`
  带纹理参数，普通/高级共用一个渲染类。
- 贴图 64×64：`training_dummy.png`、`advanced_training_dummy.png`、
  `balloon_dummy.png`、`advanced_balloon_dummy.png`，全部由
  `tools/gen_training_dummy_texture.py` 同一套画法、两套调色板生成
  （纯标准库，固定种子，可重跑）。
- 动画：生成时 8 tick 从 25% 放大到 100%；人偶站桩时身体轻微摇摆、双臂小幅摆动。
  改模型时同步 `ModelLayerLocation`、UV 和 `ClientSetup` 的 layer 注册。
- 创造页签：只在**开发者工具**页（`itemGroup.barotrauma.dev`），不进主页签。

## Steps

1. 方块/BE：人偶台 `TrainingDummyStationBlock` 只存水平 `FACING`；气球台
   `BalloonStationBlock` 存六向 `FACING`。BE 保存 `Dummy` UUID 与 `RespawnAt`，
   气球 BE 继承同一套生命周期，只覆盖 `createDummy()` / `configureDummy()`。
   `getTicker` 只在服务端返回。
2. 生成：`spawnDummy()` 调 `createDummy()`（人偶/气球、普通/高级各自覆盖），再调
   `configureDummy()`：人偶台把人偶放到 `pos + (0.5, 1.0, 0.5)` 并按水平 `FACING`
   设朝向；气球台按六向 `FACING` 放到相邻格（台座中心 + `1.1875` 格，再减碰撞箱半高）。
3. 死亡检测：BE 每 tick 用 UUID `server.getEntity`；实体活着就返回，死了/被移除就
   清 UUID，并按 `respawnTicks`（面板可调，默认 40）计时。到点重新 `spawnDummy()`。
4. 绑定：实体 `tick()` 调 `bindToStation()`；基类检查台座仍是 `TrainingDummyStationBlock`
   并把人偶钉回上方，气球覆盖成检查 `BalloonStationBlock` 并沿 `FACING` 钉到相邻格；
   返回 false 时 `discard()`。
5. 拆台：`Block.onRemove` 调 `discardDummy()`；**不要**在 BE `setRemoved()` 里删人偶，
   区块卸载也会走 `setRemoved`，会误删跨区块保存的人偶。
6. 属性：`ModEntities.onAttributes` 注册四个实体的 `MAX_HEALTH` 上限（人偶 1/200、
   气球 1/200）、`MOVEMENT_SPEED=0`、`KNOCKBACK_RESISTANCE=1`；高级 BE 生成时把
   实体的 base value 改成 `configuredHealth`（默认 50），**不要**把注册值改成 50，
   否则面板调不到 200。实体实现 `isPushable=false`、空 `push/knockback`。
7. 资源：人偶台 blockstate 四向 `y` 旋转；气球台六向 `x`/`y` 旋转（`x=90` 朝北、
   `y=90/180/270` 转东/南/西，`x=180` 朝下）。block/item 模型、loot、shaped recipe、
   `block/item/entity` 中英 lang。
8. 设置面板：高级方块 `use()` → `DummyStationMenu.open()`（`NetworkHooks.openScreen`
   写初值）；菜单只有只读 `ContainerData`；屏幕点“保存”发 `DummySettingsPacket`
   （协议 36），服务端校验“面板还开着且 pos 一致”+ 距离 ≤8 格 + 方块是高级台 +
   设置版本未变后调 `BE.setSettings()`；`stillValid` 同样要求方块仍是高级台。

## Gotchas

- 人偶是独立实体，不是方块模型；用 `MobCategory.MISC`，不注册刷怪蛋，由台座生成。
- BE 卸载不等于拆台：UUID/重生时间必须落 NBT，重载后靠 UUID 认领；找不到实体时
  走 2 秒重生，不要立刻重复生成。
- 人偶死亡后仍有死亡动画；BE 检测到 `!isAlive()` 就开始计时，40 tick 后新实体才出现。
- 台座被拆时实体自己也会在下一 tick 自毁，`onRemove` 是加速清理，不是唯一保障。
- 实体位置每 tick 重置，不能靠 AI/重力；`setNoAi`、`setNoGravity`、`setPersistenceRequired` 都要开。
- 高级版的无敌帧是在 `hurt()` 开头把 `invulnerableTime = 0`，不要改成
  `BYPASSES_COOLDOWN` 伤害类型——那会连护甲/药水结算一起绕过。
- 高级版渲染只缩放 X/Z；`poseStack.scale` 以脚底为原点，缩放 Y 会把底座按进台面。
- 设置面板只对高级台开放；普通台 1 血固定。协议升到 **36**（合并 #83 后：34 = TurretHitPacket、
  35 = DummySettingsPacket、36 = 设置版本），客户端/服务端必须同版本。
- 保存包带“设置版本”（BE 的 `SettingsRevision`，每次 `setSettings` 自增并落 NBT）：面板打开后
  别人改过就拒收，否则 A 只勾自发光也会把自己那份旧血量写回去、把上限改回旧值并回血。
- 台座认领实体前必须比对 `dummy.stationPos()`：`/clone` 会把 BE NBT 里的 UUID 一起抄到新台座，
  不校验归属时新台座会认领别人的实体、不生成自己的，还会把本台设置套上去；
  `discardDummy()` 同理，只能删绑在本台的人偶。
- `setSettings()` 只能套给 `dummy.isAlive()` 的实体：死亡中 `dummyId` 还没被 tick 清掉时，
  给尸体 `setHealth()` 会把血量从 0 拉回正数，`tickDeath()` 停止但 `dead` 标记还在——
  既不播死亡动画也不安排重生（假复活，2026-11-25 审查抓到的中优先级问题）。
  死亡中的设置只写 BE，下一只 `spawnDummy()` 时生效。
- 自发光走实体 `SynchedEntityData`（`DATA_SELF_ILLUMINATED`），不要客户端读 BE；
  高级人偶渲染器覆写 `getBlockLightLevel/getSkyLightLevel`，气球用 `LightTexture.FULL_BRIGHT`。
- 高级 BE 的 `configuredHealth` 默认 50 是 BE 字段，不是实体属性注册值；属性上限保持 200。
- 人偶 2.5 格：body pivot 在 14、立柱在 20..24（4 px）；改高度要同步改实体
  `sized(..., 1.5F)`。立柱贴图只用到 32..37 行，旧 38..45 行是余量。
- 气球碰撞箱 0.625 格，实体原点是碰撞箱底面（球心下方 `0.3125`）。这个半高同时出现在
  实体尺寸、渲染 `translate`、`configureDummy`/`bindToStation` 三处，改一处必须三处一起改。
- 气球用自定义 `EntityRenderer`，但**不要**再 `scale(0.0625)`：`ModelPart.Cube.compile`
  已经把顶点除以 16，额外的 1/16 会让气球只剩一个点（2026-11-24 实机踩过）。
  也不要套 `LivingEntityRenderer` 的 `-1.501`/翻转逻辑，否则气球会飘到 1.5 格高。
- 气球死亡动画必须用 `entity.deathTime > 0` 判断，不能写成
  `deathTime + partialTick > 0`：活实体 `deathTime=0`，但 `partialTick` 每帧在 0..1，
  会让每一帧都套用死亡缩放/旋转——表现为 20 Hz 水平抖动，暂停时停止
  （2026-11-25 实机定位，主因）。
- 气球台 `FACING` 取点击面；blockstate 六向旋转按原版惯例 `x=90` 朝北、
  `y=90/180/270` 转东/南/西，别按右手定则推（dispenser/hopper 已验证）。
- 气球朝向必须用 `SynchedEntityData`（`DATA_FACING`）同步：`stationPos` 只在服务端
  NBT 里，客户端拿不到，直接读方块状态会永远退回 UP——贴墙/吊顶时模型会插进台座，
  看起来像高频闪烁/震动（2026-11-24 实机踩过）。
- 气球绳子做成 1 px 并居中，别用 2 px：2 px 绳和台座 2×2 喷嘴的侧面会共面 z-fighting。
- 球体各层在 Y 上重叠 1 px（上层 `-5..-2`、下层 `2..5`），否则层间接缝共面，也会闪。
- 两套贴图共享 UV 布局，只有调色板不同；`Palette` 里 `eye` 单独一项，用来让高级版眼睛发光。
- 64×64 贴图的 UV 分区：人偶头 0..32/0..16、身体 0..24/16..32、左右臂 24..40/40..56、
  立柱 0..8/32..46、底座 8..40/32..42；气球中层 0..40/0..16、顶/底 40..64/0..18、
  结 0..8/16..20、绳 8..12/16..26。贴图由 `tools/gen_training_dummy_texture.py`
  按同一套 `texOffs` 绘制，改模型 UV 时脚本要一起改，否则细节会画到相邻面上。

## Verify

- [ ] 放下充气台，台面上立即出现人偶，位置不漂移
- [ ] 人偶从台座底面到头顶总高 2.5 格（1 格台座 + 1.5 格人偶）
- [ ] 打一下即死，约 2 秒后重新出现；连续击杀不会叠加多只
- [ ] 高级版放下后默认 50 血；炮塔连发时每一发都扣血（无 10 tick 受击间隔）
- [ ] 空手右键高级人偶台/高级气球台打开设置面板；血量滑条 1..200（默认 50），点“保存”
      后界面立刻关闭、有点击音，动作栏回“已应用：…”，当前实体最大血量/血量立即变化
- [ ] 面板里拖动滑条后窗口缩放不丢草稿；按 Esc/“取消”不改变实体设置；回车等同保存
- [ ] 指针在滑条行上滚轮 ±1、Shift+滚轮 ±10；标题右侧“未保存/已同步”跟着变
- [ ] 勾选“自发光”后实体在暗处也满亮度；取消后恢复环境光
- [ ] 重生间隔调到 1 秒/5 秒后，击杀后的实际重生时间跟着变；设置重进世界仍保留
- [ ] 普通台右键不弹设置面板，仍是 1 血
- [ ] 高级版台座/人偶外观与普通版明显不同，底座不陷进台面
- [ ] 气球台地面放置：台座 1 格 + 气球 1 格，气球在上方相邻格，绳子指回台座
- [ ] 贴墙/吊顶放置：气球沿 FACING 水平/向下伸出，台座模型朝向同步
- [ ] 气球 1 血打一下即死；高级气球默认 50 血、连发每发扣血，按面板间隔重生
- [ ] 拆掉台座，人偶/气球立即消失；重进世界后仍绑定
- [ ] 人偶不会走动、不会被击退、不会自然消失
- [ ] `./gradlew compileJava test --offline` 通过
