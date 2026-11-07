package com.opamb.barotrauma.world;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 初始潜艇：停在新手哨站门口那艘。
 *
 * <p>对应原版流程里"出生在站点、站点旁边连着你的第一艘船"。9×5×15 的小艇，
 * 甲板下一排机器（反应堆 / 制氧机 / 水泵 / 压载舱 / 引擎 / 驾驶台 / 声纳），
 * 中间放船核心，顶上有一个舱口和两扇舷窗。</p>
 *
 * <p>位置在前哨站水池的椭球范围内（见 {@code OutpostBuilder.carveCavern}），所以整艘都泡在水里。</p>
 *
 * <p>船顶/舱口在 y=-69，紧贴中层平台（平台舱口在 -68），两层舱口上下叠着；
 * 舱口格本身打开时可攀爬，铁梯铺在舱内和下层通道里。</p>
 */
public final class StarterSub {

    /**
     * 最小角坐标。船体占 x −4..4、y −73..−69、z −7..7，船顶直接贴住对接舱的
     * -68 中层平台：船顶舱口在 -69，中层舱口在 -68，两层舱口上下叠着，开舱门就能上下。
     */
    public static final int ORIGIN_X = -4;
    public static final int ORIGIN_Y = -73;
    public static final int ORIGIN_Z = -7;
    /** 2026-11-07 之前的存档：船在 -76，船顶舱口在 -72；登录时整体上移 3 格。 */
    public static final int OLD_ORIGIN_Y = -76;
    /** 上一版"直接对接"误上移 4 格的存档：船在 -72，登录时向下降 1 格并补回站侧平台。 */
    public static final int PREV_ORIGIN_Y = -72;
    /** 更老的存档：船在 -78（舱口 -74）；登录时整体上移 6 格。 */
    public static final int LEGACY_ORIGIN_Y = -78;
    public static final int SIZE_X = 9;
    public static final int SIZE_Y = 5;
    public static final int SIZE_Z = 15;

    private StarterSub() {
    }

    /**
     * 船脑位置 = 声纳台的位置。
     *
     * <p>声纳台现在兼任船核心（{@code SonarBlockEntity extends ShipCoreBlockEntity}），
     * 不再有独立的船核心方块。</p>
     */
    public static BlockPos corePos() {
        return new BlockPos(ORIGIN_X + 5, ORIGIN_Y + 1, ORIGIN_Z + 4);
    }

    /** 2026-11-07 之前（船在 -76）的船核心位置。 */
    public static BlockPos oldCorePos() {
        return new BlockPos(ORIGIN_X + 5, OLD_ORIGIN_Y + 1, ORIGIN_Z + 4);
    }

    /** 更老存档（船在 -78）的船核心位置。 */
    public static BlockPos legacyCorePos() {
        return new BlockPos(ORIGIN_X + 5, LEGACY_ORIGIN_Y + 1, ORIGIN_Z + 4);
    }

    /** 船核心的世界坐标（给提示文字用）。 */
    public static BlockPos doorPos() {
        return new BlockPos(ORIGIN_X + 4, ORIGIN_Y + 1, ORIGIN_Z);
    }

