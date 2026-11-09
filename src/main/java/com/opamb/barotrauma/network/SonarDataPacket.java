package com.opamb.barotrauma.network;

import com.opamb.barotrauma.sonar.ClientSonarCache;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：一帧声纳点云 + 舵位 + 仪表读数 + 自动驾驶目标 + 状态文字
 * + 对接面板数据 + 左舷"舰况"图。
 *
 * <p>{@code speedTenths}/{@code diveTenths} 是 0.1 公里/时，整数传输避免浮点抖动。
 * {@code tx/ty/tz} 是目标点相对玩家位置的偏移（没有目标时 {@code hasTarget=false}）。</p>
 *
 * <p>对接（2026-11-08 声纳界面按原作重做）：</p>
 * <ul>
 *   <li>{@code dockState}：0 = 不在对接口范围，1 = 物理船在范围内可对接，2 = 方块船正停在码头；</li>
 *   <li>{@code dockDistance}：船核心到码头标准位的水平格数（没有站点时 −1）；</li>
 *   <li>{@code dockLabel}：站点名（起始哨站 / 任务前哨站）；</li>
 *   <li>{@code physical}：这艘船现在是 VS2 刚体还是方块船——右下按钮据此决定显示「离港」还是「对接」。</li>
 * </ul>
 *
 * <p>左舷舰况图：{@code coreDX/DY/DZ} 是船核心相对玩家的偏移，客户端用它把声纳点云里的
 * {@code TYPE_HULL} 点换算回船体局部坐标；{@code rooms} 是舱室侧视矩形，5 个 int 一组
 * （minX, maxX, minY, maxY, 进水百分比），坐标同样以核心为原点。</p>
 */
public record SonarDataPacket(int range, int[] points, int power, int hx, int hy, int hz,
                              int mode, int depth, int speedTenths, int diveTenths, int autopilot,
                              boolean hasTarget, int tx, int ty, int tz, String status, String core,
                              int dockState, int dockDistance, String dockLabel, boolean physical,
                              int hullPercent, int waterPercent,
                              int coreDX, int coreDY, int coreDZ, int[] rooms,
                              boolean hasWaypoint, int waypointX, int waypointY, int waypointZ,
                              String waypointLabel) {

    /** 舱室矩形每组的 int 数。 */
    public static final int ROOM_STRIDE = 5;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(range);
        buf.writeVarIntArray(points);
        buf.writeVarInt(power);
        buf.writeByte(hx);
        buf.writeByte(hy);
        buf.writeByte(hz);
        buf.writeVarInt(mode);
        buf.writeVarInt(depth);
        buf.writeVarInt(speedTenths);
        buf.writeVarInt(diveTenths);
        buf.writeBoolean(autopilot != 0);
        buf.writeBoolean(hasTarget);
        if (hasTarget) {
            buf.writeVarInt(tx);
            buf.writeVarInt(ty);
            buf.writeVarInt(tz);
        }
        buf.writeUtf(status, 512);
        buf.writeUtf(core, 1024);
        buf.writeVarInt(dockState);
        buf.writeVarInt(dockDistance);
        buf.writeUtf(dockLabel, 32);
        buf.writeBoolean(physical);
        buf.writeVarInt(hullPercent);
        buf.writeVarInt(waterPercent);
        buf.writeByte(coreDX);
        buf.writeByte(coreDY);
        buf.writeByte(coreDZ);
        buf.writeVarIntArray(rooms);
        buf.writeBoolean(hasWaypoint);
        if (hasWaypoint) {
            buf.writeVarInt(waypointX);
            buf.writeVarInt(waypointY);
            buf.writeVarInt(waypointZ);
            buf.writeUtf(waypointLabel, 32);
        }
    }

    public static SonarDataPacket decode(FriendlyByteBuf buf) {
        int range = buf.readVarInt();
        int[] points = buf.readVarIntArray(SonarScan.MAX_POINTS);
        int power = buf.readVarInt();
        int hx = buf.readByte();
        int hy = buf.readByte();
        int hz = buf.readByte();
        int mode = buf.readVarInt();
        int depth = buf.readVarInt();
        int speed = buf.readVarInt();
        int dive = buf.readVarInt();
        int autopilot = buf.readBoolean() ? 1 : 0;
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
        String core = buf.readUtf(1024);
        int dockState = buf.readVarInt();
        int dockDistance = buf.readVarInt();
        String dockLabel = buf.readUtf(32);
        boolean physical = buf.readBoolean();
        int hullPercent = buf.readVarInt();
        int waterPercent = buf.readVarInt();
        int coreDX = buf.readByte();
        int coreDY = buf.readByte();
        int coreDZ = buf.readByte();
        int[] rooms = buf.readVarIntArray(256);
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
        return new SonarDataPacket(range, points, power, hx, hy, hz, mode, depth, speed, dive,
                autopilot, hasTarget, tx, ty, tz, status, core,
                dockState, dockDistance, dockLabel, physical, hullPercent, waterPercent,
                coreDX, coreDY, coreDZ, rooms,
                hasWaypoint, waypointX, waypointY, waypointZ, waypointLabel);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientSonarCache.accept(range, points, power, hx, hy, hz,
                mode, depth, speedTenths, diveTenths, autopilot, hasTarget, tx, ty, tz, status, core,
                dockState, dockDistance, dockLabel, physical, hullPercent, waterPercent,
                coreDX, coreDY, coreDZ, rooms,
                hasWaypoint, waypointX, waypointY, waypointZ, waypointLabel));
        context.setPacketHandled(true);
    }
}
