package com.opamb.barotrauma.mission;

/**
 * 任务类型。
 *
 * <ul>
 *   <li>{@code SALVAGE}——到沉船位置搜出黑匣子（沉船箱子里有 {@code black_box}），
 *       带回任意哨站终端交付；</li>
 *   <li>{@code RECON}——开船到目标点半径内侦察（到达即完成，回终端交付）；</li>
 *   <li>{@code DELIVERY}——货运：接取时把真实货箱装到船上货架，靠泊目的站后
 *       按原版 98% 规则自动结算（不需要回终端点交付）；</li>
 *   <li>{@code URGENT_DELIVERY}——紧急货运：规则同 DELIVERY 但有时限，
 *       超时判 FAILED（需求 §5.7：仅紧急类才有时限）。</li>
 * </ul>
 */
public enum MissionType {
    SALVAGE,
    RECON,
    DELIVERY,
    URGENT_DELIVERY
}
