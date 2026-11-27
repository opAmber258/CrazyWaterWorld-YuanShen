package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 船库 structure.nbt 的读写：原版 StructureTemplate 格式，兼容外部工具。
 *
 * <p>平台层（地块 y-1 的 smooth_stone）不写进蓝图；加载时只清空平台上方的
 * 编辑区，避免把地块本身铲掉。</p>
 */
public final class ShipyardStructureIO {

    private ShipyardStructureIO() {
    }

    public static boolean save(ServerLevel level, ShipyardSavedData.Plot plot, String id, String name) {
        return save(level, plot, id, name, "", "", false);
    }

    public static boolean save(ServerLevel level, ShipyardSavedData.Plot plot, String id, String name,
                               String ownerUuid, String ownerName, boolean verified) {
        if (!ShipLibrary.validId(id)) {
            return false;
        }
        int minX = plot.minX;
        int minZ = plot.minZ;
        // 平台层（originY-1）不属于蓝图；加载也从 originY 起放，两者必须一致。
        int minY = Math.max(plot.minY, plot.originY());
        int sizeX = Math.max(1, plot.maxX - minX + 1);
        int sizeY = Math.max(1, plot.maxY - minY + 1);
        int sizeZ = Math.max(1, plot.maxZ - minZ + 1);
        if (!validSize(sizeX, sizeY, sizeZ)) {
            return false;
        }
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(minX, minY, minZ),
                new Vec3i(sizeX, sizeY, sizeZ), false, Blocks.AIR);
        try {
            Path dir = ShipLibrary.directory(id);
            Files.createDirectories(dir);
            Path temp = dir.resolve(ShipLibrary.STRUCTURE_FILE + ".tmp");
            CompoundTag tag = template.save(new CompoundTag());
            NbtIo.writeCompressed(tag, temp.toFile());
            Files.move(temp, ShipLibrary.structurePath(id),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            ShipLibrary.Entry entry = new ShipLibrary.Entry(id, name, sizeX, sizeY, sizeZ,
                    countShipBlocks(level, plot), deviceCounts(level, plot), System.currentTimeMillis(), dir,
                    ownerUuid == null ? "" : ownerUuid, ownerName == null ? "" : ownerName, verified);
            return ShipLibrary.writeMeta(dir, entry);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean load(ServerLevel level, ShipyardSavedData.Plot plot, String id) {
        Path file = ShipLibrary.structurePath(id);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return false;
            }
            CompoundTag tag = readLimited(file);
            Vec3i size = tagSize(tag);
            if (!validSize(size.getX(), size.getY(), size.getZ())) {
                return false;
            }
            // 载入后必须仍落在地块可编辑范围内，否则船体一半会落在权限边界外。
            if (size.getX() > 2 * ShipyardServer.PLOT_RADIUS + 1
                    || size.getZ() > 2 * ShipyardServer.PLOT_RADIUS + 1
                    || size.getY() > ShipyardServer.PLOT_RADIUS + 1) {
                return false;
            }
            BlockPos origin = new BlockPos(plot.originX() - size.getX() / 2, plot.originY(),
                    plot.originZ() - size.getZ() / 2);
            if (!validStructureTag(tag, size, origin, plot)) {
                return false;
            }
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), tag);
            // 先备份旧船，放置中途失败时回滚；太大的地块跳过备份但仍有前面的 NBT 校验。
            StructureTemplate backup = null;
            BlockPos backupPos = null;
            Vec3i backupSize = null;
            int backupY = Math.max(plot.minY, plot.originY());
            int backupX = plot.maxX - plot.minX + 1;
            int backupHeight = plot.maxY - backupY + 1;
            int backupZ = plot.maxZ - plot.minZ + 1;
            if (plot.maxY >= backupY && validSize(backupX, backupHeight, backupZ)
                    && (long) backupX * backupHeight * backupZ <= 4_000_000L) {
                backup = new StructureTemplate();
                backupPos = new BlockPos(plot.minX, backupY, plot.minZ);
                backupSize = new Vec3i(backupX, backupHeight, backupZ);
                backup.fillFromWorld(level, backupPos, backupSize, false, Blocks.AIR);
            }
            try {
                clearAbovePlatform(level, plot, origin, size);
                // 外部 NBT 里的实体整表忽略：避免 TNT/刷怪笼/盔甲架被带进地块。
                boolean placed = template.placeInWorld(level, origin, origin,
                        new StructurePlaceSettings().setIgnoreEntities(true),
                        level.random, Block.UPDATE_ALL);
                if (!placed) {
                    throw new IllegalStateException("structure placement failed");
                }
            } catch (RuntimeException e) {
                if (backup != null) {
                    clearAbovePlatform(level, plot, origin, size);
                    backup.placeInWorld(level, backupPos, backupPos,
                            new StructurePlaceSettings(), level.random, Block.UPDATE_ALL);
                }
                return false;
            }
            plot.minX = origin.getX();
            plot.minY = origin.getY();
            plot.minZ = origin.getZ();
            plot.maxX = origin.getX() + size.getX() - 1;
            plot.maxY = origin.getY() + size.getY() - 1;
            plot.maxZ = origin.getZ() + size.getZ() - 1;
            plot.shipId = id;
            for (var core : ShipCores.coresNear(level, origin,
                    Math.max(96.0D, Math.max(size.getX(), size.getZ())))) {
                core.setScanBounds(new ShipGeometry.Bounds(plot.minX, plot.minY, plot.minZ,
                        plot.maxX, plot.maxY, plot.maxZ));
                core.rescan(level);
            }
            ShipLibrary.Entry meta = ShipLibrary.readMeta(ShipLibrary.directory(id));
            if (meta != null) {
                plot.shipId = meta.id();
            }
            return true;
        } catch (IOException | RuntimeException | StackOverflowError ignored) {
            return false;
        }
    }

    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_NBT_BYTES = 128L * 1024L * 1024L;

    /** 限流读取，避免外部 gzip/NBT 炸弹把服务端内存打满。 */
    private static CompoundTag readLimited(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(raw);
             DataInputStream data = new DataInputStream(gzip)) {
            return NbtIo.read(data, new NbtAccounter(MAX_NBT_BYTES));
        }
    }

    private static Vec3i tagSize(CompoundTag tag) {
        ListTag list = tag.getList("size", Tag.TAG_INT);
        if (list.size() != 3) {
            return Vec3i.ZERO;
        }
        return new Vec3i(list.getInt(0), list.getInt(1), list.getInt(2));
    }

    /**
     * 在真正放置前校验 palette 与每个方块坐标：
     * 只允许本模组的船体/设备方块，且不能越出当前地块。
     */
    private static boolean validStructureTag(CompoundTag tag, Vec3i size, BlockPos origin,
                                             ShipyardSavedData.Plot plot) {
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty() || palette.size() > 4096) {
            return false;
        }
        boolean[] allowedState = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            ResourceLocation location = ResourceLocation.tryParse(name);
            if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
                return false;
            }
            Block block = BuiltInRegistries.BLOCK.get(location);
            BlockState state = block.defaultBlockState();
            // 空气允许出现在 palette 里（结构模板可能显式清空），其余必须是船体/设备。
            allowedState[i] = state.isAir()
                    || ModShipBlocks.isShipBlock(state)
                    || ModShipBlocks.isEquipment(state);
            if (!allowedState[i]) {
                return false;
            }
        }
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        long cells = (long) size.getX() * size.getY() * size.getZ();
        if (blocks.size() > cells || blocks.size() > ShipGeometry.MAX_SCAN_BOUNDS_CELLS) {
            return false;
        }
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) {
                return false;
            }
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            if (x < 0 || y < 0 || z < 0
                    || x >= size.getX() || y >= size.getY() || z >= size.getZ()) {
                return false;
            }
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size() || !allowedState[state]) {
                return false;
            }
            if (!ShipyardServer.insidePlot(plot, origin.offset(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    public static boolean export(String id) {
        Path source = ShipLibrary.structurePath(id);
        if (!Files.isRegularFile(source)) {
            return false;
        }
        try {
            Path exports = ShipLibrary.root().resolve("exports");
            Files.createDirectories(exports);
            Files.copy(source, exports.resolve(id + ".nbt"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void clearAbovePlatform(ServerLevel level, ShipyardSavedData.Plot plot,
                                           BlockPos origin, Vec3i size) {
        int minX = Math.min(plot.minX, origin.getX()) - 1;
        int minZ = Math.min(plot.minZ, origin.getZ()) - 1;
        int maxX = Math.max(plot.maxX, origin.getX() + size.getX()) + 1;
        int maxZ = Math.max(plot.maxZ, origin.getZ() + size.getZ()) + 1;
        int minY = plot.originY();
        int maxY = Math.max(plot.maxY, origin.getY() + size.getY()) + 1;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            level.getChunkAt(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean validSize(int x, int y, int z) {
        return x > 0 && y > 0 && z > 0
                && x <= ShipGeometry.MAX_SCAN_BOUNDS_SPAN
                && y <= ShipGeometry.MAX_SCAN_BOUNDS_SPAN
                && z <= ShipGeometry.MAX_SCAN_BOUNDS_SPAN
                && (long) x * y * z <= ShipGeometry.MAX_SCAN_BOUNDS_CELLS;
    }

    private static int countShipBlocks(ServerLevel level, ShipyardSavedData.Plot plot) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(plot.minX, plot.originY(), plot.minZ,
                plot.maxX, plot.maxY, plot.maxZ)) {
            if (ModShipBlocks.isShipBlock(level.getBlockState(pos))) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Integer> deviceCounts(ServerLevel level, ShipyardSavedData.Plot plot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(plot.minX, plot.originY(), plot.minZ,
                plot.maxX, plot.maxY, plot.maxZ)) {
            BlockState state = level.getBlockState(pos);
            if (!ModShipBlocks.isShipBlock(state)) {
                continue;
            }
            String key = state.getBlock().builtInRegistryHolder().key().location().getPath();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }
}
