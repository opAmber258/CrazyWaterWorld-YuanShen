package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 哨站—潜艇对接口的几何约束：5×5 气闸室、3×1 竖井、三层舱口对齐、出生点在艇内。 */
class DockingBayTest {

    @Test
    @DisplayName("靠泊标准位：老小艇 (+1,−4)、旧儒艮号 (+6,0)、新儒艮号 (−2,−8)，副本站按站点偏移")
    void dockCoreFollowsLayout() {
        DockingBay.Site main = DockingBay.Site.MAIN;
        var legacy = DockingBay.dockCore(main, StarterSub.Layout.SMALL);
        assertEquals(StarterSub.ORIGIN_X + 5, legacy.getX());
        assertEquals(StarterSub.ORIGIN_Y + 1, legacy.getY());
        assertEquals(StarterSub.ORIGIN_Z + 4, legacy.getZ());

        var oldDugong = DockingBay.dockCore(main, StarterSub.Layout.DUGONG_V1);
        assertEquals(6, oldDugong.getX());
        assertEquals(-72, oldDugong.getY());
        assertEquals(0, oldDugong.getZ());

        var dugong = DockingBay.dockCore(main, StarterSub.Layout.DUGONG_V2);
        assertEquals(StarterSub.NEW_CORE_OFFSET_X, dugong.getX());
        assertEquals(StarterSub.NEW_CORE_OFFSET_Y, dugong.getY());
        assertEquals(StarterSub.NEW_CORE_OFFSET_Z, dugong.getZ());
        assertEquals(StarterSub.newCorePos(), dugong, "新布局码头核心必须就是儒艮号装配时的核心位");

        DockingBay.Site slot = DockingBay.Site.forSlot(2);
        var instanceDock = DockingBay.dockCore(slot, StarterSub.Layout.DUGONG_V2);
        assertEquals(StarterSub.newCorePos(slot.x(), slot.z()), instanceDock,
                "副本站的停靠核心同样按站点偏移");
    }

    @Test
    @DisplayName("主站和每个副本锚点都能被认成站点，远海不是")
    void sitesResolveForMainAndInstances() {
        assertEquals(DockingBay.Site.MAIN, DockingBay.siteAt(0, 0));
        assertEquals(DockingBay.Site.MAIN, DockingBay.siteAt(20, -12));
        DockingBay.Site slot0 = DockingBay.Site.forSlot(0);
        DockingBay.Site slot1 = DockingBay.Site.forSlot(1);
        assertEquals(MissionInstance.BASE_X, slot0.x());
        assertEquals(MissionInstance.BASE_X + MissionInstance.SPACING, slot1.x());
        assertEquals(slot0, DockingBay.siteAt(slot0.x(), 0));
        assertEquals(slot1, DockingBay.siteAt(slot1.x() + 12, 0));
        assertNull(DockingBay.siteAt(15000, 0));
        assertNull(DockingBay.siteAt(0, 200));    }

    @Test
    @DisplayName("副本站的舱口/核心判定跟着站点偏移走")
    void instanceSitePredicatesAreRelative() {
        DockingBay.Site site = DockingBay.Site.forSlot(3);
        assertTrue(DockingBay.isDockedCore(new net.minecraft.core.BlockPos(site.x() + 1, -72, site.z() - 3)));
        assertTrue(DockingBay.isStationDockHatch(new net.minecraft.core.BlockPos(site.x(), -64, site.z())));
        assertTrue(DockingBay.isStationDockHatch(new net.minecraft.core.BlockPos(site.x() + 1, -68, site.z())));
        assertTrue(DockingBay.isStationSideDoor(new net.minecraft.core.BlockPos(site.x() + 3, -67, site.z())));
        assertTrue(DockingBay.isDockCapRow(site.at(-1, -68, 0)));
        assertFalse(DockingBay.isDockCapRow(site.at(0, -68, 1)));
        assertFalse(DockingBay.isDockCapRow(site.at(2, -68, 0)));
        assertTrue(DockingBay.inDryZone(site.x() + 0.5D, site.z() + 0.5D, DockingBay.SUB_HATCH_Y));
    }

