package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 掉落物只能用右键捡（走过去不再自动拾取，见 {@code ItemEntityMixin}）。
 *
 * <p>右键对着地上的物品实体：服务端把这堆东西按正常的拾取规则塞进背包
 * （装备背包 → 热键栏；双手持物品进双手格），剩下的留在原地。客户端只负责
 * 取消原版交互并挥手。</p>
 *
 * <p>另有两个兜底：</p>
 * <ul>
 *   <li>{@link #onRightClickBlock}：物品平躺在地上、准星实际打在它下面的方块上时
 *       也能捡。原版服务端 {@code handleUseItem} 对空手直接 return，{@code RightClickItem}
 *       根本没机会发给服务端，所以只能挂在 {@code RightClickBlock} 上；为避免把机器/
 *       柜子的右键抢走，只有物品比准星命中的方块面更近时才捡。</li>
 *   <li>{@link #onItemExpire}：原版掉落物 5 分钟消失，原作里物件是永久的，
 *       这里每次到寿命就再续一轮。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemPickupEvents {

    /** 兜底拾取的锥形半角（cos 值，约 20°）。 */
    private static final double CONE_COS = 0.94D;
    /** 兜底拾取的最远距离（格）。 */
    private static final double FALLBACK_RANGE = 2.5D;

    private ItemPickupEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemEntity itemEntity)) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            // 交互包已经在客户端发过了；这里只挡掉本地处理，避免重复。
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (tryPickup(player, itemEntity) == Pickup.FAILED) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 空手右键方块时的兜底：物品躺在准星附近（锥形内、没有隔着方块）就捡。
     *
     * <p>只处理主手空手；物品必须比准星真正命中的方块面更近，否则对着机器、
     * 柜子、按钮右键时会把脚边同方向的掉落物吸走。捡不到（背包满/别人的东西）
     * 就完全不拦，方块交互照常。</p>
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().isEmpty()
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemEntity target = findAimedItem(player);
        if (target == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 hit = event.getHitVec().getLocation();
        if (eye.distanceTo(hit) < eye.distanceTo(center) - 1.0E-3D) {
            // 准星先打到方块（机器面板/柜门等在物品前面），交给方块自己处理。
            return;
        }
        if (player.level().isClientSide) {
            // 客户端也要消费这次点击：否则原版会继续走副手，抱着箱子时
            // 捡东西还会把箱子界面弹出来。实际拾取只由服务端执行。
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (tryPickup(player, target) == Pickup.FAILED) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 掉落物不超时消失（等价于原作里物件永久存在）。 */
    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        // Forge 只在事件被取消时才采用 extraLife（未取消直接返回 -1 丢弃），
        // 所以续命和取消必须成对调用。
        event.setExtraLife(6000);
        event.setCanceled(true);
    }

    private enum Pickup { PICKED, EMPTY, FAILED }

    private static Pickup tryPickup(Player player, ItemEntity itemEntity) {
        // 旁观者也会收到 RightClickBlock：不能改世界物品。
        if (player.isSpectator()) {
            return Pickup.FAILED;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return Pickup.EMPTY;
        }
        // 和原版拾取一致：别人丢的东西不能抢（Owner 不匹配就拒绝）。
        if (itemEntity.getOwner() != null
                && !itemEntity.getOwner().equals(player.getUUID())) {
            return Pickup.FAILED;
        }
        ItemStack remaining = stack.copy();
        if (!player.getInventory().add(remaining)) {
            return Pickup.FAILED;
        }
        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
        return Pickup.PICKED;
    }

    /** 视线锥形里最近的掉落物（用于空手右键兜底）。 */
    private static ItemEntity findAimedItem(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        ItemEntity best = null;
        double bestDist = FALLBACK_RANGE * FALLBACK_RANGE;
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(FALLBACK_RANGE))) {
            if (item.getItem().isEmpty()) {
                continue;
            }
            Vec3 center = item.position().add(0.0D, item.getBbHeight() * 0.5D, 0.0D);
            Vec3 delta = center.subtract(eye);
            double distance = delta.lengthSqr();
            if (distance > bestDist || distance < 1.0E-4D) {
                continue;
            }
            if (delta.normalize().dot(look) < CONE_COS) {
                continue;
            }
            // 隔墙不捡：视线到物品之间有实心方块就跳过。
            if (player.level().clip(new ClipContext(eye, center,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            best = item;
            bestDist = distance;
        }
        return best;
    }
}
