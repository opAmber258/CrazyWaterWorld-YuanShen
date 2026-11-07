package com.opamb.barotrauma.network;

import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.MedicalItem;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.medical.affliction.TreatmentSpec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：在治疗面板里对某部位用药。
 *
 * <p>itemId 为空 = 使用主手的药品；否则在背包里查找指定药品（原作：从背包取药）。
 * 服务端校验（手持/背包有药、使用者未倒地、目标在线、距离 ≤ 6 格、倒地者只能由他人治疗）
 * 后按部位登记治疗并消耗物品。用药后面板保持打开（用户指令），并回发双方最新快照。</p>
 */
public record MedicalTreatPacket(UUID target, String limb, String itemId, boolean fromCarried) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
        buf.writeUtf(limb, 32);
        buf.writeUtf(itemId, 128);
        buf.writeBoolean(fromCarried);
    }

    public static MedicalTreatPacket decode(FriendlyByteBuf buf) {
        return new MedicalTreatPacket(buf.readUUID(), buf.readUtf(32), buf.readUtf(128), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer user = context.getSender();
            if (user == null) {
                return;
            }
            ServerPlayer targetPlayer = user.server.getPlayerList().getPlayer(target);
            if (targetPlayer == null) {
                user.displayClientMessage(Component.literal("目标不在线"), true);
                return;
            }
            if (targetPlayer != user && user.distanceToSqr(targetPlayer) > 36.0) {
                user.displayClientMessage(Component.literal("距离太远"), true);
                return;
            }
            AfflictionContainer userContainer = MedicalCapabilities.container(user);
            if (userContainer == null
                    || userContainer.rawVitality(com.opamb.barotrauma.medical.bridge.MedicalEvents.MAX_VITALITY) <= 0) {
                user.displayClientMessage(Component.literal("你重伤倒地，无法用药"), true);
                return;
            }
            AfflictionContainer targetContainer = MedicalCapabilities.container(targetPlayer);
            if (targetContainer == null) {
                return;
            }
            if (targetPlayer == user
                    && targetContainer.rawVitality(com.opamb.barotrauma.medical.bridge.MedicalEvents.MAX_VITALITY) <= 0) {
                user.displayClientMessage(Component.literal("你已倒地，等待队友救援"), true);
                return;
            }

            // 找药：fromCarried = 拖拽（消耗光标上的物品）；itemId 空 = 主手；否则主手优先，其次背包
            ItemStack stack;
            if (fromCarried) {
                stack = user.containerMenu.getCarried();
                if (!isItem(stack, itemId) || !(stack.getItem() instanceof MedicalItem)) {
                    return;
                }
            } else {
                stack = user.getMainHandItem();
                if (!itemId.isEmpty() && !isItem(stack, itemId)) {
                    stack = findInInventory(user, itemId);
                    if (stack.isEmpty()) {
                        user.displayClientMessage(Component.literal("背包里没有该药品"), true);
                        return;
                    }
                }
            }
            if (!(stack.getItem() instanceof MedicalItem medicalItem)) {
                return;
            }
            TreatmentSpec spec = medicalItem.spec();
            Limb chosenLimb = parseLimb(limb);
            for (TreatmentSpec.Effect effect : spec.effects()) {
                targetContainer.applyTreatment(effect.type(),
                        effect.type().limbSpecific() ? chosenLimb : null,
                        effect.perSecond(), effect.seconds());
            }
            if (spec.multiUse()) {
                stack.hurtAndBreak(1, user, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            } else {
                stack.shrink(1);
            }
            targetPlayer.displayClientMessage(Component.literal(
                    (targetPlayer == user ? "使用了 " : user.getName().getString() + " 为你使用了 ")
                            + spec.displayName()
                            + (chosenLimb == null ? "" : "（" + limbDisplayName(chosenLimb) + "）")), true);
            // 面板保持打开：把最新快照回给使用者（目标自己会经每秒同步收到）
            MedicalSyncPacket.respondSnapshot(user, targetPlayer);
        });
        context.setPacketHandled(true);
    }

    private static boolean isItem(ItemStack stack, String itemId) {
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.toString().equals(itemId);
    }

    /** 在背包里找第一个匹配的药品堆。 */
    private static ItemStack findInInventory(ServerPlayer user, String itemId) {
        var inventory = user.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && isItem(stack, itemId)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Limb parseLimb(String name) {
        try {
            return Limb.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Limb.TORSO;
        }
    }

    private static String limbDisplayName(Limb limb) {
        return switch (limb) {
            case HEAD -> "头部";
            case TORSO -> "胸部";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }
}
