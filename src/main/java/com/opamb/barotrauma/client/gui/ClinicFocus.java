package com.opamb.barotrauma.client.gui;

import com.opamb.barotrauma.network.ClinicStatePacket;

import java.util.List;
import java.util.Objects;

/**
 * 诊所病症列表刷新时的键盘焦点映射（纯逻辑，便于单测）。
 *
 * <p>服务端每次治疗都会回一个刷新包，列表行号会变。焦点必须按
 * {@code (afflictionId, limb)} 身份跟着原条目走；只保行号会在前面的病症
 * 被治好后让 Enter 指到别的病症并误扣费。</p>
 */
public final class ClinicFocus {

    private ClinicFocus() {
    }

    /**
     * 按身份在刷新后的列表里重新定位焦点行。
     *
     * @param before 刷新前的条目
     * @param oldRow 刷新前的焦点行；负数或越界按“无焦点”处理
     * @param after  刷新后的条目
     * @return 新列表中的行号；焦点条目已被移除（治好了）时返回 -1
     */
    public static int remap(List<ClinicStatePacket.Entry> before, int oldRow,
                            List<ClinicStatePacket.Entry> after) {
        if (oldRow < 0 || oldRow >= before.size()) {
            return -1;
        }
        ClinicStatePacket.Entry focused = before.get(oldRow);
        for (int i = 0; i < after.size(); i++) {
            ClinicStatePacket.Entry candidate = after.get(i);
            if (Objects.equals(candidate.afflictionId(), focused.afflictionId())
                    && Objects.equals(candidate.limb(), focused.limb())) {
                return i;
            }
        }
        return -1;
    }
}
