// A10 회귀(2차 점검 A10-06·07·08·10): 자연 스폰 위치·높이·바이옴 예산·거리 경계를 바닐라 1.21.1 바이트코드(ddh)와 맞춘다.
package org.domi.furnace.server.entity.spawn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A10NaturalSpawnerParityTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /**
     * A10-07: NaturalSpawner.getRandomPosWithin(ddh.a(dcw,dvi)) 의 위 끝은 LevelChunk.getHeight(WORLD_SURFACE)(duy.a: firstAvailable - 1) + 1
     * = 맨 위 블록 + 1. 예전 Furnace 는 getWorldSurfaceHeight(이미 맨 위 블록 + 1) 에 또 1 을 더했다.
     */
    @Test
    void spawnHeightUpperBoundIsTopBlockPlusOne() {
        var level = new BlockPlacementTest.TestLevel();
        Pos top = new Pos(3, 70, 3);
        level.setBlock(top, B.defaultState(B.id("minecraft:stone")), 3);
        assertEquals(top.y() + 1, NaturalSpawner.randomPosTop(level, 3, 3), "바닐라 위 끝 = 맨 위 블록 y + 1");
    }

    /** 이웃 청크 칸이 엔티티 틱인지로 정하고(ServerLevel.isNaturalSpawningAllowed), 보더는 보지 않는다(A10-06). */
    private static final class TickingLevel extends BlockPlacementTest.TestLevel {
        boolean neighbourTicking;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(super.services()) {
                @Override public boolean withinBorder(int x, int z) { return false; }   // 보더 밖이어도 위치 판정은 바뀌지 않아야 한다
            };
        }

        @Override
        public boolean isNaturalSpawningAllowed(int chunkX, int chunkZ) { return neighbourTicking || (chunkX == 0 && chunkZ == 0); }
    }

    @Test
    void positionInNeighbourChunkNeedsEntityTickingNotBorder() {
        var level = new TickingLevel();
        double far = 100 * 100;
        assertFalse(NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, 0, 0, new Pos(3, 64, 3), far), "스폰 지점(0,64,0) 24 칸 안");
        Pos farSame = new Pos(1, 64, 30 * 16 + 3), farNeighbour = new Pos(17, 64, 30 * 16 + 3);
        assertTrue(NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, 0, 30, farSame, far), "같은 청크는 늘 된다(보더 밖이어도)");
        level.neighbourTicking = false;
        assertFalse(NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, 0, 30, farNeighbour, far), "엔티티 틱이 아닌 이웃 청크");
        level.neighbourTicking = true;
        assertTrue(NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, 0, 30, farNeighbour, far), "엔티티 틱인 이웃 청크(보더 밖이어도)");
        assertFalse(NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(level, 0, 30, farNeighbour, 576.0), "플레이어 24 칸 안");
    }

    /**
     * A10-08: SpawnState.canSpawn/afterSpawn 의 에너지 예산 바이옴은 스폰 중인 청크의 getNoiseBiome(quart x&3, y, z&3)(ddh.a(jd,duy)).
     * 이웃 청크로 퍼진 칸도 스폰 중인 청크의 같은 상대 자리를 본다.
     */
    @Test
    void spawnCostBiomeIsReadFromTheSpawningChunk() {
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected LevelServices services() {
                return new LevelServices.Delegating(super.services()) {
                    @Override public String roughBiomeAt(int x, int y, int z) { return (x >> 4) == 0 ? "minecraft:soul_sand_valley" : "minecraft:plains"; }
                };
            }
        };
        // 청크 (1,0) 의 칸 (21, 64, 9) — 스폰 중인 청크가 (0,0) 이면 (0*16 + (21 & 12), 64, 9 & 12) = (4, 64, 8) 을 본다
        assertEquals("minecraft:soul_sand_valley", NaturalSpawner.spawnChunkBiome(level, 0, 0, new Pos(21, 64, 9)));
        assertEquals("minecraft:plains", NaturalSpawner.spawnChunkBiome(level, 1, 0, new Pos(5, 64, 9)));
        // 음수 좌표: 청크 (-1,-1) 에서 칸 (-3, 64, -7) → (-16 + (-3 & 12), 64, -16 + (-7 & 12)) = (-4, 64, -8)
        var probe = new BlockPlacementTest.TestLevel() {
            int[] seen;

            @Override
            protected LevelServices services() {
                return new LevelServices.Delegating(super.services()) {
                    @Override public String roughBiomeAt(int x, int y, int z) {
                        seen = new int[] {x, y, z};
                        return "minecraft:plains";
                    }
                };
            }
        };
        NaturalSpawner.spawnChunkBiome(probe, -1, -1, new Pos(-3, 64, -7));
        assertArrayEquals(new int[] {-4, 64, -8}, probe.seen);
    }

    /** A10-10: LocalMobCapCalculator 의 가까움은 ChunkMap.playerIsCloseEnoughForSpawning(< 16384.0). 정확히 128 이면 아니다. */
    @Test
    void localCapDistanceIsStrictlyLessThan128() {
        Player p = new Player(1, UUID.randomUUID(), "P", new ConcurrentEmbeddedChannel());
        p.publishPosition(8 + 128.0, -60, 8, 0, 0, true);   // 청크 (0,0) 가운데에서 정확히 128
        p.drainPending();
        NaturalSpawner.SpawnState s = new NaturalSpawner.SpawnState(1_000_000, List.of(p));
        assertFalse(s.canSpawnForCategory(MobCategory.MONSTER, 0, 0), "정확히 128 블록이면 지역 상한 대상이 아니다 → 아무도 가깝지 않아 스폰 불가");
        Player q = new Player(2, UUID.randomUUID(), "Q", new ConcurrentEmbeddedChannel());
        q.publishPosition(8 + 127.9, -60, 8, 0, 0, true);
        q.drainPending();
        assertTrue(new NaturalSpawner.SpawnState(1_000_000, List.of(q)).canSpawnForCategory(MobCategory.MONSTER, 0, 0));
    }
}
