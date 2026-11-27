package com.opamb.barotrauma.world;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.EquipmentLayout;
import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.entity.NpcEntity;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.world.level.LevelType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Facilities in the starting station and the first route's copy of that station. */
public final class StarterOutpost {
    private static final int LOWER = ModDimensions.OUTPOST_SPAWN_Y;
    private static final int UPPER = -54;
    private static final BlockPos DECK_MARK = new BlockPos(0, UPPER - 1, 0);
    private static final int LADDER_X = -3;
    private static final int LADDER_Z = 1;
    private static final BlockPos REACTOR = new BlockPos(-15, LOWER, 7);
    private static final BlockPos POWER_BOX = new BlockPos(-8, LOWER, 9);
    private static final BlockPos FABRICATOR = new BlockPos(-17, LOWER, 9);
    private static final BlockPos DECONSTRUCTOR = new BlockPos(-12, LOWER, 9);
    private static final BlockPos OXYGEN = new BlockPos(-4, LOWER, -8);
    private static final List<BlockPos> POWER_PORTS = List.of(FABRICATOR, DECONSTRUCTOR, OXYGEN);

    private record Module(int west, int east, int bottom, int roof, int north, int south) {
        boolean contains(int x, int y, int z, int inset) {
            return x >= west + inset && x <= east - inset && y >= bottom + inset && y <= roof - inset
                    && z >= north + inset && z <= south - inset;
        }
    }

    private record Passage(int x, int feet, int z, Direction direction) {
        boolean opens(int px, int py, int pz) {
            return px == x && pz == z && py >= feet && py <= feet + 2;
        }
    }

    private static final Module DOCK_CONCOURSE = new Module(-21, 21, -64, -55, -2, 2);
    private static final Module DIVE_LOCKERS = new Module(-21, -7, -64, -56, -11, -2);
    private static final Module OXYGEN_ROOM = new Module(-6, 4, -64, -56, -12, -2);
    private static final Module CLINIC = new Module(6, 21, -64, -56, -11, -2);
    private static final Module WORKSHOP = new Module(-21, -7, -64, -56, 2, 12);
    private static final Module AIRLOCK = new Module(-6, 0, -64, -57, 2, 10);
    private static final Module OFFICE = new Module(1, 6, -64, -56, 2, 9);
    private static final Module STORAGE = new Module(7, 21, -64, -57, 2, 11);
    private static final Module UPPER_CONCOURSE = new Module(-18, 19, -55, -47, -2, 2);
    private static final Module ACCESS_TOWER = new Module(-5, 5, -55, -44, -3, 3);
    private static final Module MESS = new Module(-21, 5, -55, -47, -10, -2);
    private static final Module STORE = new Module(7, 19, -55, -47, -10, -2);
    private static final Module QUARTERS = new Module(-21, 6, -55, -46, 2, 11);
    private static final Module WATCH = new Module(8, 17, -55, -48, 2, 9);

    private static final List<Module> MODULES = List.of(DOCK_CONCOURSE, DIVE_LOCKERS, OXYGEN_ROOM, CLINIC,
            WORKSHOP, AIRLOCK, OFFICE, STORAGE, UPPER_CONCOURSE, ACCESS_TOWER, MESS, STORE, QUARTERS, WATCH);
    private static final List<Module> TOWER_NEIGHBORS = List.of(MESS, QUARTERS);
    private static final Passage QUARTERS_DOOR = new Passage(1, UPPER, 3, Direction.SOUTH);
    private static final Passage QUARTERS_APPROACH = new Passage(1, UPPER, 2, Direction.SOUTH);
    private static final List<Passage> PASSAGES = List.of(
            new Passage(-13, LOWER, -2, Direction.NORTH), new Passage(-3, LOWER, -2, Direction.NORTH),
            new Passage(12, LOWER, -2, Direction.NORTH), new Passage(-12, LOWER, 2, Direction.SOUTH),
            new Passage(-3, LOWER, 2, Direction.SOUTH), new Passage(4, LOWER, 2, Direction.SOUTH),
            new Passage(13, LOWER, 2, Direction.SOUTH), new Passage(-12, UPPER, -2, Direction.NORTH),
            new Passage(13, UPPER, -2, Direction.NORTH), new Passage(-11, UPPER, 2, Direction.SOUTH),
            QUARTERS_DOOR, new Passage(12, UPPER, 2, Direction.SOUTH));

