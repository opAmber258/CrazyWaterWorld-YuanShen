package com.opamb.barotrauma.world.level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 关卡难度换算与类型选择。 */
class LevelSelectorTest {

    @Test
    @DisplayName("第一趟强制 tutorial")
    void firstTripTutorial() {
        for (int i = 0; i < 20; i++) {
            assertEquals(LevelType.TUTORIAL,
                    LevelSelector.connectionType(true, i, 1234L + i));
        }
    }

    @Test
    @DisplayName("难度 0–4 只出 basic；5 是 basic/advanced 重叠点")
    void lowDifficultyBasicOnly() {
        for (int difficulty = 0; difficulty <= 4; difficulty++) {
            for (int i = 0; i < 30; i++) {
                assertEquals(LevelType.BASIC,
                        LevelSelector.connectionType(false, difficulty, 77L * i + difficulty));
            }
        }
        // 原作 basic maxleveldifficulty=5、advanced minleveldifficulty=5，5 两边都合法。
        for (int i = 0; i < 30; i++) {
            LevelType type = LevelSelector.connectionType(false, 5, 77L * i + 5);
            assertTrue(type == LevelType.BASIC || type == LevelType.ADVANCED);
        }
    }

    @Test
    @DisplayName("难度 6–7 只出 advanced")
    void midDifficultyAdvancedOnly() {
        for (int difficulty = 6; difficulty <= 7; difficulty++) {
            for (int i = 0; i < 30; i++) {
                assertEquals(LevelType.ADVANCED,
                        LevelSelector.connectionType(false, difficulty, 31L * i + difficulty));
            }
        }
    }

    @Test
    @DisplayName("难度 8+ 会出 maze，也仍会出 advanced")
    void highDifficultyMaze() {
        boolean maze = false;
        boolean advanced = false;
        for (int i = 0; i < 300; i++) {
            LevelType type = LevelSelector.connectionType(false, 10, 999L * i + 5);
            maze |= type == LevelType.MAZE;
            advanced |= type == LevelType.ADVANCED;
            assertTrue(type == LevelType.ADVANCED || type == LevelType.MAZE);
        }
        assertTrue(maze, "300 个种子里至少应抽到一次 maze");
        assertTrue(advanced, "advanced 权重更高，应仍会抽到");
    }

    @Test
    @DisplayName("相同种子/难度结果固定")
    void deterministic() {
        long seed = LevelSelector.levelSeed(42L, 7);
        assertEquals(LevelSelector.connectionType(false, 9, seed),
                LevelSelector.connectionType(false, 9, seed));
        assertNotEquals(LevelSelector.levelSeed(42L, 7), LevelSelector.levelSeed(42L, 8));
    }

    @Test
    @DisplayName("难度 = 目的深度 + 已完成出航数，封顶 15")
    void difficultyMapping() {
        assertEquals(0, LevelSelector.difficulty(-62, 0));
        assertEquals(3, LevelSelector.difficulty(-301, 0));
        assertEquals(4, LevelSelector.difficulty(-301, 1));
        assertEquals(7, LevelSelector.difficulty(-640, 0));
        assertEquals(LevelSelector.MAX_DIFFICULTY, LevelSelector.difficulty(-640, 40));
        assertEquals(0, LevelSelector.difficulty(-10, 0));
    }
}
