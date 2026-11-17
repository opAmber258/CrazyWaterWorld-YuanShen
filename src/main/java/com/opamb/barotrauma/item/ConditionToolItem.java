package com.opamb.barotrauma.item;

import com.opamb.barotrauma.network.RepairActionPacket;
import com.opamb.barotrauma.ship.RepairTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 耐久调节器：调试用工具，右键设备在几个档位之间循环，方便直接验收修理窗口。
 *
 * <p>原作设备的自然劣化有 60–270 秒的初始延迟，再按 0.125/秒 掉耐久，
 * 想看到泵掉到 80% 以下得在船上连续跑好几分钟。这个工具把耐久直接推到
 * 100% → 79% → 50% → 20% → 1% → 0% 再回到 100%，覆盖修理阈值和损坏状态。</p>
 */
public class ConditionToolItem extends Item {

    /** 档位（占最大耐久的比例），按从高到低排列。 */
    private static final double[] STEPS = {1.0D, 0.79D, 0.5D, 0.2D, 0.01D, 0.0D};

    public ConditionToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        RepairTarget target = resolve(server, pos);
        if (target == null) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.literal("这里没有可调耐久的设备").withStyle(ChatFormatting.GRAY),
                        true);
            }
            // 交给方块自己的 use，别挡住正常面板。
            return InteractionResult.PASS;
        }
        double max = target.maxCondition();
        double current = target.condition();
        double next = nextStep(current, max);
        double delta = next - current;
        if (delta > 0.0D) {
            target.applyRepair(delta);
        } else if (delta < 0.0D) {
            target.damage(-delta);
        }
        if (context.getPlayer() != null) {
            BlockState state = level.getBlockState(pos);
            String name = state.getBlock().getName().getString();
            context.getPlayer().displayClientMessage(
                    Component.literal(name + " 耐久 → " + Math.round(next / max * 100.0D) + "%")
                            .withStyle(next <= 0.0D ? ChatFormatting.RED : ChatFormatting.YELLOW),
                    false);
        }
        return InteractionResult.CONSUME;
    }

    /** 找下一个比当前低的档位；已经是 0% 就回到 100%。 */
    private static double nextStep(double current, double max) {
        for (double step : STEPS) {
            double value = step * max;
            if (value < current - 1.0E-4D) {
                return value;
            }
        }
        return max;
    }

    /**
     * 反应堆/引擎/导航终端是多块结构、门是 1×2、舱口是 3×1，右键点到的往往
     * 不是承载方块实体的那一格：先查命中格，再按主方块函数和上下左右邻格兜底。
     */
    private static RepairTarget resolve(ServerLevel level, BlockPos pos) {
        RepairTarget target = RepairActionPacket.find(level, pos);
        if (target != null) {
            return target;
        }
        // 多段设备（反应堆/引擎/加工台/解构仪/舱口/舱壁门/导航终端）统一解析
        // 到承载方块实体的主段；单格方块返回自身，重复查一次也无害。
        BlockPos master = com.opamb.barotrauma.event.RepairDamageEvents.masterOf(level, pos);
        target = RepairActionPacket.find(level, master);
        if (target != null) {
            return target;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            target = RepairActionPacket.find(level, pos.relative(direction));
            if (target != null) {
                return target;
            }
        }
        target = RepairActionPacket.find(level, pos.below());
        if (target != null) {
            return target;
        }
        return RepairActionPacket.find(level, pos.above());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("右键设备：耐久循环 100% → 79% → 50% → 20% → 1% → 0%")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("调试用，用来快速验收修理窗口").withStyle(ChatFormatting.DARK_GRAY));
    }
}