    @Test
    @DisplayName("儒艮号靠泊核心只认标称位和低一格，与 starterCore 同一判定")
    void newLayoutDockedCoreTolerance() {
        DockingBay.Site site = DockingBay.Site.forSlot(2);
        net.minecraft.core.BlockPos nominal = StarterSub.newCorePos(site.x(), site.z());
        assertTrue(DockingBay.isDockedCore(nominal), "标称核心位");
        assertTrue(DockingBay.isDockedCore(nominal.below()), "驾驶室改造后核心低一格");
        assertFalse(DockingBay.isDockedCore(nominal.below(2)), "再低一格就不是儒艮号核心");
        assertFalse(DockingBay.isDockedCore(nominal.above()));
        assertFalse(DockingBay.isDockedCore(nominal.east()), "水平偏一格不算靠泊");
        assertFalse(DockingBay.isDockedCore(nominal.offset(2, 0, 0)));
        assertFalse(DockingBay.isDockedCore(nominal.offset(0, 0, 2)));

        // 旧存档里的旧儒艮号（核心 +6,−72,0）仍然算靠泊，否则老档一靠泊就吸错位。
        net.minecraft.core.BlockPos oldNominal =
                StarterSub.Layout.DUGONG_V1.coreAt(site.x(), site.z());
        assertTrue(DockingBay.isDockedCore(oldNominal));
        assertTrue(DockingBay.isDockedCore(oldNominal.below()));
    }

    @Test
    @DisplayName("新儒艮号蓝图：塔顶舱口正对站点竖井，核心/出生点落在模板对应格")
    void buildTemplateHatchAlignsWithShaft() {
        StarterSub.Layout v2 = StarterSub.Layout.DUGONG_V2;
        // 模板尺寸 35×15×13。气闸塔顶舱口局部 (17..19, 14, 10)。
        assertEquals(35, v2.sizeX);
        assertEquals(15, v2.sizeY);
        assertEquals(13, v2.sizeZ);
        assertEquals(DockingBay.SUB_HATCH_Y, v2.originY + 14, "塔顶舱口在 -69");
        assertEquals(-1, v2.originX + 17);
        assertEquals(1, v2.originX + 19);
        assertEquals(0, v2.originZ + 10);
        // 核心 = 导航终端 lower_middle，模板局部 (16,7,2)。
        assertEquals(-2, v2.originX + 16);
        assertEquals(-76, v2.originY + 7);
        assertEquals(-8, v2.originZ + 2);

        // 出生点在下铺床边，必须落在船体包围盒里。
        var spawn = StarterSub.newSpawnPos();
        assertTrue(spawn.x > v2.originX && spawn.x < v2.originX + v2.sizeX);
        assertTrue(spawn.y > v2.originY && spawn.y < v2.originY + v2.sizeY);
        assertTrue(spawn.z > v2.originZ && spawn.z < v2.originZ + v2.sizeZ);
    }

    @Test
    @DisplayName("站内地板到潜艇顶舱口之间有四层结构")
    void dockLayersAreContinuous() {
        assertEquals(-64, DockingBay.STATION_FLOOR_Y);
        assertEquals(-63, DockingBay.STATION_FLOOR_UPPER_Y);
        assertEquals(-64, DockingBay.STATION_HATCH_Y);
        assertEquals(-68, DockingBay.MID_HATCH_Y);
        assertEquals(-69, DockingBay.SUB_HATCH_Y);
        // 气闸室 -67..-65，竖井 -71..-69，两段都是 3 高。
        assertEquals(-67, DockingBay.ROOM_MIN_Y);
        assertEquals(-65, DockingBay.ROOM_MAX_Y);
        assertEquals(3, DockingBay.ROOM_MAX_Y - DockingBay.ROOM_MIN_Y + 1);
        assertEquals(-69, DockingBay.SHAFT_TOP_Y);
        assertEquals(-71, DockingBay.SHAFT_BOTTOM_Y);
        assertEquals(3, DockingBay.SHAFT_TOP_Y - DockingBay.SHAFT_BOTTOM_Y + 1);
        // 层与层之间必须连续：站内地板 → 站侧舱口 → 气闸室 → 中层舱口 → 竖井 → 潜艇舱口。
        assertEquals(DockingBay.STATION_FLOOR_Y + 1, DockingBay.STATION_FLOOR_UPPER_Y);
        assertEquals(DockingBay.STATION_FLOOR_Y, DockingBay.STATION_HATCH_Y);
        assertEquals(DockingBay.ROOM_MAX_Y, DockingBay.STATION_HATCH_Y - 1);
        assertEquals(DockingBay.ROOM_MIN_Y, DockingBay.MID_HATCH_Y + 1);
        assertEquals(DockingBay.SHAFT_TOP_Y, DockingBay.MID_HATCH_Y - 1);
        assertEquals(DockingBay.SUB_HATCH_Y, DockingBay.MID_HATCH_Y - 1);
        assertEquals(DockingBay.SHAFT_BOTTOM_Y, DockingBay.SUB_HATCH_Y - 2);
    }

