package com.opamb.barotrauma.sonar;

import com.opamb.barotrauma.ship.NavigationMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * 客户端缓存的最近一次声纳扫描帧。
 *
 * <p>纯数据，不放任何客户端专用类型，这样网络包处理器可以直接调用。</p>
 */
public final class ClientSonarCache {

    private static SonarScan.Frame frame;
    private static final ArrayDeque<SonarPulse> pulses = new ArrayDeque<>();
    private static int menuId = -1;
    private static BlockPos center = BlockPos.ZERO;
    private static Vec3 direction = Vec3.ZERO;
    private static int settingsRevision;
    private static boolean online;
    private static String status = "";
    private static int power = 100;
    private static int headingX;
    private static int headingZ;
    private static int mode = SonarScan.Mode.ACTIVE.id();
    private static int depth;
    private static int speedTenths;
    private static int diveTenths;
    private static NavigationMode navigationMode = NavigationMode.MANUAL;
    private static boolean hasTarget;
    private static int targetX;
    private static int targetY;
    private static int targetZ;
    /** 对接：0 不可用 / 1 物理船在码头范围内 / 2 方块船停在码头。 */
    private static int dockState;
    private static int dockDistance;
    private static String dockLabel = "";
    /** 这艘船现在是 VS2 刚体（true）还是方块船（false）。 */
    private static boolean physical;
    private static long physicalShipId = -1L;
    private static Vec3 shipAnchor = Vec3.ZERO;
    private static int waypointDistance;
    private static float distortion;
    private static Vec3 hullOffset = Vec3.ZERO;
    private static Quaternionf hullRotation = new Quaternionf();
    /** 下一个目标点（相对玩家）：本轮任务的目的地前哨站；没有任务时不显示。 */
    private static boolean hasWaypoint;
    private static int waypointX;
    private static int waypointY;
    private static int waypointZ;
    private static String waypointLabel = "";

    private ClientSonarCache() {
    }

    public static void accept(int id, BlockPos scanCenter, long serverTick, boolean operating,
                              Vec3 beam, int revision, SonarPulse pulse, int range, int[] points,
                              int powerPercent, int hx, int hz,
                              int modeId, int depthMeters, int speed, int dive, int autopilotFlag,
                              boolean targetSet, int tx, int ty, int tz, String statusText,
                              int dockStateValue, int dockDistanceValue, String dockLabelValue,
                              boolean physicalValue, Vec3 hullOffsetValue, Quaternionf hullRotationValue,
                              boolean waypointSet, int wx, int wy, int wz, String waypointName,
                              long shipId, Vec3 anchor, int routeDistance, float damage) {
        if (id != menuId) {
            return;
        }
        center = scanCenter;
        online = operating;
        direction = beam;
        settingsRevision = Math.max(settingsRevision, revision);
        while (!pulses.isEmpty() && serverTick - pulses.getFirst().tick() >= SonarPulse.HISTORY_TICKS) {
            pulses.removeFirst();
        }
        if (!online) {
            pulses.clear();
        } else if (pulse != null && pulse.tick() > latestTick()) {
            pulses.addLast(pulse);
        }
        frame = new SonarScan.Frame(range, points);
        power = powerPercent;
        headingX = hx;
        headingZ = hz;
        mode = modeId;
        depth = depthMeters;
        speedTenths = speed;
        diveTenths = dive;
        navigationMode = NavigationMode.byId(autopilotFlag);
        hasTarget = targetSet;
        targetX = tx;
        targetY = ty;
        targetZ = tz;
        status = statusText;
        dockState = dockStateValue;
        dockDistance = dockDistanceValue;
        dockLabel = dockLabelValue;
        physical = physicalValue;
        physicalShipId = shipId;
        shipAnchor = anchor;
        waypointDistance = routeDistance;
        distortion = damage;
        hullOffset = hullOffsetValue;
        hullRotation = hullRotationValue;
        hasWaypoint = waypointSet;
        waypointX = wx;
        waypointY = wy;
        waypointZ = wz;
        waypointLabel = waypointName;
    }

    public static long physicalShipId() {
        return physicalShipId;
    }

    public static Vec3 shipAnchor() {
        return shipAnchor;
    }

    public static int waypointDistance() {
        return waypointDistance;
    }

    public static float distortion() {
        return distortion;
    }

    /** 本轮任务目的地是否要标在声纳上。 */
    public static boolean hasWaypoint() {
        return hasWaypoint;
    }

    public static int waypointX() {
        return waypointX;
    }

    public static int waypointY() {
        return waypointY;
    }

    public static int waypointZ() {
        return waypointZ;
    }

    public static String waypointLabel() {
        return waypointLabel;
    }

    /** 对接：0 不可用 / 1 物理船在码头范围内 / 2 方块船停在码头。 */
    public static int dockState() {
        return dockState;
    }

    public static int dockDistance() {
        return dockDistance;
    }

    public static String dockLabel() {
        return dockLabel;
    }

    public static boolean physical() {
        return physical;
    }

    public static Vec3 hullOffset() {
        return hullOffset;
    }

    public static Quaternionfc hullRotation() {
        return hullRotation;
    }

    public static SonarScan.Frame frame() {
        return frame;
    }

    public static String status() {
        return status;
    }

    public static int power() {
        return power;
    }

    public static int headingX() {
        return headingX;
    }

    public static int headingZ() {
        return headingZ;
    }

    public static SonarScan.Mode mode() {
        return SonarScan.Mode.byOrdinal(mode);
    }

    public static int depth() {
        return depth;
    }

    public static int speedTenths() {
        return speedTenths;
    }

    public static int diveTenths() {
        return diveTenths;
    }

    public static boolean autopilot() {
        return navigationMode.automatic();
    }

    public static NavigationMode navigationMode() {
        return navigationMode;
    }

    public static boolean hasTarget() {
        return hasTarget;
    }

    public static int targetX() {
        return targetX;
    }

    public static int targetY() {
        return targetY;
    }

    public static int targetZ() {
        return targetZ;
    }

    /** 目标点相对玩家的水平距离（格）。 */
    public static double targetDistance() {
        return hasTarget ? Math.sqrt((double) targetX * targetX + (double) targetY * targetY
                + (double) targetZ * targetZ) : 0.0D;
    }

    public static Vec3 direction() {
        return direction;
    }

    public static int settingsRevision() {
        return settingsRevision;
    }

    public static void open(int id) {
        menuId = id;
        frame = null;
        online = false;
        pulses.clear();
        center = BlockPos.ZERO;
        headingX = headingZ = 0;
        depth = speedTenths = diveTenths = 0;
        hasTarget = false;
        navigationMode = NavigationMode.MANUAL;
        status = "";
        settingsRevision = 0;
        // 防止重新打开时显示上一艘船的靠泊或轮廓数据。
        dockState = 0;
        dockDistance = -1;
        dockLabel = "";
        physical = false;
        physicalShipId = -1L;
        shipAnchor = Vec3.ZERO;
        waypointDistance = 0;
        distortion = 0.0F;
        hullOffset = Vec3.ZERO;
        hullRotation = new Quaternionf();
        hasWaypoint = false;
        waypointX = 0;
        waypointY = 0;
        waypointZ = 0;
        waypointLabel = "";
    }

    public static Collection<SonarPulse> pulses() {
        return pulses;
    }

    public static SonarPulse latestPulse() {
        return pulses.peekLast();
    }

    public static long latestTick() {
        SonarPulse pulse = latestPulse();
        return pulse == null ? -1L : pulse.tick();
    }

    public static boolean online() {
        return online;
    }

    public static BlockPos center() {
        return center;
    }
}
