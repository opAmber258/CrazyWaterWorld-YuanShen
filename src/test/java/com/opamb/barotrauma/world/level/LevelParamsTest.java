package com.opamb.barotrauma.world.level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 关卡参数：0.5 比例换算、开阔带、路线长度、资源 mask。 */
class LevelParamsTest {

    @Test
    @DisplayName("原作厘米按 0.5 比例换算成格")
    void blocksScale() {
        assertEquals(350, LevelParams.blocks(70000));
        assertEquals(450, LevelParams.blocks(90000));
        assertEquals(300, LevelParams.blocks(60000));
        assertEquals(150, LevelParams.blocks(30000));
        assertEquals(35, LevelParams.blocks(7000));
        assertEquals(10, LevelParams.blocks(2000));
        assertEquals(33, LevelParams.blocks(6500));
    }

    @Test
    @DisplayName("五种关卡的开阔带高度（高度 ×2 玩法放大）")
    void bandFloors() {
        assertEquals(-344, LevelType.TUTORIAL.params().bandFloor());
        assertEquals(-644, LevelType.BASIC.params().bandFloor());
        assertEquals(-744, LevelType.ADVANCED.params().bandFloor());
        assertEquals(-1000, LevelType.MAZE.params().bandFloor());
        assertEquals(-444, LevelType.OUTPOST.params().bandFloor());
        assertEquals(-44, LevelType.BASIC.params().bandCeiling());
    }

    @Test
    @DisplayName("路线长度按 StartPosition→EndPosition ×2.5，advanced 1500")
    void routeLengths() {
        assertEquals(518, LevelType.TUTORIAL.params().routeLength());
        assertEquals(700, LevelType.BASIC.params().routeLength());
        assertEquals(1500, LevelType.ADVANCED.params().routeLength());
        assertEquals(1300, LevelType.MAZE.params().routeLength());
        assertEquals(200, LevelType.OUTPOST.params().routeLength());
        assertTrue(LevelType.ADVANCED.params().routeLength() <= LevelParams.MAX_ROUTE);
    }

    @Test
    @DisplayName("大洞窟横截面：basic 440×560、advanced/maze 600×560")
    void cavernDimensions() {
        assertEquals(220, LevelType.BASIC.params().cavernHalfWidth());
        assertEquals(280, LevelType.BASIC.params().cavernHalfHeight());
        assertEquals(300, LevelType.ADVANCED.params().cavernHalfWidth());
        assertEquals(280, LevelType.ADVANCED.params().cavernHalfHeight());
        assertEquals(300, LevelType.MAZE.params().cavernHalfWidth());
        assertTrue(LevelType.BASIC.params().cavernOuterRadius()
                > LevelType.BASIC.params().cavernHalfWidth());
    }

    @Test
    @DisplayName("outpost 不是连接关；其余四种是")
    void connectionTypes() {
        assertTrue(LevelType.BASIC.params().isConnection());
        assertTrue(LevelType.ADVANCED.params().isConnection());
        assertTrue(LevelType.MAZE.params().isConnection());
        assertTrue(LevelType.TUTORIAL.params().isConnection());
        assertFalse(LevelType.OUTPOST.params().isConnection());
    }

    @Test
    @DisplayName("资源密度模数随 resourceSpawnChance 缩放")
    void veinMasks() {
        assertEquals(1024, LevelType.BASIC.params().seafloorVeinModulus());
        assertTrue(LevelType.ADVANCED.params().seafloorVeinModulus()
                < LevelType.BASIC.params().seafloorVeinModulus(),
                "0.3 比 0.2 更密 → 模数更小");
        assertEquals(256, LevelType.BASIC.params().wallVeinModulus());
        assertTrue(LevelType.TUTORIAL.params().wallVeinModulus()
                < LevelType.BASIC.params().wallVeinModulus(),
                "0.6 比 0.35 更密 → 模数更小");
    }

    @Test
    @DisplayName("模数边界：零概率取 baseline×4，极大概率封顶 128")
    void veinModulusBounds() {
        assertEquals(4096, withChances(0.0D, 0.35D).seafloorVeinModulus(),
                "海底零概率 = 1024×4");
        assertEquals(1024, withChances(0.2D, 0.0D).wallVeinModulus(),
                "岩壁零概率 = 256×4");
        assertEquals(128, withChances(100.0D, 100.0D).seafloorVeinModulus(),
                "极大概率封顶 128");
        assertEquals(128, withChances(100.0D, 100.0D).wallVeinModulus(),
                "极大概率封顶 128");
    }

    /** 复制 basic 的参数，只替换海底/岩壁资源概率。 */
    private static LevelParams withChances(double seafloor, double wall) {
        LevelParams base = LevelType.BASIC.params();
        LevelParams.Content c = base.content();
        return new LevelParams(base.type(), base.commonness(), base.minDifficulty(),
                base.maxDifficulty(), base.dims(), base.shape(),
                new LevelParams.Content(c.itemCount(), c.levelObjectAmount(),
                        c.backgroundCreatureAmount(), c.resourceIntervalMin(),
                        c.resourceIntervalMax(), c.caveResourceIntervalMin(),
                        c.caveResourceIntervalMax(), c.resourceClusterMin(),
                        c.resourceClusterMax(), seafloor, wall, c.wreckMin(),
                        c.wreckMax(), c.ruinCount(), c.thalamusProbability()),
                base.route());
    }

    @Test
    @DisplayName("fromId 认原作 id 和枚举名，未知回退 basic")
    void typeLookup() {
        assertEquals(LevelType.MAZE, LevelType.fromId("coldcavernsmaze"));
        assertEquals(LevelType.OUTPOST, LevelType.fromId("OUTPOST"));
        assertEquals(LevelType.BASIC, LevelType.fromId("nope"));
        assertEquals(LevelType.BASIC, LevelType.fromId(null));
    }
}
