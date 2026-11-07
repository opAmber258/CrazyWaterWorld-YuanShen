package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 医疗交互与倒地表现的客户端钩子（仅客户端注册，服务端永不加载本类）：
 * <ul>
 *   <li>AttackEntityEvent：手持药品左键队友 = 打开对方的身体面板（取消攻击）。</li>
 *   <li>MovementInputUpdateEvent：倒地时清空移动输入（不能移动）。</li>
 *   <li>ComputeCameraAngles：倒地时**视角完全锁定**——拖动鼠标无法转视角（用户指令：
 *       "视角固定"指的就是这个），锁定在倒地瞬间的朝向。</li>
 *   <li>PLAYER_HEALTH：倒地时血条换闪烁鲜红心；FOOD_BAR：饱食度栏整个隐藏（机制已移除）。</li>
 *   <li>VIGNETTE：倒地红色暗角（濒死视野）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MedicalClientHooks {

    /** 1.20.1 的心数图集（Gui.GUI_ICONS_LOCATION 是 private，自定义同路径）。 */
    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");

    /** 倒地瞬间锁定的视角（null = 未倒地）。 */
    private static float lockedYaw;
    private static float lockedPitch;
    private static boolean cameraLocked;
    /** 屏幕模糊着色器开关状态。 */
    private static boolean blurActive;

    private MedicalClientHooks() {
    }

    /** 倒地时加载原版模糊着色器（真屏幕模糊，替代恶心扭曲）；恢复时清除。 */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        boolean downed = self != null && self.downed();
        if (downed && !blurActive) {
            blurActive = true;
            minecraft.gameRenderer.loadEffect(new ResourceLocation("minecraft", "shaders/post/blur.json"));
        } else if (!downed && blurActive) {
            blurActive = false;
            minecraft.gameRenderer.shutdownEffect();
        }
        // 倒地姿态客户端强制（原版客户端每 tick 重算姿态会覆盖服务端设置）
        if (downed) {
            minecraft.player.setSwimming(true);
            minecraft.player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        }
        // 倒地姿态客户端强制（原版客户端每 tick 重算姿态会覆盖服务端设置）
        if (downed) {
            minecraft.player.setSwimming(true);
            minecraft.player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
        }
    }

    /** 倒地时血条：取消原版心数，画**缓冲消耗的鲜红心**——倒地流失在血条上可见（用户指令 #7）。 */
    @SubscribeEvent
    public static void onHealthOverlayRender(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        if (self == null || !self.downed()) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        int left = minecraft.getWindow().getGuiScaledWidth() / 2 - 91;
        int top = minecraft.getWindow().getGuiScaledHeight() - 39;
        // 缓冲消耗：raw ∈ (−100, 0] → 满血缓慢掉到空（倒地流失可视化）
        double buffered = (self.vitality() - com.opamb.barotrauma.medical.affliction.AfflictionContainer.MIN_VITALITY)
                / (0.0 - com.opamb.barotrauma.medical.affliction.AfflictionContainer.MIN_VITALITY);
        int filled = (int) Math.ceil(Math.max(0.0, Math.min(1.0, buffered)) * 10);
        boolean flashOn = (System.currentTimeMillis() / 400) % 2 == 0;
        for (int i = 0; i < 10; i++) {
            int hx = left + i * 8;
            g.blit(GUI_ICONS, hx, top, 16, 0, 9, 9);   // 空心容器
            boolean thisFilled = i < filled;
            if (flashOn && thisFilled) {
                g.blit(GUI_ICONS, hx, top, 52, 0, 9, 9);   // 鲜红心（闪烁=紧急，随流失减少）
            }
        }
        g.drawString(minecraft.font, Component.literal("⚠ 重伤倒地——等待队友救援"),
                left, top - 10, 0xFFFF5A5A);
    }

    /** E 键打开背包时替换为健康界面（背包左移 + 医疗面板右侧 + 全部交互）——用户指令 #3。 */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                && !(event.getNewScreen() instanceof MedicalInventoryScreen)) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                event.setNewScreen(new MedicalInventoryScreen(minecraft.player));
            }
        }
    }

    /** 手持药品左键玩家 → 打开对方的身体面板（而不是打人）。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.client.player.LocalPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof net.minecraft.world.entity.player.Player target)
                || target == event.getEntity()) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof com.opamb.barotrauma.medical.MedicalItem)) {
            return;
        }
        event.setCanceled(true);
        var self = ClientMedicalCache.snapshot(player.getUUID());
        if (self != null && self.downed()) {
            player.displayClientMessage(Component.literal("你已重伤倒地，等待救援"), true);
            return;
        }
        Minecraft.getInstance().setScreen(
                new MedicalScreen(target.getUUID(), target.getName().getString()));
    }

    /** 倒地时无法移动（清空移动输入；视角由相机锁接手）。 */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        var self = ClientMedicalCache.snapshot(event.getEntity().getUUID());
        if (self == null || !self.downed()) {
            return;
        }
        var input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /**
     * 倒地时**视角完全锁定**：拖动鼠标无法转视角（用户指令的确切含义）。
     * 锁定在倒地瞬间的朝向；恢复后解除。
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        boolean downed = self != null && self.downed();
        if (downed) {
            if (!cameraLocked) {
                lockedYaw = minecraft.player.getYRot();
                lockedPitch = minecraft.player.getXRot();
                cameraLocked = true;
            }
            event.setYaw(lockedYaw);
            event.setPitch(lockedPitch);
        } else if (cameraLocked) {
            cameraLocked = false;
        }
    }

    /** 饱食度机制已移除：饱食度栏整个隐藏。 */
    @SubscribeEvent
    public static void onFoodBarRender(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.FOOD_LEVEL.type()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isCreative()) {
            return;
        }
        event.setCanceled(true);
    }

    /** 移除旧的重复 tick 处理（合并进上方 onClientTick）。 */

    /** 倒地时取消原版暗角（含低血量的红色脉冲）——用户指令：屏幕不要变红。 */
    @SubscribeEvent
    public static void onVignetteRender(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.VIGNETTE.type()) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        if (self != null && self.downed()) {
            event.setCanceled(true);
        }
    }

    /** 全部医疗物品清单（健康面板药品栏用）。 */
    public static java.util.List<net.minecraft.world.item.Item> allMedicalItems() {
        return java.util.List.of(
                com.opamb.barotrauma.medical.ModMedicalItems.BANDAGE.get(),
                com.opamb.barotrauma.medical.ModMedicalItems.ANTIBIOTIC_GLUE.get(),
                com.opamb.barotrauma.medical.ModMedicalItems.MORPHINE.get(),
                com.opamb.barotrauma.medical.ModMedicalItems.BLOOD_PACK.get(),
                com.opamb.barotrauma.medical.ModMedicalItems.SALINE.get(),
                com.opamb.barotrauma.medical.ModMedicalItems.NALOXONE.get());
    }

    /** 右键打开自己的健康界面（背包在左 + 医疗面板在右——用户指令）。 */
    public static void openSelfTreatScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        if (self != null && self.downed()) {
            minecraft.player.displayClientMessage(Component.literal("你已重伤倒地，等待队友救援"), true);
            return;
        }
        minecraft.setScreen(new MedicalScreen(minecraft.player.getUUID(),
                minecraft.player.getName().getString()));
    }

    /**
     * 倒地模型翻面（用户指令：保持游泳姿态，只把模型翻个面=肚皮朝上）。
     * 直接 Y 镜像会被渲染器自身的姿态旋转覆盖（上一版失败的根因）——
     * 正确做法：绕身体长轴的 180° 旋转做**共轭变换**（rotX(pitch)·rotZ(180)·rotX(−pitch)），
     * 使翻面在渲染器完成俯仰旋转之后仍然生效。
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        var self = ClientMedicalCache.snapshot(event.getEntity().getUUID());
        if (self == null || !self.downed()) {
            return;
        }
        var stack = event.getPoseStack();
        stack.translate(0, 0.3, 0); // 外层整体抬升，避免翻面后陷入地面
        float pitch = event.getEntity().getXRot();
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
    }
}
