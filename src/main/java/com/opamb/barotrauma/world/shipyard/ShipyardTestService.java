package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipyardStatePacket;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipGeometry;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Set;

/**
 * 试航：把建造地块的方块快照拷到 shipyard_test，装配成 VS2 船；返回时拆船并清空测试区。
 *
 * <p>试航只读建造地块，所有损伤/进水/耗材都留在测试副本上；返回不写回。</p>
 */
public final class ShipyardTestService {

    /** 试航船放在水面以下，才能正常受浮力/水压并测试进水。 */
    private static final int TEST_Y = -8;
    private static final int IMMEDIATE_VOLUME_LIMIT = 16384;
    private static final int BATCH_CLEAN_LIMIT = 8192;
    private static final java.util.Queue<CleanTask> CLEAN_QUEUE = new java.util.ArrayDeque<>();

    private ShipyardTestService() {
    }

    public static boolean start(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            player.displayClientMessage(Component.translatable("shipyard.test.only_editor")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        if (data.testOwner() != null) {
            if (VsPilot.hasShip()) {
                player.displayClientMessage(Component.translatable("shipyard.test.busy")
                        .withStyle(ChatFormatting.RED), false);
                return false;
            }
            // 上次异常退出/重启后可能留下“有锁但没船”的脏状态，直接释放。
            data.endTest();
        }
        if (VsPilot.hasShip()) {
            player.displayClientMessage(Component.translatable("shipyard.test.ship_exists")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null) {
            return false;
        }
        ShipyardValidator.Report report = ShipyardValidator.validate(level, plot);
        if (!report.connected()) {
            player.displayClientMessage(Component.translatable("shipyard.test.disconnected")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ServerLevel test = server.getLevel(ModDimensions.SHIPYARD_TEST);
        if (test == null) {
            player.displayClientMessage(Component.translatable("shipyard.test.no_level")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }

        int minX = plot.minX;
        int minZ = plot.minZ;
        int minY = plot.originY();
        int sizeX = Math.max(1, plot.maxX - minX + 1);
        int sizeY = Math.max(1, plot.maxY - minY + 1);
        int sizeZ = Math.max(1, plot.maxZ - minZ + 1);
        if (sizeY > 300) {
            player.displayClientMessage(Component.translatable("shipyard.test.too_tall")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(minX, minY, minZ),
                new Vec3i(sizeX, sizeY, sizeZ), false, Blocks.AIR);
        BlockPos origin = new BlockPos(ModDimensions.SHIPYARD_TEST_ORIGIN_X - sizeX / 2,
                TEST_Y, ModDimensions.SHIPYARD_TEST_ORIGIN_Z - sizeZ / 2);
        flushCleanups();
        // 兼容旧存档：此前生成的试航区块没有水，先在船体周围补一圈海水。
        fillWater(test, origin, sizeX, sizeZ, 12);
        clear(test, new ShipGeometry.Bounds(origin.getX() - 2, origin.getY() - 4, origin.getZ() - 2,
                origin.getX() + sizeX + 1, origin.getY() + sizeY + 1, origin.getZ() + sizeZ + 1));
        template.placeInWorld(test, origin, origin, new StructurePlaceSettings(),
                test.random, Block.UPDATE_ALL);

        var cores = ShipCores.coresNear(test, origin.offset(sizeX / 2, sizeY / 2, sizeZ / 2),
                Math.max(96.0D, Math.max(sizeX, sizeZ)));
        if (cores.isEmpty()) {
            clear(test, new ShipGeometry.Bounds(origin.getX() - 2, origin.getY() - 4, origin.getZ() - 2,
                    origin.getX() + sizeX + 1, origin.getY() + sizeY + 1, origin.getZ() + sizeZ + 1));
            player.displayClientMessage(Component.translatable("shipyard.test.no_core")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        var core = cores.get(0);
        ShipGeometry.Bounds testBounds = new ShipGeometry.Bounds(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + sizeX - 1, origin.getY() + sizeY - 1, origin.getZ() + sizeZ - 1);
        String result;
        try {
            core.setScanBounds(testBounds);
            ShipState state = core.rescan(test);
            Set<BlockPos> blocks = state.collectBlocks(test);
            result = VsPilot.launch(test, blocks, state, core.getBlockPos());
        } catch (RuntimeException e) {
            // 装配中途异常时尽量拆掉半成品，避免单船锁和残留方块。
            if (VsPilot.hasShip()) {
                VsPilot.dock(test, true);
            }
            clear(test, new ShipGeometry.Bounds(origin.getX() - 2, origin.getY() - 4, origin.getZ() - 2,
                    origin.getX() + sizeX + 1, origin.getY() + sizeY + 1, origin.getZ() + sizeZ + 1));
            player.displayClientMessage(Component.translatable("shipyard.test.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (VsPilot.hasShip()) {
            data.beginTest(player.getUUID(), origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + sizeX - 1, origin.getY() + sizeY - 1, origin.getZ() + sizeZ - 1);
            BlockPos spawn = core.getBlockPos().above(3);
            player.teleportTo(test, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("shipyard.test.started", result), false);
            return true;
        }
        clear(test, new ShipGeometry.Bounds(origin.getX() - 2, origin.getY() - 4, origin.getZ() - 2,
                origin.getX() + sizeX + 1, origin.getY() + sizeY + 1, origin.getZ() + sizeZ + 1));
        player.displayClientMessage(Component.translatable("shipyard.test.failed", result)
                .withStyle(ChatFormatting.RED), false);
        return false;
    }

    public static boolean stop(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel shipyard = server.getLevel(ModDimensions.SHIPYARD);
        ServerLevel test = server.getLevel(ModDimensions.SHIPYARD_TEST);
        if (shipyard == null || test == null) {
            player.displayClientMessage(Component.translatable("shipyard.test.no_level")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        ShipyardSavedData data = ShipyardSavedData.get(shipyard);
        if (data.testOwner() == null || !data.testOwner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("shipyard.test.not_owner")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        // dock 之前记下船当前的位置/尺寸：船可能已经驶离起点很远。
        var shipState = VsPilot.state();
        var shipPos = VsPilot.shipPosition();
        int shipRadius = 64;
        if (shipState != null && shipState.scanBounds() != null) {
            var bounds = shipState.scanBounds();
            shipRadius = Math.min(160, Math.max(bounds.sizeX(),
                    Math.max(bounds.sizeY(), bounds.sizeZ())) / 2 + 16);
        }
        if (VsPilot.hasShip()) {
            String result = VsPilot.dock(test, true);
            player.displayClientMessage(Component.literal(result).withStyle(ChatFormatting.GRAY), false);
            if (VsPilot.hasShip()) {
                // 拆船失败时保留 testOwner，否则之后 start 会报 ship_exists、stop 又会报 not_owner。
                player.displayClientMessage(Component.translatable("shipyard.test.stop_failed")
                        .withStyle(ChatFormatting.RED), false);
                return false;
            }
        }
        // 起点区域 + 船最后所在区域都清掉，覆盖漂移/沉底后的残骸。
        clear(test, new ShipGeometry.Bounds(data.testOriginX() - 96, data.testOriginY() - 128,
                data.testOriginZ() - 96, data.testMaxX() + 96, data.testMaxY() + 96,
                data.testMaxZ() + 96));
        if (shipPos != null) {
            int cx = (int) Math.floor(shipPos.x);
            int cy = (int) Math.floor(shipPos.y);
            int cz = (int) Math.floor(shipPos.z);
            clear(test, new ShipGeometry.Bounds(cx - shipRadius, cy - shipRadius, cz - shipRadius,
                    cx + shipRadius, cy + shipRadius, cz + shipRadius));
        }
        // 测试维度只应该存在测试船和玩家；实体/掉落物/其他玩家都不应泄漏到下次试航。
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        BlockPos center = plot == null ? new BlockPos(ModDimensions.SHIPYARD_TEST_ORIGIN_X, test.getMinBuildHeight() + 8,
                ModDimensions.SHIPYARD_TEST_ORIGIN_Z) : ShipyardPlot.center(plot);
        java.util.List<net.minecraft.world.entity.Entity> entities = new java.util.ArrayList<>();
        test.getAllEntities().forEach(entities::add);
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (entity instanceof ServerPlayer other) {
                other.teleportTo(shipyard, center.getX() + 0.5D, center.getY() + 2.0D,
                        center.getZ() + 0.5D, 0.0F, 15.0F);
            } else {
                entity.discard();
            }
        }
        data.endTest();
        if (plot != null) {
            player.teleportTo(shipyard, center.getX() + 0.5D, center.getY() + 2.0D,
                    center.getZ() + 0.5D, 0.0F, 15.0F);
            ModNetwork.sendToPlayer(player, ShipyardStatePacket.of(plot));
        }
        player.displayClientMessage(Component.translatable("shipyard.test.stopped"), false);
        return true;
    }

    private static void fillWater(ServerLevel level, BlockPos origin, int sizeX, int sizeZ, int margin) {
        BlockState water = Blocks.WATER.defaultBlockState();
        int bottom = Math.max(level.getMinBuildHeight() + 1, origin.getY() - 8);
        int top = ModDimensions.SHIPYARD_TEST_WATER_TOP_Y;
        for (int x = origin.getX() - margin; x < origin.getX() + sizeX + margin; x++) {
            for (int z = origin.getZ() - margin; z < origin.getZ() + sizeZ + margin; z++) {
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, water, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    public static void clearTestArea(MinecraftServer server, ShipyardSavedData data) {
        if (server == null || data == null) {
            return;
        }
        ServerLevel test = server.getLevel(ModDimensions.SHIPYARD_TEST);
        if (test == null) {
            return;
        }
        clear(test, new ShipGeometry.Bounds(data.testOriginX() - 96, data.testOriginY() - 128,
                data.testOriginZ() - 96, data.testMaxX() + 96, data.testMaxY() + 96,
                data.testMaxZ() + 96));
    }

    public static void clearPendingCleanups() {
        CLEAN_QUEUE.clear();
    }

    private static void flushCleanups() {
        BlockState water = Blocks.WATER.defaultBlockState();
        while (!CLEAN_QUEUE.isEmpty()) {
            CleanTask task = CLEAN_QUEUE.poll();
            while (!task.done()) {
                task.step(BATCH_CLEAN_LIMIT, water);
            }
        }
    }

    public static void tickCleanup() {
        int budget = BATCH_CLEAN_LIMIT;
        BlockState water = Blocks.WATER.defaultBlockState();
        while (budget > 0 && !CLEAN_QUEUE.isEmpty()) {
            CleanTask task = CLEAN_QUEUE.peek();
            budget -= task.step(budget, water);
            if (task.done()) {
                CLEAN_QUEUE.poll();
            }
        }
    }

    private static void clear(ServerLevel level, ShipGeometry.Bounds bounds) {
        // 最底层是试航海域的石底，绝不能当成船体清掉。
        int minY = Math.max(bounds.minY(), level.getMinBuildHeight() + 1);
        long volume = Math.max(1L, (long) (bounds.maxX() - bounds.minX() + 1)
                * (bounds.maxY() - minY + 1)
                * (bounds.maxZ() - bounds.minZ() + 1));
        if (volume <= IMMEDIATE_VOLUME_LIMIT) {
            BlockState water = Blocks.WATER.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), minY, bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                clearCell(level, pos, water);
            }
            return;
        }
        CLEAN_QUEUE.add(new CleanTask(level, bounds.minX(), minY, bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }

    private static void clearCell(ServerLevel level, BlockPos pos, BlockState water) {
        BlockState current = level.getBlockState(pos);
        if (current.isAir()
                || current.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            return;
        }
        // 清船时把海面以下的空间补回水，否则每次试航都会在海底留下空气坑。
        BlockState replacement = pos.getY() <= ModDimensions.SHIPYARD_TEST_WATER_TOP_Y
                ? water : Blocks.AIR.defaultBlockState();
        level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
    }

    private static final class CleanTask {
        private final ServerLevel level;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private int x, y, z;
        private boolean done;

        CleanTask(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.level = level;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
            this.done = minX > maxX || minY > maxY || minZ > maxZ;
        }

        int step(int limit, BlockState water) {
            int count = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (!done && count < limit) {
                cursor.set(x, y, z);
                clearCell(level, cursor, water);
                count++;
                if (++x > maxX) {
                    x = minX;
                    if (++z > maxZ) {
                        z = minZ;
                        if (++y > maxY) {
                            done = true;
                        }
                    }
                }
            }
            return count;
        }

        boolean done() {
            return done;
        }
    }
}
