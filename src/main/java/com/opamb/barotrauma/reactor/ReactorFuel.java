package com.opamb.barotrauma.reactor;

import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/**
 * 反应堆燃料棒的识别与耐久换算。
 *
 * <p>耐久上限就是原作 Condition 上限（铀 80 / 钍 100 / 富尔古瑞姆 150）。
 * 剩余百分比 = (上限 - 已损耗) / 上限 × 100；四格相加就是原作 AvailableFuel。</p>
 */
public final class ReactorFuel {

    private ReactorFuel() {
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.URANIUM_FUEL_ROD.get())
                || stack.is(ModItems.THORIUM_FUEL_ROD.get())
                || stack.is(ModItems.FULGURIUM_FUEL_ROD.get()));
    }

    /** 单根燃料棒的剩余百分比（0..100）；不是燃料返回 0。 */
    public static double conditionPercent(ItemStack stack) {
        if (!isFuel(stack)) {
            return 0.0D;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return 100.0D;
        }
        return Math.max(0.0D, (max - stack.getDamageValue()) * 100.0D / max);
    }

    /**
     * 这根燃料棒给反应堆的额定燃料值（原作 reactor.xml 的 AvailableFuel）：
     * 铀 80 / 钍 100 / 富尔古瑞姆 150。剩余耐久为 0 的棒不再提供燃料。
     *
     * <p>多根相加：四根铀 = 320，这是原作反应堆允许更高裂变速率的原因。</p>
     */
    public static double fuelValue(ItemStack stack) {
        if (!isFuel(stack) || conditionPercent(stack) <= 0.0D) {
            return 0.0D;
        }
        if (stack.is(ModItems.URANIUM_FUEL_ROD.get())) {
            return 80.0D;
        }
        if (stack.is(ModItems.THORIUM_FUEL_ROD.get())) {
            return 100.0D;
        }
        return 150.0D;
    }
}
