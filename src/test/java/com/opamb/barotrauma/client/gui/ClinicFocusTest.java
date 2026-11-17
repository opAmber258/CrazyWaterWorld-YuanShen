package com.opamb.barotrauma.client.gui;

import com.opamb.barotrauma.network.ClinicStatePacket.Entry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 诊所列表刷新后按身份保留键盘焦点。 */
class ClinicFocusTest {

    private static Entry entry(String id, String limb) {
        return new Entry(id, limb, 10.0D, 100);
    }

    @Test
    @DisplayName("排在焦点前面的病症被治好后，焦点仍跟着原病症")
    void remapFollowsIdentityWhenEarlierRowRemoved() {
        List<Entry> before = List.of(entry("a", "HEAD"), entry("b", "TORSO"));
        List<Entry> after = List.of(entry("b", "TORSO"));
        assertEquals(0, ClinicFocus.remap(before, 1, after));
    }

    @Test
    @DisplayName("同一病症的不同部位按 limb 区分，不串行")
    void remapMatchesLimbToo() {
        List<Entry> before = List.of(entry("a", "HEAD"), entry("a", "TORSO"));
        List<Entry> after = List.of(entry("a", "TORSO"), entry("a", "HEAD"));
        // 刷新后 TORSO 排到第 0 行、HEAD 排到第 1 行：焦点要跟着身份走，不能只保行号。
        assertEquals(0, ClinicFocus.remap(before, 1, after));
        assertEquals(1, ClinicFocus.remap(before, 0, after));
    }

    @Test
    @DisplayName("焦点病症本身被治好时清空焦点")
    void remapClearsFocusWhenEntryGone() {
        List<Entry> before = List.of(entry("a", "HEAD"), entry("b", "TORSO"));
        List<Entry> after = List.of(entry("b", "TORSO"));
        assertEquals(-1, ClinicFocus.remap(before, 0, after));
    }

    @Test
    @DisplayName("无焦点或旧行号越界时返回 -1")
    void remapWithoutFocus() {
        List<Entry> entries = List.of(entry("a", "HEAD"));
        assertEquals(-1, ClinicFocus.remap(entries, -1, entries));
        assertEquals(-1, ClinicFocus.remap(entries, 5, entries));
    }
}
