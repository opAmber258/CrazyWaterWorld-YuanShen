package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.sonar.ClientSonarCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 声纳界面遥测（服务端 → 客户端，10 Hz，只在舵台界面打开时发）。
 *
 * <p>只带界面数字：船头朝向、功率、深度、速度、下潜速度、自动驾驶、目标点偏移。
 * **不带扫描点云**——扫描仍然是 1 秒一次的 {@link SonarPingPacket}；
 * 转船/加减速时舵台不用等下一次扫描，箭头和数字每 100 ms 刷新一次
 * （用户要求"提高刷新率，不是扫描"）。</p>
 *
 * <p>朝向是船头方向 ×1000（整数传输），客户端归一化后画箭头/算方位角。</p>
 */
public record SonarTelemetryPacket(int bowX, int bowY, int bowZ, int power, int depth,
                                   int speedTenths, int diveTenths, int autopilot,
                                   boolean hasTarget, int targetX, int targetY, int targetZ) {

    /** 服务端构建：船状态解析和 {@link SonarPingPacket} 保持同一套逻辑。 */
    public static SonarTelemetryPacket build(ServerPlayer player, BlockPos sonarPos) {
        ServerLevel level = player.serverLevel();
        // 物理船的声纳方块在船坞里，坐标无意义；玩家在船上，以玩家为准
        boolean physical = VsState.active();
        BlockPos center = !physical && level.getBlockState(sonarPos).is(ModShipBlocks.SONAR.get())
                ? sonarPos : player.blockPosition();

        ShipCoreBlockEntity core = ShipCores.resolve(level, null, center);
        ShipState ship = null;
        if (core != null) {
            ship = core.state() != null ? core.state() : core.rescan(level);
        } else if (VsSupport.available() && VsPilot.hasShip()) {
            ship = VsPilot.state();
        }

        float[] bow = VsState.bowDirection();
        int power = ship == null ? 100 : ship.power();
        double speed = ship == null ? 0.0D : ship.blocksPerSecond();
        double verticalSpeed = ship == null ? 0.0D : ship.physics().verticalSpeed();
        int speedTenths = (int) Math.round(speed * 3.6D * 10.0D);   // 格/秒 → 公里/时
        int diveTenths = (int) Math.round(Math.abs(verticalSpeed) * 3.6D * 10.0D)
                * (verticalSpeed < -0.05D ? -1 : verticalSpeed > 0.05D ? 1 : 0);

        BlockPos target = ship == null ? null : ship.autopilotTarget();
        BlockPos base = player.blockPosition();
        return new SonarTelemetryPacket(
                Math.round(bow[0] * 1000.0F),
                Math.round(bow[1] * 1000.0F),
                Math.round(bow[2] * 1000.0F),
                power,
                -center.getY(),
                speedTenths,
                diveTenths,
                ship != null && ship.autopilotHold() ? 1 : 0,
                target != null,
                target == null ? 0 : target.getX() - base.getX(),
                target == null ? 0 : target.getY() - base.getY(),
                target == null ? 0 : target.getZ() - base.getZ());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bowX);
        buf.writeVarInt(bowY);
        buf.writeVarInt(bowZ);
        buf.writeVarInt(power);
        buf.writeVarInt(depth);
        buf.writeVarInt(speedTenths);
        buf.writeVarInt(diveTenths);
        buf.writeVarInt(autopilot);
        buf.writeBoolean(hasTarget);
        if (hasTarget) {
            buf.writeVarInt(targetX);
            buf.writeVarInt(targetY);
            buf.writeVarInt(targetZ);
        }
    }

    public static SonarTelemetryPacket decode(FriendlyByteBuf buf) {
        int bowX = buf.readVarInt();
        int bowY = buf.readVarInt();
        int bowZ = buf.readVarInt();
        int power = buf.readVarInt();
        int depth = buf.readVarInt();
        int speedTenths = buf.readVarInt();
        int diveTenths = buf.readVarInt();
        int autopilot = buf.readVarInt();
        boolean hasTarget = buf.readBoolean();
        int tx = 0;
        int ty = 0;
        int tz = 0;
        if (hasTarget) {
            tx = buf.readVarInt();
            ty = buf.readVarInt();
            tz = buf.readVarInt();
        }
        return new SonarTelemetryPacket(bowX, bowY, bowZ, power, depth,
                speedTenths, diveTenths, autopilot, hasTarget, tx, ty, tz);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSonarCache.acceptTelemetry(this)));
        context.setPacketHandled(true);
    }
}
