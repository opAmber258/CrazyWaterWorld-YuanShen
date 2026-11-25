package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TrainingDummyStationBlockEntity;
import com.opamb.barotrauma.menu.DummyStationMenu;
import com.opamb.barotrauma.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：高级训练设施面板的“应用”。
 *
 * <p>只允许改高级台（普通台 1 血固定，没有面板）；距离超过 8 格或方块已拆时忽略。</p>
 */
public record DummySettingsPacket(BlockPos pos, int health, int respawnTicks,
                                  boolean selfIlluminated) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(health);
        buf.writeVarInt(respawnTicks);
        buf.writeBoolean(selfIlluminated);
    }

    public static DummySettingsPacket decode(FriendlyByteBuf buf) {
        return new DummySettingsPacket(buf.readBlockPos(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            // 必须真的开着这个台座的设置面板：只靠距离+方块类型，任意客户端都能
            // 构造包远程改配置（值虽然会被 clamp，但绕过 GUI 流程）。
            if (!(player.containerMenu instanceof DummyStationMenu menu)
                    || !menu.pos().equals(pos)) {
                reject(player, "dummy.settings.reason_no_menu");
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) {
                reject(player, "dummy.settings.reason_far");
                return;
            }
            Block block = level.getBlockState(pos).getBlock();
            if (block != ModBlocks.ADVANCED_TRAINING_DUMMY_STATION.get()
                    && block != ModBlocks.ADVANCED_BALLOON_DUMMY_STATION.get()) {
                reject(player, "dummy.settings.reason_gone");
                return;
            }
            if (level.getBlockEntity(pos) instanceof TrainingDummyStationBlockEntity station) {
                station.setSettings(health, respawnTicks, selfIlluminated);
                // 面板点“保存”后会立刻关界面，这里回一条动作栏，确认真的落到服务端。
                player.displayClientMessage(Component.translatable("dummy.settings.saved",
                        station.configuredHealth(),
                        Component.translatable("dummy.settings.seconds",
                                String.format(Locale.ROOT, "%.1f", station.respawnTicks() / 20.0D)),
                        Component.translatable(station.selfIlluminated()
                                ? "dummy.settings.on" : "dummy.settings.off")), true);
            } else {
                reject(player, "dummy.settings.reason_gone");
            }
        });
        context.setPacketHandled(true);
    }

    private static void reject(ServerPlayer player, String reasonKey) {
        player.displayClientMessage(Component.translatable("dummy.settings.rejected",
                Component.translatable(reasonKey)), true);
    }
}
