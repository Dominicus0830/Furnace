// 스폰 정책(E3)을 실제 스폰 루프(NaturalSpawner.spawnForChunk)에 걸어 본다: 월드 상한 0, 플레이어 상한 0, 조건 필터, 아무것도 안 걸면 바닐라처럼 난다.
// spawn-animals=false 와 같이 쓰이는 flags(spawnFriendlies=false)도 같은 루프로 확인한다 — 동물 종류는 한 마리도 나지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.spawn.EntityMeta;
import org.domi.furnace.server.entity.spawn.MobCategory;
import org.domi.furnace.server.entity.spawn.NaturalSpawner;
import org.domi.furnace.server.entity.spawn.WorldSpawnPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpawnPolicySpawnTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
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

    private static Arena arena(boolean night) {
        var level = new Arena();
        if (night) {
            level.day = 18000;
            level.dark = true;
        }
        var p = new Player(9, UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        level.player = p;
        return level;
    }

    /** 몹을 낼 때까지 청크를 돌려 본다(animals/enemies 플래그와 정책을 준다). */
    private static List<Entity> spawn(Arena level, WorldSpawnPolicy policy, boolean friendlies, boolean enemies, int rounds) {
        var state = NaturalSpawner.createState(289, level.entities().tickSnapshot(), level, policy);
        for (int i = 0; i < rounds; i++) {
            for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
                NaturalSpawner.spawnForChunk(level, cx, cz, state, friendlies, enemies, friendlies);
            }
        }
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof Mob).toList();
    }

    @Test
    void noPolicyBehavesLikeVanilla() {
        var level = arena(false);
        var mobs = spawn(level, new WorldSpawnPolicy(), true, false, 600);
        assertFalse(mobs.isEmpty(), "정책이 없으면 평원에서 동물이 난다");
        assertTrue(mobs.size() <= 10 - 1 + 6, "바닐라 상한(10) 안팎: " + mobs.size());
        // null 정책(옛 시험·도구 경로)도 같다
        var level2 = arena(false);
        assertFalse(spawn(level2, null, true, false, 600).isEmpty());
    }

    @Test
    void categoryCapZeroStopsThatCategoryOnly() {
        var policy = new WorldSpawnPolicy();
        policy.setCap(MobCategory.CREATURE, 0);
        assertTrue(spawn(arena(false), policy, true, false, 600).isEmpty(), "동물 상한 0 이면 한 마리도 안 난다");
        // 몬스터는 영향 없음(밤)
        var mobs = spawn(arena(true), policy, true, true, 600);
        assertTrue(mobs.stream().anyMatch(e -> EntityMeta.category(e.type().name()) == MobCategory.MONSTER), "밤에는 몬스터가 난다");
        for (Entity e : mobs) assertNotEquals(MobCategory.CREATURE, EntityMeta.category(e.type().name()), e.type().name());
    }

    @Test
    void lowerWorldCapKeepsCountNearTheCap() {
        var policy = new WorldSpawnPolicy();
        policy.setCap(MobCategory.CREATURE, 2);
        // 2*289/289 = 2 — 상한은 무리를 내기 전에만 본다: 가장 큰 무리(말 6)까지 넘을 수 있다
        var mobs = spawn(arena(false), policy, true, false, 600);
        assertFalse(mobs.isEmpty());
        assertTrue(mobs.size() <= 2 - 1 + 6, "상한 2 안팎: " + mobs.size());
    }

    @Test
    void playerCapZeroForOnePlayerBlocksSpawnsNearThem() {
        var level = arena(false);
        var policy = new WorldSpawnPolicy();
        policy.setPlayerCap(level.player.uuid(), MobCategory.CREATURE, 0);
        assertTrue(spawn(level, policy, true, false, 600).isEmpty(), "그 플레이어 근처 동물 스폰 상한 0");
        // 같은 정책의 다른 사람에게는 영향이 없다 — 이 플레이어를 다른 UUID 로 바꾸면 난다
        var other = arena(false);
        assertFalse(spawn(other, policy, true, false, 600).isEmpty());
    }

    @Test
    void filterSelectsWhatSpawns() {
        var level = arena(false);
        var policy = new WorldSpawnPolicy();
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        policy.addFilter("test", () -> true, (cat, type, dim, x, y, z, biome, nearest) -> {
            asked.incrementAndGet();
            assertEquals("minecraft:plains", biome);
            assertEquals(MobCategory.CREATURE, cat);
            assertEquals(level.player.uuid(), nearest);
            return type.equals("minecraft:sheep");
        });
        var mobs = spawn(level, policy, true, false, 1200);
        assertFalse(mobs.isEmpty(), "양은 난다");
        assertTrue(asked.get() > 0);
        for (Entity e : mobs) assertEquals("minecraft:sheep", e.type().name());
        // 아무것도 허락하지 않으면 한 마리도 안 난다
        var none = new WorldSpawnPolicy();
        none.addFilter("test", () -> true, (cat, type, dim, x, y, z, biome, nearest) -> false);
        assertTrue(spawn(arena(false), none, true, false, 600).isEmpty());
    }

    @Test
    void spawnAnimalsFalseFlagSpawnsNoAnimalsButMonstersStillSpawn() {
        // spawn-animals=false: spawnForChunk 의 spawnFriendlies=false — 바닐라와 같이 친화 종류(동물·물 동물·박쥐)는 고르지 않는다
        var day = arena(false);
        assertTrue(spawn(day, new WorldSpawnPolicy(), false, true, 600).stream()
                .noneMatch(e -> EntityMeta.category(e.type().name()).isFriendly()), "친화 종류는 없다");
        var night = arena(true);
        var mobs = spawn(night, new WorldSpawnPolicy(), false, true, 600);
        assertFalse(mobs.isEmpty(), "몬스터는 계속 난다");
        for (Entity e : mobs) assertEquals(MobCategory.MONSTER, EntityMeta.category(e.type().name()), e.type().name());
        // spawn-monsters=false 쪽도 같은 루프: 동물만 난다
        var nm = spawn(arena(true), new WorldSpawnPolicy(), true, false, 600);
        for (Entity e : nm) assertTrue(EntityMeta.category(e.type().name()).isFriendly(), e.type().name());
    }
}
