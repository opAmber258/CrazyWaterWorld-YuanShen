package com.opamb.barotrauma.registry;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 音效注册。
 *
 * <p>舱门开合用的是《潜渊症》原版的 {@code Items/Door/Door1..4.ogg}：原版门在
 * 开关时从这四段里随机挑一段（{@code doors.xml} 里 OnUse 的四个 sound），
 * 所以这里也做成一个带 4 个变体的音效事件，开和关共用。</p>
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BarotraumaMod.MOD_ID);

    /** 舱门开/关：原版四段门音随机。 */
    public static final RegistryObject<SoundEvent> DOOR_TOGGLE = register("door_toggle");

    /** 爬铁梯：原版 {@code footstep_metal}（登梯时手脚撞金属梯级的声音）。 */
    public static final RegistryObject<SoundEvent> LADDER_CLIMB = register("ladder_climb");

    /** 小丑喇叭：原创合成的滑稽喇叭声。 */
    public static final RegistryObject<SoundEvent> CLOWN_HORN = register("clown_horn");

    /** 吉他：原创合成的扫弦和弦。 */
    public static final RegistryObject<SoundEvent> GUITAR = register("guitar");

    /** 吉他单音：长按演奏用，靠 pitch 变调。 */
    public static final RegistryObject<SoundEvent> GUITAR_NOTE = register("guitar_note");

    /** 氧气瓶余量报警：原作 {@code Content/Items/WarningBeepSlow.ogg}（罐子剩余 <5% 时循环）。 */
    public static final RegistryObject<SoundEvent> LOW_OXYGEN_BEEP = register("low_oxygen_beep");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        new ResourceLocation(BarotraumaMod.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
