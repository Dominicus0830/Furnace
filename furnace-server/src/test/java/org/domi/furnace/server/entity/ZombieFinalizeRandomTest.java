// Zombie.finalizeSpawn 난수 순서: 바닐라 1.21.1 은 ZombieGroupData(getSpawnAsBabyOdds(r), true) 라 기수 여부에 난수를 쓰지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ZombieFinalizeRandomTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static List<String> record(long seed) {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        RandomSource real = RandomSource.create(seed);
        List<String> calls = new ArrayList<>();
        RandomSource rec = (RandomSource) Proxy.newProxyInstance(RandomSource.class.getClassLoader(), new Class<?>[] {RandomSource.class},
                (proxy, m, args) -> {
                    calls.add(m.getName());
                    return m.invoke(real, args);
                });
        z.finalizeSpawn(rec, level.getCurrentDifficultyAt(z.blockPosition()));
        return calls;
    }

    @Test
    void finalizeSpawnNeverDrawsNextBoolean() {
        for (long seed = 1; seed <= 50; seed++) {
            List<String> calls = record(seed);
            assertFalse(calls.contains("nextBoolean"), "seed " + seed + ": 기수 여부는 상수 true — 난수 없음 " + calls);
        }
    }

    @Test
    void floatDrawsCoverPickupBabyAndDoorRolls() {
        // 줍기 nextFloat → 아기 nextFloat → (아기면 닭 굴림) → 문 부수기 nextFloat 이므로 최소 3번
        assertTrue(record(7).stream().filter(c -> c.equals("nextFloat")).count() >= 3);
    }
}
