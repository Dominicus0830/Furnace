// spawnRadius 흩뿌리기(ServerPlayer.adjustSpawnLocation): 난수 시작 칸 → 서로소 보폭 훑기, 반경 0·모험 모드, 위아래 맞춤.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnLocationTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void radiusTen_firstCandidateFromRandomStart() {
        var level = new BlockPlacementTest.TestLevel();   // 평지: 잔디 꼭대기 y=-61
        Pos got = SpawnLocation.adjust(level, new Pos(0, -60, 0), 10, false, new LegacyRandomSource(42L));
        int start = new LegacyRandomSource(42L).nextInt(21 * 21);   // 한 번만 뽑는다
        assertEquals(new Pos(start % 21 - 10, -60, start / 21 - 10), got);
    }

    @Test
    void zeroRadiusStillUsesOneBlockMinimumOnlyNearBorder() {
        var level = new BlockPlacementTest.TestLevel();
        // 반경 0 → 1칸짜리 격자(자기 자리)
        assertEquals(new Pos(3, -60, 4), SpawnLocation.adjust(level, new Pos(3, -60, 4), 0, false, new LegacyRandomSource(1L)));
    }

    @Test
    void adventureSkipsScatterButSettlesOnGround() {
        var level = new BlockPlacementTest.TestLevel();
        assertEquals(new Pos(5, -60, 5), SpawnLocation.adjust(level, new Pos(5, -40, 5), 10, true, new LegacyRandomSource(1L)));
        assertEquals(new Pos(5, -60, 5), SpawnLocation.adjust(level, new Pos(5, -63, 5), 10, true, new LegacyRandomSource(1L)), "땅 속이면 올린다");
    }

    @Test
    void waterColumnIsNotASpawn() {
        var level = new BlockPlacementTest.TestLevel();
        int water = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt").defaultStateId("minecraft:water");
        level.setBlock(new Pos(2, -60, 2), water, 2);
        assertNull(SpawnLocation.overworldRespawnPos(level, 2, 2));
        assertEquals(new Pos(1, -60, 2), SpawnLocation.overworldRespawnPos(level, 1, 2));
    }
}
