package com.opamb.barotrauma.world;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
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

    // ---- 儒艮号（2026-11-08 用户在新存档里搭的替换出生潜艇）----
    /**
     * 新出生潜艇「儒艮号」：模板原始朝向 5 宽 × 9 高 × 17 长（长轴沿 z）。
     *
     * <p>放置时整艘绕 Y 轴顺时针转 90°：长轴改为沿 x，船顶 3×1 舱口才会和哨站
     * -68 中层舱口同向。原朝向的船顶舱口沿 z，和站侧舱口十字交叉，实际只能
     * 过 1×1，用户反馈“对接对歪了”。</p>
     *
     * <p>因此世界包围盒是 17×9×5（x 17、y 9、z 5），x/z 都从 origin 向正方向。</p>
     */
    public static final int NEW_ORIGIN_X = -9;
    public static final int NEW_ORIGIN_Y = -77;
    public static final int NEW_ORIGIN_Z = -2;
    public static final int NEW_SIZE_X = 17;
    public static final int NEW_SIZE_Y = 9;
    public static final int NEW_SIZE_Z = 5;
    /** 相对站点原点的核心偏移（旋转后声纳在船的东端 (6,-72,0)）。 */
    public static final int NEW_CORE_OFFSET_X = 6;
    public static final int NEW_CORE_OFFSET_Y = -72;
    public static final int NEW_CORE_OFFSET_Z = 0;

    /** 儒艮号的声纳/船核心：站下方 (6, -72, 0)。 */
    public static BlockPos newCorePos() {
        return new BlockPos(6, -72, 0);
    }

    /** 副本站点里的儒艮号核心（相对站点原点同一偏移）。 */
    public static BlockPos newCorePos(int siteX, int siteZ) {
        return new BlockPos(siteX + NEW_CORE_OFFSET_X, NEW_CORE_OFFSET_Y,
                siteZ + NEW_CORE_OFFSET_Z);
    }

    /** 这个存档是否已切换到儒艮号出生潜艇布局。 */
    public static boolean newLayout(ServerLevel level) {
        return com.opamb.barotrauma.data.ShipSavedData.get(level).starterLayoutNew();
    }

    /** 儒艮号床边出生点（床在 x -8..-7、z -1..1，站在 x -6 的过道）。 */
    public static net.minecraft.world.phys.Vec3 newSpawnPos() {
        return new net.minecraft.world.phys.Vec3(-5.5D, -72.0D, 0.5D);
    }

    /** 这艘儒艮号是否已经在世界里（或已经被收成物理船）。 */
    public static boolean newStarterActive(ServerLevel level) {
        if (!newLayout(level)) {
            return false;
        }
        BlockPos core = newCorePos();
        level.getChunk(core.getX() >> 4, core.getZ() >> 4);
        if (level.getBlockState(core).is(ModShipBlocks.SONAR.get())) {
            return true;
        }
        return VsState.active() && core.equals(VsState.corePos());
    }

    /** 有儒艮号时出生在它床边，否则沿用旧出生潜艇。 */
    public static net.minecraft.world.phys.Vec3 spawnPos(ServerLevel level) {
        return newStarterActive(level) ? newSpawnPos() : spawnPos();
    }

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
        // 用户在新存档里搭的儒艮号：已经在世界里（或已收成物理船）就优先认它，
        // 不再走旧出生潜艇的建造/认领逻辑；旧船在码头里保留，不主动删除。
        if (newStarterActive(level)) {
            BlockPos newCore = newCorePos();
            level.getChunk(newCore.getX() >> 4, newCore.getZ() >> 4);
            if (level.getBlockEntity(newCore) instanceof ShipCoreBlockEntity core) {
                if (core.state() == null) {
                    core.rescan(level);
                }
                return core;
            }
            BarotraumaMod.LOGGER.info("[Barotrauma] 儒艮号已收成物理船，跳过旧出生潜艇重建");
            return null;
        }
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

        return placeNewStarter(level);
    }

    /**
     * 新世界：把用户搭的儒艮号模板放到站正下方，作为出生潜艇。
     * 模板 = 原始 5×9×17（长轴沿 z），放置时整艘顺时针转 90°，
     * 船顶 3×1 舱口（局部 z=7）正好落在站点正下方，和 -68 中层舱口对齐。
     */
    @Nullable
    private static ShipCoreBlockEntity placeNewStarter(ServerLevel level) {
        StructureTemplateManager manager = level.getStructureManager();
        var template = manager.get(new ResourceLocation(BarotraumaMod.MOD_ID, "starter_sub"));
        if (template.isEmpty()) {
            BarotraumaMod.LOGGER.error("[Barotrauma] 找不到儒艮号结构模板 barotrauma:starter_sub");
            return null;
        }
        BlockPos origin = new BlockPos(NEW_ORIGIN_X, NEW_ORIGIN_Y, NEW_ORIGIN_Z);
        level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
        // 模板只存了非空气方块；先把整块体积清成空气，否则站外的水/岩石会灌进船内。
        for (BlockPos pos : BlockPos.betweenClosed(origin,
                origin.offset(NEW_SIZE_X - 1, NEW_SIZE_Y - 1, NEW_SIZE_Z - 1))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.CLOCKWISE_90);
        settings.setIgnoreEntities(false);
        // 旋转以 (0,0,0) 为轴心：局部 (x,z) → 放置点 + (-z, x)。模板长 17，
        // 把放置点放在包围盒 +x 边，船顶舱口（局部 z=7）才会落在 site.x 正下方。
        BlockPos placePos = new BlockPos(origin.getX() + NEW_SIZE_X - 1, origin.getY(), origin.getZ());
        template.get().placeInWorld(level, placePos, placePos, settings, level.random, 3);

        com.opamb.barotrauma.data.ShipSavedData data =
                com.opamb.barotrauma.data.ShipSavedData.get(level);
        data.markStarterLayoutNew();
        data.markStarterClaimed();
        BlockPos corePos = newCorePos();
        level.getChunk(corePos.getX() >> 4, corePos.getZ() >> 4);
        if (level.getBlockEntity(corePos) instanceof ShipCoreBlockEntity core) {
            core.rescan(level);
            BarotraumaMod.LOGGER.info("[Barotrauma] 儒艮号已生成：core={} 范围 {}x{}x{}",
                    corePos, NEW_SIZE_X, NEW_SIZE_Y, NEW_SIZE_Z);
            return core;
        }
        BarotraumaMod.LOGGER.error("[Barotrauma] 儒艮号模板已放置，但核心不在 {}", corePos);
        return null;
    }

    /** 玩家出生点：潜艇舱内（对接时从这里进站）。 */
    public static net.minecraft.world.phys.Vec3 spawnPos() {        return spawnPos(ORIGIN_Y);
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
