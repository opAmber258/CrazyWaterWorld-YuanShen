package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 按结构模板生成一艘方块船。
 *
 * <p>模板里的 {@code PowerBox} / {@code StorageBox} 存的是<b>相对模板原点</b>的坐标
 * （导出脚本 {@code tools/export_structure.py} 做的蓝图化）。原版
 * {@code StructureTemplate} 会把方块实体 NBT 原样写入，所以放完之后这里再读一遍
 * 模板文件，把绑定补上生成原点——设备才会绑到船上真正的接线盒。</p>
 */
public final class ShipTemplateSpawner {

    private ShipTemplateSpawner() {
    }

    /** 模板是否存在（生成前先验证，避免先清空区域再发现模板缺失）。 */
    public static boolean exists(ServerLevel level, ResourceLocation templateId) {
        return level.getStructureManager().get(templateId).isPresent();
    }

    /**
     * 在 {@code origin}（模板最小角）生成模板。
     *
     * @return 模板不存在或放置失败时返回 false
     */
    public static boolean spawn(ServerLevel level, BlockPos origin, ResourceLocation templateId) {
        StructureTemplate template = level.getStructureManager().get(templateId).orElse(null);
        if (template == null) {
            return false;
        }
        // 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS：和世界生成时的落块一致，
        // 多格机器的各分段才会在放置时拿到邻居更新。
        boolean placed = template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings(), level.random,
                Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        if (!placed) {
            return false;
        }
        rebaseBindings(level, origin, templateId);
        return true;
    }

    /** 模板里的相对绑定 → 世界坐标。直接读模板 nbt：StructureTemplate 的调色板不对外。 */
    private static void rebaseBindings(ServerLevel level, BlockPos origin, ResourceLocation templateId) {
        CompoundTag root = readTemplateNbt(level, templateId);
        if (root == null) {
            return;
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            if (!entry.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag nbt = entry.getCompound("nbt");
            // 用电设备存 PowerBox；电池（PowerStorage）也存 PowerBox（BatteryBlockEntity
            // 的 NBT 字段名就是 PowerBox），储能接口的 StorageBox 只是兼容键。
            boolean hasPower = nbt.contains("PowerBox");
            boolean hasStorage = nbt.contains("StorageBox") || hasPower;
            if (!hasPower && !hasStorage) {
                continue;
            }
            ListTag posList = entry.getList("pos", Tag.TAG_INT);
            if (posList.size() < 3) {
                continue;
            }
            BlockPos world = origin.offset(posList.getInt(0), posList.getInt(1), posList.getInt(2));
            BlockEntity be = level.getBlockEntity(world);
            if (be == null) {
                continue;
            }
            if (hasPower && be instanceof PowerConsumer consumer) {
                consumer.setPowerBox(origin.offset(BlockPos.of(nbt.getLong("PowerBox"))));
                be.setChanged();
            }
            if (hasStorage && be instanceof PowerStorage storage) {
                long storageLong = nbt.contains("StorageBox")
                        ? nbt.getLong("StorageBox") : nbt.getLong("PowerBox");
                storage.setStorageBox(origin.offset(BlockPos.of(storageLong)));
                be.setChanged();
            }
        }
    }

    /** 读模组里的 {@code data/<ns>/structures/<path>.nbt}；读不到返回 null。 */
    private static CompoundTag readTemplateNbt(ServerLevel level, ResourceLocation templateId) {
        ResourceLocation file = new ResourceLocation(templateId.getNamespace(),
                "structures/" + templateId.getPath() + ".nbt");
        try {
            var found = level.getServer().getResourceManager().getResource(file);
            if (found.isEmpty()) {
                com.opamb.barotrauma.BarotraumaMod.LOGGER.warn(
                        "[Barotrauma] 模板文件不存在 {}，电力绑定不会重定基", file);
                return null;
            }
            try (var in = found.get().open()) {
                return NbtIo.readCompressed(in);
            }
        } catch (Exception error) {
            com.opamb.barotrauma.BarotraumaMod.LOGGER.warn(
                    "[Barotrauma] 读取模板 {} 失败，电力绑定不会重定基：{}", file, error.toString());
            return null;
        }
    }
}
