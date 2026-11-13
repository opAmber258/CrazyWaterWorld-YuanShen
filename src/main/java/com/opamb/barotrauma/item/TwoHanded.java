package com.opamb.barotrauma.item;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 双手持物品：原作 {@code <Holdable slots="RightHand+LeftHand">}。
 *
 * <p>这类物品占满左右两只手：拿在手上时右手为主手，副手不能再用；
 * HUD 在快捷栏左侧单独开一格显示。第一人称会额外画一条左臂抱住物品
 * （见 {@code TwoHandedFirstPerson} / {@code ItemInHandRendererMixin}）。</p>
 *
 * <p>判定走 item tag {@code barotrauma:two_handed}，方便以后给别的物品加
 * 双手持（工具箱、发射器等）。</p>
 */
public final class TwoHanded {

    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM,
            new ResourceLocation(BarotraumaMod.MOD_ID, "two_handed"));

    private TwoHanded() {
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TAG);
    }

    /**
     * 玩家当前"抱着"的双手持物品：优先副手（生存模式的正规存放处），
     * 其次主手（创造模式从物品栏直接拿着）。
     */
    public static ItemStack heldBy(Player player) {
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (is(offhand)) {
            return offhand;
        }
        ItemStack main = player.getInventory().getSelected();
        return is(main) ? main : ItemStack.EMPTY;
    }

    public static boolean isCarrying(Player player) {
        return !heldBy(player).isEmpty();
    }
}
