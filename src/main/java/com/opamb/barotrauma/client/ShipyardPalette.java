package com.opamb.barotrauma.client;

import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 编辑器方块库：按船壳 / 舱门 / 动力 / 设备分类，只列能进船体或属于船内设备的方块。 */
public final class ShipyardPalette {

    public enum Category {
        ALL("全部"),
        HULL("船壳"),
        DOORS("舱门"),
        POWER("动力"),
        EQUIPMENT("设备");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Entry(BlockState state, ItemStack icon, String name, Category category) {
    }

    private static List<Entry> cache;

    private ShipyardPalette() {
    }

    public static List<Entry> entries() {
        if (cache != null) {
            return cache;
        }
        List<Entry> list = new ArrayList<>();
        add(list, ModShipBlocks.BLOCKS.getEntries());
        add(list, ModBlocks.BLOCKS.getEntries());
        list.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        cache = List.copyOf(list);
        return cache;
    }

    public static List<Entry> entries(Category category, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Category want = category == null ? Category.ALL : category;
        return entries().stream()
                .filter(entry -> want == Category.ALL || entry.category() == want)
                .filter(entry -> needle.isEmpty()
                        || entry.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private static void add(List<Entry> list, Iterable<? extends RegistryObject<Block>> blocks) {
        for (RegistryObject<Block> holder : blocks) {
            Block block = holder.get();
            BlockState state = block.defaultBlockState();
            if (!ModShipBlocks.isShipBlock(state) && !ModShipBlocks.isEquipment(state)) {
                continue;
            }
            ItemStack icon = new ItemStack(block.asItem());
            if (icon.isEmpty()) {
                continue;
            }
            list.add(new Entry(state, icon, block.getName().getString(), classify(block)));
        }
    }

    private static Category classify(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);
        if (path.contains("door") || path.contains("hatch")) {
            return Category.DOORS;
        }
        if (path.contains("reactor") || path.contains("engine")
                || path.contains("battery") || path.contains("capacitor")
                || path.contains("junction")) {
            return Category.POWER;
        }
        if (block instanceof HullBlock || path.contains("hull") || path.contains("plating")
                || path.contains("glass") || path.contains("ladder")
                || path.contains("wall") || path.contains("floor")
                || path.contains("stair") || path.contains("slab")) {
            return Category.HULL;
        }
        return Category.EQUIPMENT;
    }
}
