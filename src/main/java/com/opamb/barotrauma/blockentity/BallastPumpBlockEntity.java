package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import com.opamb.barotrauma.ship.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 压载泵方块实体：复用排水泵的流量/菜单逻辑，额外做两件事——
 *
 * <ol>
 *   <li>自动模式跟随舵台垂直指令：{@code throttleY < 0}（下潜）注到满、
 *       {@code > 0}（上浮）排空、{@code 0}（停车）保持在
 *       {@link com.opamb.barotrauma.ship.ShipSimulation#ballastNeutral()} 的中性相对水位；</li>
 *   <li>{@link #ballast()} 返回 true，船体模拟把泵所在舱室登记成压载舱，
 *       水量计入全船水量占比 → {@code VsThruster} 按原版公式算浮力。</li>
 * </ol>
 *
 * <p>手动模式仍保留 −100..+100% 的注排，方便单独调试某个舱。</p>
 */
public class BallastPumpBlockEntity extends DrainPumpBlockEntity implements GeoBlockEntity {

    /** 最近一次服务端读到的舵台垂直指令：负=下潜、正=上浮、0=停车。 */
    private int helm;
    /** 当前船状态；用来读全船压载比例和中性水位。 */
    private ShipState ship;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BallastPumpBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.BALLAST_PUMP.get(), pos, state);
    }

    protected BallastPumpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean ballast() {
        return true;
    }

    @Override
    protected void beforeReport(ShipState ship) {
        this.ship = ship;
        helm = ship == null ? 0 : ship.throttleY();
    }

    /** 压载舱注水不许漫过舱门：水面最高到最近的舱门/舱口下方那一格。 */
    @Override
    protected boolean keepBelowDoor() {
        return true;
    }

    /**
     * 自动模式：舵台给目标水位，泵按全船压载比例注/排到目标。
     * flow 的约定是正值泵入、负值泵出；{@link #signedRate} 会再取负成
     * "正=泵出"交给船体模拟。
     */
    @Override
    public int effectiveFlow() {
        if (!auto()) {
            return super.effectiveFlow();
        }
        // 停靠中的方块船 / 还没标定中性水位的物理船：不自动注排
        if (ship == null || !ship.simulation().ballastPrimed()) {
            return 0;
        }
        double target = helm < 0 ? 1.0D : helm > 0 ? 0.0D
                : ship.simulation().ballastNeutral();
        double current = ship.simulation().ballastPercent();
        if (current > target + 0.02D) {
            return -100;
        }
        if (current < target - 0.02D) {
            return 100;
        }
        return 0;
    }

    /**
     * 模型 3 格宽 × 1.44 格高，且向前/左右伸出主方块；给一个 3×2×3 的保守
     * 渲染包围盒，避免只看到左右段时模型被视锥裁掉。
     */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 1.0D, p.getY() - 1.0D, p.getZ() - 1.0D,
                p.getX() + 2.0D, p.getY() + 2.0D, p.getZ() + 2.0D);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型：无动画控制器。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
