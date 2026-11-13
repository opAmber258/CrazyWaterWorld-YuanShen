package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 双手持物品的 HUD 格位：快捷栏左侧单独一格显示当前抱着的物品。
 *
 * <p>原作 {@code Holdable slots="RightHand+LeftHand"} 的物品会同时占住左右手，
 * HUD 在热键栏左边把这只"双手"单独画出来。这里用原版背包 GUI 的槽位贴图
 * （{@code textures/gui/widgets.png} 的 0,0）画一格，位置贴着热键栏左端、
 * 和热键栏留一点间距。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TwoHandedHud {

    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    /** 与热键栏左端的额外间距（像素）。 */
    private static final int GAP = 28;

    private TwoHandedHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || minecraft.player.isSpectator()) {
            return;
        }
        ItemStack held = TwoHanded.heldBy(minecraft.player);
        if (held.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // 原版热键栏：条 x = width/2 - 91，y = height - 22；槽位贴图从 (1,1) 起 20×20。
        int x = width / 2 - 91 - GAP;
        int y = height - 21;
        graphics.blit(WIDGETS, x, y, 1, 1, 20, 20);
        // 选中框画在双手格上（原版热键栏的选中框由 GuiMixin 藏掉）。
        graphics.blit(WIDGETS, x - 1, y - 2, 0, 22, 24, 22);
        graphics.renderItem(held, x + 2, y + 2);
        graphics.renderItemDecorations(minecraft.font, held, x + 2, y + 2);
    }
}
