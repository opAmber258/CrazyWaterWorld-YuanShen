package com.opamb.barotrauma.item;

/**
 * 等离子切割机的氧耗结算（纯逻辑，便于单测）。
 *
 * <p>每成功切割 {@link #CUTS_PER_UNIT} 次扣 1 点氧气；未结算的次数由调用方
 * 存进玩家持久数据，退出/重登不丢。</p>
 */
public final class PlasmaCutterCredit {

    /** 每多少次成功切割扣 1 点氧气（原作 1/10 罐）。 */
    public static final int CUTS_PER_UNIT = 10;

    private PlasmaCutterCredit() {
    }

    /**
     * 记一次成功切割后的未结算次数。
     *
     * @return {@code [新次数, 是否该扣 1 点氧气]}
     */
    public static int[] applyCredit(int carried) {
        int applied = Math.max(0, carried) + 1;
        return applied >= CUTS_PER_UNIT
                ? new int[]{applied - CUTS_PER_UNIT, 1}
                : new int[]{applied, 0};
    }
}
