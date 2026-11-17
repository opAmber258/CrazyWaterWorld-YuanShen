package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.BatteryBlock;
import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 电池渲染器。资源：{@code geo/block/battery.geo.json}，贴图按电量四档切换。
 *
 * <p>geo 的正面是 −Z（北），和 GeckoLib 默认的模型正面一致，所以不需要覆盖
 * {@code getFacing}；方块 FACING 会被默认实现直接用于旋转。</p>
 */
public class BatteryRenderer extends GeoBlockRenderer<BatteryBlockEntity> {

    private static final ResourceLocation FULL =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/battery.png");
    private static final ResourceLocation MID =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/battery_mid.png");
    private static final ResourceLocation LOW =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/battery_low.png");
    private static final ResourceLocation EMPTY =
            new ResourceLocation(BarotraumaMod.MOD_ID, "textures/block/battery_empty.png");

    public BatteryRenderer() {
        super(new DefaultedBlockGeoModel<>(
                new ResourceLocation(BarotraumaMod.MOD_ID, "battery")));
    }

    @Override
    public ResourceLocation getTextureLocation(BatteryBlockEntity animatable) {
        return switch (animatable.getBlockState().getValue(BatteryBlock.CHARGE)) {
            case 0 -> EMPTY;
            case 1 -> LOW;
            case 2 -> MID;
            default -> FULL;
        };
    }
}
