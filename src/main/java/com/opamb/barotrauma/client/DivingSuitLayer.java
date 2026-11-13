package com.opamb.barotrauma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.opamb.barotrauma.equipment.DivingSuitState;
import com.opamb.barotrauma.item.DivingSuitItem;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 把潜水服模型画在穿着的玩家身上。
 *
 * <p>潜水服在自定义装备槽（菜单 46）里，不走原版 {@code Inventory.armor}，
 * 所以不能靠 {@code IClientItemExtensions#getHumanoidArmorModel}；这里给
 * {@code PlayerRenderer} 追加一层，用同一个 {@link DivingSuitArmorRenderer}
 * 手动调用 GeckoLib 的护甲渲染流程。</p>
 *
 * <p>姿态：{@code prepForRender} 拿到 {@link #getParentModel()} 后，
 * {@code GeoArmorRenderer} 会按 {@code head/body/arms/legs} 的当前旋转
 * 驱动 8 根骨骼；位置就是原版护甲层所在的模型空间，不需要额外平移缩放。</p>
 */
public final class DivingSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static DivingSuitArmorRenderer renderer;
    private static DivingSuitItem item;
    private static ItemStack displayStack;

    public DivingSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!DivingSuitState.of(player).barotrauma$wearingDivingSuit()) {
            return;
        }
        DivingSuitArmorRenderer armor = renderer();
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        armor.setHeadHidden(minecraft.screen == null
                && !BaroHud.renderingPortrait()
                && player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson());
        armor.prepForRender(player, displayStack(), EquipmentSlot.CHEST, getParentModel());
        // 传 null 让 GeckoLib 自己按模型贴图取 RenderType 和顶点缓冲；
        // 整件连体服与槽位无关，骨骼可见性已在渲染器里固定为全开。
        // 参数顺序照 GeckoLib 内部调用：0F、partialTick、packedLight。
        armor.defaultRender(poseStack, item(), buffer, null, null, 0.0F, partialTick, packedLight);
    }

    /** 首次渲染时才创建：构造器要读客户端的 EntityModelSet，不能在客户端初始化前调用。 */
    private static DivingSuitArmorRenderer renderer() {
        if (renderer == null) {
            renderer = new DivingSuitArmorRenderer();
        }
        return renderer;
    }

    private static DivingSuitItem item() {
        if (item == null) {
            item = (DivingSuitItem) ModItems.DIVING_SUIT.get();
        }
        return item;
    }

    /** 几何和贴图与 NBT 无关，一个干净实例就够（内部槽里的氧气瓶不参与渲染）。 */
    private static ItemStack displayStack() {
        if (displayStack == null) {
            displayStack = new ItemStack(item());
        }
        return displayStack;
    }
}
