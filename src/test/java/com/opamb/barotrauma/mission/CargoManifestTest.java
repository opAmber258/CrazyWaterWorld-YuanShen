package com.opamb.barotrauma.mission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 货运任务货物清单：4 条在售货运都得有箱子/单价/内容物，数值对齐原版 Missions.xml。
 */
class CargoManifestTest {

    @Test
    void 每条在售货运都有货物清单() {
        int cargo = 0;
        for (MissionSpec spec : MissionCatalog.offers()) {
            if (spec.kind() != MissionKind.CARGO) {
                continue;
            }
            cargo++;
            CargoManifest manifest = CargoManifest.forMission(spec.id());
            assertNotNull(manifest, "在售货运缺少货物清单: " + spec.id());
            assertFalse(manifest.crateItemId().isBlank(), spec.id());
            assertTrue(manifest.rewardPerCrate() > 0, "每箱报酬必须为正: " + spec.id());
            assertTrue(manifest.maxCrates() > 0, spec.id());
            assertFalse(manifest.contents().isEmpty(), "箱子不能是空的: " + spec.id());
            for (CargoManifest.Entry entry : manifest.contents()) {
                assertFalse(entry.itemId().isBlank(), spec.id());
                assertTrue(entry.count() > 0, spec.id());
            }
        }
        assertTrue(cargo >= 4, "寒冷深穴在售货运应有 4 条");
    }

    @Test
    void 每箱报酬对齐原版() {
        assertEquals(140, CargoManifest.forMission("cargochemicals").rewardPerCrate());
        assertEquals(120, CargoManifest.forMission("cargomeditems").rewardPerCrate());
        assertEquals(200, CargoManifest.forMission("cargoconstructionminerals").rewardPerCrate());
        assertEquals(400, CargoManifest.forMission("cargovolatilecompoundn").rewardPerCrate());
    }

    @Test
    void 箱内清单不超过箱子容量() {
        // 容量取自原版 containers.xml：化学箱/医疗箱/炸药箱 12，金属箱 20。
        java.util.Map<String, Integer> capacity = java.util.Map.of(
                "chemicalcrate", 12, "mediccrate", 12, "metalcrate", 20, "explosivecrate", 12);
        for (String id : new String[]{"cargochemicals", "cargomeditems",
                "cargoconstructionminerals", "cargovolatilecompoundn"}) {
            CargoManifest manifest = CargoManifest.forMission(id);
            assertTrue(manifest.contents().size() <= capacity.get(manifest.crateItemId()),
                    id + " 的箱内清单超过容量");
        }
    }

    @Test
    void 不是货运任务返回空清单() {
        assertNull(CargoManifest.forMission("quartzcollection"));
        assertNull(CargoManifest.forMission(null));
    }
}
