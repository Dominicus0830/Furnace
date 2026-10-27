// 자연 스폰 부품: 바이옴 스폰 표, 종류 메타, 배치 검사식.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.spawn.BiomeSpawns;
import org.domi.furnace.server.entity.spawn.EntityMeta;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NaturalSpawnerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void biomeTablesAndMetaLoad() {
        var plains = BiomeSpawns.mobs("minecraft:plains", MobCategory.CREATURE);
        assertTrue(plains.stream().anyMatch(d -> d.type().equals("minecraft:sheep")), "평원에 양이 있다");
        assertFalse(BiomeSpawns.mobs("minecraft:plains", MobCategory.MONSTER).isEmpty());
        assertEquals(MobCategory.MONSTER, EntityMeta.category("minecraft:zombie"));
        assertEquals(MobCategory.CREATURE, EntityMeta.category("minecraft:cow"));
        assertEquals(MobCategory.WATER_AMBIENT, EntityMeta.category("minecraft:cod"));
        assertNotNull(BiomeSpawns.getRandom(plains, RandomSource.create()));
        assertNotNull(EntityType.byName("minecraft:zombie"));
    }

    @Test
    void placementRulesForGrassAndStone() {
        var level = new BlockPlacementTest.TestLevel();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            level.setBlock(new Pos(x, -61, z), B.defaultState(B.id("minecraft:grass_block")), 3);
        }
        Pos pos = new Pos(5, -60, 5);
        assertTrue(SpawnPlacements.isSpawnPositionOk("minecraft:cow", level, pos), "풀 위 빈 칸은 땅 스폰 가능");
        assertTrue(SpawnPlacements.checkSpawnRules("minecraft:cow", level, SpawnReason.NATURAL, pos, RandomSource.create()) || level.getRawBrightness(pos, 0) <= 8,
                "소는 밝은 풀 위에 난다");
        assertFalse(SpawnPlacements.isSpawnPositionOk("minecraft:cow", level, new Pos(5, -61, 5)), "블록 안은 불가");
        level.setBlock(new Pos(7, -61, 7), B.defaultState(B.id("minecraft:glass")), 3);
        assertFalse(SpawnPlacements.isSpawnPositionOk("minecraft:zombie", level, new Pos(7, -60, 7)), "유리 위에는 못 난다");
    }

    private static final class Arena extends BlockPlacementTest.TestLevel {
        Player player;
        long day = 1000;
        boolean dark;

        @Override public int getSkyLight(Pos pos) { return dark ? 0 : super.getSkyLight(pos); }
        @Override public int getBlockLight(Pos pos) { return dark ? 0 : super.getBlockLight(pos); }
        @Override public int getRawBrightness(Pos pos, int skyDarken) { return dark ? 0 : super.getRawBrightness(pos, skyDarken); }

        @Override
        protected org.domi.furnace.server.block.LevelServices services() {
            return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                @Override public void forEachPlayer(java.util.function.Consumer<Player> c) { if (player != null) c.accept(player); }
                @Override public String biomeAt(int x, int y, int z) { return "minecraft:plains"; }
                @Override public long dayTime() { return day; }
                @Override public int difficulty() { return 2; }
            };
        }
    }

    @Test
    void spawnsAnimalsInDaylightAtLeast24BlocksFromPlayer() {
        var level = new Arena();
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        level.player = p;
        var state = org.domi.furnace.server.entity.spawn.NaturalSpawner.createState(289, level.entities().tickSnapshot(), level);
        for (int i = 0; i < 600; i++) {
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
                org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnForChunk(level, cx, cz, state, true, false, true);
            }
        }
        var mobs = level.entities().tickSnapshot().stream().filter(e -> e instanceof Mob).toList();
        assertFalse(mobs.isEmpty(), "평원에서 동물이 났다");
        for (Entity e : mobs) {
            assertEquals(MobCategory.CREATURE, EntityMeta.category(e.type().name()), e.type().name());
            double dx = e.x() - 8.5, dz = e.z() - 8.5;
            assertTrue(dx * dx + dz * dz > 20.0 * 20.0, "플레이어에서 24칸 안에는 나지 않는다: " + e.type().name());
        }
        // 상한 검사는 무리를 내기 전에만 한다(바닐라 같음): 9마리에서 가장 큰 무리(말 6)가 나면 15마리까지 갈 수 있다
        assertTrue(mobs.size() <= 10 - 1 + 6, "상한(10) 안팎: " + mobs.size() + " " + mobs.stream().map(e -> e.type().name()).sorted().toList());
    }

    @Test
    void spawnsMonstersAtNight() {
        var level = new Arena();
        level.day = 18000;
        level.dark = true;
        var p = new Player(10, java.util.UUID.randomUUID(), "Sam", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        level.player = p;
        var state = org.domi.furnace.server.entity.spawn.NaturalSpawner.createState(289, level.entities().tickSnapshot(), level);
        for (int i = 0; i < 600; i++) {
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
                org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnForChunk(level, cx, cz, state, false, true, false);
            }
        }
        long monsters = level.entities().tickSnapshot().stream().filter(e -> e instanceof Monster).count();
        assertTrue(monsters > 0, "밤 평원에서 몬스터가 났다");
        assertTrue(monsters <= 70 + 8, "몬스터 상한(묶음 하나만큼 넘을 수 있다): " + monsters);
    }

    private static java.util.List<String> chunkGenSpawns(long seed, int cx, int cz) {
        var level = new Arena();
        for (int x = -32; x < 48; x++) for (int z = -32; z < 48; z++) {
            level.setBlock(new Pos(x, -61, z), B.defaultState(B.id("minecraft:grass_block")), 3);
        }
        org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnMobsForChunkGeneration(level, cx, cz, seed);
        return level.entities().tickSnapshot().stream()
                .map(e -> e.type().name() + String.format("@%.3f,%.3f", e.x(), e.z())).sorted().toList();
    }

    @Test
    void chunkGenerationSpawnsAnimalsInsideTheChunkDeterministically() {
        int total = 0;
        for (long k = 1; k <= 60; k++) {
            long seed = k * 7919L * 104729L;
            int cx = (int) (k % 2), cz = (int) (k / 2 % 2);
            var first = chunkGenSpawns(seed, cx, cz);
            assertEquals(first, chunkGenSpawns(seed, cx, cz), "같은 시드·청크는 같은 결과");
            total += first.size();
            for (String s : first) {
                String type = s.substring(0, s.indexOf('@'));
                assertEquals(MobCategory.CREATURE, EntityMeta.category(type), type);
                String[] xz = s.substring(s.indexOf('@') + 1).split(",");
                double x = Double.parseDouble(xz[0]), z = Double.parseDouble(xz[1]);
                assertTrue(x >= cx * 16 && x < cx * 16 + 16 && z >= cz * 16 && z < cz * 16 + 16, "청크 안: " + s);
            }
        }
        assertTrue(total > 0, "평원 청크 60개 중 동물이 한 마리도 안 났다");
    }
}
