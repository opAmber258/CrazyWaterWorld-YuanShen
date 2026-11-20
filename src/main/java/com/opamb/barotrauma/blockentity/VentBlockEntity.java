package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 通风口（临时单格占位方块）方块实体。
 *
 * <p>对应原作 {@code Vent}：不耗电，只负责把"我这一舱"登记到船上的氧气分配里。
 * 制氧机按有通风口的舱室体积分配流量；通风口泡在水里时原作 {@code item.InWater}
 * 分支不送氧，这里同样上报 {@code active=false}。</p>
 */
public class VentBlockEntity extends BlockEntity {

    private ShipState cachedShip;
    private int shipCacheTicks;
    /** 当前服务哪个舱室（-1 = 不在密封舱里）。 */
    private int compartment = -1;
    /** 当前是否在送氧（泡水/不在舱室里时为 false）。 */
    private boolean active;

    public VentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  VentBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.isRemoved()) {
            return;
        }
        ShipState ship = be.resolveShip(server, pos);
        if (ship == null) {
            return;
        }
        BlockPos cell = assemblyPos(pos);
        if (!ship.tracksHullBlock(cell)) {
            return;
        }
        ShipSimulation simulation = ship.simulation();
        be.compartment = simulation.bodyOf(cell);
        boolean wet = false;
        if (be.compartment >= 0 && simulation.bodyIsWet(be.compartment)) {
            java.util.List<BlockPos> breachSources = new java.util.ArrayList<>();
            for (ShipState.Breach breach : ship.breaches()) {
                breachSources.add(breach.pos());
            }
            wet = cell.getY() < simulation.waterLevelAt(cell, breachSources);
        }
        be.active = be.compartment >= 0 && !wet;
        simulation.reportVent(cell, be.compartment, be.active);
    }

    public int compartment() {
        return compartment;
    }

    public boolean active() {
        return active;
    }

    private ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active() && VsPilot.isYardBlock(pos)) {
            return VsState.activeState();
        }
        if (shipCacheTicks > 0) {
            shipCacheTicks--;
            return cachedShip;
        }
        shipCacheTicks = 20;
        cachedShip = ShipCores.stateOwning(level, pos, 64.0D);
        return cachedShip;
    }

    private static BlockPos assemblyPos(BlockPos pos) {
        if (!VsState.active() || !VsPilot.isYardBlock(pos)) {
            return pos;
        }
        return VsPilot.toAssembly(pos);
    }
}
