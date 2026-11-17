package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.menu.RepairMenu;
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

/**
 * 客户端 → 服务端：修理窗口的三个动作。
 *
 * <p>对应原作 {@code Repairable} 的 HUD：点“修理”开始持续修理（{@link #ACTION_START}），
 * 之后每次按下按钮做一次 QTE 判定（{@link #ACTION_QTE}，带指针位置），
 * 关窗口停止（{@link #ACTION_STOP}）。持续加耐久在服务端
 * {@link RepairMenu#serverTick()} 里按 tick 推进，客户端不再发进度包，
 * 所以进度条不会一蹦一蹦。</p>
 */
public record RepairActionPacket(BlockPos pos, byte action, float marker) {

    public static final byte ACTION_START = 0;
    public static final byte ACTION_QTE = 1;
    public static final byte ACTION_STOP = 2;

    private static final double MAX_DISTANCE_SQ = 8.0D * 8.0D;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(action);
        buf.writeFloat(marker);
    }

    public static RepairActionPacket decode(FriendlyByteBuf buf) {
        return new RepairActionPacket(buf.readBlockPos(), buf.readByte(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            context.setPacketHandled(true);
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof RepairMenu menu)) {
                return;
            }
            if (!menu.pos().equals(pos)) {
                return;
            }
            Vec3 center = worldCenter(player.level(), pos);
            if (center != null && player.distanceToSqr(center) > MAX_DISTANCE_SQ) {
                return;
            }
            switch (action) {
                case ACTION_START -> menu.startRepair();
                case ACTION_QTE -> menu.qte(marker);
                case ACTION_STOP -> menu.stopRepair();
                default -> {
                    // 未知动作忽略。
                }
            }
        });
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
