package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** A connection to a distribution box in ship assembly coordinates. */
public interface PowerConnection {
    @Nullable BlockPos powerBox();
    void setPowerBox(@Nullable BlockPos box);
}
