package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.reactor.ReactorState;
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
 * 客户端 → 服务端：反应堆面板操作。
 *
 * <p>服务端权威：只接受 8 格内的方块实体；物理船的代理方块要换算到船坞里
 * 的真实反应堆。数值统一夹紧后写入 {@link ReactorState}。</p>
 */
public record ReactorActionPacket(BlockPos pos, int action, int value) {

    public static final int TOGGLE_POWER = 0;
    public static final int TOGGLE_AUTO = 1;
    public static final int SET_FISSION = 2;
    public static final int SET_TURBINE = 3;
    public static final int TEMP_BOOST_UP = 4;
    public static final int TEMP_BOOST_DOWN = 5;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeVarInt(value);
    }

    public static ReactorActionPacket decode(FriendlyByteBuf buf) {
        return new ReactorActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
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
            ReactorBlockEntity reactor = level.getBlockEntity(pos) instanceof ReactorBlockEntity direct
                    ? direct : null;
            if (reactor == null && VsState.active()
                    && level.getBlockState(pos).is(ModShipBlocks.REACTOR_PROXY.get())) {
                reactor = VsPilot.reactorBlockEntity(level);
            }
            if (reactor == null) {
                return;
            }
            switch (action) {
                case TOGGLE_POWER -> reactor.setPowerOn(!reactor.powerOn());
                case TOGGLE_AUTO -> reactor.setAutoTemp(!reactor.autoTemp());
                case SET_FISSION -> reactor.setTargetFission(Mth.clamp(value, 0, 100));
                case SET_TURBINE -> reactor.setTargetTurbine(Mth.clamp(value, 0, 100));
                case TEMP_BOOST_UP -> reactor.applyTemperatureBoost(ReactorState.TEMPERATURE_BOOST);
                case TEMP_BOOST_DOWN -> reactor.applyTemperatureBoost(-ReactorState.TEMPERATURE_BOOST);
                default -> { /* 未知操作忽略 */ }
            }
        });
        context.setPacketHandled(true);
    }
}
