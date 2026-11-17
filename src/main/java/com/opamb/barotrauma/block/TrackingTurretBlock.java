package com.opamb.barotrauma.block;

/**
 * 跟随炮台（第三种原型，开发者工具）。
 *
 * <p>和另外两种炮塔共用同一个方块实体与服务端射击判定，区别只在客户端：</p>
 * <ul>
 *   <li>视角完全交给原版鼠标视角，可以随意转动；</li>
 *   <li>炮塔自己的准心（炮管实际朝向）是屏幕上的额外准心，限速追赶视角中心；</li>
 *   <li>射击沿炮塔准心打出，所以会明显落后于你正在看的中心。</li>
 * </ul>
 */
public class TrackingTurretBlock extends TurretBlock {

    public TrackingTurretBlock(Properties properties) {
        super(properties);
    }
}