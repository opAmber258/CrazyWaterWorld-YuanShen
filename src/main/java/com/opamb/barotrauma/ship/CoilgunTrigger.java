package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.block.TurretWeaponKind;

/** Server clock for the coilgun trigger lease and the reload after a successful shot. */
public final class CoilgunTrigger {
    public static final int REFRESH_TICKS = 4;
    private static final int LEASE_TICKS = 10;
    private long heldUntil = Long.MIN_VALUE;
    private long nextShot;

    /** Returns true on a new press, including a short tap released before the next world tick. */
    public boolean press(long tick) {
        boolean first = !held(tick);
        heldUntil = tick + LEASE_TICKS;
        return first;
    }

    public void release() {
        heldUntil = Long.MIN_VALUE;
    }

    public boolean held(long tick) {
        return tick < heldUntil;
    }

    public int cooldown(long tick) {
        return (int) Math.max(0L, nextShot - tick);
    }

    public void shot(long tick) {
        nextShot = tick + TurretWeaponKind.COILGUN.cooldownTicks();
    }
}
