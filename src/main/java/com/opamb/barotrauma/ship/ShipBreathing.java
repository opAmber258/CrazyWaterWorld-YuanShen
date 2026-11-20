package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.equipment.EquipmentData;
import com.opamb.barotrauma.equipment.EquipmentEffects;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家呼吸与船舱氧气的桥：谁在哪个舱室、这个舱室还有多少氧、吸气扣多少。
 *
 * <p>对应原作 {@code Character.UpdateOxygen}：</p>
 * <ul>
 *   <li>头不在水里、且人在某个 hull 里 → 可用氧取该 hull 的 {@code OxygenPercentage}；</li>
 *   <li>自身可用氧低于 {@link #INSUFFICIENT_OXYGEN}（原作 30）→ {@code oxygenlow} 上涨；</li>
 *   <li>没有外部氧源（潜水服）时，吸气会扣所在 hull 的氧：
 *       {@code Hull.OxygenConsumptionSpeed = 700}/秒，本项目折算 7.32/秒。</li>
 * </ul>
 */
public final class ShipBreathing {

    /** 原作 {@code CharacterHealth.InsufficientOxygenThreshold}：可用氧低于 30 算缺氧。 */
    public static final double INSUFFICIENT_OXYGEN = 30.0D;

    /** 找到船时的舱室定位；{@code compartment < 0} 表示人在船体结构里但不在舱室。 */
    public record Zone(ShipState ship, int compartment) {
    }

    private ShipBreathing() {
    }

    /**
     * 玩家眼睛所在的船舱室。
     *
     * <p>物理船走 VS2 实时逆变换（{@link VsState#queryCompartmentIndex}）；方块船找附近
     * 已加载的船核心。不在船上返回 {@code null}。</p>
     */
    @Nullable
    public static Zone zone(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Integer physical = VsState.queryCompartmentIndex(level, eye);
        if (physical != null) {
            ShipState state = VsState.activeState();
            return state == null ? null : new Zone(state, physical);
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level,
                BlockPos.containing(eye), 32.0D);
        if (core == null) {
            return null;
        }
        ShipState state = core.state() != null ? core.state() : core.rescan(level);
        if (state == null) {
            return null;
        }
        // 只按“最近核心”找船不够：站在陆地/哨站上、离船核心 10 格也会选中它，
        // compartmentAt 返回 -1，可用氧被判成 0。只有眼睛真的落在舱室里才算。
        int compartment = state.compartmentAt(BlockPos.containing(eye));
        if (compartment < 0) {
            return null;
        }
        return new Zone(state, compartment);
    }

    /**
     * 当前可用氧（0..100，原作 {@code OxygenAvailable} 的近似）。
     *
     * <ul>
     *   <li>创造/旁观/水下呼吸：100；</li>
     *   <li>潜水服：只看内部氧气瓶（有瓶 100，没瓶 0）；</li>
     *   <li>头在水里/空气海里：0；</li>
     *   <li>在船舱室里：该舱室的氧气百分比（连海空腔按 0）；</li>
     *   <li>哨站、陆地、船外：100。</li>
     * </ul>
     */
    public static double availableOxygen(Player player) {
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.WATER_BREATHING)) {
            return 100.0D;
        }
        if (EquipmentData.hasDivingSuit(player)) {
            return EquipmentEffects.hasUsableSuitTank(player) ? 100.0D : 0.0D;
        }
        if (player.isEyeInFluid(FluidTags.WATER)
                || OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition())) {
            return 0.0D;
        }
        Zone zone = zone(player);
        if (zone == null) {
            return 100.0D;
        }
        return zone.compartment() < 0
                ? 0.0D : zone.ship().simulation().oxygenPercent(zone.compartment());
    }

    /**
     * 服务端每 tick 调用：无潜水服的乘员从所在舱室吸气。
     *
     * <p>原作只在 {@code OxygenAvailable * 0.98 < hull.OxygenPercentage} 时才扣舱室氧；
     * 潜水服自带氧源（{@code UseHullOxygen=false} 的等效），不扣。</p>
     */
    public static void breathe(Player player) {
        if (player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.WATER_BREATHING)
                || EquipmentData.hasDivingSuit(player)) {
            return;
        }
        if (player.isEyeInFluid(FluidTags.WATER)
                || OceanEnvironment.isOceanWaterAt(player.level(), player.getEyePosition())) {
            return;
        }
        Zone zone = zone(player);
        if (zone == null || zone.compartment() < 0) {
            return;
        }
        zone.ship().simulation().breathe(zone.compartment(), 1.0D / 20.0D);
    }
}
