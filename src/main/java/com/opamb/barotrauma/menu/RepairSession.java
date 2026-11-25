package com.opamb.barotrauma.menu;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.medical.MedicalCapabilities;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.RepairInteractions;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** The server owns the selected device, repair rate and timing. Both panel types use the same data. */
public final class RepairSession {

    public static final int CONDITION = 0;
    public static final int TOOL = 1;
    public static final int HAS_TOOL = 2;
    public static final int ELIGIBLE = 3;
    public static final int ACTIVE = 4;
    public static final int POINTER = 5;
    public static final int SKILL = 6;
    public static final int RESULT = 7;
    public static final int COOLDOWN = 8;
    public static final int FIXED_SKILL = 40;
    private static final int DATA_COUNT = 9;
    private static final int QTE_COOLDOWN = 10;
    private static final double REACH_SQUARED = 64.0D;

    private final Player owner;
    private final BlockPos pos;
    private final RepairTarget target;
    private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
    private boolean repairing;
    private int cooldown;
    private int result;
    private int pauseTicks;
    private double pointer = 1.0D;

    RepairSession(Player player, BlockPos selected) {
        owner = player;
        target = player.level() instanceof ServerLevel level ? RepairActionPacket.find(level, selected) : null;
        pos = target instanceof BlockEntity blockEntity ? blockEntity.getBlockPos().immutable() : selected.immutable();
        data.set(CONDITION, 1000);
        data.set(POINTER, 1000);
        refresh();
    }

    public ContainerData data() {
        return data;
    }

    boolean valid() {
        if (!(owner.level() instanceof ServerLevel level) || target == null || !owner.isAlive()
                || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != target) {
            return false;
        }
        Vec3 center = RepairActionPacket.worldCenter(level, pos);
        return center != null && owner.distanceToSqr(center) <= REACH_SQUARED;
    }

    public void tick() {
        if (pauseTicks > 0) pauseTicks--;
        if (repairing) {
            if (!canContinue()) {
                stop();
            } else {
                if (cooldown > 0) {
                    if (--cooldown == 0) {
                        pointer = 1.0D;
                        result = 0;
                    }
                } else {
                    pointer *= 0.9D;
                }
                target.applyRepair(target.maxCondition() / durationSeconds(successFactor()) / 20.0D);
                if (target.isFull()) stop();
            }
        }
        refresh();
    }

    public void start() {
        if (repairing || !canContinue() || !target.canRepair()) return;
        ServerLevel level = (ServerLevel) owner.level();
        for (ServerPlayer player : level.players()) {
            if (player != owner && player.containerMenu instanceof DeviceMenu menu
                    && menu.repair().target == target && menu.repair().repairing && menu.repair().valid()) {
                return;
            }
        }
        if (!safeWithoutPower(level) && level.random.nextDouble() * 0.5D >= successFactor()) {
            fail(level);
            refresh();
            return;
        }
        repairing = true;
        pointer = 1.0D;
        cooldown = 0;
        result = 0;
        refresh();
    }

    public void timePress() {
        if (!repairing || !canContinue()) return;
        if (cooldown > 0) {
            pointer = 1.0D;
            refresh();
            return;
        }
        cooldown = QTE_COOLDOWN;
        if (pointer <= target.conditionFraction()) {
            target.applyRepair(successFactor() * 3.0D);
            result = 1;
            if (target.isFull()) stop();
        } else {
            result = -1;
            ServerLevel level = (ServerLevel) owner.level();
            if (level.random.nextDouble() * 2.0D > successFactor()) {
                fail(level);
            }
        }
        refresh();
    }

    public void stop() {
        repairing = false;
        result = 0;
        cooldown = 0;
        pointer = 1.0D;
        refresh();
    }

    private boolean canContinue() {
        return pauseTicks == 0 && valid() && !target.isFull()
                && RepairInteractions.hasTool(owner, target.requiredTool());
    }

    private boolean safeWithoutPower(ServerLevel level) {
        if (target.requiredTool() != RepairTarget.Tool.SCREWDRIVER) return false;
        if (target instanceof ReactorBlockEntity reactor) return reactor.generatedKw() < 0.1D;
        if (target instanceof JunctionBoxBlockEntity box) return box.lastVoltage() < 0.1D;
        ShipState ship = DrainPumpBlockEntity.resolveShip(level, pos);
        if (ship == null) return true;
        BlockPos cell = VsState.isPhysical(level, pos) ? VsPilot.toAssembly(pos) : pos;
        if (target instanceof PowerConsumer) return ship.simulation().consumerVoltage(cell) < 0.1D;
        return (target instanceof BatteryBlockEntity || target instanceof SupercapacitorBlockEntity)
                && ship.simulation().voltage() < 0.1D;
    }

    private void fail(ServerLevel level) {
        boolean electrical = target.requiredTool() == RepairTarget.Tool.SCREWDRIVER;
        var medical = MedicalCapabilities.container(owner);
        boolean stunned = !electrical || level.random.nextBoolean();
        if (medical != null) {
            AfflictionType injury = electrical ? AfflictionType.BURN : AfflictionType.LACERATIONS;
            medical.add(injury, Limb.LEFT_ARM, 5.0D);
            medical.add(injury, Limb.RIGHT_ARM, 5.0D);
            if (stunned) medical.add(AfflictionType.STUN, Limb.HEAD, 4.0D);
        }
        Vec3 center = RepairActionPacket.worldCenter(level, pos);
        if (electrical && center != null) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                    5, 0.2D, 0.2D, 0.2D, 0.08D);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.REDSTONE_TORCH_BURNOUT,
                    SoundSource.BLOCKS, 0.65F, 1.1F);
        }
        if (stunned) {
            stop();
            pauseTicks = 80;
        } else if (!repairing) {
            pauseTicks = QTE_COOLDOWN;
        }
        result = -1;
    }

    private double successFactor() {
        return degreeOfSuccess(FIXED_SKILL, target.requiredSkill());
    }

    static double degreeOfSuccess(int skill, int required) {
        return (skill - required + 100.0D) / 200.0D;
    }

    static double durationSeconds(double success) {
        return 25.0D + (5.0D - 25.0D) * success;
    }

    private void refresh() {
        if (target == null) return;
        data.set(CONDITION, (int) Math.round(target.conditionFraction() * 1000.0D));
        data.set(TOOL, target.requiredTool().ordinal());
        data.set(HAS_TOOL, RepairInteractions.hasTool(owner, target.requiredTool()) ? 1 : 0);
        data.set(ELIGIBLE, target.canRepair() ? 1 : 0);
        data.set(ACTIVE, repairing ? 1 : 0);
        data.set(POINTER, (int) Math.round(pointer * 1000.0D));
        data.set(SKILL, target.requiredSkill());
        data.set(RESULT, result);
        data.set(COOLDOWN, Math.max(cooldown, pauseTicks));
    }
}
