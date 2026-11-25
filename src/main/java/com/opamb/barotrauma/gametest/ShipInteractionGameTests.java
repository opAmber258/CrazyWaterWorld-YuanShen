package com.opamb.barotrauma.gametest;

import com.mojang.authlib.GameProfile;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.blockentity.BulkheadDoorBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.AutomationUtil;
import com.opamb.barotrauma.world.AirOceanSwimmer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BarotraumaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipInteractionGameTests {

    private ShipInteractionGameTests() {
    }

    @GameTest(template = "empty_16x8x16", templateNamespace = BarotraumaMod.MOD_ID)
    public static void sonarMenuStaysBoundToItsOpeningTerminal(GameTestHelper helper) {
        BlockPos first = new BlockPos(4, 2, 4);
        BlockPos second = first.east(3);
        helper.setBlock(first, ModShipBlocks.SONAR.get());
        helper.setBlock(second, ModShipBlocks.SONAR.get());
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "sonar-test"));
        Vec3 closerToSecond = Vec3.atCenterOf(helper.absolutePos(second.south()));
        player.setPos(closerToSecond.x, closerToSecond.y, closerToSecond.z);

        BlockPos terminalPos = helper.absolutePos(first);
        var openedTerminal = helper.getLevel().getBlockEntity(terminalPos);
        SonarMenu menu = new SonarMenu(1, player.getInventory(), terminalPos, 96);
        helper.assertTrue(openedTerminal != null && menu.terminal() == openedTerminal && menu.stillValid(player),
                "另一个声纳台更近时，菜单仍应绑定最初打开的终端");
        helper.setBlock(first, Blocks.AIR);
        helper.setBlock(first, ModShipBlocks.SONAR.get());
        helper.assertTrue(!menu.stillValid(player), "同一格重新放置终端后，旧的菜单应失效");
        helper.succeed();
    }

    @GameTest(template = "empty_16x8x16", templateNamespace = BarotraumaMod.MOD_ID)
    public static void sensorDoorCanOpenManuallyOutsideRange(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 2, 5);
        BlockPos door = new BlockPos(6, 2, 5);
        helper.setBlock(core, ModShipBlocks.SONAR.get());
        helper.setBlock(door.below(), ModShipBlocks.TEST_HULL.get());
        helper.setBlock(door.below().east(), ModShipBlocks.TEST_HULL.get());
        Player player = helper.makeMockPlayer();
        Vec3 playerPos = Vec3.atCenterOf(helper.absolutePos(door.south(4)));
        player.setPos(playerPos.x, playerPos.y, playerPos.z);
        ItemStack doorItem = new ItemStack(ModItems.BULKHEAD_DOOR.get());
        BlockPos floor = helper.absolutePos(door.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
        ((BlockItem) doorItem.getItem()).place(new BlockPlaceContext(
                player, InteractionHand.MAIN_HAND, doorItem, hit));
        var level = helper.getLevel();
        BlockPos doorPos = helper.absolutePos(door);
        if (!(level.getBlockEntity(doorPos) instanceof BulkheadDoorBlockEntity sensor)
                || !(level.getBlockEntity(helper.absolutePos(core)) instanceof ShipCoreBlockEntity sonar)) {
            helper.fail("舱门和船核心应当已放置");
            return;
        }
        helper.assertTrue(sonar.rescan(level).tracksHullBlock(doorPos), "舱门应当属于测试船");
        sensor.cycleAutomation();
        helper.useBlock(door, player);
        helper.runAfterDelay(10, () -> helper.assertBlockProperty(door, BulkheadDoorBlock.OPEN, true));
        helper.runAfterDelay(AutomationUtil.MOTION_HOLD_TICKS + 2, () -> {
            helper.assertBlockProperty(door, BulkheadDoorBlock.OPEN, false);
            helper.succeed();
        });
    }

    @GameTest(template = "empty_16x8x16", templateNamespace = BarotraumaMod.MOD_ID)
    public static void periscopeNeedsStandAndUsesTurretCamera(GameTestHelper helper) {
        BlockPos scope = new BlockPos(4, 2, 3);
        BlockPos beside = scope.west();
        BlockPos turretCell = new BlockPos(12, 2, 3);
        helper.setBlock(beside, Blocks.IRON_BLOCK);
        helper.setBlock(turretCell.below(), Blocks.IRON_BLOCK);
        helper.setBlock(turretCell, ModBlocks.TURRET.get());

        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "periscope-test"));
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(scope.south()));
        player.setPos(position.x, position.y, position.z);
        ItemStack item = new ItemStack(ModItems.PERISCOPE.get());
        BlockItem periscope = (BlockItem) item.getItem();
        BlockPos anchor = helper.absolutePos(beside);
        var hit = new BlockHitResult(Vec3.atCenterOf(anchor), Direction.EAST, anchor, false);
        var placement = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, item, hit);
        helper.assertTrue(periscope.place(placement) == InteractionResult.FAIL,
                "没有底座时不能放置潜望镜");
        helper.setBlock(scope.below(), ModBlocks.PERISCOPE_STAND.get());
        helper.assertTrue(periscope.place(placement).consumesAction(), "有底座时应可放置");

        BlockPos scopePos = helper.absolutePos(scope);
        var scopeState = helper.getLevel().getBlockState(scopePos);
        scopeState.use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(scopePos), Direction.SOUTH, scopePos, false));
        BlockPos turretPos = helper.absolutePos(turretCell);
        if (!(helper.getLevel().getBlockEntity(turretPos) instanceof TurretBlockEntity turret)) {
            helper.fail("炮塔方块实体未加载");
            return;
        }
        var packet = turret.statePacket();
        turret.exit(player);
        helper.assertTrue(scopePos.equals(packet.controllerPos()), "潜望镜应保留操作位置");
        helper.assertTrue(packet.cameraAnchor()
                        .equals(TurretBlockEntity.cameraPosition(turret.getBlockState(), turretPos)),
                "潜望镜视角应使用炮塔的镜头坐标");
        helper.succeed();
    }

    @GameTest(template = "empty_16x8x16", templateNamespace = BarotraumaMod.MOD_ID)
    public static void vanillaTickKeepsAirOceanSwimming(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        AirOceanSwimmer swimmer = (AirOceanSwimmer) player;
        swimmer.barotrauma$setAirOceanSwimState(true, true);

        player.updateSwimming();
        player.setSwimming(false);
        player.updateSwimming();
        helper.assertTrue(player.isSwimming() && player.getPose() == Pose.SWIMMING,
                "原版更新与 VS2 的直接清除都要保留假水泳姿");

        swimmer.barotrauma$setAirOceanSwimState(true, false);
        helper.assertTrue(swimmer.barotrauma$isInAirOceanWater() && !player.isSwimming(),
                "松开疾跑时仍在水里移动，清除疾游标志");

        swimmer.barotrauma$setAirOceanSwimState(false, true);
        helper.assertTrue(!swimmer.barotrauma$isInAirOceanWater() && !player.isSwimming(),
                "干燥位置即使按疾跑也不能进入疾游");
        helper.succeed();
    }
}
