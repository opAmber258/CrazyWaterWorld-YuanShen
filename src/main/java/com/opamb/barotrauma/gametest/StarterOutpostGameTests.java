package com.opamb.barotrauma.gametest;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.EquipmentLayout;
import com.opamb.barotrauma.blockentity.DeconstructorBlockEntity;
import com.opamb.barotrauma.blockentity.FabricatorBlockEntity;
import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.OxygenGeneratorBlockEntity;
import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.entity.NpcEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.DockingBay;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.OceanEnvironment;
import com.opamb.barotrauma.world.StarterOutpost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(BarotraumaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterOutpostGameTests {
    private static final BlockPos DECK_MARK = new BlockPos(0, -55, 0);
    private static final BlockPos WEST_LIP = new BlockPos(-4, -55, 1);
    private static final BlockPos EAST_LIP = new BlockPos(-2, -55, 1);
    private static final BlockPos MESS_WALL = new BlockPos(-1, -54, -2);
    private static final BlockPos QUARTERS_WALL = new BlockPos(-1, -54, 2);
    private static final BlockPos OLD_EXIT = new BlockPos(-3, -62, 10);
    private static final BlockPos OLD_STEP = new BlockPos(-3, -63, 13);

    @GameTest(batch = "starter_outpost", template = "empty_16x8x16")
    public static void starterOutpostBuildsAndPowersFacilities(GameTestHelper helper) {
        ServerLevel deep = helper.getLevel().getServer().getLevel(ModDimensions.THE_DEEP);
        for (int x = -2; x <= 1; x++) {
            for (int z = -1; z <= 0; z++) deep.getChunk(x, z);
        }
        helper.assertTrue(deep.getBlockState(DECK_MARK).is(Blocks.WAXED_CUT_COPPER),
                "Fixture requires the revised modular starter outpost");
        var reactorPos = new BlockPos(-15, -62, 7);
        var boxPos = new BlockPos(-8, -62, 9);
        var fabricatorPos = new BlockPos(-17, -62, 9);
        var deconstructorPos = new BlockPos(-12, -62, 9);
        var oxygenPos = new BlockPos(-4, -62, -8);
        var lockerPos = new BlockPos(-16, -62, -8);
        for (var pos : List.of(reactorPos, fabricatorPos, deconstructorPos, lockerPos)) {
            var state = deep.getBlockState(pos);
            var layout = EquipmentLayout.at(pos, state);
            helper.assertTrue(layout.cells().size() > 1, "Missing facility at " + pos);
            for (var entry : layout.cells().entrySet()) {
                helper.assertTrue(entry.getValue() == deep.getBlockState(entry.getKey()), "Missing part at " + entry.getKey());
            }
        }
        var reactor = (ReactorBlockEntity) deep.getBlockEntity(reactorPos);
        var junction = (JunctionBoxBlockEntity) deep.getBlockEntity(boxPos);
        var fabricator = (FabricatorBlockEntity) deep.getBlockEntity(fabricatorPos);
        var deconstructor = (DeconstructorBlockEntity) deep.getBlockEntity(deconstructorPos);
        var oxygen = (OxygenGeneratorBlockEntity) deep.getBlockEntity(oxygenPos);
        var locker = (Container) deep.getBlockEntity(lockerPos);
        helper.assertTrue(locker.getItem(0).is(ModItems.DIVING_SUIT.get()), "Locker is missing the starter suit");
        var sign = (SignBlockEntity) deep.getBlockEntity(new BlockPos(-14, -62, 1));
        helper.assertTrue(sign != null && sign.isWaxed()
                && sign.getFrontText().getMessage(1, false).getContents() instanceof TranslatableContents text
                && text.getKey().equals("station.barotrauma.starter.engineering"),
                "Missing engineering sign");
        for (String role : List.of(NpcEntity.ROLE_MERCHANT, NpcEntity.ROLE_DOCTOR, NpcEntity.ROLE_MISSION)) {
            var spot = StarterOutpost.staffSpot(deep, DockingBay.Site.MAIN, role);
            helper.assertTrue(spot != null && deep.getBlockState(spot.below()).isSolid()
                    && deep.isEmptyBlock(spot) && deep.isEmptyBlock(spot.above()), "Blocked staff position: " + role);
        }
        helper.assertTrue(deep.getBlockState(new BlockPos(-3, -55, 1)).is(ModBlocks.IRON_LADDER.get()),
                "Ladder does not pass through the upper deck");
        helper.assertTrue(deep.getBlockState(new BlockPos(0, -63, 0)).is(ModBlocks.IRON_LADDER.get()),
                "Missing docking ladder");
        checkPassages(helper, deep);
        helper.assertTrue(deep.getBlockState(new BlockPos(-12, -62, 2)).is(ModShipBlocks.BULKHEAD_DOOR.get())
                && deep.getBlockState(new BlockPos(13, -54, -2)).is(ModShipBlocks.BULKHEAD_DOOR.get()),
                "Workshop or supply wing is missing its access");
        helper.assertTrue(OceanEnvironment.isInOutpostDryZone(deep, new Vec3(-12.5, -61.4, 7.5))
                && !OceanEnvironment.isInOutpostDryZone(deep, new Vec3(19.5, -61.4, 12.5)),
                "Starter outpost dry zone should follow its separate wings");

        var tank = new ItemStack(ModItems.OXYGEN_TANK.get());
        tank.setDamageValue(50);
        oxygen.setItem(0, tank);
        for (int tick = 0; tick < 300; tick++) {
            ReactorBlockEntity.serverTick(deep, reactorPos, reactor.getBlockState(), reactor);
            OxygenGeneratorBlockEntity.serverTick(deep, oxygenPos, oxygen.getBlockState(), oxygen);
        }
        FabricatorBlockEntity.serverTick(deep, fabricatorPos, fabricator.getBlockState(), fabricator);
        DeconstructorBlockEntity.serverTick(deep, deconstructorPos, deconstructor.getBlockState(), deconstructor);
        helper.assertTrue(reactor.state().capacityKw() == 20000.0D && reactor.lastRatio() > 0.9D,
                "Outpost reactor failed to supply its local circuit");
        helper.assertTrue(fabricator.powerAvailable() && deconstructor.powerAvailable(), "Workshop has no power");
        helper.assertTrue(oxygen.getItem(0).getDamageValue() < 50, "Oxygen machine did not refill the tank");
        junction.damage(200);
        FabricatorBlockEntity.serverTick(deep, fabricatorPos, fabricator.getBlockState(), fabricator);
        helper.assertTrue(!fabricator.powerAvailable(), "A broken junction box still supplied power");
        junction.applyRepair(200);
        checkExistingLayoutRepair(helper, deep);
        helper.succeed();
    }

    private static void checkPassages(GameTestHelper helper, ServerLevel deep) {
        var plating = ModShipBlocks.STATION_PLATING.get();
        for (int y = 0; y <= 2; y++) {
            helper.assertTrue(deep.getBlockState(OLD_EXIT.above(y)).is(plating), "Legacy exit still has an opening");
        }
        helper.assertTrue(deep.isEmptyBlock(OLD_STEP), "Removed sea exit still has a platform");
        helper.assertTrue(deep.isEmptyBlock(WEST_LIP) && deep.isEmptyBlock(EAST_LIP),
                "Upper landing trims still obstruct the ladder");
        helper.assertTrue(deep.getBlockState(MESS_WALL).is(plating)
                && deep.getBlockState(QUARTERS_WALL).is(plating), "Tower removed a room partition");
        helper.assertTrue(deep.isEmptyBlock(new BlockPos(1, -54, 2))
                && deep.getBlockState(new BlockPos(1, -54, 3)).is(ModShipBlocks.BULKHEAD_DOOR.get()),
                "Quarters door is blocked");
    }

    private static void checkExistingLayoutRepair(GameTestHelper helper, ServerLevel deep) {
        var plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        deep.setBlockAndUpdate(DECK_MARK, Blocks.CUT_COPPER.defaultBlockState());
        deep.setBlockAndUpdate(WEST_LIP, plating);
        deep.setBlockAndUpdate(EAST_LIP, plating);
        deep.setBlockAndUpdate(MESS_WALL, air);
        deep.setBlockAndUpdate(QUARTERS_WALL, air);
        var decoration = MESS_WALL.above();
        deep.setBlockAndUpdate(decoration, Blocks.GOLD_BLOCK.defaultBlockState());
        var door = ModShipBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BulkheadDoorBlock.FACING, Direction.SOUTH);
        deep.setBlockAndUpdate(OLD_EXIT, door.setValue(BulkheadDoorBlock.PART, BulkheadDoorBlock.Part.LOWER));
        deep.setBlockAndUpdate(OLD_EXIT.above(), door.setValue(BulkheadDoorBlock.PART, BulkheadDoorBlock.Part.UPPER));
        deep.setBlockAndUpdate(OLD_EXIT.above(2), air);
        deep.setBlockAndUpdate(OLD_STEP, Blocks.POLISHED_DEEPSLATE.defaultBlockState());

        DockingBay.repairStationShaft(deep);
        checkPassages(helper, deep);
        helper.assertTrue(deep.getBlockState(decoration).is(Blocks.GOLD_BLOCK), "Migration overwrote a player block");
        deep.setBlockAndUpdate(QUARTERS_WALL, air);
        DockingBay.repairStationShaft(deep);
        helper.assertTrue(deep.isEmptyBlock(QUARTERS_WALL), "Migration ran again after a player edit");
    }
}
