# GUI 缩放规范

所有**模组自建界面**都按这一套走，和导航台/声纳台一致。以后新增界面照抄，不要再自己算缩放或居中。

## 1. 继承哪个基类

| 界面类型 | 基类 |
| --- | --- |
| 有物品槽（`AbstractContainerScreen`） | `com.opamb.barotrauma.client.gui.ScaledContainerScreen<T>` |
| 没有物品槽（原来的 `Screen`） | `com.opamb.barotrauma.client.gui.ScaledScreen` |

例外（不缩放，跟随原版）：
- 原版玩家背包 `InventoryScreen`（E 界面）—— 不是模组自建界面；
- `MedicalInventoryScreen`（原版 `InventoryScreen` 的子类），同理跟随原版。

## 2. 设计坐标（唯一坐标空间）

- 每个界面有一个固定**设计尺寸**：容器界面就是 `imageWidth × imageHeight`，
  `ScaledScreen` 在构造器里传 `designWidth/designHeight`。
- 设计坐标原点固定在面板左上角 `(0,0)`。容器界面的 `leftPos/topPos` 由基类强制为 0。
- 子类里**只写设计坐标**：
  - ❌ `int left = (width - W) / 2;`
  - ❌ `graphics.pose().scale(...)` / `translate(...)` 自己缩放
  - ✅ `graphics.fill(0, 0, W, H, ...)`、`graphics.fill(leftPos + 16, topPos + 30, ...)`

## 3. 缩放规则（`GuiScale`）

```
factor = clamp(min((screenW - 24) / designW, (screenH - 24) / designH), 0.30, 1.50)
面板左上角 = ((screenW - designW * factor) / 2, (screenH - designH * factor) / 2)
```

- 四周固定 12px 边距；
- 窗口/GUI Scale 不够时整块等比缩小（最小 0.30，低于这个字就看不清）；
- 大窗口允许放大，上限 1.50；
- 面板始终居中，不会被裁切。

## 4. 渲染顺序（基类已实现，子类不要打破）

1. `renderBackground(...)`：全屏压暗，**不参与缩放**；
2. push pose → translate 到面板位置 → scale；
3. 内容 / `renderBg` / 槽位 / 标签 / 可点击 widget（全部用设计坐标）；
4. pop pose；
5. `renderTooltip(...)`：用**真实屏幕坐标**画，保持原版字号，不缩放。

自绘顺序特殊的界面（如声纳台）覆盖 `renderScaled`，但缩放/背景/tooltip 仍由基类负责。

## 5. 鼠标与交互

基类统一把屏幕坐标换算成设计坐标后再交给原版逻辑，所以：

- 槽位命中、拖拽分堆、Shift 点击、"点面板外丢出"都不用改；
- 子类覆盖 `mouseClicked / mouseReleased / mouseDragged / mouseScrolled` 时：
  - 参数是**屏幕坐标**；
  - 自己做命中测试前先 `designX(...)/designY(...)`（或 `designXi/designYi`）；
  - 最后 `super.mouseClicked(screenX, screenY, ...)` 传**原始屏幕坐标**，基类只换算一次。

## 6. 裁剪

原版 `graphics.enableScissor(...)` 不认 PoseStack 的缩放，直接用会在缩放后错位。
在缩放界面里一律用基类提供的：

```java
enableDesignScissor(graphics, x1, y1, x2, y2); // 设计坐标
```

## 7. 新增界面自查清单

- [ ] 继承 `ScaledContainerScreen` 或 `ScaledScreen`；
- [ ] 构造器设置了设计尺寸（容器界面是 `imageWidth/imageHeight`）；
- [ ] 没有任何 `(width - X) / 2`、`(height - Y) / 2` 的布局；
- [ ] 没有自己 `pose().scale`；
- [ ] `init()` 里第一行调 `super.init()`（`ScaledScreen` 需要它算缩放）；
- [ ] `mouse*` 覆盖里先换算再命中测试，`super` 传屏幕坐标；
- [ ] 裁剪用 `enableDesignScissor`；
- [ ] tooltip 交给基类（不要在 `renderDesign` 里画）；
- [ ] 界面里的弹窗/模态框也用设计坐标居中（`designWidth()/designHeight()`），
      不要用 `width/height`，否则会跑到缩放后面板的右下方并透出底下的列表；
      弹窗面板用不透明色，打开期间要挡住后面的点击和滚轮。

## 8. 容器界面的玩家物品区（和 E 界面同款）

所有有物品槽的界面，左下的玩家物品区**必须**是「3×9 装备背包区 + 快捷栏」，
不要画原版 27 格主背包：

- 菜单：先照常 `addSlot` 27 个主背包槽和 9 个快捷栏槽，再调
  `BagSlots.installAt(menu, owner, 容器格数, areaX, areaY, 160, 54)`；
  快捷栏放在 `areaY + 58`。
- 界面：容器自己的格子照常画；快捷栏每格画一个原版 `slotBg`；上面 3×9 整块交给
  `BagOverlay.renderIn(...)`，渲染前先 `BagSlots.rebuildClientAt(...)`。
- 双手格：`HandsSlot.CONTAINER_X`（-30，面板左侧外面，和物品栏平行）+ 界面里
  `HandsSlotOverlay.renderIn(...)`。**负 x 的可见槽位会自动让基类在面板左边留出
  缩放空间**（`designLeftGutter()`），那条留白也算面板内，点歪不会丢东西。

现成例子：`FabricatorScreen` / `DeconstructorScreen` / `ReactorScreen` / `CabinetScreen`。

## 9. 参考实现

- 玩家使用的设备和服务面板通过 `client/gui/PanelStyle` 绘制外框、配色和标准按钮。各面板在自己的设计坐标内画仪表与槽位。
- 有维修能力的设备菜单继承 `menu/DeviceMenu`。容器屏幕把主体和底部维护框一起缩放，主体保留自己的 `imageHeight` 和槽位范围。维修框只在持对应工具且达到阈值时出现。舱门和舱口使用同一组件的独立界面。

- 缩放数学：`client/gui/GuiScale.java`（有单测 `GuiScaleTest`）
- 容器界面：`SonarScreen`、`FabricatorScreen`、`DeconstructorScreen`、`BatteryScreen`、`CrateScreen`、`CabinetScreen`、`PumpScreen`、`EngineScreen`、`ReactorScreen`、`MissionScreen`、`RepairScreen`
- 普通界面：`CampaignMapScreen`、`JunctionBoxScreen`、`StoreScreen`、`NpcDialogueScreen`、`MedicalScreen`、`MedicalClinicScreen`、`MedicalDebugScreen`、`CthulhuGazeScreen`
