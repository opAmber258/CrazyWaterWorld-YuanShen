package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：水泵面板操作（自动控制开关 / 手动流量百分比）。
 *
 * <p>服务端权威：只接受 8 格内的方块实体；百分比夹到 -100..100
 * （负值泵出、正值泵入，和原版 {@code FlowPercentage} 一致）。改完由
 * {@code ContainerData} 自动同步回客户端。</p>
 */
public record PumpActionPacket(BlockPos pos, int action, int value) {

    public static final int TOGGLE_AUTO = 0;
    public static final int SET_FLOW = 1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(value);
    }

    public static PumpActionPacket decode(FriendlyByteBuf buf) {
        return new PumpActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64.0D) {
                return;
            }
            DrainPumpBlockEntity pump = level.getBlockEntity(pos) instanceof DrainPumpBlockEntity direct
                    ? direct : null;
            if (pump == null && VsState.active()
                    && level.getBlockState(pos).is(ModShipBlocks.PUMP_PROXY.get())) {
                // 物理船：pos 是跟船移动的代理方块，真实泵方块实体在船坞里。
                pump = VsPilot.pumpBlockEntity(level);
            }
            if (pump == null) {
                return;
            }
            switch (action) {
                case TOGGLE_AUTO -> pump.setAuto(!pump.auto());
                case SET_FLOW -> pump.setFlow(Mth.clamp(value, -100, 100));
                default -> { /* 未知操作忽略 */ }
            }
        });
        context.setPacketHandled(true);
    }
}
