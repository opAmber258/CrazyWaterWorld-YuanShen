package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.NpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 站务员 NPC 渲染：原版玩家人形模型 + 玩家皮肤。
 *
 * <p>模型按实体同步的 {@code slim} 标记切换：{@code opamber} 这张皮肤是 3px
 * 手臂布局，用 slim 模型；{@code hanxing}/{@code tenbaku} 是标准 4px 皮肤，用
 * classic 模型。商人/医生没有指定皮肤，默认 Steve/classic。自定义皮肤仍从
 * {@code assets/barotrauma/textures/entity/npc/<skin>.png} 读取。</p>
 */
public class NpcRenderer extends MobRenderer<NpcEntity, PlayerModel<NpcEntity>> {

    private final PlayerModel<NpcEntity> classic;
    private final PlayerModel<NpcEntity> slim;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.classic = this.model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(NpcEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model = entity.slim() ? slim : classic;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        String skin = entity.skin();
        if (NpcEntity.DEFAULT_SKIN.equals(skin)) {
            return new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
        }
        if ("alex".equals(skin)) {
            return new ResourceLocation("minecraft", "textures/entity/player/slim/alex.png");
        }
        return new ResourceLocation(BarotraumaMod.MOD_ID,
                "textures/entity/npc/" + skin + ".png");
    }
}
