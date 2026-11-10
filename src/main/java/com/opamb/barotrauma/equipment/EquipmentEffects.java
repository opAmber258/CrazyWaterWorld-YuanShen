package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.OxygenTankItem;
import com.opamb.barotrauma.medical.MedicalDamageTypes;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 装备系统的事件入口：
 *
 * <ul>
 *   <li>给每个玩家挂装备 Capability；</li>
 *   <li>死亡/换维度克隆时复制装备（装备不随死亡掉落）；</li>
 *   <li>登录时把旧存档的护甲/副手物品迁走；</li>
 *   <li>头盔 + 身体护甲按护甲点数减免伤害；</li>
 *   <li>氧气瓶槽每 20 tick 自动补氧并消耗 1 点耐久。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EquipmentEffects {

    /** 氧气瓶自动补氧间隔（tick）。 */
    private static final int OXYGEN_INTERVAL = 20;
    /** 和原版护甲一样的固定韧性，避免 12 点护甲被 20 点上限吃掉太多收益。 */
    private static final float EQUIPMENT_TOUGHNESS = 2.0F;

    private EquipmentEffects() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player player)) {
            return;
        }
        // 菜单如果比 capability 先建，会留下一个 pending 容器；接管同一个实例。
        EquipmentInventory pending = EquipmentData.takePending(player);
        EquipmentProvider provider = new EquipmentProvider(
                pending != null ? pending : new EquipmentInventory());
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
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EquipmentData.migrateLegacy(player);
        }
    }

    /** 头盔 + 身体护甲的护甲点数在 LivingHurt 阶段直接减伤。 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getAmount() <= 0.0F) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || event.getSource().is(MedicalDamageTypes.PRESSURE)
                || event.getSource().is(MedicalDamageTypes.MEDICAL)) {
            // 海压和医疗结算不吃头盔/护甲减伤。
            return;
        }
        int armor = EquipmentData.armorPoints(player);
        if (armor <= 0) {
            return;
        }
        float reduced = CombatRules.getDamageAfterAbsorb(event.getAmount(), armor, EQUIPMENT_TOUGHNESS);
        if (reduced < event.getAmount()) {
            event.setAmount(reduced);
        }
    }

    /** 氧气瓶槽：真正在水下且空气不满时补满，并消耗 1 点耐久（耐久 0 即耗尽）。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % OXYGEN_INTERVAL != 0) {
            return;
        }
        EquipmentInventory equipment = EquipmentData.getOrCreate(player);
        ItemStack tank = equipment.getStackInSlot(EquipmentSlotType.OXYGEN_TANK);
        if (tank.isEmpty() || !(tank.getItem() instanceof OxygenTankItem)) {
            return;
        }
        if (tank.getDamageValue() >= tank.getMaxDamage()) {
            return;
        }
        boolean underwater = player.isEyeInFluid(FluidTags.WATER)
                || OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition());
        if (player.getAirSupply() >= player.getMaxAirSupply() || !underwater) {
            return;
        }
        player.setAirSupply(player.getMaxAirSupply());
        // 装备槽不是手部槽，不广播主手碎物品动画；空回调即只扣耐久/耗尽后清空。
        tank.hurtAndBreak(1, player, p -> {
        });
        equipment.setChanged();
    }
}