    private StarterOutpost() {
    }

    static boolean generatesAt(DockingBay.Site site) {
        if (site.equals(DockingBay.Site.MAIN)) return true;
        var route = MissionInstance.routeLevel();
        return route != null && route.type() == LevelType.TUTORIAL
                && site.equals(MissionInstance.startSite(
                        new MissionInstance.Anchor(route.slot(), route.anchorX(), 0, 0), route.startDistance()));
    }

    static boolean intersects(ChunkPos chunk, DockingBay.Site site) {
        return chunk.getMaxBlockX() >= site.x() - 21 && chunk.getMinBlockX() <= site.x() + 21
                && chunk.getMaxBlockZ() >= site.z() - 12 && chunk.getMinBlockZ() <= site.z() + 12;
    }

    static void build(ChunkAccess chunk, DockingBay.Site site) {
        var bounds = chunk.getPos();
        for (int x = Math.max(-21, bounds.getMinBlockX() - site.x()); x <= Math.min(21, bounds.getMaxBlockX() - site.x()); x++) {
            for (int z = Math.max(-12, bounds.getMinBlockZ() - site.z()); z <= Math.min(12, bounds.getMaxBlockZ() - site.z()); z++) {
                for (int y = -64; y <= -44; y++) {
                    BlockState state = stateAt(x, y, z);
                    if (state != null) OutpostBuilder.setWithBe(chunk, site.at(x, y, z), state);
                }
            }
        }
        for (Passage passage : PASSAGES) {
            BlockPos pos = site.at(passage.x(), passage.feet(), passage.z());
            DockingBay.placeBulkheadDoor(chunk, pos.getX(), pos.getY(), pos.getZ(), passage.direction());
        }
        furnish(chunk, site);
        DockingBay.buildStation(chunk, site);
    }

    private static boolean ladderAt(int x, int y, int z) {
        return x == LADDER_X && z == LADDER_Z && y >= LOWER && y <= UPPER;
    }

    private static boolean ladderClearance(int x, int y, int z) {
        return Math.abs(x - LADDER_X) == 1 && z == LADDER_Z && y == UPPER - 1;
    }

    private static boolean towerPartition(int x, int y, int z) {
        if (!ACCESS_TOWER.contains(x, y, z, 1) || QUARTERS_APPROACH.opens(x, y, z)) return false;
        for (Module room : TOWER_NEIGHBORS) {
            if ((z == room.north() || z == room.south()) && room.contains(x, y, z, 0)) return true;
        }
        return false;
    }

    static boolean dryAt(int x, int y, int z) {
        if (ladderAt(x, y, z) || ladderClearance(x, y, z) || QUARTERS_APPROACH.opens(x, y, z)) return true;
        for (Passage passage : PASSAGES) {
            if (passage.opens(x, y, z)) return true;
        }
        if (towerPartition(x, y, z)) return false;
        for (Module module : MODULES) {
            if (module.contains(x, y, z, 1)) return true;
        }
        return false;
    }

    private static @Nullable BlockState stateAt(int x, int y, int z) {
        if (ladderAt(x, y, z)) {
            return ModBlocks.IRON_LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        }
        if (x == DECK_MARK.getX() && y == DECK_MARK.getY() && z == DECK_MARK.getZ()) {
            return Blocks.WAXED_CUT_COPPER.defaultBlockState();
        }
        if (towerPartition(x, y, z)) {
            return ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        }
        if (dryAt(x, y, z)) {
            if (y == LOWER - 1) return Blocks.SMOOTH_STONE.defaultBlockState();
            int feet = y < UPPER - 1 ? LOWER : UPPER;
            if (y == feet + 4 && Math.floorMod(x, 5) == 0 && Math.floorMod(z, 4) == 0) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }
        for (Module module : MODULES) {
            if (!module.contains(x, y, z, 0)) continue;
            if ((module == CLINIC || module == STORE || module == MESS) && z == module.north()
                    && y >= module.bottom() + 3 && y <= module.bottom() + 4 && Math.floorMod(x, 5) < 3) {
                return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            }
            if (y == module.bottom() + 2 && (x == module.west() || x == module.east())) {
                return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            }
            return ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        }
        return null;
    }

