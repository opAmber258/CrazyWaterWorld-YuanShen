---
name: router
description: Session bootstrap and navigation hub. Read at the start of every session before any task. Contains project state, routing table, and behavioural contract.
edges:
  - target: context/architecture.md
    condition: when working on system design, integrations, or understanding how components connect
  - target: context/stack.md
    condition: when working with specific technologies, libraries, or making tech decisions
  - target: context/conventions.md
    condition: when writing new code, reviewing code, or unsure about project patterns
  - target: context/decisions.md
    condition: when making architectural choices or understanding why something is built a certain way
  - target: context/setup.md
    condition: when setting up the dev environment or running the project for the first time
  - target: patterns/INDEX.md
    condition: when starting a task — check the pattern index for a matching pattern file
  - target: patterns/add-ship-block.md
    condition: when adding a new submarine hull block
  - target: patterns/blockbench-modeling.md
    condition: when building a block model in Blockbench or fixing UV / item-icon issues
  - target: patterns/cthulhu-post-processing.md
    condition: when adding or tuning a Cthulhu-gaze visual preset or its shader
  - target: patterns/developer-tools.md
    condition: when adding a creative-tab debug item, screen, or server-validated edit packet
  - target: patterns/debug-ship-water.md
    condition: when a ship is dry, flooding incorrectly, or missing water visuals
  - target: patterns/debug-hull-damage.md
    condition: when hull durability, damage bars, welding, or breach-on-zero-HP behavior looks wrong
  - target: patterns/run-tests.md
    condition: when building or running the verification layers
  - target: patterns/publish.md
    condition: when publishing changes (走流程 / /publish) — MEX bookkeeping is a required step
  - target: patterns/vs2-physical-ship.md
    condition: when launching, docking, or debugging a VS2 physical ship
last_updated: 2026-11-09
---

# Session Bootstrap

If you haven't already read `AGENTS.md`, read it now — it contains the project identity, non-negotiables, and commands.

Then read this file fully before doing anything else in this session.

## 合作者下次开工提醒：先读模块还原路线

开工前请阅读 [潜渊症八大模块协作路线](../docs/BAROTRAUMA_MODULE_ROADMAP.md)。它结合官方 Wiki 与仓库现状，包含白话职责、完整子步骤、首轮交付、接口边界及夜间 `/goal` 模板。

先与人类确认认领的大模块和共同基线，再开始实现；路线不是已分配任务，也不是已完成功能清单。不同模块使用独立工作目录，先约定公共接口，避免各自重造或同时覆盖船、水、供电、伤病等核心系统。方案内精确原作数值仍须逐项核实。

这是随 Git 分享的开工阅读入口，不是自动通知；合作者获取包含本提醒的提交后才能看到。

## Current Project State

**Working:**
- 1.20.1 Forge mod builds and loads; air-ocean deep dimension, outposts, and starter submarine generate.
- VS2 physical ships: launch, dock, thruster control, breach reconciliation, sonar proxy, and water-state sync.
- **Minimum gameplay loop (2026-11-04, local work merged on top of `8751a00`, uncommitted):** mission terminal (main outpost `8,-63,0` + B relay station at `320,-300`), 5 missions (salvage×3 / recon / delivery), credits + shop (repair/oxygen/welder/hull upgrade), deterministic POI wrecks, sonar-screen「下潜/靠泊」buttons, first-login guidance + objective HUD, pressure/collision threats.
- **Persistence:** `data/ShipSavedData` (assembled snapshot + hull damage + shipId + proxy), block-ship hull damage in block-entity NBT, VS2 world-load adoption (`VsPilot.adoptFromSave` + shipyard chunk force-load). `.smoketest-persist` PASS (`船=true 耐久记录=2`), `.smoketest-mission` PASS.
- Per-compartment scalar water simulation, breach inflow splash/bubbles, ballast, pumps, reactor, and `/barotrauma ship flood` diagnostics.
- Per-block hull durability (plating 200 / glass 100), melee + explosion + block-ship collision damage, welding-tool repair, camera-facing billboard bars (readable at any view angle), vanilla-style cracks (all 6 faces, outside-visible), damage-scaled leak splashes, and `ShipHullDamagePacket` sync.
- Sonar screen/hologram, depth HUD, underwater fog, movement drag, and oxygen/drowning survival.
- **Minerals creative tab (2026-11-10):** `barotrauma:minerals` tab with 60 items — 34 ores/minerals (incl. the four alien materials and paralyxis) + 26 materials (copper/iron/lead/etc.). Textures are cropped from the game's `Minerals.png` / `AlienMaterials.png` / `Crystal.png` at 64×64; names come from the official zh/en `entityname.*` texts.
- **Hatch/door fixes (2026-11-10):** hatch `isViewBlocking`/`isSuffocating` are always false (the 1px lid used to trigger the in-block fullscreen overlay), hatch models render single-sided, the deck hatch sits mid-block again, the bulkhead door keeps a full-width 1px collision plate (no sideways squeeze), and `hatch_ladder` keeps the middle ladder collision when closed. Small pump gained `FACING`; the welding-tool item model dropped its `item/handheld` parent (generated-model flattening produced 0 quads).
- **Bulkhead door (`barotrauma:bulkhead_door`, 1×2) and deck hatch (`barotrauma:hatch`, 3×1)**, GeckoLib
  rendered, toggled by right-clicking the fixed frame (the open state's pick shape is frame-only, so
  the doorway does not block attacks). The door was redrawn 2026-11-04: 15 axis-aligned cuboids,
  6×8 real window opening with cutout glass, panel slides 16px into the side wall (`open` 0.6 s).
  Pick shapes are cached per `FACING` via `DoorShapes.slab`; a fixed north-facing shape previously
  left east/west doors clickable only at a 1px centre overlap. Toggle sounds are the original
  Barotrauma `Door1..4.ogg`.
