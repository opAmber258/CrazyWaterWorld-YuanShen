package com.opamb.barotrauma.network;

import com.opamb.barotrauma.sonar.ClientSonarCache;
import com.opamb.barotrauma.sonar.SonarPulse;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Quaternionf;

import java.util.function.Supplier;

/**
 * 当前声纳中心、驾驶读数与回波。船体方块点以装配核心为原点（物理船）或
 * 扫描中心为原点（方块船）。旋转后的点加 hullOffset，即可与世界回波一起投影。
 */
public record SonarDataPacket(int range, int[] points, int power, int hx, int hz,
                              int mode, int depth, int speedTenths, int diveTenths, int autopilot,
                              boolean hasTarget, int tx, int ty, int tz, String status,
                              int dockState, int dockDistance, String dockLabel, boolean physical,
                              Vec3 hullOffset, Quaternionf hullRotation,
                              boolean hasWaypoint, int waypointX, int waypointY, int waypointZ,
                              String waypointLabel, int menuId, BlockPos center, long serverTick,
                              boolean online, Vec3 direction, int settingsRevision, SonarPulse pulse,
                              long shipId, Vec3 shipAnchor, int waypointDistance, float distortion) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(range);
        buf.writeVarIntArray(points);
        buf.writeVarInt(power);
        buf.writeByte(hx);
        buf.writeByte(hz);
        buf.writeVarInt(mode);
        buf.writeVarInt(depth);
        buf.writeVarInt(speedTenths);
        buf.writeVarInt(diveTenths);
        buf.writeVarInt(autopilot);
        buf.writeBoolean(hasTarget);
        if (hasTarget) {
            buf.writeVarInt(tx);
            buf.writeVarInt(ty);
            buf.writeVarInt(tz);
        }
        buf.writeUtf(status, 512);
        buf.writeVarInt(dockState);
        buf.writeVarInt(dockDistance);
        buf.writeUtf(dockLabel, 32);
        buf.writeBoolean(physical);
        writeVector(buf, hullOffset);
        buf.writeFloat(hullRotation.x());
        buf.writeFloat(hullRotation.y());
        buf.writeFloat(hullRotation.z());
        buf.writeFloat(hullRotation.w());
        buf.writeBoolean(hasWaypoint);
        if (hasWaypoint) {
            buf.writeVarInt(waypointX);
            buf.writeVarInt(waypointY);
            buf.writeVarInt(waypointZ);
            buf.writeUtf(waypointLabel, 32);
        }
        buf.writeVarInt(menuId);
        buf.writeBlockPos(center);
        buf.writeVarLong(serverTick);
        buf.writeBoolean(online);
        writeVector(buf, direction);
        buf.writeVarInt(settingsRevision);
        buf.writeLong(shipId);
        writeVector(buf, shipAnchor);
        buf.writeVarInt(waypointDistance);
        buf.writeFloat(distortion);
        buf.writeBoolean(pulse != null);
        if (pulse != null) {
            buf.writeVarLong(pulse.tick());
            buf.writeBlockPos(pulse.origin());
            buf.writeVarInt(pulse.range());
            buf.writeVarInt(pulse.mode().id());
            writeVector(buf, pulse.direction());
            buf.writeVarIntArray(pulse.points());
        }
    }

    public static SonarDataPacket decode(FriendlyByteBuf buf) {
        int range = buf.readVarInt();
        int[] points = buf.readVarIntArray(SonarScan.MAX_POINTS);
        int power = buf.readVarInt();
        int hx = buf.readByte();
        int hz = buf.readByte();
        int mode = buf.readVarInt();
        int depth = buf.readVarInt();
        int speed = buf.readVarInt();
        int dive = buf.readVarInt();
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
        String status = buf.readUtf(512);
        int dockState = buf.readVarInt();
        int dockDistance = buf.readVarInt();
        String dockLabel = buf.readUtf(32);
        boolean physical = buf.readBoolean();
        Vec3 hullOffset = readVector(buf);
        Quaternionf hullRotation = new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        boolean hasWaypoint = buf.readBoolean();
        int waypointX = 0;
        int waypointY = 0;
        int waypointZ = 0;
        String waypointLabel = "";
        if (hasWaypoint) {
            waypointX = buf.readVarInt();
            waypointY = buf.readVarInt();
            waypointZ = buf.readVarInt();
            waypointLabel = buf.readUtf(32);
        }
        int menuId = buf.readVarInt();
        BlockPos center = buf.readBlockPos();
        long serverTick = buf.readVarLong();
        boolean online = buf.readBoolean();
        Vec3 direction = readVector(buf);
        int settingsRevision = buf.readVarInt();
        long shipId = buf.readLong();
        Vec3 shipAnchor = readVector(buf);
        int waypointDistance = buf.readVarInt();
        float distortion = buf.readFloat();
        SonarPulse pulse = null;
        if (buf.readBoolean()) {
            pulse = new SonarPulse(buf.readVarLong(), buf.readBlockPos(), buf.readVarInt(),
                    SonarScan.Mode.byOrdinal(buf.readVarInt()),
                    readVector(buf),
                    buf.readVarIntArray(SonarScan.MAX_POINTS));
        }
        return new SonarDataPacket(range, points, power, hx, hz, mode, depth, speed, dive,
                autopilot, hasTarget, tx, ty, tz, status,
                dockState, dockDistance, dockLabel, physical, hullOffset, hullRotation,
                hasWaypoint, waypointX, waypointY, waypointZ, waypointLabel,
                menuId, center, serverTick, online, direction, settingsRevision, pulse,
                shipId, shipAnchor, waypointDistance, distortion);
    }

    private static void writeVector(FriendlyByteBuf buf, Vec3 vector) {
        buf.writeFloat((float) vector.x);
        buf.writeFloat((float) vector.y);
        buf.writeFloat((float) vector.z);
    }

    private static Vec3 readVector(FriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientSonarCache.accept(menuId, center, serverTick, online,
                direction, settingsRevision, pulse,
                range, points, power, hx, hz,
                mode, depth, speedTenths, diveTenths, autopilot, hasTarget, tx, ty, tz, status,
                dockState, dockDistance, dockLabel, physical, hullOffset, hullRotation,
                hasWaypoint, waypointX, waypointY, waypointZ, waypointLabel,
                shipId, shipAnchor, waypointDistance, distortion));
        context.setPacketHandled(true);
    }
}