    /**
     * 造好初始潜艇并扫描一次。已经造过就直接返回现有的核心。
     */
    @Nullable
    public static ShipCoreBlockEntity ensure(ServerLevel level) {
        DockingBay.ensureStationHatch(level);
        // 先处理旧档：潜艇之前在 -76/-78，现在直接顶到 -68 平台；登录时整体上移。
        liftLegacySub(level);
        BlockPos corePos = corePos();
        // 这艘船已经被收成 VS2 物理刚体了：方块搬到船坞区块里、世界里这一格是空气。
        // 如果这里还照旧"没看到声纳就现造一艘"，就会在物理船旁边凭空长出一艘方块潜艇，
        // 用户看到的就是"两套移动系统叠在一起 / 方块没删干净"。
        if (VsState.active()) {
            BlockPos activeCore = VsState.corePos();
            // 只有"被收成物理船的就是这艘初始潜艇"才跳过；玩家先装了别的船时，
            // 初始潜艇仍然应该出现在哨站门口。旧存档的船在 -78，也算这艘。
            if (corePos.equals(activeCore) || oldCorePos().equals(activeCore)
                    || legacyCorePos().equals(activeCore)) {
                BarotraumaMod.LOGGER.info("[Barotrauma] 初始潜艇已收成物理船，跳过重建");
                return null;
            }
            BarotraumaMod.LOGGER.info("[Barotrauma] 当前物理船不是初始潜艇（core={}），继续检查初始潜艇",
                    activeCore);
        }
        // 已发过船就不能再造：装配走/靠泊在别处/世界重开后刚体还没认领，
        // 原位看起来都是空的，但再来一艘就是"两艘初始潜艇"。
        if (com.opamb.barotrauma.data.ShipSavedData.get(level).starterClaimed()) {
            BarotraumaMod.LOGGER.info("[Barotrauma] 初始潜艇已认领过，跳过重建");
            return null;
        }
        // 必须先把区块加载出来：Level#getBlockEntity 对未加载区块返回 null，
        // 世界重开后船其实还在，却会被判成"没造过"。
        level.getChunk(corePos.getX() >> 4, corePos.getZ() >> 4);
        if (level.getBlockState(corePos).is(ModShipBlocks.SONAR.get())) {
            if (level.getBlockEntity(corePos) instanceof ShipCoreBlockEntity existing) {
                if (existing.state() == null) {
                    existing.rescan(level);
                }
                return existing;
            }
        }
        // 如果上移没跑到（例如没有旧核心），认 -76/-78 的旧核心，避免又长一艘。
        BlockPos oldCore = oldCorePos();
        level.getChunk(oldCore.getX() >> 4, oldCore.getZ() >> 4);
        if (level.getBlockState(oldCore).is(ModShipBlocks.SONAR.get())) {
            if (level.getBlockEntity(oldCore) instanceof ShipCoreBlockEntity existing) {
                if (existing.state() == null) {
                    existing.rescan(level);
                }
                return existing;
            }
        }
        BlockPos legacyCore = legacyCorePos();
        level.getChunk(legacyCore.getX() >> 4, legacyCore.getZ() >> 4);
        if (level.getBlockState(legacyCore).is(ModShipBlocks.SONAR.get())) {
            if (level.getBlockEntity(legacyCore) instanceof ShipCoreBlockEntity existing) {
                if (existing.state() == null) {
                    existing.rescan(level);
                }
                return existing;
            }
        }
        // 声纳没了但船体还在（被打了、或者核心被拆走）——**不要重建**，
        // 否则同一片海面上会出现两艘叠在一起的初始潜艇
        if (hasRemainingHull(level, ORIGIN_Y) || hasRemainingHull(level, OLD_ORIGIN_Y)
                || hasRemainingHull(level, LEGACY_ORIGIN_Y)) {
            BarotraumaMod.LOGGER.warn("[Barotrauma] {} 处没有声纳台，但船体还在，跳过重建", corePos);
            return null;
        }

        // 船壳用不透明装甲板：物理船是连续坐标渲染、排水体是整格方块，
        // 船 y 不是整数时两者在船壳处差半格——透明船壳会把"半个方块"的水/缺口暴露出来。
        // 飞机舷窗用玻璃（HULL_GLASS），既能看到外面，又不会把整艘船做成透明壳。
        BlockState hull = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState glass = ModShipBlocks.HULL_GLASS.get().defaultBlockState();

        for (int dx = 0; dx < SIZE_X; dx++) {
            for (int dy = 0; dy < SIZE_Y; dy++) {
                for (int dz = 0; dz < SIZE_Z; dz++) {
                    BlockPos pos = new BlockPos(ORIGIN_X + dx, ORIGIN_Y + dy, ORIGIN_Z + dz);
                    boolean shell = dx == 0 || dx == SIZE_X - 1
                            || dy == 0 || dy == SIZE_Y - 1
                            || dz == 0 || dz == SIZE_Z - 1;
                    if (!shell) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        continue;
                    }
                    // 顶部舷窗（dx 3..5, dz 4..6）
                    boolean window = dy == SIZE_Y - 1 && dx >= 3 && dx <= 5 && dz >= 4 && dz <= 6;
                    level.setBlock(pos, window ? glass : hull, 2);
                }
            }
        }

