package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelType;
import com.opamb.barotrauma.world.level.TerrainTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTerrainTest {
    private static final long WORLD_SEED = 4811L;
    private static final long ROUTE_SEED = 9371L;

    @Test
    void dimensionsUseOriginalScaleAndExistingVerticalBoundary() {
        LevelParams basic = LevelType.BASIC.params();
        assertEquals(1040, basic.terrainWidth());
        assertEquals(832, basic.terrainCrossWidth());
        assertEquals(780, basic.terrainHeight());
        assertEquals(601, basic.terrainRouteLength());
        assertEquals(1950, LevelType.ADVANCED.params().terrainWidth());
        assertEquals(910, LevelType.ADVANCED.params().terrainHeight());
        assertEquals(956, LevelType.MAZE.params().terrainHeight());
    }

    @Test
    void basinKeepsBroadWaterAndReachesDeeperThanCanyon() {
        RouteTerrain canyon = terrain(TerrainTemplate.CANYON);
        RouteTerrain sea = terrain(TerrainTemplate.OPEN_SEA);
        RouteTerrain.Column center = sea.column(WORLD_SEED, 300, 0);
        RouteTerrain.Column canyonSide = canyon.column(WORLD_SEED, 300, 300);
        RouteTerrain.Column seaSide = sea.column(WORLD_SEED, 300, 300);
        assertNotNull(center);
        assertNotNull(canyonSide);
        assertNotNull(seaSide);
        assertTrue(center.floor() < -650);
        assertTrue(center.ceiling() < -115);
        assertTrue(seaSide.ceiling() - seaSide.floor() >= 300, seaSide.toString());
        assertTrue(canyonSide.ceiling() - canyonSide.floor() < 72, canyonSide.toString());
        assertNull(sea.column(WORLD_SEED, 300, 450));
        assertEquals(center, terrain(TerrainTemplate.OPEN_SEA).column(WORLD_SEED, 300, 0));
    }

    @Test
    void mainRouteDescendsAndRemainsClearForLargeShipSamples() {
        for (TerrainTemplate template : new TerrainTemplate[]{TerrainTemplate.CANYON, TerrainTemplate.OPEN_SEA}) {
            for (LevelType type : new LevelType[]{LevelType.TUTORIAL, LevelType.BASIC, LevelType.ADVANCED, LevelType.MAZE}) {
                for (long seed : new long[]{0L, 8L, ROUTE_SEED}) {
                    int length = type.params().terrainRouteLength();
                    RoutePath path = RoutePath.build(type.params(), seed, 0, 0, length, 0, template);
                    RouteTerrain terrain = terrain(type, template, seed, path);
                    assertTrue(path.pointAt(0.5D).y() < -175.0D, type + " " + template);
                    for (int t = 0; t <= 50; t++) {
                        RoutePath.Point point = path.pointAt(t / 50.0D);
                        int x = (int) Math.round(point.x());
                        int z = (int) Math.round(point.z());
                        RouteTerrain.Column sample = terrain.column(WORLD_SEED, x, z);
                        assertNotNull(sample);
                        assertTrue(sample.floor() < point.y() - 28, type + " floor at " + point);
                        assertTrue(sample.ceiling() > point.y() + 24, type + " roof at " + point);
                        assertTrue(!sample.hasRockAt((int) point.y() + 20), type + " arch at " + point);
                        RoutePath.Point before = path.pointAt((t - 0.1D) / 50.0D);
                        RoutePath.Point after = path.pointAt((t + 0.1D) / 50.0D);
                        double dx = after.x() - before.x();
                        double dz = after.z() - before.z();
                        double heading = Math.hypot(dx, dz);
                        for (int longitudinal = -40; longitudinal <= 40; longitudinal += 10) {
                            for (int lateral = -15; lateral <= 15; lateral += 5) {
                                int sampleX = (int) Math.round(point.x() + (dx * longitudinal - dz * lateral) / heading);
                                int sampleZ = (int) Math.round(point.z() + (dz * longitudinal + dx * lateral) / heading);
                                RouteTerrain.Column box = terrain.column(WORLD_SEED, sampleX, sampleZ);
                                assertNotNull(box);
                                int y = (int) Math.round(point.y());
                                assertTrue(box.floor() < y - 12 && box.ceiling() > y + 12
                                                && (box.rockBottom() > box.rockTop() || box.rockBottom() > y + 12
                                                    || box.rockTop() < y - 12),
                                        type + " " + template + " T3 sample " + t + " at " + sampleX + "," + y + "," + sampleZ
                                                + " in " + box);
                            }
                        }
                    }
                    RouteTerrain.Column berth = terrain.column(WORLD_SEED, length, 0);
                    assertEquals(LevelParams.BAND_CEILING_Y + MissionInstance.DESTINATION_OFFSET_Y, berth.ceiling());
                    assertEquals(MissionInstance.ANCHOR_Y, path.pointAt(0).y());
                    assertEquals(MissionInstance.destinationY(template), path.pointAt(1).y());
                }
            }
        }
    }

    @Test
    void deepOutpostMovesDockAndUsesRealRoomsAsDryBoundary() {
        LevelType type = LevelType.BASIC;
        int length = type.params().terrainRouteLength();
        int x = MissionInstance.BASE_X;
        var route = new MissionInstance.RouteLevel(0, type, ROUTE_SEED, 0, length, x, TerrainTemplate.CANYON);
        MissionInstance.publishRoute(route, 0, true, true);
        try {
            var site = DockingBay.Site.forSlot(0);
            var start = MissionInstance.startSite(MissionInstance.anchorForRound(0));
            assertEquals(-80, MissionInstance.ANCHOR_Y);
            assertEquals(-152, MissionInstance.anchorForRound(0).y());
            assertEquals(-64, start.at(0, DockingBay.STATION_HATCH_Y, 0).getY());
            assertEquals(-136, site.at(0, DockingBay.STATION_HATCH_Y, 0).getY());
            assertTrue(DockingBay.isStationDockHatch(site.at(0, DockingBay.MID_HATCH_Y, 0)));
            assertEquals(-148, DockingBay.dockCore(site, StarterSub.Layout.DUGONG_V2).getY());
            assertTrue(DockingBay.inDryZone(x + 0.5D, 0.5D, -137.5D));
            assertTrue(HabitationOutpost.inside(17, -62, 0));
            assertTrue(HabitationOutpost.inside(-17, -62, 0));
            assertTrue(HabitationOutpost.inside(0, -52, 2));
            assertTrue(!HabitationOutpost.inside(16, -58, 13));
            RouteTerrain active = MissionInstance.activeTerrain();
            assertNotNull(active);
            assertEquals(-116, active.column(WORLD_SEED, x, 0).ceiling());
            assertEquals(-44, active.column(WORLD_SEED, start.x(), start.z()).ceiling());
            MissionInstance.publishRoute(route, 0, true, false);
            assertEquals(-64, site.at(0, DockingBay.STATION_HATCH_Y, 0).getY());
            assertEquals(-152, MissionInstance.anchorForRound(0).y());
        } finally {
            MissionInstance.publishRoute(null, Integer.MIN_VALUE, true, true);
        }
    }

    private static RouteTerrain terrain(TerrainTemplate template) {
        LevelType type = LevelType.BASIC;
        int length = type.params().terrainRouteLength();
        RoutePath path = RoutePath.build(type.params(), ROUTE_SEED, 0, 0, length, 0, template);
        return terrain(type, template, ROUTE_SEED, path);
    }

    private static RouteTerrain terrain(LevelType type, TerrainTemplate template, long seed, RoutePath path) {
        int length = type.params().terrainRouteLength();
        return new RouteTerrain(new MissionInstance.RouteLevel(0, type, seed, 5, length, length, template),
                new DockingBay.Site(0, 0), new MissionInstance.Anchor(0, length, -80, 0), path);
    }
}
