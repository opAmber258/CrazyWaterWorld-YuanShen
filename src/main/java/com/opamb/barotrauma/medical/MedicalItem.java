package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.TreatmentSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 医疗物品基类（原作交互语义）：手持右键 → 打开自己的身体部位面板，
 * 移动鼠标选择部位后左键用药；对着队友左键 → 打开队友的面板对他用药。
 *
 * <p>面板与用药结算是客户端 {@code MedicalScreen} + 服务端
 * {@code MedicalTreatPacket} 的协作；本类只负责规格、tooltip 与注册属性。
 * 凝胶类多用途物品走耐久（原作 Condition 12.5×8 次），其余一次性消耗。</p>
 */
public class MedicalItem extends Item {

    private final TreatmentSpec spec;

    public MedicalItem(Properties properties, TreatmentSpec spec) {
        super(properties);
        this.spec = spec;
    }

    public TreatmentSpec spec() {
        return spec;
    }

    /** 右键空中：打开自己的身体部位面板（客户端）；服务端无操作。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            com.opamb.barotrauma.client.MedicalClientHooks.openSelfTreatScreen();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** tooltip：按用户指令只留药物名，作用/副作用文字提示全部删除。 */
}
