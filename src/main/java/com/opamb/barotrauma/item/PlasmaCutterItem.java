package com.opamb.barotrauma.item;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.block.MineralVeinBlock;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 等离子切割机：潜渊症里的切割工具，和焊接工具相反——负修复。
 *
 * <ul>
 *   <li><b>矿脉</b>：对着岩壁矿脉按住右键约 4 秒，切碎后掉矿物（采矿任务用）；</li>
 *   <li><b>船壳/舷窗/装甲板</b>：持续切割扣耐久，0% 时走原有破口流程（会进水）；</li>
 *   <li><b>舱门/舱口</b>：约 6 秒切烂，门/舱口物品掉在切口；</li>
 *   <li><b>普通方块</b>：不切（按用户要求只做矿 + 船体结构）。</li>
 * </ul>
 *
 * <p>消耗的是悬停子格里的**氧气瓶**（原作 plasmacutter 的容器接受
 * {@code oxygensource}，每秒扣 1 点；往里塞焊接油箱会爆炸，所以这里直接拒收）。
 * 原作 plasmacutter 也是 {@code RepairTool}，{@code structurefixamount} 为负。</p>
 */
public class PlasmaCutterItem extends Item {

    /** 每 2 tick 对船壳造成多少伤害：15 HP/s，200 血外壳约 13 秒。 */
    public static final float HULL_DAMAGE_PER_APPLICATION = 1.5F;
    /** 切碎一块矿脉要多少次应用（每 2 tick 一次）：40 次 = 4 秒。 */
    private static final int VEIN_CUT_APPLICATIONS = 40;
    /** 切烂一扇门/一个舱口要多少次应用：60 次 = 6 秒。 */
    private static final int FIXTURE_CUT_APPLICATIONS = 60;
    /**
     * 每成功应用多少次切割扣 1 点氧气（原作 1/s；这里按真正切到的次数结算）。
     *
     * <p>不能用 {@code player.tickCount % 20}：生存玩家可以在扣氧的 tick 之前把准星
     * 移开让 MISS 提前返回，反复蹭出船壳伤害却不耗氧。</p>
     */
    private static final int OXYGEN_CUTS_PER_UNIT = 10;
    private static final double REACH = 4.5D;

    /** 服务端每名玩家当前的切割目标；换目标/松手就清零。 */
    private static final Map<UUID, CutTarget> CUTS = new HashMap<>();

    /**
     * 已成功切割、还没结算成氧气的应用次数。
     *
     * <p>松手/换目标都不清零（否则反复瞄一下就能白嫖），只有扣氧时减去结算部分；
     * 玩家退出时随 {@link #clearPlayer(UUID)} 一起清掉。</p>
     */
    private static final Map<UUID, Integer> OXYGEN_CREDIT = new HashMap<>();

    /** 正在切的格子、已切次数、是否被拒绝、最后一次应用的玩家 tick。 */
    private record CutTarget(BlockPos pos, int applications, boolean allowed, int tick) {
    }

    /** 一次切割应用的结果：切到了 / 没命中 / 目标被权限拒绝。 */
    private enum CutResult { CUT, MISS, DENIED }

