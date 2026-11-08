package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.DrainPumpBlock;
import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.block.IronLadderBlock;
import com.opamb.barotrauma.block.MissionTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册表。
 *
 * <p>注意：这里只注册 Block 本体，对应的 BlockItem 在 {@link ModItems} 里注册，
 * 这样方块物品能直接进创造模式物品栏。</p>
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BarotraumaMod.MOD_ID);

    /** 潜艇装甲板：高压环境下也不变形的船体钢板。 */
    public static final RegistryObject<Block> SUBMARINE_PLATING = BLOCKS.register("submarine_plating",
            () -> new HullBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(25.0F, 1200.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    .isViewBlocking((state, level, pos) -> !HullBlock.isBreached(state))
                    .isSuffocating((state, level, pos) -> !HullBlock.isBreached(state))));

    /** 任务终端：接任务/交付/商店的界面入口（哨站和 B 中继站各一台）。 */
    public static final RegistryObject<Block> MISSION_TERMINAL = BLOCKS.register("mission_terminal",
            () -> new MissionTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(15.0F, 1200.0F)
                    .sound(SoundType.METAL)));

    /** 排水泵：把船内模拟水往外抽；右键菜单设开关与排水量。 */
    public static final RegistryObject<Block> DRAIN_PUMP = BLOCKS.register("drain_pump",
            () -> new DrainPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(10.0F, 600.0F)
                    .sound(SoundType.METAL)));

    /** 小型泵：船内大多数位置用的小功率排水泵（GeckoLib 体素模型）。 */
    public static final RegistryObject<Block> SMALL_PUMP = BLOCKS.register("small_pump",
            () -> new com.opamb.barotrauma.block.SmallPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(6.0F, 300.0F)
                    .sound(SoundType.METAL)
                    // 方块本身是 INVISIBLE、靠 GeckoLib 渲染；不关遮挡的话相邻方块
                    // 朝向它的面会被剔除，模型没遮住的地方就会露出“洞”（用户实测）。
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /** 免支撑铁梯：整格宽、2px 厚的钢板梯，立在对接井中央，可攀爬、有碰撞。 */
    public static final RegistryObject<Block> IRON_LADDER = BLOCKS.register("iron_ladder",
            () -> new IronLadderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    // 透明（cutout）薄板模型：不关遮挡会让相邻方块朝它的面被剔除。
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    /**
     * 排水口：对照原版 duct block（地板通水格栅）。
     *
     * <p>不是泵，没有方块实体；水可以穿过这一格流向相邻舱室（见
     * {@link ModShipBlocks#isDoorway} / {@link ModShipBlocks#isOpenDoorway}）。</p>
     */
    public static final RegistryObject<Block> DRAIN_OUTLET = BLOCKS.register("drain_outlet",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(4.0F, 200.0F)
                    .sound(SoundType.METAL)));

    /** 黑匣子方块：沉船船舱里那一格，打掉掉落 black_box（打捞任务的交付物）。 */
    public static final RegistryObject<Block> BLACK_BOX_BLOCK = BLOCKS.register("black_box_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
