package com.opamb.barotrauma.gametest;

import com.mojang.authlib.GameProfile;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CoilgunBlock;
import com.opamb.barotrauma.block.EquipmentLayout;
import com.opamb.barotrauma.block.EngineBlock;
import com.opamb.barotrauma.block.TurretBlock;
import com.opamb.barotrauma.blockentity.CoilgunLoaderBlockEntity;
import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import com.opamb.barotrauma.blockentity.PeriscopeBlockEntity;
import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.entity.CoilgunBolt;
import com.opamb.barotrauma.entity.CoilgunInteractionEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.item.WrenchItem;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(BarotraumaMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoilgunGameTests {
    private CoilgunGameTests() {
    }

    @GameTest(template = "empty_16x8x16")
    public static void coilgunMountPartsResolveAndBreakTogether(GameTestHelper helper) {
        BlockPos cell = new BlockPos(6, 2, 6);
        helper.setBlock(cell.below(), Blocks.IRON_BLOCK);
        FakePlayer player = playerAt(helper, cell.north());
        ItemStack gun = new ItemStack(ModItems.COILGUN.get());
        BlockPos support = helper.absolutePos(cell.below());
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, gun, hit(support, Direction.UP));
        helper.assertTrue(((BlockItem) gun.getItem()).place(context).consumesAction(), "Coilgun placement rejected");
        BlockPos master = helper.absolutePos(cell);
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockEntity(master) instanceof TurretBlockEntity, "Missing central turret entity");
        for (BlockPos part : BlockPos.betweenClosed(master.offset(-2, 0, -2), master.offset(2, 0, 2))) {
            helper.assertTrue(TurretBlockEntity.at(level, part) == level.getBlockEntity(master),
                    "Mount section did not resolve to its central turret");
        }
        level.destroyBlock(master.offset(2, 0, -2), false);
        helper.assertTrue(level.getBlockState(master).isAir(), "Breaking a mount section left the turret behind");
        helper.assertTrue(level.getBlockState(master.offset(-2, 0, 2)).isAir(), "Mount left an orphan section");
        helper.succeed();
    }

    @GameTest(template = "empty_16x8x16")
    public static void wrenchRecoversOneCoilgunFromItsOuterSection(GameTestHelper helper) {
        BlockPos cell = new BlockPos(6, 2, 6);
        helper.setBlock(cell.below(), Blocks.IRON_BLOCK);
        FakePlayer player = playerAt(helper, cell.north(4));
        ItemStack gun = new ItemStack(ModItems.COILGUN.get());
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, gun,
                hit(helper.absolutePos(cell.below()), Direction.UP));
        helper.assertTrue(((BlockItem) gun.getItem()).place(context).consumesAction(), "Coilgun placement rejected");
        var level = helper.getLevel();
        BlockPos master = helper.absolutePos(cell);
        BlockPos rim = master.north(2);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        player.setShiftKeyDown(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(rim).add(0, -0.44, 0.4));
        Vec3 eye = player.getEyePosition();
        var actualHit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(player.getBlockReach())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        helper.assertTrue(rim.equals(actualHit.getBlockPos()) && actualHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                "Test did not target the outer mount section: " + actualHit.getBlockPos() + " instead of " + rim);
        var protection = new ProtectedSection(level, master.south(2));
        MinecraftForge.EVENT_BUS.register(protection);
        try {
            helper.assertTrue(!WrenchItem.dismantleAt(player, rim).consumesAction(), "Wrench bypassed a protected section");
            helper.assertTrue(level.getBlockState(master).is(ModBlocks.COILGUN.get()), "Protection allowed partial dismantle");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protection);
        }
        // The see-through reactor menu proxy must not mask the actual device behind it.
        BlockPos proxy = rim.north();
        level.setBlockAndUpdate(proxy, com.opamb.barotrauma.registry.ModShipBlocks.REACTOR_PROXY.get().defaultBlockState());
        var proxyHit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(player.getBlockReach())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        helper.assertTrue(proxy.equals(proxyHit.getBlockPos()), "Menu proxy did not intercept the ordinary ray");
        helper.assertTrue(player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                InteractionHand.MAIN_HAND, proxyHit).consumesAction(), "Wrench use rejected");
        for (BlockPos part : BlockPos.betweenClosed(master.offset(-2, 0, -2), master.offset(2, 0, 2))) {
            helper.assertTrue(level.getBlockState(part).isAir(), "An orphan coilgun section remained");
        }
        helper.assertTrue(player.getInventory().countItem(ModItems.COILGUN.get()) == 1, "Expected one recovered gun");
        helper.assertTrue(level.getBlockState(master.below()).is(Blocks.IRON_BLOCK), "Wrench removed the deck");
        helper.assertTrue(!WrenchItem.dismantleAt(player, rim).consumesAction(), "Repeat dismantle recovered another gun");
        helper.succeed();
    }

    public record ProtectedSection(ServerLevel level, BlockPos pos) {
        @SubscribeEvent
        public void protect(BlockEvent.BreakEvent event) {
            if (event.getLevel() == level && event.getPos().equals(pos)) event.setCanceled(true);
        }
    }

    @GameTest(template = "empty_16x8x16")
    public static void creativeWrenchReturnsCabinetContentsWithoutAnotherCabinet(GameTestHelper helper) {
        BlockPos master = helper.absolutePos(new BlockPos(6, 2, 6));
        var level = helper.getLevel();
        var cabinet = ModBlocks.MEDIUM_STEEL_CABINET.get();
        var state = cabinet.defaultBlockState();
        var layout = EquipmentLayout.at(master, state);
        layout.cells().forEach((part, section) -> level.setBlockAndUpdate(part, section));
        var container = (net.minecraft.world.Container) level.getBlockEntity(master);
        container.setItem(0, new ItemStack(Items.DIAMOND, 7));
        FakePlayer player = playerAt(helper, new BlockPos(6, 2, 4));
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        player.setShiftKeyDown(true);
        helper.assertTrue(player.isCreative(), "Test did not enter creative mode");
        helper.assertTrue(WrenchItem.dismantleAt(player, master.above()).consumesAction(), "Cabinet dismantle rejected");
        helper.assertTrue(layout.cells().keySet().stream().allMatch(pos -> level.getBlockState(pos).isAir()),
                "An orphan cabinet section remained");
        helper.assertTrue(player.getInventory().countItem(cabinet.asItem()) == 0, "Creative issued a cabinet");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7, "Cabinet contents were not recovered once");
        helper.succeed();
    }

    @GameTest(template = "empty_16x8x16")
    public static void survivalWrenchKeepsEngineWearAfterReinstallation(GameTestHelper helper) {
        var level = helper.getLevel();
        var engine = ModBlocks.ENGINE.get();
        BlockPos master = helper.absolutePos(new BlockPos(6, 2, 6));
        var layout = EquipmentLayout.at(master, engine.defaultBlockState());
        layout.cells().forEach((part, section) -> level.setBlockAndUpdate(part, section));
        var installed = (EngineBlockEntity) level.getBlockEntity(master);
        installed.damage(12);
        double condition = installed.condition();
        FakePlayer player = playerAt(helper, new BlockPos(6, 2, 4));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        player.setShiftKeyDown(true);
        BlockPos outer = layout.cells().keySet().stream().filter(pos -> pos.getY() > master.getY()).findFirst().orElseThrow();
        helper.assertTrue(WrenchItem.dismantleAt(player, outer).consumesAction(), "Engine dismantle rejected");
        helper.assertTrue(layout.cells().keySet().stream().allMatch(pos -> level.getBlockState(pos).isAir()),
                "An orphan engine section remained");
        helper.assertTrue(player.getInventory().countItem(engine.asItem()) == 1, "Expected one recovered engine");
        ItemStack packed = player.getInventory().items.stream().filter(stack -> stack.is(engine.asItem())).findFirst().orElseThrow();
        var facing = player.getDirection().getOpposite();
        for (var part : EngineBlock.Part.values()) {
            if (part.row() == 0 && part.layer() < 2) {
                level.setBlockAndUpdate(EngineBlock.partPos(master, facing, part).below(), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        player.setShiftKeyDown(false);
        var placement = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, packed, hit(master.below(), Direction.UP));
        helper.assertTrue(((BlockItem) packed.getItem()).place(placement).consumesAction(), "Recovered engine did not place");
        var reinstalled = (EngineBlockEntity) level.getBlockEntity(master);
        helper.assertTrue(reinstalled.condition() == condition, "Reinstalling the engine changed its condition");
        helper.succeed();
    }

    @GameTest(template = "empty_16x8x16", timeoutTicks = 35)
    public static void linkedCoilgunOnlySpendsOnAcceptedShot(GameTestHelper helper) {
        BlockPos gun = new BlockPos(5, 2, 3);
        BlockPos scope = new BlockPos(4, 2, 4);
        BlockPos loaderCell = new BlockPos(6, 2, 3);
        BlockPos capacitorCell = new BlockPos(7, 2, 3);
        BlockPos batteryCell = new BlockPos(8, 2, 3);
        helper.setBlock(gun.below(), Blocks.IRON_BLOCK);
        helper.setBlock(gun, ModBlocks.COILGUN.get().defaultBlockState()
                .setValue(TurretBlock.FACING, Direction.SOUTH));
        helper.setBlock(scope.below(), ModBlocks.PERISCOPE_STAND.get());
        helper.setBlock(scope, ModBlocks.PERISCOPE.get());
        helper.setBlock(loaderCell, ModBlocks.COILGUN_LOADER.get());
        helper.setBlock(capacitorCell, ModBlocks.SUPERCAPACITOR.get());
        helper.setBlock(batteryCell, ModBlocks.BATTERY.get());

        var level = helper.getLevel();
        BlockPos gunPos = helper.absolutePos(gun);
        BlockPos scopePos = helper.absolutePos(scope);
        var turret = (TurretBlockEntity) level.getBlockEntity(gunPos);
        var loader = (CoilgunLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(loaderCell));
        var capacitor = (SupercapacitorBlockEntity) level.getBlockEntity(helper.absolutePos(capacitorCell));
        var battery = (BatteryBlockEntity) level.getBlockEntity(helper.absolutePos(batteryCell));
        var periscope = (PeriscopeBlockEntity) level.getBlockEntity(scopePos);
        var player = playerAt(helper, scope.north());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TURRET_BINDER.get()));
        bind(player, gunPos, scopePos);
        bind(player, helper.absolutePos(loaderCell), gunPos);
        bind(player, gunPos, helper.absolutePos(capacitorCell));
        bind(player, helper.absolutePos(batteryCell), gunPos);
        bind(player, helper.absolutePos(batteryCell), gunPos);
        helper.assertTrue(gunPos.equals(periscope.turretPos()), "Periscope binding missing");
        helper.assertTrue(loader.getBlockPos().equals(turret.loaderPos()), "Loader binding missing");
        helper.assertTrue(turret.powerSourcePositions().size() == 2
                && turret.powerSourcePositions().contains(capacitor.getBlockPos())
                && turret.powerSourcePositions().contains(battery.getBlockPos()), "Power links missing or duplicated");

        var nbt = new CompoundTag();
        nbt.putDouble("Charge", 1.0);
        capacitor.load(nbt);
        battery.load(nbt);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(scopePos).use(level, player, InteractionHand.MAIN_HAND, hit(scopePos, Direction.NORTH));
        helper.assertTrue(scopePos.equals(turret.statePacket().controllerPos()), "Cannot take control from periscope");
        turret.fire(player);
        helper.assertTrue(capacitor.weaponCharge(level) == 1.0 && battery.weaponCharge(level) == 1.0,
                "Dry fire consumed charge");

        loader.inventory().setItem(0, new ItemStack(ModItems.COILGUN_AMMO_BOX.get()));
        Vec3 muzzle = turret.muzzlePosition();
        Vec3 sight = turret.cameraAnchor();
        var target = EntityType.COW.create(level);
        target.setNoAi(true);
        target.setPos(muzzle.x, muzzle.y - 0.25, muzzle.z + 3.0);
        level.addFreshEntity(target);

        turret.fireInput(player, true);
        turret.fireInput(player, false);
        turret.fireInput(player, true);
        turret.fireInput(player, false);
        var bolts = level.getEntitiesOfClass(CoilgunBolt.class, new AABB(muzzle, muzzle).inflate(0.5));
        helper.assertTrue(bolts.size() == 1, "Expected one bolt at the model muzzle");
        Vec3 fromSight = bolts.get(0).position().subtract(sight);
        Vec3 travel = bolts.get(0).getDeltaMovement().normalize();
        helper.assertTrue(fromSight.dot(travel) > 0 && fromSight.cross(travel).lengthSqr() < 1.0e-8,
                "Bolt was fired off the periscope sight axis at level aim");
        var ceiling = turret.getBlockState().setValue(TurretBlock.MOUNT, Direction.DOWN);
        Vec3 angledMuzzle = CoilgunBlock.muzzle(ceiling, gunPos, 37, 55);
        Vec3 angledSight = CoilgunBlock.sight(ceiling, gunPos, 37, 55);
        Vec3 angledTravel = TurretBlockEntity.localLookVector(37, 55);
        Vec3 angledOffset = angledMuzzle.subtract(angledSight);
        helper.assertTrue(angledOffset.dot(angledTravel) > 0
                && angledOffset.cross(angledTravel).lengthSqr() < 1.0e-8,
                "Ceiling-mounted sight was off the barrel axis after turning");
        helper.assertTrue(loader.shots() == 199, "One shot did not consume exactly one round");
        double expected = 1.0 - 500.0 / 3600;
        helper.assertTrue(Math.abs(capacitor.weaponCharge(level) - expected) < 0.00001
                && Math.abs(battery.weaponCharge(level) - expected) < 0.00001,
                "One shot did not share the upstream power pulse equally");
        helper.assertTrue(turret.cooldown() == 5, "Reload time should be five ticks");
        helper.assertTrue(target.getHealth() == target.getMaxHealth(), "Target was hit before the bolt arrived");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(!target.isAlive() || target.getHealth() < target.getMaxHealth(), "Flying bolt missed target");
            helper.assertTrue(level.getEntitiesOfClass(CoilgunBolt.class, target.getBoundingBox().inflate(10)).isEmpty(),
                    "Bolt remained after impact");
            helper.succeed();
        });
    }

    @GameTest(template = "empty_16x8x16")
    public static void movingBarrelEntityBindsOnlyFromClearReach(GameTestHelper helper) {
        BlockPos gun = new BlockPos(6, 2, 5);
        BlockPos loader = new BlockPos(4, 2, 5);
        BlockPos playerCell = gun.offset(2, 1, 4);
        BlockPos wall = playerCell.west().above();
        helper.setBlock(gun.below(), Blocks.IRON_BLOCK);
        helper.setBlock(gun, ModBlocks.COILGUN.get().defaultBlockState()
                .setValue(TurretBlock.FACING, Direction.SOUTH));
        helper.setBlock(loader, ModBlocks.COILGUN_LOADER.get());
        var level = helper.getLevel();
        BlockPos master = helper.absolutePos(gun);
        var turret = (TurretBlockEntity) level.getBlockEntity(master);
        var player = playerAt(helper, playerCell);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TURRET_BINDER.get()));

        helper.runAfterDelay(4, () -> {
            var entities = level.getEntitiesOfClass(CoilgunInteractionEntity.class, new AABB(master).inflate(7),
                    entity -> entity.masterPos().equals(master));
            helper.assertTrue(entities.size() == 1, "Coilgun must maintain one barrel interaction entity");
            var barrel = entities.get(0);
            Vec3 eye = player.getEyePosition();
            Vec3 target = Vec3.atBottomCenterOf(master).add(0, 2.7, 4);
            AABB barrelContact = new AABB(target, target).inflate(0.15);
            AABB emptySide = barrelContact.move(1.2, 0, 0);
            helper.assertTrue(!level.noCollision(player, barrelContact), "Barrel still allows characters through");
            helper.assertTrue(level.noCollision(player, emptySide), "Broad phase hid empty space next to barrel");
            var walkingBox = emptySide;
            Vec3 walking = new Vec3(-1.2, 0, 0);
            Vec3 blocked = net.minecraft.world.entity.Entity.collideBoundingBox(player, walking, walkingBox,
                    level, level.getEntityCollisions(player, walkingBox.expandTowards(walking)));
            helper.assertTrue(blocked.x > walking.x + 0.1, "Character movement ignored barrel collision");
            Vec3 pick = barrel.clip(point -> point, eye, target, 1, 0);
            helper.assertTrue(pick != null, "Aim at barrel missed the moving interaction shape");
            Vec3 offset = pick.subtract(barrel.position());
            helper.setBlock(wall, Blocks.IRON_BLOCK);
            helper.assertTrue(!barrel.interactAt(player, offset, InteractionHand.MAIN_HAND).consumesAction(),
                    "Binder clicked the barrel through a wall");
            helper.setBlock(wall, Blocks.AIR);
            helper.assertTrue(barrel.interactAt(player, offset, InteractionHand.MAIN_HAND).consumesAction(),
                    "Binder did not accept a clear hit on the barrel");
            var binder = player.getMainHandItem().getItem();
            BlockPos loaderPos = helper.absolutePos(loader);
            binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(loaderPos, Direction.UP)));
            helper.assertTrue(loaderPos.equals(turret.loaderPos()), "Barrel did not bind to the loader");
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
            player.setShiftKeyDown(true);
            helper.setBlock(wall, Blocks.IRON_BLOCK);
            helper.assertTrue(!barrel.interactAt(player, offset, InteractionHand.MAIN_HAND).consumesAction(),
                    "Wrench dismantled a barrel through a wall");
            helper.setBlock(wall, Blocks.AIR);
            helper.assertTrue(barrel.interactAt(player, offset, InteractionHand.MAIN_HAND).consumesAction(),
                    "Wrench did not accept a clear hit on the moving barrel");
            helper.assertTrue(level.getBlockState(loaderPos).is(ModBlocks.COILGUN_LOADER.get()),
                    "Dismantling the turret also removed its connected loader");
            helper.assertTrue(barrel.isRemoved(), "Removing the mount left its interaction entity behind");
            helper.succeed();
        });
    }

    private static FakePlayer playerAt(GameTestHelper helper, BlockPos at) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "coilgun-test"));
        Vec3 point = Vec3.atBottomCenterOf(helper.absolutePos(at));
        player.setPos(point.x, point.y, point.z);
        return player;
    }

    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
    }

    private static void bind(FakePlayer player, BlockPos first, BlockPos second) {
        var binder = player.getMainHandItem().getItem();
        binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(first, Direction.UP)));
        binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(second, Direction.UP)));
    }
}