- L1 unit tests (46, including `HullDamageNbtTest`) pass; 3 GameTests and L3 `.smoketest-vs` passed earlier.
- **医疗系统（PR #28 已合并 2026-11-07，含 17 条复审修复）**：病症即血量模型（取消原版玩家伤害/回血，vitality=100−Σ病症消耗，原作 CalculateVitality 语义源码核对）；部位鉴定按伤害来源（摔=腿、投射物=命中高度、近战=躯干；头部不做外伤）；六药（绷带/凝胶/吗啡/血袋/盐水/纳洛酮）+芬太尼，数值对照原作 XML；双界面（E 背包=窄条辅助健康栏+拖拽/双击用药，右键药品=治疗面板点选部位+对症方框）；重伤倒地状态机（≤0 倒地可救、≤−100 真死、自然流失同伤势挂钩）；饱食度机制移除。M0 底表 `docs/medical/vanilla-baseline.md`（§8 vitality 语义源码核对）。L1 医疗测试 29 个全绿、L3 任务链冒烟 PASS；合并后 main 复跑 L1 全绿（2026-11-08）。
  - **出血来源已接通（2026-11-08 方案 A）**：近战 +出血 5×HP×50%、投射物 +2.5×HP×100%、爆炸 ≥8HP 时 5% 概率 40 固定强度、摔落 ≥6HP 双腿各 10；失血由出血推进自动产生（原作 AfflictionBleeding 语义）。设计文档 `docs/medical/bleeding-sources.md`（原作 XML 行号索引）。**downedDrain 已按原版语义移除**（0 以下无额外流失，原 CharacterHealth.cs 复核）。
  - **未决**：L2 医疗用例缺 gameteststructures 模板（环境基建）；缺氧第二来源（舱内 O2）依赖氧气模块 issue #24；出血来源待实机验收。
