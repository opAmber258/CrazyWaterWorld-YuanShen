package com.opamb.barotrauma.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 给「完全挡光的薄板方块」补 BER 光照。
 *
 * <p>舱壁门和舱口为了不让光漏到隔壁，{@code getLightBlock} 在关闭时返回 15。
 * 代价是方块自己那一格的光照值是 0，而 GeckoLib 的 BER 只拿
 * {@code BlockEntityRenderer} 传进来的「方块所在格」光照，于是关着的门被手电筒
 * 照上去会整块全黑（相邻的普通墙面按面取空气格光照，所以正常）。</p>
 *
 * <p>这里改成取方块周围空气格的最亮值：门/舱口模型是薄的，两面都贴着空气格，
 * 取最大值就能让门和墙面亮度一致。多扫一格高度是因为舱壁门是 1×2、
 * 舱口是 3×1，光斑可能只贴着其中一段。</p>
 */
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
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos center = pos.above(dy);
            for (Direction direction : Direction.values()) {
                int neighbor = LevelRenderer.getLightColor(level, center.relative(direction));
                packedLight = brighterLight(packedLight, neighbor);
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