    public PlasmaCutterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasOxygen(stack)) {
            player.displayClientMessage(Component.translatable("message.barotrauma.plasma_no_oxygen")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!hasOxygen(context.getItemInHand())) {
            player.displayClientMessage(Component.translatable("message.barotrauma.plasma_no_oxygen")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (!(living instanceof Player player)) {
            return;
        }
        if (!hasOxygen(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.barotrauma.plasma_no_oxygen")
                        .withStyle(ChatFormatting.RED), true);
                player.stopUsingItem();
            }
            return;
        }
        if (level.isClientSide) {
            if (player.tickCount % 2 == 0) {
                spawnCutParticles(level, player);
                com.opamb.barotrauma.client.MineralVeinClient.tickFromPlayer(level, player, REACH);
            }
            return;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        CutResult result = cutSomething(server, (ServerPlayer) player);
        if (result == CutResult.MISS) {
            // 没切到东西：清掉半截进度，别让下次瞄到同一格时直接完成。
            clearProgress((ServerPlayer) player, server);
            return;
        }
        if (result == CutResult.DENIED) {
            // 被拒绝的目标保留记录：按住不放时不再每 2 tick 重发破坏事件。
            return;
        }
        // 只有真正切到东西的应用才计数；累计到 10 次扣 1 点氧气。
        int applied = OXYGEN_CREDIT.merge(player.getUUID(), 1, Integer::sum);
        if (applied >= OXYGEN_CUTS_PER_UNIT) {
            OXYGEN_CREDIT.put(player.getUUID(), applied - OXYGEN_CUTS_PER_UNIT);
            consumeOxygen(stack, player);
            if (!hasOxygen(stack)) {
                player.stopUsingItem();
            }
        }
    }

    /** 松开右键：客户端清进度条，服务端清掉半截切割状态。 */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (level.isClientSide) {
            // 只有本地玩家释放才清共享进度，远端玩家的事件不能串台。
            if (living == net.minecraft.client.Minecraft.getInstance().player) {
                com.opamb.barotrauma.client.MineralVeinClient.reset();
            }
        } else if (living instanceof ServerPlayer player && level instanceof ServerLevel server) {
            clearProgress(player, server);
        }
    }

    /** 服务端一次切割应用：区分切到、没命中、被权限拒绝三种结果。 */
    private CutResult cutSomething(ServerLevel level, ServerPlayer player) {
        // 和原版方块破坏同一套权限判定：冒险模式/领地限制下不能靠切割器绕过。
        if (!player.mayBuild()) {
            clearProgress(player, level);
            return CutResult.MISS;
        }
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MineralVeinBlock vein) {
                if (denied(player, pos)) {
                    return CutResult.DENIED;
                }
                if (startingCut(player, pos) && !canBreak(level, player, pos)) {
                    deny(level, player, pos);
                    return CutResult.DENIED;
                }
                return progress(level, player, pos, VEIN_CUT_APPLICATIONS, () -> {
                    // 完成时再查一次权限：切到一半被划进保护区就不能继续破坏。
                    if (!canBreak(level, player, pos)) {
                        return;
                    }
                    level.destroyBlock(pos, false);
                    int count = vein.cutYield(level.random);
                    Block.popResource(level, pos,
                            MineralVeinBlock.mined(vein.mineral(), count));
                    level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK,
                            SoundSource.PLAYERS, 0.9F, 0.8F);
                    level.sendParticles(ParticleTypes.CRIT,
                            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                            12, 0.3D, 0.3D, 0.3D, 0.05D);
                }) ? CutResult.CUT : CutResult.MISS;
            }
            // 只有命中格真的属于物理船才走快照切割；否则普通舱口/未装配船壳
            // 会被 tryCut 吞掉，永远进不了下面的普通分支。
            if (VsState.isPhysical(level, pos)) {
                return VsState.tryCut(level, player, HULL_DAMAGE_PER_APPLICATION)
                        ? CutResult.CUT : CutResult.MISS;
            }
            if (isFixture(state)) {
                if (denied(player, pos)) {
                    return CutResult.DENIED;
                }
                if (startingCut(player, pos) && !canBreak(level, player, pos)) {
                    deny(level, player, pos);
                    return CutResult.DENIED;
                }
                return progress(level, player, pos, FIXTURE_CUT_APPLICATIONS, () -> {
                    if (breakFixture(level, player, pos, state)) {
                        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK,
                                SoundSource.PLAYERS, 0.8F, 0.8F);
                    }
                }) ? CutResult.CUT : CutResult.MISS;
            }
            if (ModShipBlocks.isRepairableHull(state)) {
                // 船壳耐久是逐次扣的：开始切时查一次，之后每 4 tick 由 startingCut 复查。
                // 被拒绝的窗口内必须直接返回，否则 startingCut 为 false，会跳过权限检查
                // 直接走 damageBlock，受保护船壳被持续扣耐久。
                if (denied(player, pos)) {
                    return CutResult.DENIED;
                }
                if (startingCut(player, pos)) {
                    if (!canBreak(level, player, pos)) {
                        deny(level, player, pos);
                        return CutResult.DENIED;
                    }
                    CUTS.put(player.getUUID(),
                            new CutTarget(pos.immutable(), 0, true, player.tickCount));
                }
                // 两艘船靠近时最近的核心可能属于另一艘船；要选几何真正包含
                // 这段船壳的核心，否则 damageBlock 会因归属不符直接拒绝。
                ShipCoreBlockEntity core = null;
                for (ShipCoreBlockEntity candidate : ShipCores.coresNear(level, pos, 64.0D)) {
                    ShipState candidateState = candidate.state() != null
                            ? candidate.state() : candidate.rescan(level);
                    if (candidateState != null && candidateState.tracksHullBlock(pos)) {
                        core = candidate;
                        break;
                    }
                }
                if (core == null) {
                    return CutResult.MISS;
                }
                return core.damageBlock(level, pos, HULL_DAMAGE_PER_APPLICATION, player)
                        ? CutResult.CUT : CutResult.MISS;
            }
            return CutResult.MISS;
        }
        // 物理船的方块在世界里是空气，只能按装配快照做视线检测。
        return VsState.active() && VsState.tryCut(level, player, HULL_DAMAGE_PER_APPLICATION)
                ? CutResult.CUT : CutResult.MISS;
    }

    /** 是否刚开始切这一格：换目标或中断超过 4 tick 才重新发破坏事件。 */
    private static boolean startingCut(ServerPlayer player, BlockPos pos) {
        CutTarget current = CUTS.get(player.getUUID());
        return current == null || !current.pos().equals(pos)
                || player.tickCount - current.tick() > 4;
    }

    /** 这一段瞄准已经被拒绝过；按住不放时刷新窗口，不要重复发破坏事件。 */
    private static boolean denied(ServerPlayer player, BlockPos pos) {
        CutTarget current = CUTS.get(player.getUUID());
        if (current == null || !current.pos().equals(pos) || current.allowed()) {
            return false;
        }
        if (player.tickCount - current.tick() > 4) {
            return false;
        }
        // 刷新 tick：持续按住时拒绝状态一直有效，松手/换目标后才重新判定。
        CUTS.put(player.getUUID(), new CutTarget(current.pos(), current.applications(),
                false, player.tickCount));
        return true;
    }

    /** 记下被拒绝的目标并清掉裂纹；换目标或中断 4 tick 后才会重新检查权限。 */
    private static void deny(ServerLevel level, ServerPlayer player, BlockPos pos) {
        CUTS.put(player.getUUID(), new CutTarget(pos.immutable(), 0, false, player.tickCount));
        level.destroyBlockProgress(player.getId(), pos, -1);
    }

    /**
     * 与原版方块破坏路径一致：先发出 {@code BlockEvent.BreakEvent}，再检查位置级
     * 破坏限制（冒险模式/领地）；{@code mayBuild()} 只是玩家级权限，出生点保护
     * 在 {@link net.minecraft.server.level.ServerLevel#mayInteract} 里，必须单独查。
     */
    private static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        int event = ForgeHooks.onBlockBreakEvent(level, mode, player, pos);
        boolean allowed = level.mayInteract(player, pos)
                && event != -1
                && !player.blockActionRestricted(level, pos, mode);
        // 创造模式下 ForgeHooks 会把该位置当成已破坏同步给客户端；切割器并没有真的
        // 破坏方块，必须把真实状态补回去，否则矿脉会一直显示成空气。
        player.connection.send(
                new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level, pos));
        return allowed;
    }

    /** 累计切割进度；达到 {@code applications} 次就执行 {@code finish}。 */
    private static boolean progress(ServerLevel level, ServerPlayer player, BlockPos pos,
                                    int applications, Runnable finish) {
        UUID id = player.getUUID();
        CutTarget current = CUTS.get(id);
        // 中断超过 4 tick 视为重新开始，不继承之前的裂纹进度。
        boolean same = current != null && current.pos().equals(pos)
                && player.tickCount - current.tick() <= 4;
        int done = same ? current.applications() + 1 : 1;
        CUTS.put(id, new CutTarget(pos.immutable(), done, true, player.tickCount));
        // 原版裂纹动画：stage 0..9，切完要显式清掉。
        int stage = Math.min(9, done * 10 / applications);
        level.destroyBlockProgress(player.getId(), pos, stage);
        if (done >= applications) {
            CUTS.remove(id);
            level.destroyBlockProgress(player.getId(), pos, -1);
            finish.run();
        }
        return true;
    }

    private static void clearProgress(ServerPlayer player, ServerLevel level) {
        CutTarget current = CUTS.remove(player.getUUID());
        if (current != null) {
            level.destroyBlockProgress(player.getId(), current.pos(), -1);
        }
    }

    /** 玩家退出时清掉服务端残留的切割/拒绝记录与未结算的扣氧计数。 */
    public static void clearPlayer(UUID player) {
        CUTS.remove(player);
        OXYGEN_CREDIT.remove(player);
    }

    /** 方块船上的舱门/舱口（物理船上的由 {@link VsState#tryCut} 处理）。 */
    private static boolean isFixture(BlockState state) {
        Block block = state.getBlock();
        return block == ModShipBlocks.BULKHEAD_DOOR.get()
                || block == ModShipBlocks.HATCH.get()
                || block == ModShipBlocks.HATCH_LADDER.get();
    }

    /**
     * 拆掉命中的设施。三格舱口要整套拆（左/中/右），只掉一个物品；
     * 只删命中格会让玩家切三格拿三个舱口。
     */
    private static boolean breakFixture(ServerLevel level, ServerPlayer player,
                                        BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof HatchBlock) {
            var longAxis = state.getValue(HatchBlock.FACING).getClockWise();
            int offset = state.getValue(HatchBlock.PART).ordinal() - 1;
            BlockPos middle = pos.relative(longAxis, -offset);
            BlockState middleState = level.getBlockState(middle);
            if (middleState.getBlock() instanceof HatchBlock
                    && middleState.getValue(HatchBlock.PART) == HatchBlock.Part.MIDDLE) {
                List<BlockPos> parts = new ArrayList<>();
                for (int i = -1; i <= 1; i++) {
                    BlockPos part = middle.relative(longAxis, i);
                    if (level.getBlockState(part).getBlock() instanceof HatchBlock) {
                        parts.add(part);
                    }
                }
                // 任一格没有位置权限就整套不拆，避免拆一半留下残件。
                for (BlockPos part : parts) {
                    if (!canBreak(level, player, part)) {
                        return false;
                    }
                }
                ItemStack drop = new ItemStack(middleState.getBlock().asItem());
                for (BlockPos part : parts) {
                    level.destroyBlock(part, false);
                }
                if (!drop.isEmpty()) {
                    Block.popResource(level, middle, drop);
                }
                return true;
            }
        }
        if (state.getBlock() instanceof BulkheadDoorBlock) {
            // 舱壁门占两格：上下一起拆，只掉一个物品；只删命中格会留下半扇门。
            BlockPos lower = state.getValue(BulkheadDoorBlock.PART)
                    == BulkheadDoorBlock.Part.UPPER ? pos.below() : pos;
            List<BlockPos> cells = new ArrayList<>();
            for (BlockPos cell : new BlockPos[]{lower, lower.above()}) {
                if (level.getBlockState(cell).getBlock() instanceof BulkheadDoorBlock) {
                    cells.add(cell);
                }
            }
            for (BlockPos cell : cells) {
                if (!canBreak(level, player, cell)) {
                    return false;
                }
            }
            for (BlockPos cell : cells) {
                level.destroyBlock(cell, false);
            }
            Block.popResource(level, pos, new ItemStack(state.getBlock().asItem()));
            return true;
        }
        if (!canBreak(level, player, pos)) {
            return false;
        }
        ItemStack drop = new ItemStack(state.getBlock().asItem());
        level.destroyBlock(pos, false);
        if (!drop.isEmpty()) {
            Block.popResource(level, pos, drop);
        }
        return true;
    }

    /** 子格里的氧气瓶还有氧气吗（原作 plasmacutter 只接受 oxygensource）。 */
    private static boolean hasOxygen(ItemStack tool) {
        ItemStack tank = ItemSubSlot.get(tool);
        return !tank.isEmpty()
                && tank.is(com.opamb.barotrauma.equipment.EquipmentTags.OXYGEN_TANK)
                && tank.getDamageValue() < tank.getMaxDamage();
    }

    /** 消耗 1 点氧气；氧气瓶耗尽就从子格里消失。创造模式不消耗。 */
    private static void consumeOxygen(ItemStack tool, Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack tank = ItemSubSlot.get(tool);
        if (tank.isEmpty() || !tank.is(com.opamb.barotrauma.equipment.EquipmentTags.OXYGEN_TANK)) {
            return;
        }
        int damage = tank.getDamageValue() + 1;
        if (damage >= tank.getMaxDamage()) {
            ItemSubSlot.set(tool, ItemStack.EMPTY);
        } else {
            tank.setDamageValue(damage);
            ItemSubSlot.set(tool, tank);
        }
    }

    /** 客户端切割火花：只做视觉，真正的进度在服务端。 */
    private void spawnCutParticles(Level level, Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        HitResult hit = player.pick(REACH, 1.0F, false);
        Vec3 at = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation()
                : eye.add(look.scale(2.0D));
        for (int i = 0; i < 2; i++) {
            double x = at.x + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
            double y = at.y + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
            double z = at.z + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
        }
        if (player.getRandom().nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0D, 0.03D, 0.0D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.plasma_cutter")
                .withStyle(ChatFormatting.GRAY));
        ItemStack tank = ItemSubSlot.get(stack);
        if (tank.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.barotrauma.plasma_no_oxygen")
                    .withStyle(ChatFormatting.RED));
        } else {
            int left = Math.max(0, tank.getMaxDamage() - tank.getDamageValue());
            tooltip.add(Component.translatable("tooltip.barotrauma.plasma_oxygen",
                    left, tank.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
