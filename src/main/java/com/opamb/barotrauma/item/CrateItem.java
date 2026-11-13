package com.opamb.barotrauma.item;

import com.opamb.barotrauma.equipment.CrateContainer;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.menu.CrateMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 原作货运箱：双手持的移动容器（{@code <ItemContainer capacity=...>}）。
 *
 * <p>容量按原作 XML：金属箱 / 保险金属箱 20 格，医疗箱 / 炸药箱 / 化学箱 12 格，
 * 磁悬浮货箱 1 格（电池），密封供给箱没有容器（只能当货物搬运）。内容存在
 * 物品 NBT 里（{@link CrateContainer}），所以搬箱子、放货架、丢地上内容都跟着走。</p>
 *
 * <p>右键打开容器界面（原作点容器弹出 GUI）；在背包界面里右键箱子物品也走
 * {@code OpenCratePacket} 打开同一个界面。</p>
 */
public class CrateItem extends Item {

    private final int capacity;

    public CrateItem(Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    /** 内部格数；0 = 密封箱，没有可打开的容器。 */
    public int capacity() {
        return capacity;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (capacity <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CrateMenu.open(serverPlayer, menuIndexForHand(player, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 手上这份箱子在玩家背包菜单里的槽位下标（界面里打开时客户端也按同一下标找）。 */
    public static int menuIndexForHand(Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            // 47 号双手栏位绑的就是副手容器。
            return HandsSlot.MENU_INDEX;
        }
        // 创造模式直接把箱子放在热键栏：热键栏在背包菜单里是 36..44。
        return 36 + player.getInventory().selected;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        if (capacity > 0) {
            tooltip.add(Component.translatable("tooltip.barotrauma.crate_slots", capacity)
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.barotrauma.crate_sealed")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        // 货运任务的箱子：标出来，免得玩家当普通箱子卖掉/丢掉。
        if (stack.getTag() != null
                && stack.getTag().contains(com.opamb.barotrauma.mission.MissionCargo.TAG_MISSION)) {
            tooltip.add(Component.translatable("tooltip.barotrauma.mission_cargo")
                    .withStyle(ChatFormatting.AQUA));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
