package com.opamb.barotrauma.world;

import com.opamb.barotrauma.campaign.CampaignMap;
import com.opamb.barotrauma.campaign.MapNode;
import com.opamb.barotrauma.campaign.NodeType;
import com.opamb.barotrauma.campaign.RoundState;
import com.opamb.barotrauma.world.level.LevelSelector;
import com.opamb.barotrauma.world.level.LevelType;
import com.opamb.barotrauma.world.level.TerrainTemplate;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** 战役节点和双向连线在深海维度里的独立关卡。旧版正 X 航次留在原位。 */
public final class CampaignLevels {
    private static final int STATION_X = -4096;
    private static final int STATION_SPACING = 1024;
    private static final int ROUTE_X = -32768;
    private static final int ROUTE_SPACING = 4096;
    private static final int REGION_Z = 2048;
    private static final List<Connection> CONNECTIONS = connections();

    private CampaignLevels() {}

    private static List<Connection> connections() {
        List<Connection> result = new ArrayList<>();
        for (MapNode node : CampaignMap.nodes()) {
            for (int neighbor : node.neighbors()) {
                if (neighbor > node.id()) {
                    int index = result.size();
                    result.add(new Connection(node.id(), neighbor, index * 4, ROUTE_X - index * ROUTE_SPACING));
                }
            }
        }
        return List.copyOf(result);
    }

    public record Connection(int first, int second, int slot, int x) {
        public MissionInstance.RouteLevel level(long worldSeed) {
            boolean tutorial = first == CampaignMap.START_NODE && second == 1;
            int difficulty = LevelSelector.difficulty(CampaignMap.node(second).anchor().getY(), 0);
            long seed = LevelSelector.levelSeed(worldSeed, first * 65536 + second);
            LevelType type = LevelSelector.connectionType(tutorial, difficulty, seed);
            return new MissionInstance.RouteLevel(slot, type, seed, difficulty,
                    type.params().terrainRouteLength(), x, TerrainTemplate.forRoute(type, seed));
        }

        public DockingBay.Site endpoint(MissionInstance.RouteLevel level, int node) {
            return node == first ? MissionInstance.startSite(anchor(level), level.startDistance())
                    : new DockingBay.Site(x, 0);
        }
    }

    public static Connection connection(int a, int b) {
        int first = Math.min(a, b);
        int second = Math.max(a, b);
        for (Connection connection : CONNECTIONS) {
            if (connection.first() == first && connection.second() == second) {
                return connection;
            }
        }
        throw new IllegalArgumentException("没有这条航线：" + a + " → " + b);
    }

    public static Connection regionConnection(int x, int z) {
        if (z < -REGION_Z || z >= REGION_Z) {
            return null;
        }
        int index = Math.floorDiv(ROUTE_X - x + ROUTE_SPACING / 2, ROUTE_SPACING);
        return index >= 0 && index < CONNECTIONS.size() ? CONNECTIONS.get(index) : null;
    }

    public static MissionInstance.Anchor anchor(MissionInstance.RouteLevel level) {
        return new MissionInstance.Anchor(level.slot(), level.anchorX(),
                MissionInstance.destinationY(level.terrain()), 0);
    }

    public static boolean hasOutpost(int node) {
        MapNode location = CampaignMap.node(node);
        return location != null && location.type() == NodeType.OUTPOST;
    }

    public static DockingBay.Site station(int node) {
        return node == CampaignMap.START_NODE ? DockingBay.Site.MAIN
                : new DockingBay.Site(STATION_X - node * STATION_SPACING, 0);
    }

    static DockingBay.Site regionStation(int x, int z) {
        if (z < -256 || z >= 256) {
            return null;
        }
        int node = Math.floorDiv(STATION_X - x + STATION_SPACING / 2, STATION_SPACING);
        return node > 0 && hasOutpost(node) ? station(node) : null;
    }

    static boolean isStation(DockingBay.Site site) {
        return site.equals(regionStation(site.x(), site.z()));
    }

    public static boolean isEntrance(DockingBay.Site site) {
        return regionConnection(site.x(), site.z()) != null;
    }

    /** 纯坐标识别供客户端使用。泊位实际存在由中层舱口确认。 */
    static DockingBay.Site nearbySite(int x, int z, int radius) {
        DockingBay.Site station = regionStation(x, z);
        if (near(station, x, z, radius)) {
            return station;
        }
        Connection connection = regionConnection(x, z);
        if (connection == null) {
            return null;
        }
        if (hasOutpost(connection.second())) {
            DockingBay.Site end = new DockingBay.Site(connection.x(), 0);
            if (near(end, x, z, radius)) {
                return end;
            }
        }
        if (hasOutpost(connection.first())) {
            var anchor = new MissionInstance.Anchor(connection.slot(), connection.x(), 0, 0);
            for (LevelType type : LevelType.values()) {
                if (type.isConnection()) {
                    DockingBay.Site start = MissionInstance.startSite(anchor, type.params().terrainRouteLength());
                    if (near(start, x, z, radius)) {
                        return start;
                    }
                }
            }
        }
        return null;
    }

    private static boolean near(DockingBay.Site site, int x, int z, int radius) {
        return site != null && Math.abs(x - site.x()) <= radius && Math.abs(z - site.z()) <= radius;
    }

    static int stationOffset(DockingBay.Site site) {
        Connection connection = regionConnection(site.x(), site.z());
        return isStation(site) || (connection != null && site.x() == connection.x() && site.z() == 0)
                ? MissionInstance.DESTINATION_OFFSET_Y : 0;
    }

    public static DockingBay.Site origin(RoundState round) {
        MissionInstance.RouteLevel level = MissionInstance.routeLevel();
        if (round.connection()) {
            return connection(round.origin(), round.destination()).endpoint(level, round.origin());
        }
        return MissionInstance.startSite(anchor(level), level.startDistance());
    }

    public static DockingBay.Site destination(RoundState round) {
        if (round.connection()) {
            return connection(round.origin(), round.destination())
                    .endpoint(MissionInstance.routeLevel(), round.destination());
        }
        return new DockingBay.Site(round.instanceX(), round.instanceZ());
    }

    public static BlockPos destinationExit(RoundState round) {
        DockingBay.Site site = destination(round);
        int y = round.connection() ? site.y(MissionInstance.ANCHOR_Y) : MissionInstance.destinationY(round.terrain());
        return new BlockPos(site.x(), y, site.z());
    }
}
