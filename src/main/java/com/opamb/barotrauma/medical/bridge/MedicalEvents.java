package com.opamb.barotrauma.medical.bridge;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.MedicalDamageTypes;
import com.opamb.barotrauma.medical.MedicalItem;
import com.opamb.barotrauma.medical.MedicalProvider;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.DamageConversions;
import com.opamb.barotrauma.network.MedicalSyncPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医疗系统的 Forge 总线桥接——**病症即血量**（2026-11-07 设计指令）。
 *
 * <p>原作语义（源码 CharacterHealth.CalculateVitality）：
 * {@code vitality = MaxVitality − Σ GetVitalityDecrease(affliction)}——
 * 血量是从病症实时算出来的，没有独立的扣血积分。因此：</p>
 * <ol>
 *   <li>取消原版 MC 对玩家的一切伤害结算（@{code setCanceled}），全部转成病症；
 *       未映射来源（生物/武器等）过渡期折算为躯干钝器伤——武器模块接入后替换。</li>
 *   <li>取消原版自然回血/药水治疗——治疗权归医疗系统（M2 的药品）。</li>
 *   <li>每秒推进强度自然变化 + 缺氧写入，然后把 vitality 镜像到 MC 心数
 *       （M3 换成专属 HUD）；vitality≤0 用 {@link MedicalDamageTypes#MEDICAL}
 *       伤害源结算死亡（转化器对该来源放行，防止死循环）。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MedicalEvents {

    private static final ResourceLocation MEDICAL_KEY =
            new ResourceLocation(BarotraumaMod.MOD_ID, "medical");

    /** 原作 vitality 满值（CharacterHealth 构造：vitality = 100.0f）。 */
    public static final double MAX_VITALITY = 100.0;

    /** 每个玩家上次同步的签名（行+活力），变化才发包。 */
    private static final Map<UUID, String> lastSyncSignatures = new HashMap<>();

    /** 本模组确实施加过倒地效果的玩家；清理时只动自己加的效果。 */
    private static final Set<UUID> DOWNED_APPLIED = ConcurrentHashMap.newKeySet();

    private MedicalEvents() {
    }

    /** 世界加载时强制关闭自然回血 gamerule——本模组所有治疗归医疗系统（原作同款哲学）。 */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        var server = event.getServer();
        server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION)
                .set(false, server);
        BarotraumaMod.LOGGER.info("[medical] 已关闭自然回血（gamerule naturalRegeneration=false）");
    }

    /** 给每个玩家挂上医疗容器（其他实体不挂）。 */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(MEDICAL_KEY, new MedicalProvider());
        }
    }

    /**
     * 服务端每秒（每 20 tick）：缺氧写入 → 强度自然变化 → 生命状态机（正常/倒地/真死）。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        // 移除饱食度机制（用户指令 2026-11-07：原作潜渊症不吃东西）——
        // 饱食度锁满、饱和度锁 0：饥饿永不消耗、饱食度回血的驱动源彻底消失
        if (!player.isCreative() && !player.isSpectator()) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(0.0F);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            // 切创造/旁观 = 上帝模式：清空医疗状态并解除倒地效果，并回发快照
            //（修复“切创造无法恢复”：不清签名的话客户端缓存一直是倒地态）
            clearDownedEffects(player);
            AfflictionContainer c = MedicalCapabilities.container(player);
            if (c != null) {
                c.clear();
                if (player instanceof ServerPlayer serverPlayer) {
                    lastSyncSignatures.remove(serverPlayer.getUUID());
                    syncIfChanged(serverPlayer, c);
                }
            }
            return;
        }
        AfflictionContainer container = MedicalCapabilities.container(player);
        if (container == null) {
            return;
        }
        // 缺氧写入：憋气到 0 之后（原版溺水节奏）每秒注入 12 强度（底表 §2.5 量级）。
        // 氧气模块（舱内 O2）将来通过同一条路径写；届时这里只保留溺水分支。
        if (player.getAirSupply() <= 0) {
            container.add(AfflictionType.OXYGEN_LOW, null, DamageConversions.hypoxiaPerSecond());
        }
        container.tickSecond();
        // 每秒 tick 才累加一次倒地流失；受伤事件只更新状态，不重复加。
        updateLifeState(player, container, true);
    }

    /**
     * 取消原版 MC 对玩家的伤害，全部转成病症（叠加的"立即掉血"语义由
     * 病症强度本身承担：强度上去了，vitality 立刻按公式下降——原作同款）。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        // 自己的死亡结算放行；/kill、虚空等 bypass 类放行（保留调试与兜底手段）
        if (source.is(MedicalDamageTypes.MEDICAL)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AfflictionContainer container = MedicalCapabilities.container(player);
        if (container == null) {
            return;
        }
        event.setCanceled(true);
        if (player.isDeadOrDying()) {
            return;
        }
        double amount = event.getAmount();
        List<DamageConversions.Application> plan = null;
        if (source.is(DamageTypeTags.IS_FALL)) {
            plan = DamageConversions.fromFall(amount);
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            DamageConversions.Application application =
                    DamageConversions.fromFire(amount, player.getRandom().nextDouble());
            container.add(AfflictionType.BURN, application.limb(), application.strength());
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            plan = DamageConversions.fromExplosion(amount);
        } else if (source.is(DamageTypeTags.IS_DROWNING)) {
            // 缺氧已由憋气管线按秒写入（onPlayerTick），溺水伤害本身不再重复折算
        } else if (source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.WITHER)) {
            // 这些来源没有攻击者，但被取消的原版伤害必须转成病症，否则完全不掉血。
            plan = DamageConversions.fromGeneric(amount);
        } else if (source.is(MedicalDamageTypes.PRESSURE)) {
            // 深海挤压是 UnderwaterSurvival 的持续伤害；取消原版结算后必须转成病症。
            plan = DamageConversions.fromGeneric(amount);
        } else if (source.getDirectEntity() instanceof Projectile projectile) {
            // limbByHeightRatio 的 0 = 头顶、1 = 脚底；实体高度比例是 0=脚、1=头，先反转。
            double heightRatio = 1.0 - (projectile.getY() - player.getY()) / player.getBbHeight();
            var look = player.getLookAngle();
            double leftDot = (projectile.getX() - player.getX()) * look.z
                    - (projectile.getZ() - player.getZ()) * look.x;
            plan = DamageConversions.fromProjectile(amount, heightRatio, leftDot > 0.0);
        } else if (source.getEntity() != null) {
            // 有攻击者的近战伤害：原作三件套过渡版（武器模块接入后按部位归属替换）
            plan = DamageConversions.fromGeneric(amount);
        } else {
            // 无攻击者的环境杂项（浆果丛/仙人掌/饥饿/窒息等）：只取消原版伤害，不乱加病症
            BarotraumaMod.LOGGER.debug("[medical] 未映射的无主伤害 {} 已取消（不产生病症）",
                    source.getMsgId());
        }
        if (plan != null) {
            apply(container, plan);
            BarotraumaMod.LOGGER.debug("[medical] {} 受到 {}（{}）→ 注入 {} 条病症",
                    player.getName().getString(), source.getMsgId(), amount, plan.size());
        }
        updateLifeState(player, container);
    }

    /** 服务端也要取消“手持药品左键玩家”：客户端取消只影响本地攻击，攻击包仍会到服务端。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (!(event.getTarget() instanceof Player target) || target == attacker) {
            return;
        }
        if (attacker.getMainHandItem().getItem() instanceof MedicalItem) {
            event.setCanceled(true);
        }
    }

    /** 病症即血量：一切原版治疗（自然回血/药水）一律作废，治疗权归 M2 的医疗物品。 */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player player
                && !player.level().isClientSide()
                && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    /** 死亡清空全部病症（REQUIREMENTS §5.5 验收点）。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            DOWNED_APPLIED.remove(player.getUUID());
            AfflictionContainer container = MedicalCapabilities.container(player);
            if (container != null) {
                container.clear();
            }
        }
    }

    /**
     * 生命状态机（原作 MinVitality 语义，无布娃娃——倒地表现为缓慢爬行+发光轮廓）：
     * <ul>
     *   <li>raw &gt; 0：正常，心数镜像 vitality；被救回时清掉倒地效果。</li>
     *   <li>0 ≥ raw &gt; −100：**重伤倒地**——缓慢爬行（缓慢Ⅴ）+ 虚弱 + 发光轮廓（队友能找到），
     *       心数压到半颗心；此状态下仍会因失血继续恶化，队友把伤口治到 vitality &gt; 0 即起死回生。</li>
     *   <li>raw ≤ −100：**真死**——{@code die()} 直调（0 血状态下 hurt() 会被
     *       isDeadOrDying 拒绝——上一版"躺地无法恢复"卡死 bug 的根因）。</li>
     * </ul>
     */
    private static void updateLifeState(Player player, AfflictionContainer container) {
        updateLifeState(player, container, false);
    }

    private static void updateLifeState(Player player, AfflictionContainer container,
                                        boolean accumulateDownedDrain) {
        if (player.isDeadOrDying()) {
            return; // 已死实体不做任何镜像（修复死亡循环/鞭尸）
        }
        double raw = container.rawVitality(MAX_VITALITY);
        if (raw <= AfflictionContainer.MIN_VITALITY) {
            // 真死：0 血状态下 hurt() 会被 isDeadOrDying 拒绝——先把心数抬到 2 再结算
            //（修"持续死亡/无法重生"：die() 直调不会给客户端发死亡屏）
            player.setHealth(2.0F);
            Holder<DamageType> holder = player.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(MedicalDamageTypes.MEDICAL);
            player.hurt(new DamageSource(holder), Float.MAX_VALUE);
            return;
        }
        if (raw <= 0) {
            // 进入倒地：清掉倒地前的残余治疗（只清一次），开启自然流失
            // 流失速度**跟原版一样，同伤势挂钩**（用户指令 #1）：
            // 每秒流失 = 当前伤势的活力消耗率（与原作出血推失血后的活力下降速率同构）
            container.enterDowned();
            DOWNED_APPLIED.add(player.getUUID());
            if (accumulateDownedDrain) {
                container.addDownedDrain(-container.vitalityDecreaseTotal(MAX_VITALITY) / 100.0);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 4, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 2, true, false));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 45, 0, true, false));
            // 屏幕模糊：由客户端 blur.json 后处理着色器实现（MedicalClientHooks）；
            // 黑暗脉冲（濒死压迫感）。CONFUSION 恶心扭曲按用户指令移除（会转动视角）
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0, true, false));
            // 强制游泳姿态：视角压低 + 呈趴伏状（不是布娃娃，只是姿态数据）
            player.setSwimming(true);
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            if (player.getHealth() > 1.0F) {
                player.setHealth(1.0F);
            }
        } else {
            // 被救回（vitality 回到正数）：恢复治疗能力、流失归零并解除倒地效果
            container.exitDowned();
            container.resetDownedDrain();
            clearDownedEffects(player);
            // 心数无条件镜像 vitality：任何不被病症支撑的血量变化（漏网回血等）一秒内回正
            player.setHealth((float) Math.max(1.0F,
                    Math.min(player.getMaxHealth(), raw / MAX_VITALITY * player.getMaxHealth())));
        }
        if (player instanceof ServerPlayer serverPlayer) {
            syncIfChanged(serverPlayer, container);
        }
    }

    /** 解除倒地效果（只清本模组确实施加过的那四种，避免误删药水/其他模组的同名效果）。 */
    private static void clearDownedEffects(Player player) {
        if (!DOWNED_APPLIED.remove(player.getUUID())) {
            return;
        }
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MobEffects.GLOWING);
        player.removeEffect(MobEffects.DARKNESS);
    }

    /** 病症行 / raw 活力 / 倒地状态 任一变化时才发包（登录/重生/换维度由 lastSync 置空强制触发）。 */
    private static void syncIfChanged(ServerPlayer player, AfflictionContainer container) {
        double raw = container.rawVitality(MAX_VITALITY);
        boolean downed = raw <= 0;
        String signature = String.join(";", container.rows())
                + "|" + String.format("%.1f", raw) + "|" + downed;
        if (!signature.equals(lastSyncSignatures.get(player.getUUID()))) {
            lastSyncSignatures.put(player.getUUID(), signature);
            ModNetwork.sendToPlayer(player, new MedicalSyncPacket(player.getUUID(),
                    container.rows().toArray(String[]::new), (float) raw, downed));
        }
    }

    /** 非死亡克隆（换维度等）复制医疗状态；死亡克隆保持空容器。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            DOWNED_APPLIED.remove(event.getOriginal().getUUID());
            return;
        }
        // Forge 在 Clone 前会失效旧实体的 capability，读取旧容器前必须临时恢复。
        event.getOriginal().reviveCaps();
        try {
            AfflictionContainer old = MedicalCapabilities.container(event.getOriginal());
            AfflictionContainer fresh = MedicalCapabilities.container(event.getEntity());
            if (old != null && fresh != null) {
                fresh.copyFrom(old);
            }
        } finally {
            event.getOriginal().invalidateCaps();
        }
        if (DOWNED_APPLIED.remove(event.getOriginal().getUUID())) {
            DOWNED_APPLIED.add(event.getEntity().getUUID());
        }
    }

    /** 登录 / 重生 / 换维度：清签名强制发一次全量。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        resetSync(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resetSync(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resetSync(event.getEntity());
    }

    private static void resetSync(Entity entity) {
        if (entity instanceof ServerPlayer player && !player.level().isClientSide()) {
            lastSyncSignatures.remove(player.getUUID());
            AfflictionContainer container = MedicalCapabilities.container(player);
            if (container != null) {
                syncIfChanged(player, container);
            }
        }
    }

    private static void apply(AfflictionContainer container, List<DamageConversions.Application> plan) {
        for (DamageConversions.Application application : plan) {
            container.add(application.type(), application.limb(), application.strength());
        }
    }
}
