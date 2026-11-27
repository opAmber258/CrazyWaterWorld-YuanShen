package com.opamb.barotrauma.client;

import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/** 造船厂内置模块化潜艇分段/舱室预制件，一键装入三维剪贴板并支持旋转/镜像拼装。 */
public final class ShipyardPresets {

    public record Preset(String name, String subtitle, List<ShipyardClient.ClipboardBlock> blocks) {
    }

    private static List<Preset> cache;

    private ShipyardPresets() {
    }

    public static List<Preset> all() {
        if (cache != null) {
            return cache;
        }
        cache = List.of(
                buildStandardCabin(),
                buildStreamlinedBow(),
                buildDoubleBallast(),
                buildAirlockSection()
        );
        return cache;
    }

    private static Preset buildStandardCabin() {
        BlockState hull = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState glass = ModShipBlocks.HULL_GLASS.get().defaultBlockState();
        BlockState lamp = ModBlocks.LAMP.get().defaultBlockState();
        List<ShipyardClient.ClipboardBlock> list = new ArrayList<>();
        for (BlockPos p : ShipyardShapes.collectCells(
                new BlockPos(0, 0, 0), new BlockPos(8, 4, 6), ShipyardShapes.MODE_FILL_HOLLOW, 4096)) {
            boolean window = p.getY() == 2 && (p.getZ() == 0 || p.getZ() == 6)
                    && p.getX() >= 3 && p.getX() <= 5;
            list.add(new ShipyardClient.ClipboardBlock(p, window ? glass : hull));
        }
        list.add(new ShipyardClient.ClipboardBlock(new BlockPos(4, 3, 3), lamp));
        return new Preset("标准舱室", "9×5×7 · 带舷窗与照明", List.copyOf(list));
    }

    private static Preset buildStreamlinedBow() {
        BlockState hull = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState glass = ModShipBlocks.HULL_GLASS.get().defaultBlockState();
        List<ShipyardClient.ClipboardBlock> list = new ArrayList<>();
        for (BlockPos p : ShipyardShapes.collectCells(
                new BlockPos(0, 0, 0), new BlockPos(10, 5, 8), ShipyardShapes.MODE_ELLIPSOID, 4096)) {
            boolean noseWindow = p.getY() == 3 && p.getX() >= 8;
            list.add(new ShipyardClient.ClipboardBlock(p, noseWindow ? glass : hull));
        }
        return new Preset("流线艇首", "11×6×9 · 曲面抗压艇壳", List.copyOf(list));
    }

    private static Preset buildDoubleBallast() {
        BlockState hull = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState pump = ModBlocks.SMALL_PUMP.get().defaultBlockState();
        BlockState hatch = ModShipBlocks.HATCH_LADDER.get().defaultBlockState();
        if (hatch.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            hatch = hatch.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        List<ShipyardClient.ClipboardBlock> list = new ArrayList<>();
        for (BlockPos p : ShipyardShapes.collectCells(
                new BlockPos(0, 0, 0), new BlockPos(8, 6, 6), ShipyardShapes.MODE_FILL_HOLLOW, 4096)) {
            list.add(new ShipyardClient.ClipboardBlock(p, hull));
        }
        // 中层甲板（中间 3 格留出带梯舱口通道）
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 5; z++) {
                if (z == 3 && x >= 3 && x <= 5) {
                    continue;
                }
                list.add(new ShipyardClient.ClipboardBlock(new BlockPos(x, 3, z), hull));
            }
        }
        list.add(new ShipyardClient.ClipboardBlock(new BlockPos(4, 3, 3), hatch));
        list.add(new ShipyardClient.ClipboardBlock(new BlockPos(2, 1, 3), pump));
        return new Preset("双层压载舱", "9×7×7 · 含甲板梯口与水泵", List.copyOf(list));
    }

    private static Preset buildAirlockSection() {
        BlockState hull = ModBlocks.SUBMARINE_PLATING.get().defaultBlockState();
        BlockState hatch = ModShipBlocks.HATCH.get().defaultBlockState();
        BlockState door = ModShipBlocks.BULKHEAD_DOOR.get().defaultBlockState();
        if (door.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            door = door.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        }
        List<ShipyardClient.ClipboardBlock> list = new ArrayList<>();
        for (BlockPos p : ShipyardShapes.collectCells(
                new BlockPos(0, 0, 0), new BlockPos(6, 4, 6), ShipyardShapes.MODE_FILL_HOLLOW, 4096)) {
            boolean topOpening = p.getY() == 4 && p.getZ() == 3 && p.getX() >= 2 && p.getX() <= 4;
            boolean sideDoor = p.getX() == 0 && p.getZ() == 3 && (p.getY() == 1 || p.getY() == 2);
            if (topOpening || sideDoor) {
                continue;
            }
            list.add(new ShipyardClient.ClipboardBlock(p, hull));
        }
        list.add(new ShipyardClient.ClipboardBlock(new BlockPos(3, 4, 3), hatch));
        list.add(new ShipyardClient.ClipboardBlock(new BlockPos(0, 1, 3), door));
        return new Preset("气闸对接段", "7×5×7 · 顶舱口与侧舱壁门", List.copyOf(list));
    }
}
