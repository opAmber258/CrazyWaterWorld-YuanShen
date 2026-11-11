package com.opamb.barotrauma.item;

import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 焊接工具：潜渊症里修船壳的唯一工具。
 *
 * <p>官方数值（本机 {@code Content/Items/Tools/tools.xml}）：结构修复
 * {@code structurefixamount=2.0}/秒、燃料每秒 1 点、燃料罐 200 点；结构被焊接时
 * 用红→绿矩形显示剩余耐久。这里按“右键持续焊接”实现：每 2 tick 修
 * {@link #REPAIR_PER_APPLICATION} 点，燃料每秒扣 1 点。</p>
 *
 * <p>燃料放在工具的悬停子格里（{@code ItemSubSlot}），必须是
 * {@code welding_fuel_tank}；工具有没有油都从子格读，工具本体不再消耗耐久。</p>
 *
 * <p>物理船的船壳在世界里是空气，所以不能只依赖原版 {@code useOn}：
 * 服务端在 {@code onUseTick} 里问 {@link VsState#tryRepair}，由它沿玩家视线
 * 对装配快照做射线检测。方块船则走普通方块射线。</p>
 */
public class WeldingToolItem extends Item {

    /** 每 2 tick（0.1 s）修多少血：25 HP/s，200 血的外壳约 8 秒。 */
    public static final float REPAIR_PER_APPLICATION = 2.5F;
    /** 焊接时每多少 tick 扣 1 点燃料（原作 1/s；油箱 200 点 = 200 秒）。 */
    private static final int FUEL_INTERVAL = 20;
    private static final double REACH = 5.0D;

    public WeldingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasFuel(stack)) {
            player.displayClientMessage(Component.translatable("message.barotrauma.welding_empty")
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
        if (!hasFuel(context.getItemInHand())) {
            player.displayClientMessage(Component.translatable("message.barotrauma.welding_empty")
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
        if (!hasFuel(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.barotrauma.welding_empty")
                        .withStyle(ChatFormatting.RED), true);
                player.stopUsingItem();
            }
            return;
        }
        if (level.isClientSide) {
            if (player.tickCount % 2 == 0) {
                spawnWeldParticles(level, player);
            }
            return;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        boolean repaired;
        if (VsState.active()) {
            repaired = VsState.tryRepair((ServerLevel) level, (ServerPlayer) player, REPAIR_PER_APPLICATION);
        } else {
            repaired = repairWorldBlock((ServerLevel) level, player);
        }
        if (!repaired) {
            return;
        }
        if (player.tickCount % FUEL_INTERVAL == 0) {
            consumeFuel(stack, player);
            if (!hasFuel(stack)) {
                player.stopUsingItem();
            }
        }
    }

    /** 子格里的焊接油箱还有燃料吗。 */
    private static boolean hasFuel(ItemStack tool) {
        ItemStack fuel = ItemSubSlot.get(tool);
        return !fuel.isEmpty()
                && fuel.is(ModItems.WELDING_FUEL_TANK.get())
                && fuel.getDamageValue() < fuel.getMaxDamage();
    }

    /** 消耗 1 点燃料；油箱耗尽就从子格里消失。创造模式不消耗。 */
    private static void consumeFuel(ItemStack tool, Player player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        ItemStack fuel = ItemSubSlot.get(tool);
        if (fuel.isEmpty() || !fuel.is(ModItems.WELDING_FUEL_TANK.get())) {
            return;
        }
        int damage = fuel.getDamageValue() + 1;
        if (damage >= fuel.getMaxDamage()) {
            ItemSubSlot.set(tool, ItemStack.EMPTY);
        } else {
            fuel.setDamageValue(damage);
            ItemSubSlot.set(tool, fuel);
        }
    }

    /** 方块船：瞄准的方块 → 最近的船核心 → 修耐久。 */
    private boolean repairWorldBlock(ServerLevel level, Player player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!ModShipBlocks.isRepairableHull(level.getBlockState(pos))) {
            return false;
        }
        ShipCoreBlockEntity core = ShipCores.nearestCore(level, pos, 64.0D);
        if (core == null || core.state() == null) {
            return false;
        }
        boolean repaired = core.repairBlock(level, pos, REPAIR_PER_APPLICATION, (ServerPlayer) player);
        if (repaired && player.tickCount % 10 == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.35F, 1.7F);
        }
        return repaired;
    }

    /** 客户端焊接火花：只做视觉，真正的伤害在服务端。 */
    private void spawnWeldParticles(Level level, Player player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        HitResult hit = player.pick(REACH, 1.0F, false);
        Vec3 at = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation()
                : eye.add(look.scale(2.0D));
        double x = at.x + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
        double y = at.y + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
        double z = at.z + (player.getRandom().nextDouble() - 0.5D) * 0.25D;
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.01D, 0.0D);
        if (player.getRandom().nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.03D, 0.0D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.barotrauma.welding_tool").withStyle(ChatFormatting.GRAY));
        ItemStack fuel = ItemSubSlot.get(stack);
        if (fuel.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.barotrauma.welding_no_fuel")
                    .withStyle(ChatFormatting.RED));
        } else {
            int left = Math.max(0, fuel.getMaxDamage() - fuel.getDamageValue());
            tooltip.add(Component.translatable("tooltip.barotrauma.welding_fuel",
                    left, fuel.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
