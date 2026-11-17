package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;
import java.util.function.Supplier;

/**
 * 岩壁矿脉：嵌在副本岩层里的矿点，用等离子切割机切约 4 秒后碎掉并掉落矿物。
 *
 * <p>直接拿镐子硬挖也能掉 1 个（避免卡死），但速度远不如切割机；任务采集
 * 走的是切割机那条路（见 {@code PlasmaCutterItem}）。</p>
 */
public class MineralVeinBlock extends Block {

    /**
     * 采矿任务只认"从矿脉里采出来的"矿物：带这个 NBT 标记的才计数，
     * 创造模式/商店拿到的同种矿物不会算完成任务（原作只统计任务生成的那批资源）。
     */
    public static final String TAG_MINED = "BarotraumaMined";

    private final Supplier<? extends Item> drop;

    public MineralVeinBlock(Properties properties, Supplier<? extends Item> drop) {
        super(properties);
        this.drop = drop;
    }

    /** 这块矿脉掉什么矿物。 */
    public Item mineral() {
        return drop.get();
    }

    /** 给矿脉掉落的物品打上"采出来的"标记。 */
    public static ItemStack mined(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putBoolean(TAG_MINED, true);
        return stack;
    }

    /** 这一堆矿物是不是从矿脉里采出来的。 */
    public static boolean isMined(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag()
                && stack.getTag().getBoolean(TAG_MINED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(mined(drop.get(), 1));
    }

    /** 切割机切碎时一次掉几个：基础 1 个，20% 概率多掉 1 个。 */
    public int cutYield(net.minecraft.util.RandomSource random) {
        return random.nextInt(5) == 0 ? 2 : 1;
    }
}
