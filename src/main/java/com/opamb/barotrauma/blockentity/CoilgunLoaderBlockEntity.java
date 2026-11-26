package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.item.CoilgunAmmoBoxItem;
import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CoilgunLoaderBlockEntity extends BlockEntity implements RepairTarget {
    private final SimpleContainer inventory = new SimpleContainer(1);
    private double condition = 100;
    private int graceTicks = -1;
    private int activeTicks;

    public CoilgunLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COILGUN_LOADER.get(), pos, state);
        inventory.addListener(container -> setChanged());
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ItemStack ammo() {
        return inventory.getItem(0);
    }

    public int shots() {
        return CoilgunAmmoBoxItem.shotsLeft(ammo());
    }

    public void feedRound() {
        ItemStack box = ammo();
        box.setDamageValue(box.getDamageValue() + 1);
        activeTicks = 20;
        setChanged();
    }

    public static void serverTick(ServerLevel level, CoilgunLoaderBlockEntity loader) {
        if (loader.condition <= 0) return;
        if (loader.activeTicks > 0) {
            loader.activeTicks--;
            if (loader.graceTicks < 0) loader.resetGrace();
            if (loader.graceTicks > 0) loader.graceTicks--;
            else loader.damage(10.0 / 20);
            loader.setChanged();
        }
        if (level.getGameTime() % 20 != 0) return;
        BlockPos pos = loader.getBlockPos();
        ShipState ship = DrainPumpBlockEntity.resolveShip(level, pos);
        BlockPos cell = VsState.isPhysical(level, pos) ? VsPilot.toAssembly(pos) : pos;
        if (ship != null && ship.tracksHullBlock(cell)
                && ship.simulation().submergedAt(cell, ship.breaches().stream().map(ShipState.Breach::pos).toList())) {
            loader.damage(0.25);
        }
    }

    @Override
    public double condition() {
        return condition;
    }

    @Override
    public double maxCondition() {
        return 100;
    }

    @Override
    public double repairThreshold() {
        return 0.5;
    }

    @Override
    public Tool requiredTool() {
        return Tool.WRENCH;
    }

    @Override
    public void applyRepair(double points) {
        if (points <= 0) return;
        condition = Mth.clamp(condition + points, 0, 100);
        if (isFull()) resetGrace();
        setChanged();
    }

    @Override
    public void damage(double points) {
        if (points <= 0) return;
        condition = Math.max(0, condition - points);
        setChanged();
    }

    private void resetGrace() {
        graceTicks = 1200 + level.random.nextInt(4801);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Ammo", ammo().save(new CompoundTag()));
        tag.putDouble("Condition", condition);
        tag.putInt("Grace", graceTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Condition", Tag.TAG_ANY_NUMERIC)) condition = tag.getDouble("Condition");
        if (tag.contains("Grace", Tag.TAG_INT)) graceTicks = tag.getInt("Grace");
        inventory.setItem(0, ItemStack.of(tag.getCompound("Ammo")));
    }
}
