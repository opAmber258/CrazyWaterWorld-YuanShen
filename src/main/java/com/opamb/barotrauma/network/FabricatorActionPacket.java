package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.FabricatorBlockEntity;
import com.opamb.barotrauma.fabricator.FabricatorCatalog;
import com.opamb.barotrauma.fabricator.FabricatorData;
import com.opamb.barotrauma.menu.FabricatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：加工台“制作 / 取消”。
 *
 * <p>不能走原版 {@code ServerboundContainerButtonClickPacket}：按钮编号是
 * {@code writeByte}，配方下标 + 数量 × 1000 会被截断成负数或别的下标。
 * 这里用 VarInt 分开传配方下标和数量，服务端再校验菜单、距离和范围。</p>
 */
public record FabricatorActionPacket(BlockPos pos, int action, int recipeIndex, int amount) {

    public static final int START = 0;
    public static final int CANCEL = 1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(Math.max(0, recipeIndex));
        buf.writeVarInt(Math.max(1, amount));
    }

    public static FabricatorActionPacket decode(FriendlyByteBuf buf) {
        return new FabricatorActionPacket(buf.readBlockPos(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            // 只认当前打开的同一台机器菜单，防止伪造包操作别处的加工台。
            if (!(player.containerMenu instanceof FabricatorMenu menu)
                    || !menu.pos().equals(pos)) {
                return;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof FabricatorBlockEntity machine)) {
                return;
            }
            switch (action) {
                case CANCEL -> machine.cancel();
                case START -> {
                    if (recipeIndex < 0
                            || recipeIndex >= FabricatorData.catalog().recipes().size()) {
                        return;
                    }
                    FabricatorCatalog.Recipe recipe =
                            FabricatorData.catalog().recipes().get(recipeIndex);
                    machine.start(player, recipe, Mth.clamp(amount, 1, 99));
                }
                default -> {
                    // 未知操作忽略。
                }
            }
        });
        context.setPacketHandled(true);
    }
}
