package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.registry.ModSounds;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 装备系统的事件入口：
 *
 * <ul>
 *   <li>给每个玩家挂装备 Capability，并把腰带槽变化接到背包容器；</li>
 *   <li>死亡/换维度克隆时复制装备（装备不随死亡掉落）；</li>
 *   <li>登录时把旧存档的护甲/副手物品迁走；</li>
 *   <li>潜水服：内部氧气瓶供氧、低氧报警、陆上减速；</li>
 *   <li>每 tick 同步装备背包（工具带/背包）的绑定。</li>
 * </ul>
 *
 * <p>护甲减伤不在这里：原作没有"护甲点数"，头盔/防弹衣/潜水服是
 * {@link ArmorModifiers} 的按病症伤害倍率，由 {@code MedicalEvents}
 * 把伤害换算成病症时统一结算。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EquipmentEffects {

    /** 氧气瓶自动补氧间隔（tick）。 */
    private static final int OXYGEN_INTERVAL = 20;
    /** 报警蜂鸣间隔（tick）：原作 WarningBeepSlow.ogg 长 0.57 秒、循环播放 ≈ 12 tick。 */
    private static final int BEEP_INTERVAL = 12;
    /** 陆上减速：原作走 ×0.63 / 跑 ×0.53，这里取 ×0.6。 */
    private static final UUID SLOW_MODIFIER_ID =
            UUID.fromString("6f1d2a7e-7c83-4f5a-9d0c-2b8e4a6c1f37");
    private static final AttributeModifier SLOW_MODIFIER = new AttributeModifier(
            SLOW_MODIFIER_ID, "barotrauma.diving_suit", -0.4D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    private EquipmentEffects() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player player)) {
            return;
        }
        // 菜单如果比 capability 先建，会留下一个 pending 容器；接管同一个实例。
        EquipmentInventory pending = EquipmentData.takePending(player);
        EquipmentInventory inventory = pending != null ? pending : new EquipmentInventory();
        // 腰带槽一换（背包/工具带）就立刻重绑，避免换包同 tick 点击丢东西。
        inventory.setBagListener(stack -> {
            if (!player.level().isClientSide) {
                BagManager.bindFromEquipment(player);
            }
        });
        EquipmentProvider provider = new EquipmentProvider(inventory);
        event.addCapability(new net.minecraft.resources.ResourceLocation(
                BarotraumaMod.MOD_ID, "equipment"), provider);
        // 注意：不要把 provider::invalidate 挂到 addListener 上。
        // PlayerEvent.Clone 的 reviveCaps() 只恢复 provider 的 valid 标志，
        // 不会复活已经 invalidate 的 LazyOptional；挂上去会导致死亡后读不到旧装备。
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // 原实体的 capability 在 Clone 事件里默认已失效，先 revive 再读。
        event.getOriginal().reviveCaps();
        try {
            EquipmentInventory original = EquipmentData.getOrCreate(event.getOriginal());
            EquipmentInventory replacement = EquipmentData.getOrCreate(event.getEntity());
            replacement.copyFrom(original);
        } finally {
            event.getOriginal().invalidateCaps();
        }
        BagManager.bindFromEquipment(event.getEntity());
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EquipmentData.migrateLegacy(player);
            BagManager.bindFromEquipment(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BagManager.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        // 背包绑定/潜水服减速每 tick 都要检查（状态变化要及时生效）。
        BagManager.bindFromEquipment(player);
        updateDivingSuitSlowdown(player);
        // 报警要按原作 0.57s 的循环播放，不能塞进每秒一次的供氧更新里。
        updateDivingSuitBeep(player);
        if (player.tickCount % OXYGEN_INTERVAL != 0) {
            return;
        }
        updateDivingSuitOxygen(player);
    }

    /** 陆上减速：只在水面以上生效（原作潜水服只换行走/奔跑动画，游泳不减速）。 */
    private static void updateDivingSuitSlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        // 我们的大海是"空气海域"：isInWater() 在水里也是 false，必须同时问 OceanEnvironment。
        boolean submerged = player.isInWater()
                || player.isEyeInFluid(FluidTags.WATER)
                || OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition());
        boolean shouldSlow = EquipmentData.hasDivingSuit(player)
                && !submerged
                && !player.getAbilities().flying
                && !player.isSpectator();
        if (shouldSlow) {
            if (!speed.hasModifier(SLOW_MODIFIER)) {
                speed.addTransientModifier(SLOW_MODIFIER);
            }
        } else if (speed.hasModifier(SLOW_MODIFIER)) {
            speed.removeModifier(SLOW_MODIFIER);
        }
    }

    /** 潜水服里是否有可用的氧气瓶（服务端/客户端都能算；供呼吸规则用）。 */
    public static boolean hasUsableSuitTank(Player player) {
        EquipmentInventory equipment = EquipmentData.get(player);
        if (equipment == null) {
            return false;
        }
        ItemStack suit = equipment.getStackInSlot(EquipmentSlotType.DIVING_SUIT);
        if (suit.isEmpty()) {
            return false;
        }
        ItemStack tank = ItemSubSlot.get(suit);
        return !tank.isEmpty()
                && tank.is(EquipmentTags.OXYGEN_TANK)
                && tank.getDamageValue() < tank.getMaxDamage();
    }

    /** FOV 修正用：当前是否挂着我们的潜水服减速。 */
    public static boolean hasDivingSuitSlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SLOW_MODIFIER);
    }

    /**
     * 潜水服内部氧气瓶（照原作 {@code divinggear.xml}）：
     *
     * <p>氧气瓶 {@code OxygenAvailable="1000.0"} 让穿戴者能呼吸；
     * {@code <StatusEffect type="OnWearing" target="Contained" Condition="-0.3">} 在
     * 穿戴者 {@code NeedsAir} 时每秒扣瓶 0.3 点 Condition。<b>扣耐久与所在位置无关</b>——
     * 原作穿着潜水服站在哨站里同样耗瓶。本模组瓶子最大耐久 300，按每秒 1 点折算
     * （≈ 原作 100 Condition / 0.3 每秒 = 333 秒）。</p>
     *
     * <p>空气本身由 {@code UnderwaterSurvival} 的 {@code LivingBreatheEvent} 维持；
     * 这里只负责耗瓶。</p>
     */
    private static void updateDivingSuitOxygen(Player player) {
        EquipmentInventory equipment = EquipmentData.getOrCreate(player);
        ItemStack suit = equipment.getStackInSlot(EquipmentSlotType.DIVING_SUIT);
        if (suit.isEmpty()) {
            return;
        }
        ItemStack tank = ItemSubSlot.get(suit);
        boolean usable = !tank.isEmpty()
                && tank.is(EquipmentTags.OXYGEN_TANK)
                && tank.getDamageValue() < tank.getMaxDamage();
        if (!usable) {
            return;
        }
        // 原作 NeedsAir：创造/旁观/水下呼吸药水不需要空气，也就不耗瓶。
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) {
            return;
        }
        player.setAirSupply(player.getMaxAirSupply());
        tank.hurtAndBreak(1, player, p -> {
        });
        ItemSubSlot.set(suit, tank);
        equipment.setChanged();
    }

    /**
     * 低氧报警：原作是氧气瓶里的
     * {@code <StatusEffect type="OnWearing" target="Contained"><Conditional condition="lt 5.0"/>}
     * 上挂的 {@code <Sound file="WarningBeepSlow.ogg" loop="true"/>}——瓶 Condition 低于
     * 5%（本模组耐久 ≤5%）时<b>无缝循环播放</b>。音效文件本身长 0.57 秒，所以每
     * {@link #BEEP_INTERVAL} tick 重放一次就是原作节奏。
     *
     * <p>另外按用户要求补上<b>没放氧气瓶也报警</b>：原作空瓶（Condition 0 < 5）本来就会
     * 一直响，没瓶在语义上一样是"完全没氧"，不能静音让玩家察觉不到。</p>
     */
    private static void updateDivingSuitBeep(Player player) {
        if (player.tickCount % BEEP_INTERVAL != 0 || player.isDeadOrDying()) {
            return;
        }
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING)) {
            return;
        }
        EquipmentInventory equipment = EquipmentData.get(player);
        if (equipment == null) {
            return;
        }
        ItemStack suit = equipment.getStackInSlot(EquipmentSlotType.DIVING_SUIT);
        if (suit.isEmpty()) {
            return;
        }
        ItemStack tank = ItemSubSlot.get(suit);
        boolean tankPresent = !tank.isEmpty() && tank.is(EquipmentTags.OXYGEN_TANK);
        int left = tankPresent ? tank.getMaxDamage() - tank.getDamageValue() : 0;
        if (!tankPresent || left <= Math.max(1, tank.getMaxDamage() / 20)) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.LOW_OXYGEN_BEEP.get(), SoundSource.PLAYERS, 0.7F, 1.0F);
        }
    }
}
