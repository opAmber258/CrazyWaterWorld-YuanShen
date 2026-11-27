package com.opamb.barotrauma.world.shipyard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipLibraryTest {

    @TempDir
    Path temp;

    @Test
    void metaRoundTripsThroughJson() {
        ShipLibrary.Entry entry = new ShipLibrary.Entry("my_ship", "我的船",
                37, 14, 13, 842, Map.of("reactor", 1, "engine", 2), 123456789L, temp);
        ShipLibrary.writeMeta(temp, entry);

        ShipLibrary.Entry loaded = ShipLibrary.readMeta(temp);
        assertNotNull(loaded);
        assertEquals("my_ship", loaded.id());
        assertEquals("我的船", loaded.name());
        assertEquals(37, loaded.sizeX());
        assertEquals(14, loaded.sizeY());
        assertEquals(13, loaded.sizeZ());
        assertEquals(842, loaded.blocks());
        assertEquals(1, loaded.devices().get("reactor"));
        assertEquals(2, loaded.devices().get("engine"));
        assertEquals(123456789L, loaded.modified());
        assertEquals("37×14×13", loaded.sizeText());
    }

    @Test
    void invalidOrMissingMetaIsIgnored() {
        assertNull(ShipLibrary.readMeta(temp));
        assertFalse(ShipLibrary.validId("../escape"));
        assertFalse(ShipLibrary.validId(""));
        assertTrue(ShipLibrary.validId("dugong-2"));
    }

    @Test
    void chineseNamesGetDistinctAsciiIds() {
        String first = ShipyardLibraryServer.slug("潜艇甲");
        String second = ShipyardLibraryServer.slug("潜艇乙");
        assertTrue(ShipLibrary.validId(first));
        assertTrue(ShipLibrary.validId(second));
        assertTrue(first.equals(ShipyardLibraryServer.slug("潜艇甲")));
        assertFalse(first.equals(second));
        assertTrue(ShipLibrary.clip("x".repeat(80)).length() <= 48);
    }

    @Test
    void ownerAndVerifiedMetaRoundTrips() {
        ShipLibrary.Entry entry = new ShipLibrary.Entry("owned_sub", "认证潜艇",
                20, 10, 12, 350, Map.of("reactor", 1), 987654321L, temp,
                "11111111-2222-3333-4444-555555555555", "CaptainA", true);
        assertTrue(ShipLibrary.writeMeta(temp, entry));

        ShipLibrary.Entry loaded = ShipLibrary.readMeta(temp);
        assertNotNull(loaded);
        assertEquals("11111111-2222-3333-4444-555555555555", loaded.ownerUuid());
        assertEquals("CaptainA", loaded.ownerName());
        assertTrue(loaded.verified());
    }
}
