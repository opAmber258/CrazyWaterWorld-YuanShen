package com.opamb.barotrauma.mixin;

import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 抱着双手持物品时数字键不切热键栏。
 *
 * <p>直接在 {@code Minecraft.handleKeybinds} 写入 {@code Inventory.selected} 的地方
 * 拦掉，热键栏选中值根本不会变（不是等服务端丢包）。滚轮由
 * {@code InventoryMixin.swapPaint} 拦。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Redirect(method = "handleKeybinds",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Inventory;selected:I",
                    opcode = Opcodes.PUTFIELD))
    private void barotrauma$lockHotbarKeys(Inventory inventory, int value) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !TwoHanded.isCarrying(player)) {
            inventory.selected = value;
        }
    }
}
