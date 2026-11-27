package com.opamb.barotrauma.client;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.ship.vs.VsClientTurret;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class BlockLightSampler {

    // Colorful Lighting 用高四位标记 RGB 光图，其他位的布局也和原版不同。
    private static final int COLORFUL_MARKER = 0xF0000000;

    private BlockLightSampler() {
    }

    /** 用同一光图编码合并门格和邻格，保留真实彩色光。 */
    public static int neighborLight(BlockEntity blockEntity, int packedLight) {
        var level = blockEntity.getLevel();
        if (level == null) {
            return packedLight;
        }
        BlockPos pos = blockEntity.getBlockPos();
        var state = blockEntity.getBlockState();
        boolean hatch = state.getBlock() instanceof HatchBlock;
        Direction facing = hatch ? state.getValue(HatchBlock.FACING) : state.getValue(BulkheadDoorBlock.FACING);
        boolean open = hatch ? state.getValue(HatchBlock.OPEN) : state.getValue(BulkheadDoorBlock.OPEN);
        Direction normal = hatch ? Direction.UP : facing;
        Direction side = normal;
        if (!open && level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
            Vec3 center = Vec3.atCenterOf(pos).add(0, hatch ? 0.4375D : 0.5D, 0);
            Vec3 worldCenter = VsClientTurret.toWorld(clientLevel, pos, center);
            Vec3 worldNormal = VsClientTurret.directionToWorld(clientLevel, pos, Vec3.atLowerCornerOf(normal.getNormal()));
            Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (eye.subtract(worldCenter).dot(worldNormal) < 0.0D) side = side.getOpposite();
        }
        Direction segment = hatch ? facing.getClockWise() : Direction.UP;
        for (int part = hatch ? -1 : 0; part <= 1; part++) {
            BlockPos center = pos.relative(segment, part);
            packedLight = brighterLight(packedLight, LevelRenderer.getLightColor(level, center.relative(side)));
            if (open) {
                packedLight = brighterLight(packedLight,
                        LevelRenderer.getLightColor(level, center.relative(side.getOpposite())));
            }
        }
        return packedLight;
    }

    private static int brighterLight(int first, int second) {
        boolean firstColored = (first & COLORFUL_MARKER) == COLORFUL_MARKER;
        boolean secondColored = (second & COLORFUL_MARKER) == COLORFUL_MARKER;
        if (firstColored && secondColored) {
            return COLORFUL_MARKER
                    | brighterChannel(first, second, 0x000000FF)  // red
                    | brighterChannel(first, second, 0x0000FF00)  // green
                    | brighterChannel(first, second, 0x000F0000)  // sky
                    | brighterChannel(first, second, 0x0FF00000); // blue
        }
        if (firstColored || secondColored) {
            return firstColored ? first : second;
        }
        return LightTexture.pack(Math.max(LightTexture.block(first), LightTexture.block(second)),
                Math.max(LightTexture.sky(first), LightTexture.sky(second)));
    }

    private static int brighterChannel(int first, int second, int mask) {
        return Math.max(first & mask, second & mask);
    }
}
