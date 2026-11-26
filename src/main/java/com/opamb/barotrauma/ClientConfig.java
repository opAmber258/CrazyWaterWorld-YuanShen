package com.opamb.barotrauma;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client preferences are stored per installation by Forge. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue TURRET_SENSITIVITY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        TURRET_SENSITIVITY = builder.comment("Turret mouse sensitivity multiplier, as a percentage.")
                .defineInRange("turretSensitivity", 150, 25, 300);
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
