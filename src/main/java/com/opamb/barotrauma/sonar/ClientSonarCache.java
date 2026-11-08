package com.opamb.barotrauma.sonar;

/**
 * 客户端缓存的最近一次声纳扫描帧。
 *
 * <p>纯数据，不放任何客户端专用类型，这样网络包处理器可以直接调用。</p>
 */
public final class ClientSonarCache {

    private static SonarScan.Frame frame;
    private static String status = "";
    private static int power = 100;
    private static int headingX;
    private static int headingY;
    private static int headingZ;
    private static int mode = SonarScan.Mode.ACTIVE.id();
    private static int depth;
    private static int speedTenths;
    private static int diveTenths;
    private static boolean autopilot;
    private static boolean hasTarget;
    private static int targetX;
    private static int targetY;
    private static int targetZ;
    /** 核心页正文（服务端拼好的多行文本：船体扫描 + 机器 + 实时状态）。 */
    private static String core = "";
    /** 对接：0 不可用 / 1 物理船在码头范围内 / 2 方块船停在码头。 */
    private static int dockState;
    private static int dockDistance;
    private static String dockLabel = "";
    /** 这艘船现在是 VS2 刚体（true）还是方块船（false）。 */
    private static boolean physical;
    /** 船壳完好度（0..100）。 */
    private static int hullPercent = 100;
    /** 全船进水比例（0..100）。 */
    private static int waterPercent;
    /** 船核心相对玩家的偏移（把声纳船体点换算回船体局部坐标）。 */
    private static int coreDX;
    private static int coreDY;
    private static int coreDZ;
    /** 舱室侧视矩形，5 个 int 一组（minX,maxX,minY,maxY,进水%）。 */
    private static int[] rooms = new int[0];
    /** 下一个目标点（相对玩家）：本轮任务的目的地前哨站；没有任务时不显示。 */
    private static boolean hasWaypoint;
    private static int waypointX;
    private static int waypointY;
    private static int waypointZ;
    private static String waypointLabel = "";
    private static long stamp;

    private ClientSonarCache() {
    }

    public static void accept(int range, int[] points, int powerPercent, int hx, int hy, int hz,
                              int modeId, int depthMeters, int speed, int dive, int autopilotFlag,
                              boolean targetSet, int tx, int ty, int tz, String statusText,
                              String coreText,
                              int dockStateValue, int dockDistanceValue, String dockLabelValue,
                              boolean physicalValue, int hullPercentValue, int waterPercentValue,
                              int cdx, int cdy, int cdz, int[] roomRects,
                              boolean waypointSet, int wx, int wy, int wz, String waypointName) {
        frame = new SonarScan.Frame(range, points);
        power = powerPercent;
        headingX = hx;
        headingY = hy;
        headingZ = hz;
        mode = modeId;
        depth = depthMeters;
        speedTenths = speed;
        diveTenths = dive;
        autopilot = autopilotFlag != 0;
        hasTarget = targetSet;
        targetX = tx;
        targetY = ty;
        targetZ = tz;
        status = statusText;
        core = coreText;
        dockState = dockStateValue;
        dockDistance = dockDistanceValue;
        dockLabel = dockLabelValue;
        physical = physicalValue;
        hullPercent = hullPercentValue;
        waterPercent = waterPercentValue;
        coreDX = cdx;
        coreDY = cdy;
        coreDZ = cdz;
        rooms = roomRects;
        hasWaypoint = waypointSet;
        waypointX = wx;
        waypointY = wy;
        waypointZ = wz;
        waypointLabel = waypointName;
        stamp = System.currentTimeMillis();
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

    public static int hullPercent() {
        return hullPercent;
    }

    public static int waterPercent() {
        return waterPercent;
    }

    public static int coreDX() {
        return coreDX;
    }

    public static int coreDY() {
        return coreDY;
    }

    public static int coreDZ() {
        return coreDZ;
    }

    /** 舱室侧视矩形，5 个 int 一组（minX,maxX,minY,maxY,进水%）。 */
    public static int[] rooms() {
        return rooms;
    }

    /** 核心页正文（多行）。 */
    public static String core() {
        return core;
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

    public static int headingY() {
        return headingY;
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
        return autopilot;
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

    public static long stamp() {
        return stamp;
    }

    /** 新数据到达后 1 秒内的亮度加成（0..1）。 */
    public static float flash() {
        if (stamp == 0L) {
            return 0.0F;
        }
        float age = (System.currentTimeMillis() - stamp) / 1000.0F;
        return Math.max(0.0F, 1.0F - age);
    }

    public static void clear() {
        frame = null;
        status = "";
        stamp = 0L;
        // 对接/舰况数据也清掉：重新打开界面时不要先闪一帧上一艘船的信息
        dockState = 0;
        dockDistance = -1;
        dockLabel = "";
        physical = false;
        hullPercent = 100;
        waterPercent = 0;
        coreDX = 0;
        coreDY = 0;
        coreDZ = 0;
        rooms = new int[0];
        hasWaypoint = false;
        waypointX = 0;
        waypointY = 0;
        waypointZ = 0;
        waypointLabel = "";
    }
}
