package com.opamb.barotrauma.block;

import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Maps a clicked section to the cells that the existing block removal callback can touch. */
public record EquipmentLayout(BlockPos master, Map<BlockPos, BlockState> cells) {
    public static boolean supports(BlockState state) {
        return ModShipBlocks.isEquipment(state) || state.getBlock() instanceof TurretBlock
                || state.getBlock() instanceof TurretStationBlock;
    }

    public static EquipmentLayout at(BlockPos pos, BlockState state) {
        Direction facing = state.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        var block = state.getBlock();
        if (block instanceof CoilgunBlock) {
            BlockPos master = CoilgunBlock.masterPos(state, pos);
            return withParts(master, state, CoilgunBlock.PART, part -> CoilgunBlock.partPos(master, state, part));
        }
        if (block instanceof CabinetBlock cabinet) {
            BlockPos master = CabinetBlock.masterPos(pos, state);
            return withParts(master, state, CabinetBlock.PART,
                    IntStream.range(0, cabinet.kind().cells()).boxed().toList(), part -> CabinetBlock.partPos(master, part));
        }
        if (block instanceof ReactorBlock) {
            BlockPos master = ReactorBlock.masterPos(pos, state);
            return withParts(master, state, ReactorBlock.PART, part -> ReactorBlock.partPos(master, facing, part));
        }
        if (block instanceof EngineBlock) {
            BlockPos master = EngineBlock.masterPos(pos, state);
            return withParts(master, state, EngineBlock.PART, part -> EngineBlock.partPos(master, facing, part));
        }
        if (block instanceof BallastPumpBlock) {
            BlockPos master = BallastPumpBlock.masterPos(pos, state);
            return withParts(master, state, BallastPumpBlock.PART, part -> BallastPumpBlock.partPos(master, facing, part));
        }
        if (block instanceof NavigationTerminalBlock) {
            BlockPos master = NavigationTerminalBlock.masterPos(pos, state);
            return withParts(master, state, NavigationTerminalBlock.PART, part -> NavigationTerminalBlock.partPos(master, facing, part));
        }
        if (block instanceof FabricatorBlock) {
            BlockPos master = FabricatorBlock.masterPos(pos, state);
            return withParts(master, state, FabricatorBlock.PART, part -> FabricatorBlock.partPos(master, facing, part));
        }
        if (block instanceof DeconstructorBlock) {
            BlockPos master = DeconstructorBlock.masterPos(pos, state);
            return withParts(master, state, DeconstructorBlock.PART, part -> DeconstructorBlock.partPos(master, facing, part));
        }
        return new EquipmentLayout(pos, Map.of(pos, state));
    }

    public boolean owns(BlockPos pos, BlockState state) {
        BlockState expected = cells.get(pos);
        if (expected == null || !state.is(expected.getBlock())) return false;
        if (expected.hasProperty(DeconstructorBlock.WORKING)) {
            expected = expected.setValue(DeconstructorBlock.WORKING, state.getValue(DeconstructorBlock.WORKING));
        }
        return expected == state;
    }

    private static <T extends Comparable<T>> EquipmentLayout withParts(BlockPos master, BlockState state,
            Property<T> property, Function<T, BlockPos> position) {
        return withParts(master, state, property, property.getPossibleValues(), position);
    }

    private static <T extends Comparable<T>> EquipmentLayout withParts(BlockPos master, BlockState state,
            Property<T> property, Iterable<T> parts, Function<T, BlockPos> position) {
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        for (T part : parts) {
            cells.put(position.apply(part), state.setValue(property, part));
        }
        return new EquipmentLayout(master, cells);
    }
}
