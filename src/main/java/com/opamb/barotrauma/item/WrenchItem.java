package com.opamb.barotrauma.item;

import com.opamb.barotrauma.block.EquipmentLayout;
import com.opamb.barotrauma.blockentity.CoilgunLoaderBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.RepairTarget;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.vs.VsBridge;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.ship.vs.VsSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.wrench.dismantle"));
    }

    public static boolean isDismantling(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && player.getItemInHand(hand).getItem() instanceof WrenchItem;
    }

    public static boolean canTarget(BlockState state) {
        return EquipmentLayout.supports(state) || isInteractionProxy(state);
    }

    private static boolean isInteractionProxy(BlockState state) {
        return state.is(ModShipBlocks.PUMP_PROXY.get()) || state.is(ModShipBlocks.REACTOR_PROXY.get())
                || state.is(ModShipBlocks.ENGINE_PROXY.get()) || state.is(ModShipBlocks.SONAR_PROXY.get());
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null || !isDismantling(player, context.getHand())
                || !canTarget(context.getLevel().getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        return player instanceof ServerPlayer serverPlayer ? dismantleBlock(serverPlayer, pos)
                : InteractionResult.SUCCESS;
    }

    /** Used for ordinary clicks and forwarded VS2 shipyard clicks. Recheck the ray on the server. */
    public static InteractionResult dismantleBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1).scale(player.getBlockReach()));
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.FAIL;
        if (isInteractionProxy(level.getBlockState(hit.getBlockPos()))) {
            // Invisible menu targets are not equipment and can lag behind a moving ship.
            // Verify the real block behind the proxy against the same unobstructed ray.
            var equipmentHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, player) {
                @Override
                public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos cell) {
                    return isInteractionProxy(state) ? Shapes.empty() : super.getBlockShape(state, blocks, cell);
                }
            });
            if (equipmentHit.getType() != HitResult.Type.BLOCK
                    || (!pos.equals(hit.getBlockPos()) && !pos.equals(equipmentHit.getBlockPos()))) {
                return InteractionResult.FAIL;
            }
            return dismantleAt(player, equipmentHit.getBlockPos());
        }
        return pos.equals(hit.getBlockPos()) ? dismantleAt(player, pos) : InteractionResult.FAIL;
    }

    /** The caller has already checked the block ray or the moving coilgun hitbox. */
    public static InteractionResult dismantleAt(ServerPlayer player, BlockPos clicked) {
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !isDismantling(player, InteractionHand.MAIN_HAND)
                || !level.hasChunkAt(clicked)) return InteractionResult.FAIL;
        var state = level.getBlockState(clicked);
        if (!EquipmentLayout.supports(state)) return InteractionResult.FAIL;
        EquipmentLayout layout = EquipmentLayout.at(clicked, state);
        BlockPos master = layout.master();
        if (!layout.cells().containsKey(clicked) || !level.hasChunkAt(master)
                || !layout.owns(master, level.getBlockState(master))) {
            return reject(player, "incomplete");
        }

        var equipment = level.getBlockEntity(master);
        if (level.getBlockState(master).hasBlockEntity() && equipment == null) {
            return reject(player, "incomplete");
        }
        boolean physical = VsState.isPhysical(level, master);
        if (physical) {
            BlockPos helm = VsPilot.toYard(VsPilot.corePos());
            if (helm == null) return reject(player, "incomplete");
            if (equipment instanceof ShipCoreBlockEntity && master.equals(helm)) {
                return reject(player, "active_helm");
            }
        }
        UnaryOperator<Vec3> toWorld = VsSupport.available() ? VsBridge.collisionTransform(level, master)
                : UnaryOperator.identity();
        Map<BlockPos, BlockState> present = new LinkedHashMap<>();
        for (BlockPos cell : layout.cells().keySet()) {
            if (!level.hasChunkAt(cell)) return reject(player, "incomplete");
            BlockState part = level.getBlockState(cell);
            if (!part.is(state.getBlock())) continue;
            if (!layout.owns(cell, part)) return reject(player, "incomplete");
            present.put(cell, part);
        }
        var mode = player.gameMode.getGameModeForPlayer();
        for (var entry : present.entrySet()) {
            BlockPos world = BlockPos.containing(toWorld.apply(Vec3.atCenterOf(entry.getKey())));
            if (!level.getWorldBorder().isWithinBounds(world) || !level.mayInteract(player, world)
                    || player.blockActionRestricted(level, world, mode)
                    || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, world, entry.getValue(), player))) {
                return reject(player, "protected");
            }
        }
        for (var entry : present.entrySet()) {
            if (level.getBlockState(entry.getKey()) != entry.getValue()) return reject(player, "incomplete");
        }

        List<ItemStack> contents = new ArrayList<>();
        Container inventory = equipment instanceof Container container ? container
                : equipment instanceof CoilgunLoaderBlockEntity loader ? loader.inventory() : null;
        var backup = inventory != null || equipment instanceof TurretBlockEntity || equipment instanceof RepairTarget
                ? equipment.saveWithoutMetadata() : null;
        if (inventory != null) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) contents.add(stack.copy());
            }
            inventory.clearContent();
        }
        if (equipment instanceof TurretBlockEntity turret) {
            ItemStack ammo = turret.takeAmmo();
            if (!ammo.isEmpty()) contents.add(ammo);
        }
        // Removing the master also removes its validated sections and runs normal block cleanup.
        if (!level.removeBlock(master, false)) {
            if (backup != null) equipment.load(backup);
            return reject(player, "incomplete");
        }
        if (physical) {
            VsPilot.recordDismantledEquipment(level, present);
        } else {
            ShipCores.refreshGeometryNear(level, master);
        }
        if (!player.isCreative()) {
            ItemStack device = new ItemStack(state.getBlock());
            if (!device.isEmpty()) {
                if (backup != null && backup.contains("Condition")) {
                    // Keep wear without copying installed wiring, fuel, charge or operating state.
                    var wear = new CompoundTag();
                    for (String key : List.of("Condition", "ConditionGrace", "ConditionGraceInitialized", "Grace")) {
                        if (backup.contains(key)) wear.put(key, backup.get(key).copy());
                    }
                    BlockItem.setBlockEntityData(device, equipment.getType(), wear);
                }
                player.getInventory().placeItemBackInInventory(device);
            }
        }
        for (ItemStack stack : contents) {
            // Refund carried items even if the player is in creative or the chest was on a rotating ship.
            player.getInventory().placeItemBackInInventory(stack);
        }
        BlockPos sound = BlockPos.containing(toWorld.apply(Vec3.atCenterOf(master)));
        level.playSound(null, sound, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.6F, 1.25F);
        player.displayClientMessage(Component.translatable("message.barotrauma.dismantle.done",
                Component.translatable(state.getBlock().getDescriptionId())), true);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult reject(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.barotrauma.dismantle." + reason), true);
        return InteractionResult.FAIL;
    }
}
