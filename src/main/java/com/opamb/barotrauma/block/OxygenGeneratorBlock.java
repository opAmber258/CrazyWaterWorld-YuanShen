package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.OxygenGeneratorBlockEntity;
import com.opamb.barotrauma.menu.OxygenGeneratorMenu;
import com.opamb.barotrauma.ship.ShipCores;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 制氧机（临时单格版本）。
 *
 * <p>原作是约 2.1×2.0 格的落地机器：1000 kW、五个氧气瓶槽、给氧气瓶/焊接油箱充气，
 * 并按接线盒/通风口把氧气送进各舱室。现在先用一个普通立方体占位把逻辑和面板跑通，
 * 模型和 2×2 多块等美术确认后再换。</p>
 */
public class OxygenGeneratorBlock extends Block implements EntityBlock {

    public OxygenGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 临时方块：普通立方体模型（不是 GeckoLib 的 ENTITYBLOCK_ANIMATED）。
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OxygenGeneratorBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // 航行中放下的制氧机也要进船体几何和靠港还原快照。
        ShipCores.refreshNearby(level, pos);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        // 这个方块只注册了这一种方块实体，不需要再比对 type。
        return (BlockEntityTicker<T>) (BlockEntityTicker<OxygenGeneratorBlockEntity>)
                OxygenGeneratorBlockEntity::serverTick;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof OxygenGeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider(
                        (id, inventory, p) -> new OxygenGeneratorMenu(id, inventory, pos),
                        Component.translatable(state.getBlock().getDescriptionId())),
                buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof OxygenGeneratorBlockEntity generator) {
            Containers.dropContents(level, pos, generator);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
