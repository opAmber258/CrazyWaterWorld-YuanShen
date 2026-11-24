package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.FreeAimTurretBlock;
import com.opamb.barotrauma.block.TrackingTurretBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * 炮塔 BER 的模型数据（图集、旋转基座/炮管的 cube、图集 UV 布局）。
 *
 * <p>这里只放“长什么样”，旋转、后坐、近处淡出都在 {@link TurretRenderer}。
 * 换模型时优先改这里 + 对应 png/json；如果新模型需要不同的绘制方式，
 * 再改 {@link TurretRenderer}，瞄准/开火/相机逻辑完全不用动。</p>
 *
 * <p>cube 坐标是模型像素（16 = 1 格）；轴心由 {@code TurretSpec} 决定，
 * 所以 2×2 / 3×3 炮塔可以继续用同一套 UV/绘制代码，只要改 spec 和 cube 数组。</p>
 *
 * <p><strong>绘制约定</strong>：16 模型像素 = 1 格；每块瓦片按 2×2 象限切分
 * （左上顶面 / 右上 Z 面 / 左下 X 面 / 右下预留）。这两条由 {@code TurretRenderer.boxPx}
 * 实现，若新模型不是 16px/格或不是四象限瓦片，需要同步改渲染器，而不只是换本文件。</p>
 */
public final class TurretModels {

    /**
     * 一套模型：图集 + 两组 cube + 图集布局。
     * 图集布局字段允许未来换非 512×256 / 非 8 列瓦片的贴图。
     */
    public record Model(ResourceLocation atlas,
                        float[][] turntable,
                        float[][] barrel,
                        float atlasWidth,
                        float atlasHeight,
                        float tileSize,
                        int tilesPerRow) {

        public Model withAtlas(ResourceLocation newAtlas) {
            return new Model(newAtlas, turntable, barrel,
                    atlasWidth, atlasHeight, tileSize, tilesPerRow);
        }
    }

    private static final ResourceLocation ATLAS =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas.png");
    private static final ResourceLocation ATLAS_FREE_AIM =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas_freeaim.png");
    private static final ResourceLocation ATLAS_TRACKING =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas_tracking.png");
    private static final ResourceLocation ATLAS_COILGUN =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/turret_atlas_coilgun.png");

    /** {x1,y1,z1,x2,y2,z2,tile}；同一组共用一个淡出透明度。 */
    private static final float[][] TURNTABLE_BOXES = {
            {2.8F, 5.0F, 2.8F, 13.2F, 7.0F, 13.2F, 7},
            {5.5F, 7.0F, 5.5F, 10.5F, 9.5F, 10.5F, 8},
            {2.5F, 8.0F, 5.5F, 4.5F, 14.5F, 10.0F, 9},
            {11.5F, 8.0F, 5.5F, 13.5F, 14.5F, 10.0F, 9},
            {1.5F, 10.5F, 6.5F, 3.5F, 13.5F, 9.5F, 10},
            {12.5F, 10.5F, 6.5F, 14.5F, 13.5F, 9.5F, 10},
            {5.5F, 7.5F, 2.5F, 10.5F, 10.5F, 5.5F, 11},
            {4.2F, 8.5F, 6.5F, 5.2F, 12.5F, 7.5F, 12},
            {10.8F, 8.5F, 6.5F, 11.8F, 12.5F, 7.5F, 12},
            {7.0F, 13.5F, 6.0F, 9.0F, 15.0F, 8.0F, 13},
    };

    private static final float[][] BARREL_BOXES = {
            {4.5F, 9.0F, 1.5F, 11.5F, 15.0F, 8.5F, 14},
            {4.8F, 9.3F, 0.8F, 11.2F, 14.7F, 1.8F, 15},
            {5.5F, 9.5F, 8.0F, 10.5F, 14.5F, 12.0F, 16},
            {6.0F, 10.0F, 11.5F, 10.0F, 14.0F, 23.0F, 17},
            {5.2F, 9.4F, 13.5F, 10.8F, 14.6F, 15.0F, 18},
            {5.2F, 9.4F, 19.5F, 10.8F, 14.6F, 21.0F, 18},
            {5.0F, 9.5F, 22.5F, 11.0F, 14.5F, 26.24F, 19},
    };

    /** 512×256 的 8×4 瓦片表，每块 64×64。 */
    public static final Model DEFAULT = new Model(
            ATLAS, TURNTABLE_BOXES, BARREL_BOXES, 512.0F, 256.0F, 64.0F, 8);

    public static final Model FREE_AIM = DEFAULT.withAtlas(ATLAS_FREE_AIM);
    public static final Model TRACKING = DEFAULT.withAtlas(ATLAS_TRACKING);
    public static final Model COILGUN = DEFAULT.withAtlas(ATLAS_COILGUN);

    private TurretModels() {
    }

    public static Model forBlock(Block block) {
        if (block instanceof com.opamb.barotrauma.block.CoilgunBlock) {
            return COILGUN;
        }
        if (block instanceof FreeAimTurretBlock) {
            return FREE_AIM;
        }
        if (block instanceof TrackingTurretBlock) {
            return TRACKING;
        }
        return DEFAULT;
    }
}
