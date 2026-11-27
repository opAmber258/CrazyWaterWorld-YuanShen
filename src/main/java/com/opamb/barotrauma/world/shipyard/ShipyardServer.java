package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipGeometry;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 船厂编辑包的服务端校验与落方块。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID)
public final class ShipyardServer {

    public static final int PLOT_RADIUS = ModDimensions.SHIPYARD_PLOT_RADIUS;
    private static final int UNDO_LIMIT = 64;
    /** 撤销历史同时限制总格数，避免一次 4096 格填充把整船历史挤掉。 */
    private static final int UNDO_CELL_LIMIT = 8192;
    private static final Map<UUID, Deque<Batch>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<Batch>> REDO = new HashMap<>();
    private static final Map<UUID, PendingValidation> PENDING = new HashMap<>();

    private record PendingValidation(ServerLevel level, ShipyardSavedData.Plot plot, int ticks) {
    }

    /** 一次用户操作（单块、填充、状态恢复）对应一个撤销单元。 */
    private record Batch(List<Change> changes) {
    }

    private record Change(BlockPos pos, BlockState oldState, CompoundTag oldBe,
                          BlockState newState, CompoundTag newBe) {
    }

    private ShipyardServer() {
    }

    public static void edit(ServerPlayer player, byte action, BlockPos pos, CompoundTag stateTag) {
        edit(player, action, pos, stateTag, ShipyardShapes.MIRROR_NONE, 0);
    }

