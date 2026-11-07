package com.opamb.barotrauma.medical.affliction;

/**
 * 人体六部位（REQUIREMENTS §5.5：头、胸、左臂、右臂、左腿、右腿）。
 *
 * <p>对应原作 HumanoidModel 的 Limb 枚举；MC 没有部位命中，归属启发式在
 * {@code DamageConversions} 里做，这里只是纯数据。</p>
 */
public enum Limb {
    HEAD,
    TORSO,
    LEFT_ARM,
    RIGHT_ARM,
    LEFT_LEG,
    RIGHT_LEG
}
