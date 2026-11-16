# 哨站服务：商店与医疗诊所（2026-11-15）

> 本文件记录 2026-11-15 批次里哨站服务的实现与和原作 `Barotrauma 1.13.5` 的对应关系，
> 供 PR 审核和新会话接手使用。原作依据：本机 `Content/` 与反编译源码
> `Location.cs`、`Store.cs`、`CargoManager.cs`、`MedicalClinic.cs`、`OutpostNpcs.xml`、
> `Content/Map/locationTypes.xml`，以及 wiki `Store` / `Campaign#Outposts`。

## 1. NPC 与交互

- `NpcEntity` 增加 `DATA_ROLE`：`mission`（站务员）/ `merchant`（商人）/ `doctor`（医生），
  NBT `Role`；旧存档 NPC 默认 `mission`。
- 右键按角色分发到三个**独立界面**，不共用任务终端标签页：
  - 站务员 → `MissionNpc.talk`（对话 + 海图接任务）
  - 商人 → `StoreService.open`（商店）
  - 医生 → `MedicalClinicService.open`（医疗诊所）
- `MissionNpc.ensureStations` 每站补齐三个角色，各自找 1.6 格内无其他 NPC 的站位；
  `MissionInstance.outpostExpected(site)` 为 false 的无人航路点不生成。
- 皮肤：站务员按站点坐标散列 `opamber / hanxing / tenbaku` 三张指定皮肤；
  `opamber` 贴图是 3px 手臂布局，用 slim 模型，另两张标准 4px 用 classic。
  商人/医生没有指定皮肤，用默认史蒂夫 classic。旧存档 NPC 进站时迁移。

## 2. 商店

### 2.1 数据与价格

- `StoreCatalog`：医疗/潜水/工具/燃料/容器/矿物材料；价格尽量取原作 `baseprice`
  （绷带 30、氧气瓶 75、焊接工具 150、燃料罐 120、等离子切割机 150、潜水服 250、
  燃料棒 150–250、矿物 25–300）。
- `StationStoreSavedData`：按站点 `(x,z)` 存库存、商人余额（初始 5000）、
  ±5% 价格修正、上次补货轮次；`SiteStore(..., initializeStock=false)` 用于读档时
  避免提前访问物品注册表。
- `StorePricing`：买价 = 基础价 × (1 + 修正%)；卖价 = 基础价 × 30% × (1 + 修正%)；
  每次到访补货 +1，封顶初始库存 +5。
- 出售规则（原作）：只收主背包，耐久 ≥90%，没有装填/子格/箱子内容。

### 2.2 三个标签页（原作 `StoreTab`）

`Store.cs` 的枚举是 `Buy / Sell / SellSub`，第三个标签名取 `"submarine"`：

- **购买**：按 `buyable` 显示库存/买价；每行 `−/数量/+`，选好的数量进购物车，底部总价整单购买。
- **出售**：列出主背包里商店收购的物品，同样按行选数量。
- **潜艇**（`SellSub`）：服务端用 `ShipCores.nearestCore` 找玩家潜艇，按
  `ShipGeometry.Bounds` 扫 `CabinetBlockEntity`（钢柜/货架/潜水服柜/氧气瓶架），
  按物品 id 聚合可出售件数；排除任务货箱（`MissionCargo.isMissionCrate`）、
  装了东西的箱子、耐久 <90% 的物品。只扫 `CabinetBlockEntity`，不含反应堆燃料。

### 2.3 购买确认的两个交付选项（原作 `AllowImmediateItemDelivery`）

原作单机 `CampaignMode.AllowImmediateItemDelivery()` 恒 true，购买确认弹
`GUIMessageBox`，官方简中：

- 提示：`suppliespurchased.deliverymethod` =「要立即获得已购买物品，还是在起航时送至潜艇？」
- 选项 1：`...deliverimmediately` =「立即获得」→ 逐件进角色背包
- 选项 2：`...delivertosub` =「送至潜艇」→ 先记账，**起航时**交付到潜艇

本模组实现：

- `StoreActionPacket` 增加 `deliverImmediately`（协议 13）。
- 「立即获得」：逐件 `Inventory.add`，放不下的件数退款；先取物品名再 add，避免
  `Inventory.add` 吃掉 stack 后提示变成「已购买空气」。
- 「送至潜艇」：购买时只写入 `StoreDeliverySavedData`（按玩家 UUID、随世界存档持久化）；
  `CampaignManager.startTravel` 在 VS2 装配前调用 `StoreService.deliverPending`，
  把货送进船上钢柜/货架，货柜和背包都放不下的留在待交付里，下次起航再送。

### 2.4 网络

`ModNetwork` 当前协议 **16**（协议 13 是 2026-11-15 的商店交付批次值）；
`StoreStatePacket`（钱包、商人余额、`open` 首开/刷新标记、全部有价商品、潜艇页聚合列表）、
`StoreActionPacket`（BUY / SELL / SELL_SUB + 购物车行 + 交付选项）；
诊所 `ClinicActionPacket` 带玩家确认价 `maxPrice`。出售只接受主背包槽位 0–35，
护甲槽（36–39）和副手槽（40）不会被商店读取。

## 3. 医疗诊所

- `ClinicPricing`：原作 `HealCost = (1 - Reputation/100*0.1) * (BaseHealCost + Strength*HealCostMultiplier)`；
  本模组暂不含声望折扣。倍率：出血 2.5、烧伤 2.25、缺氧 0.5、失血 1.5、
  阿片中毒 1.25、阿片成瘾 2.85、辐射 4.0，其余 1.0。
- `MedicalClinicService` 从 `AfflictionContainer.rows()` 读病症（跳过 `v|`/`t|` 行），
  按强度算价、`setStrength(...,0)` 治疗，发 `MedicalSyncPacket.respondSnapshot` 刷新 HUD；
  支持单条治疗和全部治疗，钱包不足/商人余额规则与服务端校验一致。
- 客户端 `MedicalClinicScreen`：病症名/肢体/强度/价格/单条治疗/全部治疗/钱包。

## 4. 测试与验证

- `StorePricingTest`、`ClinicPricingTest` 锁价格/补货/收费公式。
- 2026-11-15 全量 `./gradlew test --offline`：**272 项测试 0 失败（47 个测试类）**。
- 客户端实机验收待用户；本轮未起客户端。

## 5. 已知限制 / 未做

- 船员雇佣（`HireManager`）、完整升级树（`UpgradeStore`）按用户决定暂不做。
- 每日特价（`DailySpecials`）和需求物资（`RequestedGoods`）暂不做；购买/出售价格已按
  原作 ±5% 修正。
- 「送至潜艇」的交付目标是船上钢柜/货架；原作使用潜艇的 Cargo 航点。方块船移动不搬
  散落物品，因此不生成地面散货，避免离港丢货。
- 商店只收主背包；工具带/背包内的物品、潜艇货柜以外的散落物暂不在出售范围。
