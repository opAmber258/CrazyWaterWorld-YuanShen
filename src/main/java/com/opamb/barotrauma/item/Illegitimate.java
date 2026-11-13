package com.opamb.barotrauma.item;

import com.opamb.barotrauma.equipment.CrateContainer;
import net.minecraft.world.item.ItemStack;

/**
 * "赃物"标记（照原作 {@code Item.Illegitimate}）。
 *
 * <p>原作里 {@code AllowStealing=false && SpawnedInCurrentOutpost} 的物品算赃物：
 * 格子上画一个红色小手图标、名字变红，被哨站安全官查到会被抓。货运任务的箱子和
 * 箱内货物照原作都带这个标记。</p>
 */
public final class Illegitimate {

    /** 物品 NBT：这件东西是赃物。 */
    public static final String TAG = "BarotraumaIllegitimate";

    private Illegitimate() {
    }

    /** 这件物品是不是赃物；箱子里有赃物，箱子本身也算（原作同款递归判断）。 */
    public static boolean is(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getTag() != null && stack.getTag().getBoolean(TAG)) {
            return true;
        }
        if (stack.getItem() instanceof CrateItem crate && crate.capacity() > 0) {
            CrateContainer contents = new CrateContainer(stack, crate.capacity());
            for (int i = 0; i < crate.capacity(); i++) {
                if (is(contents.getItem(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 打上赃物标记。 */
    public static ItemStack mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(TAG, true);
        }
        return stack;
    }
}