    private static void furnish(ChunkAccess chunk, DockingBay.Site site) {
        equipment(chunk, site, REACTOR, ModBlocks.REACTOR.get(), Direction.SOUTH);
        equipment(chunk, site, FABRICATOR, ModBlocks.FABRICATOR.get(), Direction.NORTH);
        equipment(chunk, site, DECONSTRUCTOR, ModBlocks.DECONSTRUCTOR.get(), Direction.NORTH);
        equipment(chunk, site, POWER_BOX, ModBlocks.JUNCTION_BOX.get(), Direction.NORTH);
        equipment(chunk, site, OXYGEN, ModBlocks.OXYGEN_GENERATOR.get(), Direction.SOUTH);
        for (int x : new int[]{-12, 0, 12}) {
            equipment(chunk, site, new BlockPos(x, -57, -7), ModBlocks.VENT.get(), Direction.SOUTH);
            equipment(chunk, site, new BlockPos(x, -48, -7), ModBlocks.VENT.get(), Direction.SOUTH);
        }
        if (entity(chunk, site, REACTOR) instanceof ReactorBlockEntity reactor) {
            reactor.setItem(0, new ItemStack(ModItems.THORIUM_FUEL_ROD.get()));
            reactor.setPowerOn(true);
        }
        for (BlockPos port : POWER_PORTS) {
            if (entity(chunk, site, port) instanceof PowerConsumer device) device.setPowerBox(at(site, POWER_BOX));
        }

        for (int x : new int[]{-16, -13}) {
            BlockPos locker = new BlockPos(x, LOWER, -8);
            equipment(chunk, site, locker, ModBlocks.DIVING_SUIT_LOCKER.get(), Direction.SOUTH);
            stock(chunk, site, locker, ModItems.DIVING_SUIT.get());
        }
        BlockPos shelf = new BlockPos(-10, LOWER, -8);
        equipment(chunk, site, shelf, ModBlocks.OXYGEN_TANK_SHELF.get(), Direction.SOUTH);
        stock(chunk, site, shelf, ModItems.OXYGEN_TANK.get());
        equipment(chunk, site, new BlockPos(10, LOWER, -8), ModBlocks.MEDIUM_STEEL_CABINET.get(), Direction.SOUTH);
        for (int x : new int[]{10, 13, 16}) {
            equipment(chunk, site, new BlockPos(x, LOWER, 8), ModBlocks.CRATE_SHELF.get(), Direction.NORTH);
        }
        equipment(chunk, site, new BlockPos(16, UPPER, -8), ModBlocks.MEDIUM_STEEL_CABINET.get(), Direction.SOUTH);
        bunk(chunk, site, 15, LOWER, -8, Blocks.LIGHT_BLUE_WOOL);
        bunk(chunk, site, 17, LOWER, -8, Blocks.LIGHT_BLUE_WOOL);
        for (int x : new int[]{-16, -13, -10, -4, 0, 4}) {
            bunk(chunk, site, x, UPPER, 8, Blocks.GRAY_WOOL);
        }
        for (int x : new int[]{-15, -9, -2}) {
            put(chunk, site, new BlockPos(x, UPPER, -7), Blocks.POLISHED_ANDESITE.defaultBlockState());
            put(chunk, site, new BlockPos(x + 1, UPPER, -7), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            put(chunk, site, new BlockPos(x, UPPER, -8), Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }
        counter(chunk, site, LOWER, 2, 4, 6);
        counter(chunk, site, UPPER, 11, 16, -4);
        equipment(chunk, site, new BlockPos(14, UPPER, 7), ModBlocks.MEDIUM_WINDOWED_STEEL_CABINET.get(), Direction.NORTH);
        counter(chunk, site, UPPER, 10, 12, 7);
        sign(chunk, site, -14, LOWER, 1, "engineering");
        sign(chunk, site, -15, LOWER, -1, "diving");
        sign(chunk, site, -5, LOWER, -1, "oxygen");
        sign(chunk, site, 10, LOWER, -1, "clinic");
        sign(chunk, site, 15, LOWER, 1, "storage");
        sign(chunk, site, 6, LOWER, 1, "office");
        sign(chunk, site, 15, UPPER, -1, "supply");
        sign(chunk, site, -13, UPPER, 1, "quarters");
        sign(chunk, site, -14, UPPER, -1, "mess");
        sign(chunk, site, 14, UPPER, 1, "watch");
    }

    private static void equipment(ChunkAccess chunk, DockingBay.Site site, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        EquipmentLayout.at(pos, state).cells().forEach((cell, part) -> put(chunk, site, cell, part));
    }

    private static void bunk(ChunkAccess chunk, DockingBay.Site site, int x, int y, int z, Block blanket) {
        // Use a bunk the player can approach safely in a dimension where vanilla beds explode.
        put(chunk, site, new BlockPos(x, y, z), Blocks.WHITE_WOOL.defaultBlockState());
        put(chunk, site, new BlockPos(x, y, z + 1), blanket.defaultBlockState());
    }

    private static void counter(ChunkAccess chunk, DockingBay.Site site, int y, int from, int to, int z) {
        for (int x = from; x <= to; x++) {
            put(chunk, site, new BlockPos(x, y, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        }
    }

    private static void sign(ChunkAccess chunk, DockingBay.Site site, int x, int y, int z, String key) {
        BlockPos pos = new BlockPos(x, y, z);
        put(chunk, site, pos, Blocks.SPRUCE_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, z < 0 ? 0 : 8));
        if (entity(chunk, site, pos) instanceof SignBlockEntity sign) {
            var text = sign.getFrontText().setMessage(1, Component.translatable("station.barotrauma.starter." + key));
            // ProtoChunk block entities have no Level yet. Set the saved text without a block update.
            var tag = sign.saveWithoutMetadata();
            var encoded = SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, text)
                    .result().orElseThrow();
            tag.put("front_text", encoded);
            tag.put("back_text", encoded.copy());
            tag.putBoolean("is_waxed", true);
            sign.load(tag);
        }
    }

    private static void stock(ChunkAccess chunk, DockingBay.Site site, BlockPos pos, Item item) {
        if (entity(chunk, site, pos) instanceof Container container) container.setItem(0, new ItemStack(item));
    }

    private static void put(ChunkAccess chunk, DockingBay.Site site, BlockPos local, BlockState state) {
        BlockPos world = at(site, local);
        if (chunk.getPos().equals(new ChunkPos(world))) {
            OutpostBuilder.setWithBe(chunk, world, state);
        }
    }

    private static BlockEntity entity(ChunkAccess chunk, DockingBay.Site site, BlockPos local) {
        BlockPos world = at(site, local);
        return chunk.getPos().equals(new ChunkPos(world)) ? chunk.getBlockEntity(world) : null;
    }

    private static BlockPos at(DockingBay.Site site, BlockPos local) {
        return site.at(local.getX(), local.getY(), local.getZ());
    }

    static boolean isModular(@Nullable Level level, DockingBay.Site site) {
        if (level == null) return false;
        BlockPos mark = at(site, DECK_MARK);
        if (!level.hasChunkAt(mark)) return false;
        BlockState state = level.getBlockState(mark);
        return state.is(Blocks.CUT_COPPER) || state.is(Blocks.WAXED_CUT_COPPER);
    }

    /** Updates the first modular layout once, limited to the affected generated cells. */
    static void repairLayout(ServerLevel level, DockingBay.Site site) {
        BlockPos mark = at(site, DECK_MARK);
        if (!level.getBlockState(mark).is(Blocks.CUT_COPPER)) return;
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int side : new int[]{-1, 1}) {
            BlockPos lip = site.at(LADDER_X + side, UPPER - 1, LADDER_Z);
            if (level.getBlockState(lip).equals(plating)) level.setBlockAndUpdate(lip, air);
        }
        for (int x = ACCESS_TOWER.west() + 1; x < ACCESS_TOWER.east(); x++) {
            for (int z : new int[]{-2, 2}) {
                for (int y = UPPER; y < ACCESS_TOWER.roof(); y++) {
                    if (!towerPartition(x, y, z)) continue;
                    BlockPos wall = site.at(x, y, z);
                    if (level.getBlockState(wall).equals(air)) level.setBlockAndUpdate(wall, plating);
                }
            }
        }
        BlockPos exit = site.at(-3, LOWER, AIRLOCK.south());
        BlockState lower = level.getBlockState(exit);
        BlockState upper = level.getBlockState(exit.above());
        if (lower.is(ModShipBlocks.BULKHEAD_DOOR.get())
                && lower.getValue(BulkheadDoorBlock.FACING) == Direction.SOUTH
                && lower.getValue(BulkheadDoorBlock.PART) == BulkheadDoorBlock.Part.LOWER
                && upper.equals(lower.setValue(BulkheadDoorBlock.PART, BulkheadDoorBlock.Part.UPPER))
                && level.getBlockState(exit.above(2)).equals(air)) {
            for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(exit.above(y), plating);
        }
        for (int x = -5; x <= -1; x++) {
            for (int z = 11; z <= 13; z++) {
                BlockPos step = site.at(x, LOWER - 1, z);
                if (level.getBlockState(step).is(Blocks.POLISHED_DEEPSLATE)) level.setBlockAndUpdate(step, air);
            }
        }
        level.setBlockAndUpdate(mark, Blocks.WAXED_CUT_COPPER.defaultBlockState());
    }

    private static boolean installed(ServerLevel level, DockingBay.Site site) {
        if (isModular(level, site)) return true;
        BlockPos mark = at(site, DECK_MARK);
        return level.hasChunkAt(mark) && level.getBlockState(mark).is(Blocks.WHITE_CONCRETE);
    }

    public static @Nullable BlockPos staffSpot(ServerLevel level, DockingBay.Site site, String role) {
        if (!installed(level, site)) return null;
        return switch (role) {
            case NpcEntity.ROLE_MERCHANT -> site.at(13, UPPER, -6);
            case NpcEntity.ROLE_DOCTOR -> site.at(12, LOWER, -6);
            default -> site.at(4, LOWER, isModular(level, site) ? 7 : 8);
        };
    }

    public static @Nullable DockingBay.Site reactorSite(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(ModDimensions.THE_DEEP) || pos.getY() != LOWER) return null;
        var site = DockingBay.siteAt(pos.getX(), pos.getZ());
        return site != null && pos.equals(at(site, REACTOR)) && installed(level, site) ? site : null;
    }

