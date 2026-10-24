// 몹 생성: 실효 난이도 계산, 스폰 알 경로(바닥에 맞춰 놓기·finalizeSpawn 속성), 같은 시드면 같은 결과.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void effectiveDifficultyScales() {
        assertEquals(0.0F, new DifficultyInstance(0, 1_000_000L, 5_000_000L, 1.0F).effectiveDifficulty());
        assertEquals(1.5F, new DifficultyInstance(2, 0L, 0L, 0.0F).effectiveDifficulty(), 1e-6F, "보통, 시간 0 → 2 × 0.75");
        float full = new DifficultyInstance(3, 100_000_000L, 4_000_000L, 1.0F).effectiveDifficulty();
        assertEquals(6.75F, full, 1e-5F, "어려움 최대: 3 × (0.75+0.25+1.0+0.25)");
        assertEquals(0.0F, new DifficultyInstance(2, 0L, 0L, 0.0F).specialMultiplier());
        assertEquals(1.0F, new DifficultyInstance(3, 100_000_000L, 4_000_000L, 1.0F).specialMultiplier());
    }

    @Test
    void spawnEggPlacesZombieOnGround() {
        var level = new BlockPlacementTest.TestLevel();
        assertTrue(level.spawnMobFromEgg("minecraft:zombie", new Pos(3, -60, 3), true, false));
        List<Zombie> zs = level.getEntitiesOfClass(Zombie.class, new AABB(-50, -80, -50, 50, 80, 50), e -> true);
        assertEquals(1, zs.size());
        Zombie z = zs.get(0);
        assertEquals(-60.0, z.y(), 1e-9, "바닥 위(y=-61 블록 윗면)에 놓인다");
        assertEquals(3.5, z.x(), 1e-9);
        assertNotEquals(0.0, z.attributeValue(Attribute.FOLLOW_RANGE) - 35.0, "random_spawn_bonus 가 붙는다");
        assertFalse(level.spawnMobFromEgg("minecraft:unknown_thing", new Pos(0, -60, 0), true, false));
    }

    @Test
    void zombieReinforcementChanceIsRandomizedWithinBounds() {
        var level = new BlockPlacementTest.TestLevel();
        for (int i = 0; i < 20; i++) level.spawnMobFromEgg("minecraft:zombie", new Pos(i % 4, -60, i / 4), true, false);
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, new AABB(-50, -80, -50, 50, 80, 50), e -> true)) {
            double r = z.attributeValue(Attribute.SPAWN_REINFORCEMENTS);
            assertTrue(r >= 0.0 && r <= 1.0, "범위: " + r);
            assertTrue(z.attributeValue(Attribute.KNOCKBACK_RESISTANCE) <= 0.05 + 1e-9);
        }
    }
}