    @Test
    @DisplayName("气闸室 5×3，竖井 3 长 × 1 宽（用户存档）")
    void roomAndShaftFootprints() {
        assertTrue(DockingBay.inRoom(0, 0));
        assertTrue(DockingBay.inRoom(-2, -1));
        assertTrue(DockingBay.inRoom(2, 1));
        assertFalse(DockingBay.inRoom(3, 0), "气闸室 x 方向只有 5 格");
        assertFalse(DockingBay.inRoom(0, 2), "气闸室 z 方向只有 3 格");
        assertFalse(DockingBay.inRoom(0, -2), "气闸室 z 方向只有 3 格");

        assertTrue(DockingBay.inTube(0, 0));
        assertTrue(DockingBay.inTube(-1, 0));
        assertTrue(DockingBay.inTube(1, 0));
        assertFalse(DockingBay.inTube(0, 1), "竖井只有 1 格深");
        assertFalse(DockingBay.inTube(0, -1), "竖井只有 1 格深");
        assertFalse(DockingBay.inTube(2, 0), "竖井 x 方向只有 3 格");
    }

    @Test
    @DisplayName("干燥区覆盖气闸室和竖井，但不外溢到海里")
    void dryZoneCoversSealedDockOnly() {
        assertTrue(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.ROOM_MAX_Y));
        assertTrue(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.MID_HATCH_Y));
        assertTrue(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.SHAFT_TOP_Y));
        assertTrue(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.SUB_HATCH_Y));
        assertTrue(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.STATION_HATCH_Y), "站侧舱口层也属于通道");
        assertFalse(DockingBay.inDryZone(3.5D, 0.5D, DockingBay.ROOM_MAX_Y), "气闸室外墙外是海");
        assertFalse(DockingBay.inDryZone(0.5D, 2.5D, DockingBay.SHAFT_TOP_Y), "竖井外是海");
        assertFalse(DockingBay.inDryZone(0.5D, -1.5D, DockingBay.SUB_HATCH_Y + 0.5D), "旧井的外沿是海");
        assertFalse(DockingBay.inDryZone(0.5D, 1.0D, DockingBay.MID_HATCH_Y + 0.5D), "舱口外的中层格不借用干区");
        assertFalse(DockingBay.inDryZone(12.686D, 1.838D, -68.633D), "儒艮号顶上远离对接口的空气格是海");
        assertFalse(DockingBay.inDryZone(0.5D, 0.5D, DockingBay.STATION_FLOOR_UPPER_Y + 1),
                "站内地板以上归哨站干燥区，不由对接通道负责");
    }

    @Test
    @DisplayName("潜艇顶部舱口正对竖井，出生点在艇内")
    void hatchAlignsAndSpawnIsInside() {
        var spawn = StarterSub.spawnPos();
        assertTrue(spawn.x > StarterSub.ORIGIN_X && spawn.x < StarterSub.ORIGIN_X + StarterSub.SIZE_X);
        assertTrue(spawn.z > StarterSub.ORIGIN_Z && spawn.z < StarterSub.ORIGIN_Z + StarterSub.SIZE_Z);
        assertTrue(spawn.y > StarterSub.ORIGIN_Y && spawn.y < StarterSub.ORIGIN_Y + StarterSub.SIZE_Y);
        // 潜艇上移 3 格后，顶部舱口在 -69，紧贴中层平台舱口（-68）。
        assertEquals(DockingBay.SUB_HATCH_Y, StarterSub.ORIGIN_Y + StarterSub.SIZE_Y - 1);
        assertEquals(DockingBay.MID_HATCH_Y, DockingBay.SUB_HATCH_Y + 1);
    }

    @Test
    @DisplayName("潜艇水平中心对准竖井中心")
    void subIsCenteredUnderPort() {
        double centerX = StarterSub.ORIGIN_X + StarterSub.SIZE_X / 2.0D;
        double centerZ = StarterSub.ORIGIN_Z + StarterSub.SIZE_Z / 2.0D;
        assertEquals(DockingBay.X + 0.5D, centerX, 0.01D);
        assertEquals(DockingBay.Z + 0.5D, centerZ, 0.01D);
    }
}
