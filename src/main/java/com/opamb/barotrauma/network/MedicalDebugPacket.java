package com.opamb.barotrauma.network;

import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：医疗调试器的编辑指令（开发者工具）。
 *
 * <p>只作用于使用者自己。服务端校验 {@code 创造模式 || 权限等级 ≥ 2}，
 * 防止普通生存玩家拿到物品后改自己血量。每次编辑后立即回发一次快照，
 * 面板不用等每秒同步就能看到结果。</p>
 *
 * <p>病症类型用字符串 id（{@link AfflictionType#byId}），部位用枚举名
 * （全身类型传 "-"）。{@code value} 在 SET 时是目标强度/目标活力，在 ADD 时是增量。</p>
 */
public record MedicalDebugPacket(int action, String typeId, String limb, double value) {

    public static final int SET_AFFLICTION = 0;
    public static final int ADD_AFFLICTION = 1;
    public static final int CLEAR_LIMB = 2;
    public static final int CLEAR_ALL = 3;
    public static final int SET_VITALITY = 4;
    public static final int ADD_VITALITY = 5;
    public static final int CLEAR_VITALITY = 6;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(typeId, 64);
        buf.writeUtf(limb, 32);
        buf.writeDouble(value);
    }

    public static MedicalDebugPacket decode(FriendlyByteBuf buf) {
        return new MedicalDebugPacket(buf.readVarInt(), buf.readUtf(64), buf.readUtf(32), buf.readDouble());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer user = context.getSender();
            if (user == null) {
                return;
            }
            // 开发者工具：创造模式或管理员（单机开作弊的 2 级权限也算）
            if (!user.isCreative() && !user.hasPermissions(2)) {
                user.displayClientMessage(Component.literal("医疗调试器仅限创造模式/管理员使用"), true);
                return;
            }
            AfflictionContainer container = MedicalCapabilities.container(user);
            if (container == null) {
                return;
            }
            Limb parsedLimb = parseLimb(limb);
            switch (action) {
                case SET_AFFLICTION -> {
                    AfflictionType type = AfflictionType.byId(typeId);
                    if (type != null) {
                        container.setStrength(type, parsedLimb, value);
                    }
                }
                case ADD_AFFLICTION -> {
                    AfflictionType type = AfflictionType.byId(typeId);
                    if (type != null) {
                        container.addStrength(type, parsedLimb, value);
                    }
                }
                case CLEAR_LIMB -> {
                    if (parsedLimb == null) {
                        // 全身页签的"清空"：只清不分部位的病症
                        for (AfflictionType type : AfflictionType.ALL) {
                            if (!type.limbSpecific()) {
                                container.setStrength(type, null, 0.0);
                            }
                        }
                    } else {
                        // 只清该部位的分部位病症；全身病症不受"清空部位"影响
                        for (AfflictionType type : AfflictionType.ALL) {
                            if (type.limbSpecific()) {
                                container.setStrength(type, parsedLimb, 0.0);
                            }
                        }
                    }
                }
                case CLEAR_ALL -> container.clear();
                case SET_VITALITY -> container.setVitalityOverride(value);
                case ADD_VITALITY -> {
                    double current = container.vitalityOverride() != null
                            ? container.vitalityOverride()
                            : container.rawVitality(
                                    com.opamb.barotrauma.medical.bridge.MedicalEvents.MAX_VITALITY);
                    container.setVitalityOverride(current + value);
                }
                case CLEAR_VITALITY -> container.clearVitalityOverride();
                default -> {
                    return;
                }
            }
            MedicalSyncPacket.respondSnapshot(user, user);
        });
        context.setPacketHandled(true);
    }

    private static Limb parseLimb(String name) {
        if (name == null || name.isEmpty() || "-".equals(name)) {
            return null;
        }
        try {
            return Limb.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