        // 顶部 3×1 对接舱口：正对哨站通道，出生时打开
        for (int i = 0; i < 3; i++) {
            level.setBlock(new BlockPos(ORIGIN_X + 3 + i, ORIGIN_Y + SIZE_Y - 1, ORIGIN_Z + 7),
                    DockingBay.subHatchState(i, true), 2);
        }
        // 舱口爬梯井：玩家从舱底爬到顶部对接舱口（铁梯免支撑、有碰撞）
        // 船内梯子按用户要求转 90°：薄板沿 z，东西朝向。
        BlockState ladder = com.opamb.barotrauma.registry.ModBlocks.IRON_LADDER.get()
                .defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING,
                        net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.LadderBlock.WATERLOGGED, Boolean.FALSE);
        for (int y = ORIGIN_Y + 1; y <= ORIGIN_Y + SIZE_Y - 2; y++) {
            level.setBlock(new BlockPos(ORIGIN_X + 4, y, ORIGIN_Z + 7), ladder, 2);
        }

        // 声纳台就是船脑（机器/舱门已按用户要求全部删除，船上只留船壳和声纳）
        level.setBlock(new BlockPos(ORIGIN_X + 5, ORIGIN_Y + 1, ORIGIN_Z + 4),
                ModShipBlocks.SONAR.get().defaultBlockState(), 2);
        // 排水泵：普通泵只留在压载舱（船尾）
        level.setBlock(new BlockPos(ORIGIN_X + 4, ORIGIN_Y + 1, ORIGIN_Z + 2),
                com.opamb.barotrauma.registry.ModBlocks.DRAIN_PUMP.get().defaultBlockState(), 2);
        // 小型泵/排水口铺在船舱前后段
        level.setBlock(new BlockPos(ORIGIN_X + 6, ORIGIN_Y + 1, ORIGIN_Z + 11),
                com.opamb.barotrauma.registry.ModBlocks.SMALL_PUMP.get().defaultBlockState(), 2);
        level.setBlock(new BlockPos(ORIGIN_X + 2, ORIGIN_Y + 1, ORIGIN_Z + 11),
                com.opamb.barotrauma.registry.ModBlocks.DRAIN_OUTLET.get().defaultBlockState(), 2);
        level.setBlock(new BlockPos(ORIGIN_X + 2, ORIGIN_Y + 1, ORIGIN_Z + 2),
                com.opamb.barotrauma.registry.ModBlocks.DRAIN_OUTLET.get().defaultBlockState(), 2);

        // 声纳台就是船脑（在上面的机器布局里已经放下去了），这里直接拿它的方块实体扫一次
        if (level.getBlockEntity(corePos) instanceof ShipCoreBlockEntity core) {
            core.rescan(level);
            com.opamb.barotrauma.data.ShipSavedData.get(level).markStarterClaimed();
            BarotraumaMod.LOGGER.info("[Barotrauma] 初始潜艇已建造：core={} 范围 {}x{}x{}",
                    corePos, SIZE_X, SIZE_Y, SIZE_Z);
            return core;
        }
        return null;
    }

    /** 玩家出生点：潜艇舱内（对接时从这里进站）。 */
    public static net.minecraft.world.phys.Vec3 spawnPos() {
        return spawnPos(ORIGIN_Y);
    }

    /** 2026-11-07 之前（船在 -76）的出生点。 */
    public static net.minecraft.world.phys.Vec3 oldSpawnPos() {
        return spawnPos(OLD_ORIGIN_Y);
    }

    /** 更老存档（船在 -78）的出生点。 */
    public static net.minecraft.world.phys.Vec3 legacySpawnPos() {
        return spawnPos(LEGACY_ORIGIN_Y);
    }

    private static net.minecraft.world.phys.Vec3 spawnPos(int originY) {
        return new net.minecraft.world.phys.Vec3(0.5D, originY + 1.0D, 4.5D);
    }

    // ------------------------------------------------------------------
    // 旧档上移：把 -76/-78 的整艘方块潜艇搬到 -72，直接顶到中层平台
    // ------------------------------------------------------------------

    private record SavedBlock(BlockPos pos, BlockState state, @Nullable CompoundTag tag) {
    }

    /** 新位置没有核心、旧位置有核心时，把旧潜艇整体上移（只跑一次）。 */
    private static void liftLegacySub(ServerLevel level) {
        if (VsState.active()) {
            return;
        }
        BlockPos target = corePos();
        level.getChunk(target.getX() >> 4, target.getZ() >> 4);
        if (level.getBlockState(target).is(ModShipBlocks.SONAR.get())) {
            return;
        }
        // 上一版（-72）先处理：它把站侧 -68 平台顶掉了，降 1 格后要补回来。
        BlockPos prevCore = new BlockPos(ORIGIN_X + 5, PREV_ORIGIN_Y + 1, ORIGIN_Z + 4);
        level.getChunk(prevCore.getX() >> 4, prevCore.getZ() >> 4);
        if (level.getBlockState(prevCore).is(ModShipBlocks.SONAR.get())) {
            liftSub(level, PREV_ORIGIN_Y);
            DockingBay.restoreMidDeck(level);
            return;
        }
        for (int oldY : new int[]{OLD_ORIGIN_Y, LEGACY_ORIGIN_Y}) {
            BlockPos oldCore = new BlockPos(ORIGIN_X + 5, oldY + 1, ORIGIN_Z + 4);
            level.getChunk(oldCore.getX() >> 4, oldCore.getZ() >> 4);
            if (level.getBlockState(oldCore).is(ModShipBlocks.SONAR.get())) {
                liftSub(level, oldY);
                return;
            }
        }
    }

    /** 快照旧船体 → 清空旧位置 → 铺到新位置 → 搬方块实体 NBT → 重扫。 */
    private static void liftSub(ServerLevel level, int oldOriginY) {
        int dy = ORIGIN_Y - oldOriginY;
        List<SavedBlock> saved = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(ORIGIN_X, oldOriginY, ORIGIN_Z),
                new BlockPos(ORIGIN_X + SIZE_X - 1, oldOriginY + SIZE_Y - 1,
                        ORIGIN_Z + SIZE_Z - 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            CompoundTag tag = be == null ? null : be.saveWithoutMetadata();
            saved.add(new SavedBlock(pos.immutable(), state, tag));
        }
        if (saved.isEmpty()) {
            return;
        }
        // 先全部清空旧位置：新旧包围盒在 -72 那一层有重叠，直接边读边写会覆盖。
        for (SavedBlock block : saved) {
            level.setBlock(block.pos(), Blocks.AIR.defaultBlockState(), 2);
        }
        for (SavedBlock block : saved) {
            BlockPos target = block.pos().offset(0, dy, 0);
            level.setBlock(target, block.state(), 3);
            if (block.tag() != null) {
                BlockEntity be = level.getBlockEntity(target);
                if (be != null) {
                    be.load(block.tag());
                    be.setChanged();
                }
            }
        }
        // 新核心的 state 是运行时对象，搬完必须重扫；船壳耐久从 NBT 的 pendingDamage 接回。
        if (level.getBlockEntity(corePos()) instanceof ShipCoreBlockEntity core) {
            core.rescan(level);
        }
        BarotraumaMod.LOGGER.info("[Barotrauma] 旧潜艇已从 y={} 平移到 y={}（{} 格）",
                oldOriginY, ORIGIN_Y, dy);
    }

    /** 初始潜艇的包围盒里还有没有船体方块（用来判断"船还在、只是核心没了"）。 */
    private static boolean hasRemainingHull(ServerLevel level, int originY) {
        for (BlockPos pos : BlockPos.betweenClosed(
                new BlockPos(ORIGIN_X, originY, ORIGIN_Z),
                new BlockPos(ORIGIN_X + SIZE_X - 1, originY + SIZE_Y - 1, ORIGIN_Z + SIZE_Z - 1))) {
            if (ModShipBlocks.isShipBlock(level.getBlockState(pos))
                    && !DockingBay.isStationDockHatch(pos)) {
                return true;
            }
        }
        return false;
    }
}
