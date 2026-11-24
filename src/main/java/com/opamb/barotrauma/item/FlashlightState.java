package com.opamb.barotrauma.item;

/** Mixin 把这些状态放入玩家实体的原版跟踪数据。 */
public interface FlashlightState {
    byte OFF = 0;
    byte MAIN_HAND = 1;
    byte OFF_HAND = 2;
    byte HEAD = 3;
    byte LOW_CHARGE = 4;

    byte barotrauma$flashlight();

    void barotrauma$setFlashlight(byte state);
}
