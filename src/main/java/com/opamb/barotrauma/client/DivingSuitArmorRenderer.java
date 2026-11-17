package com.opamb.barotrauma.client;

import com.opamb.barotrauma.item.DivingSuitItem;
import com.opamb.barotrauma.item.GlassDivingSuitItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * 潜水服是**一整件连体服**，不是原版四件套，所以不按装备槽藏骨骼：
 * 不管挂在哪个槽位，8 根骨骼全部画出来（否则只穿"胸甲"会只剩身体和手臂）。
 */
public final class DivingSuitArmorRenderer extends GeoArmorRenderer<DivingSuitItem> {

    /**
     * 第一人称世界里镜头在头盔内部，藏掉头部避免面罩内壁糊脸。
     * 背包 GUI 的玩家预览也走同一个渲染器，但那时 {@code screen != null}，
     * 必须保留头部，否则预览只剩一件无头潜水服。
     */
    private boolean headHidden;
    /** 透明潜水服第二遍：只画面罩，走半透明通道。 */
    private boolean visorOnly;
    /** 只打一次面罩骨骼诊断，避免每帧刷屏。 */
    private static boolean visorLogged;

    public DivingSuitArmorRenderer() {
        super(new DivingSuitModel());
    }

    public void setHeadHidden(boolean hidden) {
        this.headHidden = hidden;
    }

    public void setVisorOnly(boolean visorOnly) {
        this.visorOnly = visorOnly;
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        boolean glass = this.animatable instanceof GlassDivingSuitItem;
        if (glass && this.visorOnly) {
            // 只画玻璃面罩；第一人称连面罩也不画（镜头就在里面）。
            setAllBonesVisible(false);
            if (!this.headHidden && this.lastModel != null) {
                // setBoneVisible(head,false) 会连带把 head 的 childrenHidden 置 true，
                // 而 renderChildBones 会因此整段跳过子骨骼——面罩是 armorHead 的子骨骼，
                // 必须把 childrenHidden 放开，只让 head 自己的壳体不画。
                this.lastModel.getBone("armorHead")
                        .ifPresent(headBone -> headBone.setChildrenHidden(false));
                this.lastModel.getBone("helmetVisor").ifPresent(visor -> setBoneVisible(visor, true));
                if (!visorLogged) {
                    visorLogged = true;
                    com.opamb.barotrauma.BarotraumaMod.LOGGER.info(
                            "[suit] 透明潜水服面罩诊断：骨骼存在={} 立方体数={} 可见={}",
                            this.lastModel.getBone("helmetVisor").isPresent(),
                            this.lastModel.getBone("helmetVisor")
                                    .map(bone -> bone.getCubes().size()).orElse(-1),
                            this.lastModel.getBone("helmetVisor")
                                    .map(bone -> !bone.isHidden()).orElse(false));
                }
            }
            return;
        }
        setAllBonesVisible(true);
        if (glass && this.lastModel != null) {
            // 面罩单独走半透明通道，壳体这一遍不画它。
            this.lastModel.getBone("helmetVisor").ifPresent(visor -> setBoneVisible(visor, false));
        }
        if (!this.headHidden) {
            return;
        }
        if (this.head != null) {
            setBoneVisible(this.head, false);
        }
        // 透明潜水服的玻璃面罩是 armorHead 的子骨骼，GeckoLib 的 hidden 只跳过
        // 自己那几块立方体、不会跳过子骨骼，所以第一人称要单独藏掉。
        if (this.lastModel != null) {
            this.lastModel.getBone("helmetVisor").ifPresent(visor -> setBoneVisible(visor, false));
        }
    }

    /**
     * 透明潜水服分两遍画：壳体走默认 cutout no-cull（中空头盔内壁要双面），
     * 面罩单独走半透明通道（贴图 alpha 约 39%），透出后面的玩家脸部。
     * 普通潜水服保持 GeckoLib 默认行为。
     */
    @Override
    public RenderType getRenderType(DivingSuitItem animatable, ResourceLocation texture,
                                   MultiBufferSource bufferSource, float partialTick) {
        if (animatable instanceof GlassDivingSuitItem && this.visorOnly) {
            // 按贴图缓存，别每帧 new RenderType（每帧新实例会不断产生新的 BufferBuilder 键）。
            return TRANSLUCENT.computeIfAbsent(texture, RenderType::entityTranslucent);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    private static final java.util.Map<ResourceLocation, RenderType> TRANSLUCENT =
            new java.util.concurrent.ConcurrentHashMap<>();
}