    public static double powerDemand(ServerLevel level, DockingBay.Site site) {
        BlockPos box = at(site, POWER_BOX);
        if (!level.hasChunkAt(box) || !(level.getBlockEntity(box) instanceof JunctionBoxBlockEntity junction)
                || junction.broken()) return 0.0D;
        double demand = 0.0D;
        for (BlockPos port : POWER_PORTS) {
            BlockPos cell = at(site, port);
            if (level.hasChunkAt(cell) && level.getBlockEntity(cell) instanceof PowerConsumer consumer
                    && box.equals(consumer.powerBox())) {
                demand += consumer.powerDemandKw();
            }
        }
        return demand;
    }

    public static double voltage(ServerLevel level, BlockPos pos, @Nullable BlockPos box) {
        if (box == null || !level.dimension().equals(ModDimensions.THE_DEEP) || pos.getY() != LOWER) return 0.0D;
        var site = DockingBay.siteAt(pos.getX(), pos.getZ());
        if (site == null || !box.equals(at(site, POWER_BOX))
                || !POWER_PORTS.contains(pos.offset(-site.x(), 0, -site.z()))
                || !level.hasChunkAt(box) || !(level.getBlockEntity(box) instanceof JunctionBoxBlockEntity junction)
                || junction.broken()) return 0.0D;
        return junctionVoltage(level, box);
    }

    public static double junctionVoltage(ServerLevel level, BlockPos pos) {
        if (!level.dimension().equals(ModDimensions.THE_DEEP) || pos.getY() != LOWER) return 0.0D;
        var site = DockingBay.siteAt(pos.getX(), pos.getZ());
        if (site == null || !pos.equals(at(site, POWER_BOX)) || !installed(level, site)) return 0.0D;
        BlockPos reactor = at(site, REACTOR);
        return level.hasChunkAt(reactor) && level.getBlockEntity(reactor) instanceof ReactorBlockEntity source
                && source.powerOn() && source.availableFuel() > 0.0D ? source.lastRatio() : 0.0D;
    }
}
