package com.opamb.barotrauma.client;

import com.opamb.barotrauma.item.DivingSuitItem;
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

    public DivingSuitArmorRenderer() {
        super(new DivingSuitModel());
    }

    public void setHeadHidden(boolean hidden) {
        this.headHidden = hidden;
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
        setAllBonesVisible(true);
        if (this.headHidden && this.head != null) {
            setBoneVisible(this.head, false);
        }
    }
}
