package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.entity.NpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 站务员 NPC 渲染：直接用原版玩家人形模型 + 玩家皮肤。
 *
 * <p>皮肤在 {@code assets/barotrauma/textures/entity/npc/<skin>.png}，
 * 文件名由实体同步的皮肤字段决定。</p>
 */
public class NpcRenderer extends MobRenderer<NpcEntity, PlayerModel<NpcEntity>> {

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return new ResourceLocation(BarotraumaMod.MOD_ID,
                "textures/entity/npc/" + entity.skin() + ".png");
    }
}