- **开发者工具（PR #37/#38/#40，2026-11-08）**：创造模式独立页签 `itemGroup.barotrauma.dev`（图标医疗调试器）。内容：`medical_debugger`（右键开面板，六部位+全身逐病症 ±1/±10/±100、活力覆盖可设负数；`MedicalDebugPacket` 服务端校验创造/权限≥2；`AfflictionContainer.vitalityOverride` 以 `v|值` 行持久化）、`67`（从主页签挪来）、`cpu_6700x3d`（Blockbench 建模的 2-element 方块，128×64 双贴图 + 32×32 物品栏图标）、`cthulhu_trigger`（一个物品选全部预设/档位/时长）。**坑**：创造模式每秒清空病况的上帝模式要对携带调试器的玩家豁免；`PostChain.passes` 在正式包是 SRG `f_110009_`，反射必须走 `ObfuscationReflectionHelper`。详见 `patterns/developer-tools.md`、`patterns/cthulhu-post-processing.md`、`patterns/blockbench-modeling.md`。
- **物品与材料页签（2026-11-09）**：创造模式新增 `itemGroup.barotrauma.materials`「潜渊症-物品与材料」，首批两个乐器：`clown_horn`（右键一声，服务端 tick 限流，不使用物品冷却 UI）与 `guitar`（长按右键持续演奏 C–Am–F–G 琶音占位曲目，松手停止）。贴图与音效全部程序化生成（`tools/gen_instrument_textures.py`、`tools/gen_instrument_sounds.py`），**不复制原版/维基素材**；6700X3D 放置时不弹提示，右键已放置的方块显示 CPU/GPU/内存面板。
- **克苏鲁的注视（PR #38/#40，2026-11-08；2026-11-09 加平面凝视/万花筒）**：8 个纯视觉 MobEffect 预设（鱼眼/深渊低语/星之彩/时间涟漪/静电噪点/梦境漂移/平面凝视/万花筒），共用一套后处理 `shaders/post/cthulhu_gaze.json`（`Mode` 0–7 + `Strength` uniform）；客户端按 amplifier 换算 0.35/0.65/1.0 强度，按 0.05/tick 淡入、0.028/tick 淡出（中档约 0.7s / 1.2s，满档约 1.0s / 1.8s），叠加 FOV 呼吸与镜头滚转；8 张 18×18 `textures/mob_effect/cthulhu_*.png` 统一暗紫眼图标。静电是 3 像素细行带 + 16Hz 重掷 + 3% 概率错位；梦境是 6×4 网格规则透视错位（阈限空间感）。**平面凝视走投影矩阵**：`CameraProjectionMixin` 把透视与正交按 `FLAT_BASE 0.28 ± FLAT_WOBBLE 0.09` 混合，第四档「满」（仅该预设，amplifier 3）= 完全正交。**万花筒走核心顶点着色器**：`tools/gen_kaleido_shaders.py` 生成 5 个 `assets/minecraft/shaders/core/*.vsh` 覆盖（地形 4 + 粒子；**不覆盖 entity 系列**，否则背包/快捷栏图标和掉落物会被一起折），`ShaderUniformMixin` 用实例字段缓存 location 直传 `Kaleido`；形变是连续径向折返 + 六瓣角向调制（不要用无条件镜像，最弱档也会不可玩），强度 `strength × 0.5 × 呼吸`。详见 `patterns/cthulhu-post-processing.md`。
- **泳姿闪烁修复（PR #36）**：客户端不再用 `isSwimming`（医疗钩子也会设置）判断干湿；服务端用 `OceanEnvironment.isOceanWaterAt`（干舱/哨站优先）判定脚部浸没，经 `SwimStatePacket` 推给 `ClientSwimState`，水中移动入口以服务端状态为准。

**Not yet built:**
- Multi-ship support (one `VsPilot` singleton manages one physical ship).
- Per-block water surface geometry (compartments are sent as greedy rectangular prisms; no per-cell fluid surface).
- Physical-ship collision → hull damage via a VS2 collision callback (currently an AABB sampling fallback).
- Tier/upgrade system beyond the shop's flat +20%/level `hullHealthMultiplier` (no T1/T2/T3 caps yet).
- Compartment water volume / ship oxygen persistence across reload (hull damage and upgrade level persist; water does not).
- Migration of old saves containing real water or removed `hull_plug` blocks.

**Known issues:**
- **OPEN (not yet confirmed in game): east/west-facing bulkhead doors close by right-clicking the frame.** Shapes now rotate with `FACING`; the old symptom was "only the door centre reacts".
- **OPEN: the drain pump still empties a whole connected compartment** ("只要在一个房间都会抽" per user). The `[pump]` log (every 20 ticks) in `ShipSimulation` is intentionally kept for this.
- **Do not read "cannot sink inside the ship" as a swim bug.** 2026-11-04: the player stood on the cabin floor while the breached ship sank (world y falling, assembly coordinates constant). On VS2 physical ships `getBlockState` at the ship's world position is always air and `getBlockCollisions` does not see the hull; map back with `pos - VsState.offset*` before concluding anything.
- **OPEN: compartment water surface still shows visible seams/boundary lines** after multiple fix rounds (2026-11-04 user test). Current renderer uses per-corner surface averaging + waves + no step faces; still not clean. Next step: run `-Dbarotrauma.waterDebug=true` in a real flooded scene (assembled physical ship + breaches) and dump the `[water-diag]` log before changing geometry again. See `patterns/debug-ship-water.md`.
- Without any `ShipWaterStatePacket` (fresh world, ship not assembled: `ship=-1 boxes=0`), the water renderer does nothing and the underwater filter tints ship interiors — do not misread that as a water-rendering bug. Check `[water] 画水面` box counts first.
- The current MEX code graph indexes only the 3 files under `tools/`; Java sources are not in the graph. Use Grep/Read for Java.
- `ShipWaterRenderer` draws compartment water client-side by default (10 Hz packet + per-frame interpolation); the air ocean still has no real water blocks. Disable with `-Dbarotrauma.renderWater=false` when isolating data vs render issues.
- The existing `run/world` predates the air-ocean change; L3 `.smoketest-vs` currently stops with “找不到 9×9×9 空气空腔” until `run/world` is deleted and regenerated.
- `docs/IMPLEMENTATION.md` still describes the older water-column world; prefer the code and `.mex/context/architecture.md` when they conflict.
- **Local MVP work is uncommitted** on top of upstream `8751a00` (46 files); commit/push need explicit human authorization. The running `run-client` is still the pre-merge build.
- Full manual playthrough of the 7-step loop (terminal → dive → salvage → dock → turn in → upgrade → quit/reload) has not been done on the merged build; automated coverage is `.smoketest-mission` + `.smoketest-persist`, and a 73-minute idle-render soak showed 0 OOM.
- The hull damage bar is a world-space billboard (not a block texture); if it is missing, load `patterns/debug-hull-damage.md` before touching the water renderer.

