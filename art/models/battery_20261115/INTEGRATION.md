# 电池（设备）接入记录（2026-11-15）

本目录是 Han-xing-official 交付的 `java_block` 资产。接入 1.20.1 Forge 时补充了
GeckoLib 运行格式与模组资源，原 `battery.bbmodel` / 贴图 / 预览未改动。

## 为什么生成 `.geo.json`

`battery.bbmodel` 的三个插口端子是 67.5° 旋转；1.20.1 原版方块模型反序列化只接受
`-45 / -22.5 / 0 / 22.5 / 45`（`BlockElement$Deserializer#getAngle` 会直接抛
`Invalid rotation`），所以不能直接导出 Java 方块模型。改为 GeckoLib 的 Bedrock
geometry，保留全部旋转角度。

## 转换规则

按 Blockbench 自己导出 Bedrock geometry 的规则（`bedrock.js` 的 `compileCube`）：

- 模型内部坐标以方块中心为 X/Z 原点、y=0 为底面；
- `.geo.json` 的 X 轴按 Bedrock 约定镜像：`origin.x = -(内部 x_max)`，pivot/rotation
  的 X/Y 同步取反；GeckoLib 载入时会再做一次镜像，和 Blockbench 一致；
- 逐面 UV：普通面 `uv=[u1,v1] / uv_size=[u2-u1, v2-v1]`；`up`/`down` 面按
  Blockbench 规则反向（`uv=[u2,v2] / uv_size=[u1-u2, v1-v2]`）；
- 骨骼按原 `电池 / 面板 / 插口组 / 插口1..3` 分组，顶层“电池”并入 root。

生成文件：`src/main/resources/assets/barotrauma/geo/block/battery.geo.json`
（`geometry.battery`，64×64 贴图）。按交付约定，本目录不追加 `.geo.json`。

## 模组内接入

- 方块：`barotrauma:battery`，墙挂，`FACING` + `CHARGE`（0 空 / 1 低 / 2 半 / 3 满）。
- 贴图：`battery.png`（满）+ `battery_mid.png` / `battery_low.png` / `battery_empty.png`
  （取自 `reference/screen_states/`），按 `CHARGE` 切换。
- 数值取自本机原作 `Content/Items/Electricity/poweritems.xml`：
  `capacity=2000` kW·min、`maxoutput=1000` kW、`maxrechargespeed=500` kW、
  `efficiency=0.95`；耐久衰减 0.125/s、泡水 -0.5/s。
- 电池可绑到接线盒（电力面板里和用电设备一起列出），绑定的盒子损坏后不充不放。

## 验证状态

- 离线渲染器复算正视轮廓与 `preview_front.png` 一致；未改动几何/贴图。
- `compileJava test --offline` 通过（电池数学 10 项单测）。
- **尚未进游戏验收**（模型朝向、四档屏幕、充放电、VS2 物理船上的表现）。

## 本轮未做

- 顶部三个插口目前只是造型：没有手持电池 cell 物品，也没有原版的三个充电槽。
- 数值用 prefab 的 2000 / 1000 / 500；原版潜艇会在 `.sub` 里覆写（例如 Dugong
  是 500 容量 / 650 输出），本模组暂不按船覆写。
- 电池 GUI 已做：电量条、充电速率滑条（默认 500 kW，可调 0–500）、耐久/QTE 修理；
  电池没有独立的三格电池 cell 充电槽。
