package com.opamb.barotrauma.block;

/**
 * 自由瞄准炮台（原型，开发者工具）。
 *
 * <p>方块实体、服务端占用/瞄准/射击判定和炮管渲染都与 {@link TurretBlock} 共用
 * （同一个 {@code BlockEntityType}），只有客户端输入方式不同：</p>
 * <ul>
 *   <li>鼠标不再直接转视角，而是推动一个可以拖到屏幕任意位置的准心；</li>
 *   <li>视角（中心准星）以有限角速度跟随那个准心，准心会随着视角追上而收回中心。</li>
 * </ul>
 *
 * <p>射击仍然沿中心准星（炮塔当前朝向）打出，自由准心只负责引导视角。</p>
 */
public class FreeAimTurretBlock extends TurretBlock {

    public FreeAimTurretBlock(Properties properties) {
        super(properties);
    }
}
