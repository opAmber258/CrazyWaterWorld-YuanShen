package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.TwoHanded;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PickupItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 掉落物拾取：原作 {@code Pickable} 是**左键（Action/Use）**触发，这里以左键为主，
 * 右键保留为补充；走过去不再自动拾取（见 {@code ItemEntityMixin}）。
 *
 * <p>左键对着地上的物品实体：服务端把这堆东西按正常的拾取规则塞进背包
 * （装备背包 → 热键栏；双手持物品进双手格），剩下的留在原地。客户端只负责
 * 取消原版交互并挥手。</p>
 *
 * <p>另有两个兜底：</p>
 * <ul>
 *   <li>{@link #onLeftClickBlock}：物品平躺在地上、准星实际打在它下面的方块上时
 *       也能捡（原作就是左键点物品）。只处理 START；物品必须在准星附近、没有隔着
 *       方块，捡不动（背包满/别人的东西）就让方块正常挖。</li>
 *   <li>{@link #onRightClickBlock}：空手右键的兜底（拿着东西时右键要留给方块）。
 *       物理船上服务端收不到这个方块交互（VS2 的方块坐标在船坞，距离检查直接
 *       丢包），所以客户端额外发 {@code PickupItemPacket} 报实体 id。</li>
 *   <li>{@link #onRightClickItem}：准星没打到方块（MISS）时补同样的请求：
 *       服务端对空手的 use-item 包直接 return，不能指望它。</li>
 *   <li>{@link #onItemExpire}：原版掉落物 5 分钟消失，原作里物件是永久的，
 *       这里每次到寿命就再续一轮。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemPickupEvents {

    /** 右键兜底拾取的锥形半角（cos 值，约 20°）。 */
    private static final double CONE_COS = 0.94D;
    /** 左键拾取的锥形半角（cos 值，约 8°）：左键同时负责挖方块，必须瞄得更准。 */
    private static final double LEFT_CLICK_CONE_COS = 0.99D;
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
     * 左键点中物品实体：原作 {@code Pickable} 的拾取键是 {@code Action}/{@code Use}
     * （也就是左键，见 {@code Item.cs} 的 {@code component.Pick(user)}），所以这里
     * 把"左键打物品"接成拾取，而不是把它当攻击。
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ItemEntity itemEntity)) {
            return;
        }
        Player player = event.getEntity();
        // 物品不是攻击目标：两端都取消；实际拾取只由服务端执行。
        event.setCanceled(true);
        if (!player.level().isClientSide) {
            tryPickup(player, itemEntity);
        }
    }

    /**
     * 左键点到物品下面的方块时的兜底：物品在准星锥形里就捡（原作就是左键点物品）。
     *
     * <p>只在 START 处理；捡不动（背包满/别人的东西）就完全不拦，方块正常挖。
     * 客户端只取消本地挖掘，{@code START_DESTROY_BLOCK} 包仍会发给服务端
     * （Forge 的左键事件是在发包流程里触发的），服务端再执行拾取。</p>
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSpectator()) {
            return;
        }
        ItemEntity target = findAimedItem(player, LEFT_CLICK_CONE_COS);
        if (target == null) {
            return;
        }
        if (player.level().isClientSide) {
            event.setCanceled(true);
            return;
        }
        if (tryPickup(player, target) == Pickup.FAILED) {
            return;
        }
        event.setCanceled(true);
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
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        boolean emptyHand = event.getItemStack().isEmpty();
        // 空手用宽松锥形；手里拿着工具/单手物品时只对双手持箱子放宽，
        // 用更准的锥形（用户实测：拿着焊接工具去捡任务箱，右键被方块交互吃掉）。
        ItemEntity target = findAimedItem(player, emptyHand ? CONE_COS : LEFT_CLICK_CONE_COS);
        if (target == null) {
            return;
        }
        if (!emptyHand && (TwoHanded.is(event.getItemStack()) || !TwoHanded.is(target.getItem()))) {
            // 已经抱着箱子（或目标不是箱子）时，右键留给方块/手里物品自己处理。
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
            // 统一由客户端报实体 id：取消这个事件后 use-on 包根本不会发给服务端
            //（服务端的 RightClickBlock 兜底不会触发）；物理船的方块坐标还在船坞里，
            // 即使发过去也会被 handleUseItemOn 的距离检查丢掉。
            // 之前的实现只在"命中方块离玩家 > 8 格"时才发包，方块船/站内（坐标正常）
            // 反而一条请求都不发——用户实测右键有人物挥手动画但东西捡不起来。
            ModNetwork.sendToServer(new PickupItemPacket(target.getId()));
            return;
        }
        if (tryPickup(player, target) == Pickup.FAILED) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 右键空气（准星没打到方块）也捡：物理船里 VS2 的射线可能只返回 MISS，
     * 此时客户端走 {@code RightClickItem}，而服务端对空手的 use-item 包直接 return。
     * 客户端自己发现准星里的物品后发包给服务端；手里拿着工具时同样只对箱子放宽。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSpectator() || !player.level().isClientSide) {
            return;
        }
        boolean emptyHand = event.getItemStack().isEmpty();
        ItemEntity target = findAimedItem(player, emptyHand ? CONE_COS : LEFT_CLICK_CONE_COS);
        if (target == null) {
            return;
        }
        if (!emptyHand && (TwoHanded.is(event.getItemStack()) || !TwoHanded.is(target.getItem()))) {
            return;
        }
        ModNetwork.sendToServer(new PickupItemPacket(target.getId()));
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

    /**
     * 网络包入口：客户端已经确认准星对着这个掉落物，服务端也校验过距离。
     * 这里只按正常拾取规则（装备背包 → 热键栏、双手持物品进双手格）执行。
     */
    public static boolean pickup(Player player, ItemEntity itemEntity) {
        return tryPickup(player, itemEntity) == Pickup.PICKED;
    }

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
        // 注意 ItemEntity.getOwner() 走 TraceableEntity，返回的是**实体**而不是 UUID
        //（2026-11-19 实测日志：归属打印出 ServerPlayer[...]）；拿它和 UUID 比永远不等，
        // 玩家自己丢的东西也捡不起来。
        net.minecraft.world.entity.Entity owner = itemEntity.getOwner();
        if (owner != null && !owner.getUUID().equals(player.getUUID())) {
            BarotraumaMod.LOGGER.info("[pickup] 拒绝 {}：归属 {} 不是 {}",
                    stack.getDescriptionId(), owner.getUUID(), player.getUUID());
            return Pickup.FAILED;
        }
        ItemStack remaining = stack.copy();
        if (!player.getInventory().add(remaining)) {
            BarotraumaMod.LOGGER.info("[pickup] 拒绝 {}：装备背包/热键栏都满了",
                    stack.getDescriptionId());
            return Pickup.FAILED;
        }
        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
        return Pickup.PICKED;
    }

    /** 视线锥形里最近的掉落物（空手右键兜底用，宽松锥形）。 */
    private static ItemEntity findAimedItem(Player player) {
        return findAimedItem(player, CONE_COS);
    }

    /** 视线锥形里最近的掉落物（左键用更准的锥形）。 */
    private static ItemEntity findAimedItem(Player player, double coneCos) {
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
            if (delta.normalize().dot(look) < coneCos) {
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
