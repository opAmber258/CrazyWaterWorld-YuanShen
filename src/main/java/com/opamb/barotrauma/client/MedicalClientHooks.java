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
 *   <li>H 键：**单击**打开自己的健康面板；**长按 0.67 秒**打开最近队友的面板
 *       （长按期间最近队友被描边高亮，提示"松开就对他治疗"）。</li>
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

    /** H 键：短按开自己，长按开最近队友。 */
    public static final net.minecraft.client.KeyMapping OPEN_HEALTH = new net.minecraft.client.KeyMapping(
            "key.barotrauma.health", org.lwjgl.glfw.GLFW.GLFW_KEY_H, "key.categories.barotrauma");

    /**
     * 长按判定阈值：用户指定 0.67 秒。用 {@link System#nanoTime()} 而不是 tick 计数——
     * 20 tick 的量化误差最大可达 50ms，0.67s 这种"略长于短按"的阈值需要更细的粒度。
     */
    public static final long HOLD_NANOS = (long) (0.67D * 1_000_000_000L);

    /**
     * 能相互治疗的最大距离（格）——与服务端 {@code MedicalTuning} 同源，
     * 用户 2026-11-10 指定 2 格。
     */
    private static final double NEARBY_RADIUS = com.opamb.barotrauma.medical.MedicalTuning.TREAT_RANGE_BLOCKS;

    /** H 键按下的时刻（0 = 未按下）。 */
    private static long holdStartNanos;
    /** 长按已达到阈值、正在高亮阶段（松开即开面板）。 */
    private static boolean holdPrimed;
    /** 长按时被选中的队友 UUID（渲染高亮用）。 */
    private static java.util.UUID holdTarget;
    /** 上一 tick 被高亮的队友（效果清理用）。 */
    private static java.util.UUID lastHighlighted;

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

    /** 退出登录：清空所有玩家快照，避免下次进入世界读到上一局的旧数据。 */
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMedicalCache.clearAll();
    }

    /**
     * 每客户端 tick：倒地表现 + H 键（短按自己 / 长按最近队友）。
     *
     * <p>H 键用**原始按键状态**而不是 {@code consumeClick()}：长按需要一个持续时长，
     * 而 `consumeClick` 只在按下的那一 tick 返回一次。松开时若还没到阈值就按单击处理
     * （用户指定的 0.67 秒）。</p>
     */
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
        tickHealthKey(minecraft, downed);
        syncHighlightEffect(minecraft);
    }

    /**
     * 维护"长按高亮"的发光效果：有目标就续，没目标就摘掉。
     *
     * <p>{@code addEffect} 在客户端是本地状态，不清理会一直留在实体上
     * （而且每次续期都会重置计时）。这里统一收口：每 tick 只在目标变化或
     * 持续时间快到时刷新，避免每帧都 addEffect。</p>
     */
    private static void syncHighlightEffect(Minecraft minecraft) {
        java.util.UUID target = holdTargetId();
        if (target == null) {
            if (lastHighlighted != null && minecraft.level != null) {
                var entity = minecraft.level.getPlayerByUUID(lastHighlighted);
                if (entity != null) {
                    entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                }
            }
            lastHighlighted = null;
            return;
        }
        if (minecraft.level == null) {
            return;
        }
        var entity = minecraft.level.getPlayerByUUID(target);
        if (entity == null) {
            return;
        }
        lastHighlighted = target;
        var existing = entity.getEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        // 剩余不足 10 tick 就续期（约 100ms 持续，够覆盖两次 tick 的抖动）
        if (existing == null || existing.getDuration() < 10) {
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 20, 0, false, false, false));
        }
    }

    /**
     * H 键：**单击**开自己的面板；**按住 0.67 秒**后进入"锁定队友"状态，
     * 该队友全程高光，**松开时**打开他的面板。
     *
     * <p>为什么不按住满就直接开面板：用户要的是"长按时被选中的队友以高光显示"——
     * 高光必须有一段可感知的展示时间，并在松开那一刻兑现成面板，玩家才能确认
     * "我瞄的是这个队友"。因此分三段：</p>
     * <ol>
     *   <li>按住 &lt; 0.67s → 什么都不做（短按路过不会闪高光）；</li>
     *   <li>按住 ≥ 0.67s → 标记 holdPrimed，目标高亮，持续到时松开；</li>
     *   <li>松开时：holdPrimed → 开队友面板；否则 → 视为单击，开自己的面板。</li>
     * </ol>
     */
    private static void tickHealthKey(Minecraft minecraft, boolean downedSelf) {
        boolean pressed = OPEN_HEALTH.isDown();

        if (!pressed) {
            if (holdStartNanos != 0) {
                boolean primed = holdPrimed;
                java.util.UUID target = holdTarget;
                resetHold();
                // 只有在按下期间没有界面被打开过，才兑现这次操作
                if (minecraft.screen != null) {
                    return;
                }
                if (!primed) {
                    // 没到 0.67 秒 = 单击 → 开自己
                    openSelfTreatScreen();
                    return;
                }
                if (downedSelf) {
                    minecraft.player.displayClientMessage(
                            Component.literal("你已重伤倒地，等待队友救援"), true);
                    return;
                }
                if (target == null) {
                    minecraft.player.displayClientMessage(
                            Component.literal("附近没有可治疗的队友"), true);
                    return;
                }
                var teammate = minecraft.level == null ? null
                        : minecraft.level.getPlayerByUUID(target);
                if (teammate == null) {
                    return;
                }
                minecraft.setScreen(new MedicalScreen(teammate.getUUID(),
                        teammate.getName().getString()));
            }
            return;
        }
        if (holdStartNanos == 0) {
            // 有界面开着时不计时：否则在背包里按住 H 再关掉背包，
            // 计时会从"按下背包里那一刻"算起，一松开就误开面板。
            if (minecraft.screen == null) {
                holdStartNanos = System.nanoTime();
            }
            return;
        }
        if (!holdPrimed && System.nanoTime() - holdStartNanos >= HOLD_NANOS) {
            holdPrimed = true;
            // 达到阈值那一刻选一次目标；之后按住期间不再换人，
            // 否则玩家走动时高光会在队友之间跳。
            var nearest = nearestTeammate(minecraft);
            holdTarget = nearest == null ? null : nearest.getUUID();
        }
    }

    /** 清空长按状态。 */
    private static void resetHold() {
        holdStartNanos = 0;
        holdPrimed = false;
        holdTarget = null;
    }

    /** 最近的可治疗队友（不含自己、旁观、已死亡；带距离上限）。 */
    public static net.minecraft.world.entity.player.Player nearestTeammate(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        net.minecraft.world.entity.player.Player best = null;
        double bestDist = NEARBY_RADIUS * NEARBY_RADIUS;
        for (var other : minecraft.level.players()) {
            if (other == minecraft.player || other.isSpectator() || other.isDeadOrDying()) {
                continue;
            }
            double dist = minecraft.player.distanceToSqr(other);
            if (dist <= bestDist) {
                bestDist = dist;
                best = other;
            }
        }
        return best;
    }

    /** 当前高亮的队友 UUID（null = 不高亮）。只在按住满 0.67 秒后非 null。 */
    public static java.util.UUID holdTargetId() {
        return holdPrimed ? holdTarget : null;
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

    /**
     * E 键打开背包时替换为健康界面（背包左移 + 医疗面板右侧 + 全部交互）——用户指令 #3。
     *
     * <p>**倒地时整个拦截**：用户指令"自己倒地禁用药"，而背包里的健康栏可以拖药使用——
     * 不拦就等于倒地还能自救。倒地只保留"等待救援"提示。</p>
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
                || event.getNewScreen() instanceof MedicalInventoryScreen) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var self = ClientMedicalCache.snapshot(minecraft.player.getUUID());
        if (self != null && self.downed()) {
            // 倒地：取消打开背包，只给一行提示
            event.setCanceled(true);
            minecraft.player.displayClientMessage(
                    Component.literal("你已重伤倒地，无法打开背包——等待队友救援"), true);
            return;
        }
        event.setNewScreen(new MedicalInventoryScreen(minecraft.player));
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
        return com.opamb.barotrauma.medical.ModMedicalItems.all().stream()
                .map(net.minecraftforge.registries.RegistryObject::get)
                .toList();
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