## Routing Table

Load the relevant file based on the current task. Always load `context/architecture.md` first if not already in context this session.

| Task type | Load |
|-----------|------|
| Understanding how the system works | `context/architecture.md` |
| Working with a specific technology | `context/stack.md` |
| Writing or reviewing code | `context/conventions.md` |
| Making a design decision | `context/decisions.md` |
| Setting up or running the project | `context/setup.md` |
| Changing dev scripts, textures, or GameTest templates | `context/tools.md` |
| Debugging hull damage, durability bars, or welding | `patterns/debug-hull-damage.md` |
| Building a block model in Blockbench | `patterns/blockbench-modeling.md` |
| Adding/tuning a Cthulhu-gaze preset | `patterns/cthulhu-post-processing.md` |
| Adding a creative-tab debug item or panel | `patterns/developer-tools.md` |
| Publishing changes (走流程 / `/publish`) | `patterns/publish.md` |
| Any specific task | Check `patterns/INDEX.md` for a matching pattern |

## Behavioural Contract

For every task, follow this loop:

1. **CONTEXT** — Load the relevant context file(s) from the routing table above. Check `patterns/INDEX.md` for a matching pattern. If one exists, follow it.
2. **BUILD** — Do the work. If a pattern exists, follow its Steps. If you are about to deviate from an established pattern, say so before writing any code — state the deviation and why.
3. **VERIFY** — Load `context/conventions.md` and run the Verify Checklist item by item. State each item and whether the output passes. Do not summarise — enumerate explicitly.
4. **DEBUG** — If verification fails or something breaks, check `patterns/INDEX.md` for a debug pattern. Follow it. Fix the issue and re-run VERIFY.
5. **GROW** — After meaningful work, run this binary checklist:
   - **Ground:** What changed in reality? Name the changed behavior, system, command, dependency, or workflow.
   - **Record:** If project state changed, update the "Current Project State" section above. If documented facts changed, update the relevant `context/` file surgically. Record a new behavioral claim about specific code as its own entity (`mex wiki apply` with a `create-entry` operation), grounded to every symbol whose change could make it false and naming the identifiers and literals it depends on. Ground with node ids only; MEX records the fingerprints. Give the entity 3 to 6 `aliases` (short plain-word phrases a developer would type for the symptom or outcome it covers, written from the claim itself, not its title or identifiers).
   - **Connect:** Link section entities to their enclosing knowledge with `refines` using existing ids. Preview missing structural links with `mex wiki link-sections`, then apply explicitly. Review semantic relationships from evidence; do not connect records just because they share keywords or code groundings.
   - **Orient:** If this task can recur and no pattern exists, create one in `patterns/` using `patterns/README.md`, then add it to `patterns/INDEX.md`. If a pattern exists but you learned a gotcha, update it.
   - **Write:** Bump `last_updated` in every scaffold file you changed. Read `mex logging --json` before optional `mex log` notes: `significant` records material rationale, `checkpoints` batches useful notes at task/session boundaries, and `manual` avoids unsolicited notes. Honor explicit user log requests in every mode; mandatory workflow Activity and recovery audits remain required.

发布时走 `.zcode/commands/publish.md`（`/publish`，或用户说「走流程」）：MEX 记账是流程的第 2 步，不是可选项。PR 阶段的兜底是 `.github/workflows/mex-guard.yml`——只统计 `src/` 与 `tools/`：改这两处却不补 `.mex/` 或 `docs/` 会让检查变红；豁免方式是给 PR 加 `mex-skip` 标签（门禁只校验标签，原因须写在说明里，靠 reviewer 把关）。`mex check` 需要 CLI 在 PATH 里；若提示 `command not found`，先按 `AGENTS.md` 的"新机器"一节处理。
