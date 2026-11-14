package com.opamb.barotrauma.station;

/**
 * 商店价格与补货的纯数学。对照原作：
 *
 * <ul>
 *   <li>购买价 = 基础价 × (1 + 商店随机修正 ±5%)；</li>
 *   <li>出售价 = 基础价 × 30% × (1 + 商店随机修正)；</li>
 *   <li>每次到访新哨站，库存 +1，封顶初始库存 +5。</li>
 * </ul>
 *
 * <p>声望/每日特价/收购这次不做（用户选择"真实库存 + 买卖"）。</p>
 */
public final class StorePricing {

    /** 原作：0 声望时出售价是原购买价的 30%。 */
    public static final double SELL_FRACTION = 0.30D;
    /** 每个商店独立随机价格修正范围（百分比）。 */
    public static final int PRICE_MODIFIER_RANGE = 5;
    /** 每次到访补货量。 */
    public static final int RESTOCK_AMOUNT = 1;
    /** 补货上限 = 初始库存 + 这个值。 */
    public static final int RESTOCK_HEADROOM = 5;

    private StorePricing() {
    }

    /** 购买价：基础价 × (1 + 修正%)，至少 1。 */
    public static int buyPrice(int basePrice, int modifierPercent) {
        if (basePrice <= 0) {
            return 0;
        }
        double price = basePrice * (1.0D + modifierPercent / 100.0D);
        return Math.max(1, (int) Math.round(price));
    }

    /** 出售价：基础价 × 30% × (1 + 修正%)，至少 1。 */
    public static int sellPrice(int basePrice, int modifierPercent) {
        if (basePrice <= 0) {
            return 0;
        }
        double price = basePrice * SELL_FRACTION * (1.0D + modifierPercent / 100.0D);
        return Math.max(1, (int) Math.round(price));
    }

    /** 每次到访补货 1 个，封顶 initial + headroom。 */
    public static int restock(int stock, int initialStock) {
        int max = Math.max(0, initialStock) + RESTOCK_HEADROOM;
        return Math.min(max, Math.max(0, stock) + RESTOCK_AMOUNT);
    }

    /** 每个站点固定的 ±5% 价格修正（确定性，读档不变）。 */
    public static int modifierFor(int siteX, int siteZ) {
        int hash = siteX * 31 + siteZ * 17;
        return Math.floorMod(hash, PRICE_MODIFIER_RANGE * 2 + 1) - PRICE_MODIFIER_RANGE;
    }
}