    public static void edit(ServerPlayer player, byte action, BlockPos pos, CompoundTag stateTag,
                            byte mirrorAxis, int mirrorCoord) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            reject(player, "not allowed");
            return;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (action == 3) {
            undo(level, player);
            return;
        }
        if (action == 4) {
            redo(level, player);
            return;
        }
        if (plot == null || !insidePlot(plot, pos)) {
            reject(player, "outside plot " + pos.toShortString());
            return;
        }
        if (action == 1) {
            BlockState state;
            try {
                state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
            } catch (RuntimeException e) {
                reject(player, "bad block state");
                return;
            }
            if (!allowed(state)) {
                reject(player, "block not allowed: " + state);
                return;
            }
            BlockState oldState = level.getBlockState(pos);
            if (!oldState.canBeReplaced()) {
                reject(player, "target not replaceable: " + pos.toShortString());
                return;
            }
            List<Change> placed = placeWithParts(level, player, plot, pos, state);
            if (placed == null) {
                reject(player, "placement overlaps or leaves plot: " + pos.toShortString());
                return;
            }
            List<Change> allChanges = new ArrayList<>(placed);
            if (mirrorAxis == ShipyardShapes.MIRROR_X || mirrorAxis == ShipyardShapes.MIRROR_Z) {
                BlockPos mPos = ShipyardShapes.mirrorPos(pos, mirrorAxis, mirrorCoord);
                if (!mPos.equals(pos) && insidePlot(plot, mPos)
                        && level.getBlockState(mPos).canBeReplaced()) {
                    BlockState mState = ShipyardShapes.mirrorState(state, mirrorAxis);
                    List<Change> mirrored = placeWithParts(level, player, plot, mPos, mState);
                    if (mirrored != null) {
                        allChanges.addAll(mirrored);
                    }
                }
            }
            push(player, allChanges);
            BarotraumaMod.LOGGER.info("[shipyard] place {} at {}", state, pos.toShortString());
            trackChanges(level, plot, allChanges);
        } else if (action == 2) {
            BlockState current = level.getBlockState(pos);
            if (!ModShipBlocks.isShipBlock(current) && !ModShipBlocks.isEquipment(current)) {
                reject(player, "not a ship block: " + current);
                return;
            }
            Set<BlockPos> targets = new LinkedHashSet<>();
            collectRemovalTargets(level, pos, current, targets);
            if (mirrorAxis == ShipyardShapes.MIRROR_X || mirrorAxis == ShipyardShapes.MIRROR_Z) {
                BlockPos mPos = ShipyardShapes.mirrorPos(pos, mirrorAxis, mirrorCoord);
                if (!mPos.equals(pos) && insidePlot(plot, mPos)) {
                    BlockState mCurrent = level.getBlockState(mPos);
                    if (ModShipBlocks.isShipBlock(mCurrent) || ModShipBlocks.isEquipment(mCurrent)) {
                        collectRemovalTargets(level, mPos, mCurrent, targets);
                    }
                }
            }
            List<Change> removed = removeTargets(level, targets);
            if (removed.isEmpty()) {
                return;
            }
            push(player, removed);
            trackChanges(level, plot, removed);
            BarotraumaMod.LOGGER.info("[shipyard] remove at {}", pos.toShortString());
        } else {
            return;
        }
        // 大船的核心可能离编辑点超过 64 格；trackChanges 已按实际变化范围重扫。
        data.setDirty(player.getUUID(), true);
        data.setDirty();
        schedule(player, level, plot);
    }

    private static void collectRemovalTargets(ServerLevel level, BlockPos pos, BlockState current,
                                              Set<BlockPos> out) {
        if (hasPartProperty(current)) {
            out.addAll(partGroup(level, pos, current.getBlock()));
        } else {
            out.add(pos.immutable());
        }
    }

    private static List<Change> removeTargets(ServerLevel level, Set<BlockPos> targets) {
        List<Change> removed = new ArrayList<>();
        for (BlockPos target : targets) {
            BlockState old = level.getBlockState(target);
            if (old.isAir()) {
                continue;
            }
            CompoundTag oldBe = blockEntityTag(level, target);
            level.removeBlock(target, false);
            removed.add(new Change(target.immutable(), old, oldBe,
                    Blocks.AIR.defaultBlockState(), null));
        }
        return removed;
    }

    public static boolean insidePlot(ShipyardSavedData.Plot plot, BlockPos pos) {
        return Math.abs(pos.getX() - plot.originX()) <= PLOT_RADIUS
                && Math.abs(pos.getZ() - plot.originZ()) <= PLOT_RADIUS
                && pos.getY() >= plot.originY() - 8
                && pos.getY() <= plot.originY() + PLOT_RADIUS;
    }

    public static ShipGeometry.Bounds plotBounds(ShipyardSavedData.Plot plot) {
        return new ShipGeometry.Bounds(
                plot.originX() - PLOT_RADIUS, plot.originY() - 8, plot.originZ() - PLOT_RADIUS,
                plot.originX() + PLOT_RADIUS, plot.originY() + PLOT_RADIUS, plot.originZ() + PLOT_RADIUS);
    }

    public static void fill(ServerPlayer player, BlockPos min, BlockPos max, CompoundTag stateTag) {
        fill(player, min, max, ShipyardShapes.MODE_FILL_SOLID, ShipyardShapes.MIRROR_NONE, 0, stateTag);
    }

    public static void fill(ServerPlayer player, BlockPos a, BlockPos b, byte mode,
                            byte mirrorAxis, int mirrorCoord, CompoundTag stateTag) {
        fill(player, a, b, mode, mirrorAxis, mirrorCoord, stateTag, new CompoundTag());
    }

    public static void fill(ServerPlayer player, BlockPos a, BlockPos b, byte mode,
                            byte mirrorAxis, int mirrorCoord,
                            CompoundTag stateTag, CompoundTag filterTag) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            reject(player, "not allowed");
            return;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null || !insidePlot(plot, a) || !insidePlot(plot, b)) {
            reject(player, "fill outside plot");
            return;
        }
        long estimate = ShipyardShapes.estimateCount(a, b, mode);
        if (estimate <= 0 || (mode != ShipyardShapes.MODE_ELLIPSOID && estimate > ShipyardShapes.MAX_VOLUME)) {
            reject(player, "fill volume " + estimate + " (max " + ShipyardShapes.MAX_VOLUME + ")");
            return;
        }
        List<BlockPos> cells = ShipyardShapes.collectCells(a, b, mode, ShipyardShapes.MAX_VOLUME);
        if (cells.isEmpty()) {
            if (mode == ShipyardShapes.MODE_ELLIPSOID) {
                reject(player, "ellipsoid shell exceeds " + ShipyardShapes.MAX_VOLUME + " blocks");
            }
            return;
        }
        boolean hasMirror = mirrorAxis == ShipyardShapes.MIRROR_X
                || mirrorAxis == ShipyardShapes.MIRROR_Z;

        if (mode == ShipyardShapes.MODE_CLEAR) {
            Set<BlockPos> targets = new LinkedHashSet<>();
            for (BlockPos cell : cells) {
                if (insidePlot(plot, cell)) {
                    BlockState current = level.getBlockState(cell);
                    if (ModShipBlocks.isShipBlock(current) || ModShipBlocks.isEquipment(current)) {
                        collectRemovalTargets(level, cell, current, targets);
                    }
                }
                if (hasMirror) {
                    BlockPos mPos = ShipyardShapes.mirrorPos(cell, mirrorAxis, mirrorCoord);
                    if (!mPos.equals(cell) && insidePlot(plot, mPos)) {
                        BlockState mState = level.getBlockState(mPos);
                        if (ModShipBlocks.isShipBlock(mState) || ModShipBlocks.isEquipment(mState)) {
                            collectRemovalTargets(level, mPos, mState, targets);
                        }
                    }
                }
            }
            List<Change> removed = removeTargets(level, targets);
            if (removed.isEmpty()) {
                return;
            }
            push(player, removed);
            trackChanges(level, plot, removed);
            data.setDirty(player.getUUID(), true);
            data.setDirty();
            schedule(player, level, plot);
            BarotraumaMod.LOGGER.info("[shipyard] clear {} blocks {} -> {}", removed.size(),
                    a.toShortString(), b.toShortString());
            return;
        }

        BlockState state;
        try {
            state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), stateTag);
        } catch (RuntimeException e) {
            reject(player, "bad block state");
            return;
        }
        if (!allowed(state)) {
            reject(player, "block not allowed: " + state);
            return;
        }
        // 多方块设备每个主格会额外写 10+ 个副段；大批填充既卡服又难以校验。
        if (hasPartProperty(state) && cells.size() > 64) {
            reject(player, "multipart fill volume " + cells.size() + " (max 64)");
            return;
        }
        if (mode == ShipyardShapes.MODE_REPLACE) {
            if (hasPartProperty(state)) {
                reject(player, "cannot batch-replace with multipart block");
                return;
            }
            Block filterBlock = null;
            if (filterTag != null && !filterTag.isEmpty()) {
                try {
                    BlockState filterState = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), filterTag);
                    if (!filterState.isAir()) {
                        filterBlock = filterState.getBlock();
                    }
                } catch (RuntimeException ignored) {
                }
            }
            Map<BlockPos, BlockState> replacePlan = new LinkedHashMap<>();
            for (BlockPos cell : cells) {
                if (insidePlot(plot, cell)) {
                    replacePlan.put(cell, state);
                }
            }
            if (hasMirror) {
                BlockState mirroredState = ShipyardShapes.mirrorState(state, mirrorAxis);
                for (BlockPos cell : cells) {
                    BlockPos mCell = ShipyardShapes.mirrorPos(cell, mirrorAxis, mirrorCoord);
                    if (!replacePlan.containsKey(mCell) && insidePlot(plot, mCell)) {
                        replacePlan.put(mCell, mirroredState);
                    }
                }
            }
            List<Change> changes = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> entry : replacePlan.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState old = level.getBlockState(pos);
                if (old.isAir() || hasPartProperty(old)
                        || (!ModShipBlocks.isShipBlock(old) && !ModShipBlocks.isEquipment(old))) {
                    continue;
                }
                if (filterBlock != null && !old.is(filterBlock)) {
                    continue;
                }
                BlockState next = entry.getValue();
                if (old == next) {
                    continue;
                }
                CompoundTag oldBe = blockEntityTag(level, pos);
                level.setBlockAndUpdate(pos, next);
                changes.add(new Change(pos.immutable(), old, oldBe, next, blockEntityTag(level, pos)));
            }
            if (changes.isEmpty()) {
                return;
            }
            push(player, changes);
            trackChanges(level, plot, changes);
            data.setDirty(player.getUUID(), true);
            data.setDirty();
            schedule(player, level, plot);
            BarotraumaMod.LOGGER.info("[shipyard] replace {} blocks {} -> {}", changes.size(),
                    a.toShortString(), b.toShortString());
            return;
        }
        Map<BlockPos, BlockState> planned = new LinkedHashMap<>();
        for (BlockPos cell : cells) {
            if (insidePlot(plot, cell)) {
                planned.put(cell, state);
            }
        }
        if (hasMirror) {
            BlockState mirroredState = ShipyardShapes.mirrorState(state, mirrorAxis);
            for (BlockPos cell : cells) {
                BlockPos mCell = ShipyardShapes.mirrorPos(cell, mirrorAxis, mirrorCoord);
                if (!planned.containsKey(mCell) && insidePlot(plot, mCell)) {
                    planned.put(mCell, mirroredState);
                }
            }
        }
        int placed = 0;
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.getBlockState(pos).canBeReplaced()) {
                continue;
            }
            List<Change> cellChanges = placeWithParts(level, player, plot, pos, entry.getValue());
            if (cellChanges == null) {
                continue;
            }
            changes.addAll(cellChanges);
            placed++;
        }
        if (placed == 0) {
            return;
        }
        // 整个 fill 是一个撤销单元，而不是每格一条历史。
        push(player, changes);
        trackChanges(level, plot, changes);
        data.setDirty(player.getUUID(), true);
        data.setDirty();
        schedule(player, level, plot);
        BarotraumaMod.LOGGER.info("[shipyard] fill(mode={}) {} blocks {} -> {}", mode, placed,
                a.toShortString(), b.toShortString());
    }

    public static void paste(ServerPlayer player, BlockPos anchor,
                             List<com.opamb.barotrauma.network.ShipyardPastePacket.Entry> entries,
                             byte mirrorAxis, int mirrorCoord) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            reject(player, "not allowed");
            return;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null || entries == null || entries.isEmpty()) {
            return;
        }
        if (entries.size() > ShipyardShapes.MAX_VOLUME) {
            reject(player, "paste volume " + entries.size() + " (max " + ShipyardShapes.MAX_VOLUME + ")");
            return;
        }
        boolean hasMirror = mirrorAxis == ShipyardShapes.MIRROR_X
                || mirrorAxis == ShipyardShapes.MIRROR_Z;
        var lookup = level.holderLookup(Registries.BLOCK);
        Map<BlockPos, BlockState> planned = new LinkedHashMap<>();
        for (var entry : entries) {
            BlockState state;
            try {
                state = NbtUtils.readBlockState(lookup, entry.state());
            } catch (RuntimeException e) {
                continue;
            }
            if (!allowed(state)) {
                continue;
            }
            BlockPos pos = anchor.offset(entry.rel()).immutable();
            if (insidePlot(plot, pos)) {
                planned.put(pos, state);
            }
        }
        if (hasMirror) {
            Map<BlockPos, BlockState> mirrored = new LinkedHashMap<>();
            for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
                BlockPos mPos = ShipyardShapes.mirrorPos(entry.getKey(), mirrorAxis, mirrorCoord);
                if (!planned.containsKey(mPos) && insidePlot(plot, mPos)) {
                    mirrored.put(mPos, ShipyardShapes.mirrorState(entry.getValue(), mirrorAxis));
                }
            }
            planned.putAll(mirrored);
        }
        int placed = 0;
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.getBlockState(pos).canBeReplaced()) {
                continue;
            }
            List<Change> cellChanges = placeWithParts(level, player, plot, pos, entry.getValue());
            if (cellChanges == null) {
                continue;
            }
            changes.addAll(cellChanges);
            placed++;
        }
        if (placed == 0) {
            return;
        }
        push(player, changes);
        trackChanges(level, plot, changes);
        data.setDirty(player.getUUID(), true);
        data.setDirty();
        schedule(player, level, plot);
        BarotraumaMod.LOGGER.info("[shipyard] paste {} blocks at {}", placed, anchor.toShortString());
    }

    /** 把一组方块变化的实际范围并入地块/核心扫描范围（含多方块副段）。 */
    private static void trackChanges(ServerLevel level, ShipyardSavedData.Plot plot,
                                     List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Change change : changes) {
            BlockPos pos = change.pos();
            plot.expand(pos);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        refreshCore(level, plot, new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static boolean allowed(BlockState state) {
        return !state.isAir() && (ModShipBlocks.isShipBlock(state) || ModShipBlocks.isEquipment(state));
    }

    private static boolean hasPartProperty(BlockState state) {
        for (var property : state.getProperties()) {
            if ("part".equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 拆任意一段时整台设备一起拆，避免留下不可见的副段。 */
    private static Set<BlockPos> partGroup(ServerLevel level, BlockPos start, Block block) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.contains(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.is(block) && hasPartProperty(state)
                        && start.distManhattan(next) <= 12) {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        return seen;
    }

    /**
     * 放置主方块并捕获 setPlacedBy 写出的所有多方块副段。
     *
     * <p>副段也必须落在自己地块内、不能覆盖不可替换的方块，并且全部进入同一个
     * 撤销单元；否则拆掉主块后副段会永久残留。</p>
     */
    private static List<Change> placeWithParts(ServerLevel level, ServerPlayer player,
                                                ShipyardSavedData.Plot plot, BlockPos pos,
                                                BlockState state) {
        int radius = hasPartProperty(state) ? 4 : 0;
        if (!level.getBlockState(pos).canBeReplaced()) {
            return null;
        }
        Map<BlockPos, BlockState> before = new HashMap<>();
        Map<BlockPos, CompoundTag> beforeBe = new HashMap<>();
        for (BlockPos cursor : BlockPos.betweenClosed(
                pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            BlockPos immutable = cursor.immutable();
            before.put(immutable, level.getBlockState(immutable));
            CompoundTag tag = blockEntityTag(level, immutable);
            if (tag != null) {
                beforeBe.put(immutable, tag);
            }
        }
        level.setBlockAndUpdate(pos, state);
        Block block = state.getBlock();
        block.setPlacedBy(level, pos, state, player, new ItemStack(block.asItem()));
        List<Change> changes = new java.util.ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockPos changedPos = entry.getKey();
            BlockState old = entry.getValue();
            BlockState now = level.getBlockState(changedPos);
            CompoundTag oldBe = beforeBe.get(changedPos);
            CompoundTag nowBe = blockEntityTag(level, changedPos);
            if (old == now && java.util.Objects.equals(oldBe, nowBe)) {
                continue;
            }
            boolean main = changedPos.equals(pos);
            if (!insidePlot(plot, changedPos) || (!main && !old.canBeReplaced())) {
                restore(level, before, beforeBe);
                return null;
            }
            changes.add(new Change(changedPos, old, oldBe, now, nowBe));
        }
        return changes;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> states,
                                Map<BlockPos, CompoundTag> tags) {
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (state.isAir()) {
                level.removeBlock(pos, false);
            } else {
                level.setBlockAndUpdate(pos, state);
                CompoundTag tag = tags.get(pos);
                BlockEntity be = level.getBlockEntity(pos);
                if (tag != null && be != null) {
                    be.load(tag);
                    be.setChanged();
                }
            }
        }
    }

    public static void schedule(ServerPlayer player, ServerLevel level, ShipyardSavedData.Plot plot) {
        int area = Math.max(1, plot.maxX - plot.minX + 1) * Math.max(1, plot.maxZ - plot.minZ + 1);
        int delay = area > 2500 ? 16 : 10;
        PENDING.put(player.getUUID(), new PendingValidation(level, plot, delay));
    }

    /** 登出时清掉会话级历史，避免换存档后旧坐标的撤销作用到新世界。 */
    public static void clearPlayer(UUID player) {
        UNDO.remove(player);
        REDO.remove(player);
        PENDING.remove(player);
    }

    public static void clearAllSessions() {
        UNDO.clear();
        REDO.clear();
        PENDING.clear();
        ShipyardTestService.clearPendingCleanups();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAllSessions();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ShipyardTestService.tickCleanup();
        if (PENDING.isEmpty()) {
            return;
        }
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingValidation pending = entry.getValue();
            if (pending.ticks() > 1) {
                entry.setValue(new PendingValidation(pending.level(), pending.plot(), pending.ticks() - 1));
                continue;
            }
            iterator.remove();
            ServerPlayer player = pending.level().getServer() == null ? null
                    : pending.level().getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                    com.opamb.barotrauma.network.ShipyardStatusPacket.of(
                            ShipyardValidator.validate(pending.level(), pending.plot())));
        }
    }

    private static CompoundTag blockEntityTag(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be == null ? null : be.saveWithoutMetadata();
    }

    private static void push(ServerPlayer player, List<Change> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Deque<Batch> undo = UNDO.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        undo.addFirst(new Batch(List.copyOf(changes)));
        while (undo.size() > UNDO_LIMIT || cellCount(undo) > UNDO_CELL_LIMIT) {
            undo.removeLast();
        }
        REDO.remove(player.getUUID());
    }

    private static int cellCount(Deque<Batch> batches) {
        int count = 0;
        for (Batch batch : batches) {
            count += batch.changes().size();
        }
        return count;
    }

    private static void undo(ServerLevel level, ServerPlayer player) {
        Deque<Batch> undo = UNDO.get(player.getUUID());
        if (undo == null || undo.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("没有可撤销的编辑"), true);
            return;
        }
        Batch batch = undo.removeFirst();
        List<Change> changes = batch.changes();
        for (int i = changes.size() - 1; i >= 0; i--) {
            apply(level, changes.get(i), false);
        }
        REDO.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>()).addFirst(batch);
        refresh(player, level, changes);
    }

    private static void redo(ServerLevel level, ServerPlayer player) {
        Deque<Batch> redo = REDO.get(player.getUUID());
        if (redo == null || redo.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("没有可重做的编辑"), true);
            return;
        }
        Batch batch = redo.removeFirst();
        List<Change> changes = batch.changes();
        for (Change change : changes) {
            apply(level, change, true);
        }
        Deque<Batch> undo = UNDO.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
        undo.addFirst(batch);
        while (undo.size() > UNDO_LIMIT || cellCount(undo) > UNDO_CELL_LIMIT) {
            undo.removeLast();
        }
        refresh(player, level, changes);
    }

    private static void apply(ServerLevel level, Change change, boolean forward) {
        BlockState state = forward ? change.newState() : change.oldState();
        CompoundTag tag = forward ? change.newBe() : change.oldBe();
        if (state.isAir()) {
            level.removeBlock(change.pos(), false);
        } else {
            level.setBlockAndUpdate(change.pos(), state);
            if (tag != null) {
                BlockEntity be = level.getBlockEntity(change.pos());
                if (be != null) {
                    be.load(tag);
                    be.setChanged();
                }
            }
        }
    }

    private static void refresh(ServerPlayer player, ServerLevel level, List<Change> changes) {
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot != null) {
            trackChanges(level, plot, changes);
            data.setDirty(player.getUUID(), true);
            data.setDirty();
            schedule(player, level, plot);
        }
    }

    /**
     * 把编辑点并入最近核心的扫描范围后再重扫。
     *
     * <p>不能把整个 plot bounds 写进核心：plot bounds 只增不减，一次远距离误点
     * 会让之后每次编辑都按历史最大包围盒全量扫描。这里的范围只随真实编辑增长。</p>
     */
    public static void refreshCore(ServerLevel level, ShipyardSavedData.Plot plot, BlockPos pos) {
        refreshCore(level, plot, pos, pos);
    }

    /** 用编辑影响范围（多方块副段/批量填充）一次性扩展核心扫描范围后重扫。 */
    public static void refreshCore(ServerLevel level, ShipyardSavedData.Plot plot,
                                   BlockPos minPos, BlockPos maxPos) {
        List<ShipCoreBlockEntity> cores = ShipCores.coresNear(level, minPos, 96.0D);
        if (cores.isEmpty()) {
            cores = ShipCores.coresNear(level,
                    new BlockPos(plot.originX(), plot.originY(), plot.originZ()), 256.0D);
        }
        for (ShipCoreBlockEntity core : cores) {
            ShipGeometry.Bounds current = core.scanBounds();
            ShipGeometry.Bounds base = current != null ? current : new ShipGeometry.Bounds(
                    core.getBlockPos().getX() - 24, core.getBlockPos().getY() - 24,
                    core.getBlockPos().getZ() - 24,
                    core.getBlockPos().getX() + 24, core.getBlockPos().getY() + 24,
                    core.getBlockPos().getZ() + 24);
            // 多包一格，保证新方块的相邻船壳也在洪水填充范围内。
            int minX = Math.min(base.minX(), minPos.getX() - 1);
            int minY = Math.min(base.minY(), minPos.getY() - 1);
            int minZ = Math.min(base.minZ(), minPos.getZ() - 1);
            int maxX = Math.max(base.maxX(), maxPos.getX() + 1);
            int maxY = Math.max(base.maxY(), maxPos.getY() + 1);
            int maxZ = Math.max(base.maxZ(), maxPos.getZ() + 1);
            ShipGeometry.Bounds expanded = new ShipGeometry.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
            if (!expanded.equals(current)) {
                core.setScanBounds(expanded);
            }
            core.rescan(level);
        }
    }

    private static void reject(ServerPlayer player, String reason) {
        BarotraumaMod.LOGGER.warn("[shipyard] reject edit for {}: {}", player.getGameProfile().getName(), reason);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "船厂编辑被拒绝：" + reason), true);
    }
}
