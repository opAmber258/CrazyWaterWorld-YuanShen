package com.opamb.barotrauma.station;

import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.entity.NpcEntity;
import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.ClinicActionPacket;
import com.opamb.barotrauma.network.ClinicStatePacket;
import com.opamb.barotrauma.network.MedicalSyncPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 哨站医生：按原作公式收费治疗玩家当前病症。
 *
 * <p>原作 {@code MedicalClinic.HealCost}：{@code BaseHealCost + Strength × Multiplier}，
 * 声望折扣这次不做。医生只治疗当前交互的玩家；单机里就是玩家自己。</p>
 */
public final class MedicalClinicService {

    private static final double MAX_DISTANCE_SQ = 8.0D * 8.0D;

    private MedicalClinicService() {
    }

    public static void open(ServerPlayer player, NpcEntity npc) {
        if (!NpcEntity.ROLE_DOCTOR.equals(npc.role())) {
            return;
        }
        if (player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
            return;
        }
        sendState(player, true);
    }

    public static void handle(ServerPlayer player, ClinicActionPacket packet) {
        if (doctorNear(player) == null) {
            return;
        }
        AfflictionContainer container = MedicalCapabilities.container(player);
        if (container == null) {
            return;
        }
        List<ClinicStatePacket.Entry> entries = entries(container);
        boolean healAll = ClinicActionPacket.HEAL_ALL.equals(packet.afflictionId());
        List<ClinicStatePacket.Entry> selected = new ArrayList<>();
        if (healAll) {
            selected.addAll(entries);
        } else {
            for (ClinicStatePacket.Entry entry : entries) {
                if (entry.afflictionId().equals(packet.afflictionId())
                        && entry.limb().equals(packet.limb() == null ? "" : packet.limb())) {
                    selected.add(entry);
                    break;
                }
            }
        }
        if (selected.isEmpty()) {
            message(player, "没有找到要治疗的病症", ChatFormatting.GRAY);
            sendState(player, false);
            return;
        }
        int total = 0;
        for (ClinicStatePacket.Entry entry : selected) {
            total += entry.price();
        }
        CampaignState wallet = CampaignState.wallet(player.serverLevel());
        // 免费病症（total=0）也要能治；spendCredits(0) 在空钱包上会误报余额不足。
        if (total > 0 && !wallet.spendCredits(total)) {
            message(player, "治疗需要 " + total + " mk，余额不足（当前 "
                    + wallet.credits() + "）", ChatFormatting.RED);
            sendState(player, false);
            return;
        }
        int healed = 0;
        for (ClinicStatePacket.Entry entry : selected) {
            AfflictionType type = AfflictionType.byId(entry.afflictionId());
            if (type == null) {
                continue;
            }
            Limb limb = parseLimb(entry.limb());
            container.setStrength(type, limb, 0.0D);
            healed++;
        }
        if (healed == 0) {
            wallet.addCredits(total);
            message(player, "治疗失败（已退款）", ChatFormatting.RED);
        } else {
            message(player, "治疗完成，共 " + healed + " 项（-" + total + " mk）",
                    ChatFormatting.GREEN);
        }
        MedicalSyncPacket.respondSnapshot(player, player);
        sendState(player, false);
    }

    private static List<ClinicStatePacket.Entry> entries(AfflictionContainer container) {
        List<ClinicStatePacket.Entry> out = new ArrayList<>();
        for (String row : container.rows()) {
            String[] parts = row.split("\\|");
            if (parts.length < 2 || "v".equals(parts[0]) || "t".equals(parts[0])) {
                continue;
            }
            String id = parts[0];
            String limb = "";
            String strengthText;
            if (parts.length == 2) {
                strengthText = parts[1];
            } else {
                limb = parts[1];
                strengthText = parts[2];
            }
            double strength;
            try {
                strength = Double.parseDouble(strengthText);
            } catch (NumberFormatException e) {
                continue;
            }
            if (strength <= 0.0D) {
                continue;
            }
            out.add(new ClinicStatePacket.Entry(id, limb, strength,
                    ClinicPricing.healCost(id, strength)));
        }
        return out;
    }

    private static Limb parseLimb(String limb) {
        if (limb == null || limb.isBlank() || "-".equals(limb)) {
            return null;
        }
        try {
            return Limb.valueOf(limb);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static NpcEntity doctorNear(ServerPlayer player) {
        List<NpcEntity> found = player.serverLevel().getEntitiesOfClass(
                NpcEntity.class, player.getBoundingBox().inflate(8.0D),
                npc -> NpcEntity.ROLE_DOCTOR.equals(npc.role()));
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * @param open true = 首次打开（客户端可以切屏）；false = 治疗后刷新，
     *             客户端只在诊所界面仍开着时更新，不抢玩家当前界面。
     */
    private static void sendState(ServerPlayer player, boolean open) {
        AfflictionContainer container = MedicalCapabilities.container(player);
        List<ClinicStatePacket.Entry> entries = container == null
                ? List.of() : entries(container);
        ModNetwork.sendToPlayer(player, new ClinicStatePacket(
                CampaignState.wallet(player.serverLevel()).credits(), entries, open));
    }

    private static void message(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }
}
