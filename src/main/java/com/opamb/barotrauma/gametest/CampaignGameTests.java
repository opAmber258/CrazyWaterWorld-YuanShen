package com.opamb.barotrauma.gametest;

import com.mojang.authlib.GameProfile;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.LightBlockEntity;
import com.opamb.barotrauma.blockentity.PeriscopeBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.campaign.CampaignManager;
import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.campaign.RoundPhase;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.mission.MissionCargo;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.world.CampaignLevels;
import com.opamb.barotrauma.world.DeepSeaChunkGenerator;
import com.opamb.barotrauma.world.DockingBay;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import com.opamb.barotrauma.world.StarterSub;
import com.opamb.barotrauma.world.StationWaters;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(BarotraumaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CampaignGameTests {
    @GameTest(batch = "campaign_transitions", template = "empty_16x8x16", timeoutTicks = 400)
    public static void dockingMovesShipIntoStationAndSupportsReturn(GameTestHelper helper) {
        ServerLevel deep = helper.getLevel().getServer().getLevel(ModDimensions.THE_DEEP);
        try {
            checkTransfers(helper, deep);
        } finally {
            if (VsPilot.hasShip()) {
                VsPilot.dock(deep, true);
            }
        }
    }

    @GameTest(batch = "campaign_entrance_roof", template = "empty_16x8x16", timeoutTicks = 400)
    public static void routeEntrancesConnectToTheCeiling(GameTestHelper helper) {
        ServerLevel deep = helper.getLevel().getServer().getLevel(ModDimensions.THE_DEEP);
        var generator = (DeepSeaChunkGenerator) deep.getChunkSource().getGenerator();
        var connection = CampaignLevels.connection(0, 1);
        var route = connection.level(generator.terrainSeed(deep));
        DockingBay.Site entrance = connection.endpoint(route, 1);
        var origin = connection.endpoint(route, 0);
        for (DockingBay.Site site : List.of(origin, entrance)) {
            for (int y = -62; y <= -44; y++) {
                helper.assertTrue(deep.getBlockState(site.at(0, y, 0)).is(Blocks.STONE),
                        "航行关入口顶盖和岩壁之间有空隙");
            }
            helper.assertTrue(deep.isEmptyBlock(site.at(10, -62, 0))
                    && deep.isEmptyBlock(site.at(0, -74, 0)), "岩壁堵到入口侧边或泊位");
        }
        var oldGap = entrance.at(0, -62, 0);
        var placedBlock = oldGap.above();
        deep.setBlockAndUpdate(oldGap, Blocks.AIR.defaultBlockState());
        deep.setBlockAndUpdate(placedBlock, Blocks.GOLD_BLOCK.defaultBlockState());
        DockingBay.repairEntranceRoof(deep, entrance);
        helper.assertTrue(deep.getBlockState(oldGap).is(Blocks.STONE)
                && deep.getBlockState(placedBlock).is(Blocks.GOLD_BLOCK), "旧入口补岩没有保留已有方块");
        deep.setBlockAndUpdate(placedBlock, Blocks.STONE.defaultBlockState());
        helper.succeed();
    }

    private static void checkTransfers(GameTestHelper helper, ServerLevel deep) {
        var generator = (DeepSeaChunkGenerator) deep.getChunkSource().getGenerator();
        var connection = CampaignLevels.connection(0, 1);
        var route = connection.level(generator.terrainSeed(deep));
        DockingBay.Site entrance = connection.endpoint(route, 1);
        DockingBay.Site station = CampaignLevels.station(1);
        deep.getChunkAt(entrance.at(0, -68, 0));
        helper.assertTrue(DockingBay.hasBerth(deep, entrance), "航行关缺少对接入口");
        helper.assertTrue(!deep.isEmptyBlock(entrance.at(0, -64, 0)), "入口顶板漏水");
        helper.assertTrue(!OceanEnvironment.isInOutpostDryZone(deep, Vec3.atCenterOf(entrance.at(4, -62, 3))),
                "航行关入口外有虚构的干燥站区");
        var outside = entrance.at(640, -120, 0);
        deep.getChunkAt(outside);
        helper.assertTrue(deep.getBlockState(outside).isSolid(), "航行关外围连通全局海域");
        deep.getChunkAt(station.at(4, -62, 3));
        helper.assertTrue(DockingBay.hasBerth(deep, station)
                && OceanEnvironment.isInOutpostDryZone(deep, Vec3.atCenterOf(station.at(4, -62, 3))),
                "独立站区的码头或站内干区未生成");
        var openWater = station.at(72, -150, 64);
        var oldChunk = deep.getChunkAt(openWater);
        helper.assertTrue(deep.isEmptyBlock(openWater), "独立站区还被限制在旧椭球里");
        helper.assertTrue(deep.getBlockState(station.at(72, -22, 64)).isSolid(), "独立站区缺少顶部岩层");
        for (int[] sample : new int[][]{{2, 2}, {13, 2}, {8, 8}, {2, 13}, {13, 13}}) {
            deep.setBlockAndUpdate(new net.minecraft.core.BlockPos(oldChunk.getPos().getBlockX(sample[0]),
                    station.y(-132), oldChunk.getPos().getBlockZ(sample[1])), Blocks.STONE.defaultBlockState());
        }
        deep.setBlockAndUpdate(openWater, Blocks.STONE.defaultBlockState());
        deep.setBlockAndUpdate(openWater.above(), Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(StationWaters.repairLegacyChunk(deep, oldChunk)
                && deep.isEmptyBlock(openWater) && deep.getBlockState(openWater.above()).is(Blocks.GOLD_BLOCK),
                "旧站区迁移未打开岩层，或改变了人工方块");
        deep.removeBlock(openWater.above(), false);

        var startingCore = StarterSub.ensure(deep);
        helper.assertTrue(startingCore != null, "起始泊位没有潜艇");
        helper.assertTrue(deep.getBlockState(new BlockPos(2, -76, -5)).is(ModBlocks.DECONSTRUCTOR.get()),
                "初始船缺少工坊分解机");
        helper.assertTrue(deep.getBlockState(new BlockPos(0, DockingBay.SUB_HATCH_Y, 0))
                .is(ModShipBlocks.HATCH_LADDER.get()), "初始船对接舱口偏离竖井");
        var lowerTurret = new BlockPos(0, -83, -4);
        helper.assertTrue(deep.getBlockEntity(lowerTurret) instanceof TurretBlockEntity turret
                && Set.copyOf(turret.powerSourcePositions()).equals(
                        Set.of(new BlockPos(-3, -76, 0), new BlockPos(-1, -76, 0)))
                && new BlockPos(-5, -76, 0).equals(turret.loaderPos()), "倒装炮或供弹、供电绑定遗漏");
        helper.assertTrue(deep.getBlockEntity(new BlockPos(-4, -75, 0)) instanceof PeriscopeBlockEntity scope
                && lowerTurret.equals(scope.turretPos()), "潜望镜未绑定倒装炮");
        helper.assertTrue(deep.getBlockEntity(new BlockPos(-14, -79, -8)) instanceof LightBlockEntity lamp
                && new BlockPos(1, -78, 0).equals(lamp.powerBox()), "护罩灯的接线盒坐标未重定基");
        var stationCore = DockingBay.dockCore(station, StarterSub.layout(deep));
        var state = CampaignState.get(deep);
        // 本用例用真实潜艇和区块验证转场，不向无连接的测试玩家发送网络数据。
        ServerPlayer requester = new ServerPlayer(deep.getServer(), deep,
                new GameProfile(UUID.randomUUID(), "CampaignTest")) {
            @Override public void sendSystemMessage(Component message) {}
        };
        requester.setPos(Vec3.atCenterOf(startingCore.getBlockPos()));
        var origin = connection.endpoint(route, 0);
        helper.assertTrue(CampaignManager.startTravel(requester, 0, 1, List.of()), "首航没有成功进入航行关");
        helper.assertTrue(state.round().isDiving() && state.round().connection()
                && VsPilot.shipPosition().distanceToSqr(Vec3.atBottomCenterOf(origin.at(0, -80, 0))) < 4,
                "首航落点或战役状态错误");
        // 先在本端对接，模拟取消本趟。不会推进海图或多记完成轮数。
        moveToApproach(helper, deep, origin);
        helper.assertTrue(!VsPilot.hasShip() && state.currentNode() == 0
                && state.round().stationNode() == 0 && state.roundsCompleted() == 0, "返港未回原站");
        helper.assertTrue(CampaignManager.startTravel(requester, 0, 1, List.of()), "返港后无法再次出航");
        helper.assertTrue(state.round().levelSeed() == route.seed(), "重试改变了同一条航线的种子");

        deep.setBlockAndUpdate(stationCore, Blocks.GOLD_BLOCK.defaultBlockState());
        moveShipNear(helper, deep, entrance);
        String blocked = VsPilot.dock(deep);
        helper.assertTrue(VsPilot.hasShip() && state.round().isDiving()
                && deep.getBlockState(stationCore).is(Blocks.GOLD_BLOCK), "受阻靠泊不应拆船或推进战役：" + blocked);
        deep.removeBlock(stationCore, false);
        var data = MissionSavedData.get(deep);
        Mission cargo = data.missions().values().stream().filter(mission -> mission.type == MissionType.DELIVERY)
                .findFirst().orElseThrow();
        helper.assertTrue(data.accept(UUID.randomUUID(), cargo.id, 0, 1, deep.getGameTime()),
                "无法准备离线货主的测试委托");
        cargo.setCargo(1, 123);
        int creditsBefore = state.credits();
        var position = com.opamb.barotrauma.ship.vs.VsState.boardingPositions(deep, 1).get(0);
        var crate = new ItemStack(Items.CHEST);
        crate.getOrCreateTag().putInt(MissionCargo.TAG_MISSION, cargo.id);
        var loose = new ItemEntity(deep, position.x, position.y, position.z, crate);
        deep.addFreshEntity(loose);

        moveShipNear(helper, deep, entrance);
        Vec3 boarding = com.opamb.barotrauma.ship.vs.VsState.boardingPositions(deep, 1).get(0);
        var rider = new ServerPlayer(deep.getServer(), deep,
                new GameProfile(UUID.randomUUID(), "DockingTest"));
        new ServerGamePacketListenerImpl(deep.getServer(), new Connection(PacketFlow.SERVERBOUND), rider);
        rider.moveTo(boarding.x, boarding.y, boarding.z);
        deep.addNewPlayer(rider);
        try {
            var active = com.opamb.barotrauma.ship.vs.VsState.queryActiveShipId(deep);
            com.opamb.barotrauma.ship.vs.VsServerDrag.keepDragged(deep, rider, active);
            String message = VsPilot.dock(deep);
            helper.assertTrue(!VsPilot.hasShip(), "载人靠泊失败：" + message);
            helper.assertTrue(rider.position().distanceTo(Vec3.atCenterOf(stationCore)) < 24,
                    "乘员没有落到 B 站船内：" + rider.position());
            rider.moveTo(station.x() - 4, stationCore.getY(), station.z() - 252);
            helper.assertTrue(CampaignManager.rejoinScene(rider, deep, false)
                    && rider.position().distanceTo(Vec3.atCenterOf(stationCore)) < 24,
                    "旧档玩家没有回到已抵达站区的泊位：" + rider.position());
        } finally {
            deep.removePlayerImmediately(rider, Entity.RemovalReason.DISCARDED);
        }
        helper.assertTrue(cargo.state() == Mission.State.DONE
                && state.credits() == creditsBefore + 123 && loose.isRemoved(),
                "离线货主放在潜艇里的散货没有随船结算");
        helper.assertTrue(deep.getBlockEntity(stationCore) instanceof ShipCoreBlockEntity
                && state.currentNode() == 1 && state.round().phase() == RoundPhase.AT_NODE
                && state.round().stationNode() == 1 && !state.round().hasInstance(), "船或战役未进入独立 B 站");

        helper.assertTrue(CampaignManager.startTravel(requester, 1, 0, List.of()), "B 站无法启程返往起始站");
        helper.assertTrue(state.round().levelSeed() == route.seed()
                && CampaignLevels.destination(state.round()).equals(origin), "反向航行没有复用地图并交换端点");
        moveToApproach(helper, deep, origin);
        helper.assertTrue(state.currentNode() == 0 && state.roundsCompleted() == 2
                && StarterSub.starterCore(deep) != null, "完整返航没有把潜艇送回起始站");

        helper.assertTrue(CampaignManager.startTravel(requester, 0, 1, List.of()), "第二次去 B 站未能出航");
        moveToApproach(helper, deep, entrance);
        helper.assertTrue(CampaignManager.startTravel(requester, 1, 2, List.of()), "B 站无法出发前往无人航路点");
        var exit = CampaignLevels.destinationExit(state.round());
        helper.assertTrue(VsPilot.teleportShipTo(deep, Vec3.atCenterOf(exit)) == null
                && CampaignManager.exitAt(deep, VsPilot.shipPosition()) == 2, "无人航路点出口未识别");
        helper.assertTrue(CampaignManager.leaveAtExit(requester, deep)
                && state.currentNode() == 2 && state.round().phase() == RoundPhase.AT_NODE
                && VsPilot.hasShip() && state.round().stationNode() == -1, "无人出口没有停留在艇上并结束航程");
        helper.assertTrue(CampaignManager.startTravel(requester, 2, 1, List.of()), "无人航路点不能进入下一关");
        var relayEntrance = CampaignLevels.destination(state.round());
        moveToApproach(helper, deep, relayEntrance);
        helper.assertTrue(state.currentNode() == 1 && state.round().stationNode() == 1,
                "无人航路点返航未进入完整站区");
        helper.succeed();
    }

    private static void moveToApproach(GameTestHelper helper, ServerLevel level, DockingBay.Site approach) {
        moveShipNear(helper, level, approach);
        String message = VsPilot.dock(level);
        helper.assertTrue(!VsPilot.hasShip(), "靠泊失败：" + message);
    }

    private static void moveShipNear(GameTestHelper helper, ServerLevel level, DockingBay.Site approach) {
        var dockCore = DockingBay.dockCore(approach, StarterSub.layout(level));
        Vec3 target = new Vec3(dockCore.getX() + 0.5D, dockCore.getY(), dockCore.getZ() + 0.5D)
                .subtract(VsPilot.shipCoreOffset());
        String message = VsPilot.teleportShipTo(level, target);
        helper.assertTrue(message == null, "测试船无法移动：" + message);
    }
}
