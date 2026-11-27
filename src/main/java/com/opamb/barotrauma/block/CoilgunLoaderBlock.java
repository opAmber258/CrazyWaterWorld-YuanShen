package com.opamb.barotrauma.block;

import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.ship.vs.VsBridge;
import com.opamb.barotrauma.blockentity.CoilgunLoaderBlockEntity;
import com.opamb.barotrauma.item.TurretBinderItem;
import com.opamb.barotrauma.menu.CoilgunLoaderMenu;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class CoilgunLoaderBlock extends BaseEntityBlock {
    public CoilgunLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoilgunLoaderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server)) return null;
        return createTickerHelper(type, ModBlockEntities.COILGUN_LOADER.get(),
                (world, pos, block, loader) -> CoilgunLoaderBlockEntity.serverTick(server, loader));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof TurretBinderItem) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CoilgunLoaderBlockEntity) {
            NetworkHooks.openScreen(server, new SimpleMenuProvider(
                    (id, inventory, owner) -> new CoilgunLoaderMenu(id, inventory, pos),
                    state.getBlock().getName()), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        boolean assembly = VsSupport.available() && VsBridge.isAssembling();
        if (!level.isClientSide && !assembly && !moving && !next.is(this)
                && level.getBlockEntity(pos) instanceof CoilgunLoaderBlockEntity loader) {
            Containers.dropContents(level, pos, loader.inventory());
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
