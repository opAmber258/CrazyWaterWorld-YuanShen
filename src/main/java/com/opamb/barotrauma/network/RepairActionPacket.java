package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.menu.DeviceMenu;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


/** Repair controls apply only to the server's currently open device menu. */
public record RepairActionPacket(int menuId, byte action) {
    public static final byte ACTION_START = 0;
    public static final byte ACTION_QTE = 1;
    public static final byte ACTION_STOP = 2;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(menuId);
        buf.writeByte(action);
    }

    public static RepairActionPacket decode(FriendlyByteBuf buf) {
        return new RepairActionPacket(buf.readVarInt(), buf.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof DeviceMenu menu)
                    || menu.containerId != menuId || !menu.stillValid(player)) {
                return;
            }
            switch (action) {
                case ACTION_START -> menu.repair().start();
                case ACTION_QTE -> menu.repair().timePress();
                case ACTION_STOP -> menu.repair().stop();
                default -> { }
            }
        });
        context.setPacketHandled(true);
    }

    /** 电池/接线盒既可能在普通世界，也可能在 VS2 船坞里。 */
    public static RepairTarget find(ServerLevel level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct instanceof RepairTarget target) {
            return target;
        }
        // 物理船的交互落点是船坞代理方块，要按代理找回真实方块实体。
        if (level.getBlockState(pos).is(com.opamb.barotrauma.registry.ModShipBlocks.PUMP_PROXY.get())) {
            return VsPilot.pumpBlockEntity(level, pos);
        }
        if (level.getBlockState(pos).is(com.opamb.barotrauma.registry.ModShipBlocks.REACTOR_PROXY.get())) {
            return VsPilot.reactorBlockEntity(level);
        }
        if (level.getBlockState(pos).is(com.opamb.barotrauma.registry.ModShipBlocks.ENGINE_PROXY.get())) {
            return VsPilot.engineBlockEntity(level);
        }
        if (level.getBlockState(pos).is(com.opamb.barotrauma.registry.ModShipBlocks.SONAR_PROXY.get())) {
            return VsPilot.sonarCoreBlockEntity(level);
        }
        if (VsState.active()) {
            BlockPos yard = VsPilot.toYard(pos);
            if (yard != null && level.getBlockEntity(yard) instanceof RepairTarget target) {
                return target;
            }
        }
        return null;
    }

    public static Vec3 worldCenter(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level != null) {
            var state = level.getBlockState(pos);
            if (state.is(com.opamb.barotrauma.registry.ModShipBlocks.PUMP_PROXY.get())
                    || state.is(com.opamb.barotrauma.registry.ModShipBlocks.REACTOR_PROXY.get())
                    || state.is(com.opamb.barotrauma.registry.ModShipBlocks.ENGINE_PROXY.get())
                    || state.is(com.opamb.barotrauma.registry.ModShipBlocks.SONAR_PROXY.get())) {
                return Vec3.atCenterOf(pos);
            }
            // 普通世界的方块实体（方块船）：坐标就是世界坐标。
            if (level.getBlockEntity(pos) != null && !VsPilot.isYardBlock(pos)) {
                return Vec3.atCenterOf(pos);
            }
        }
        if (VsState.active()) {
            if (VsPilot.isYardBlock(pos)) {
                Vec3 world = VsPilot.assemblyToWorld(VsPilot.toAssembly(pos));
                if (world != null) {
                    return world;
                }
            } else {
                Vec3 world = VsPilot.assemblyToWorld(pos);
                if (world != null) {
                    return world;
                }
            }
        }
        return Vec3.atCenterOf(pos);
    }

    /** 电池实体的快捷查询（电池界面用）。 */
    public static BatteryBlockEntity battery(ServerLevel level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct instanceof BatteryBlockEntity battery) {
            return battery;
        }
        if (VsState.active()) {
            BlockPos yard = VsPilot.toYard(pos);
            if (yard != null && level.getBlockEntity(yard) instanceof BatteryBlockEntity battery) {
                return battery;
            }
        }
        return null;
    }
}
