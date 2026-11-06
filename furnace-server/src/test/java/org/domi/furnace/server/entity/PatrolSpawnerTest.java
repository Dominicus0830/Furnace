// 약탈자 순찰(PatrolSpawner·PatrollingMonster): 스폰 조건, 고정 시드 난수 순서 대조, 순찰 상태 저장 왕복, 지도자 굴림.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.spawn.PatrolSpawner;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatrolSpawnerTest {

    @BeforeAll
    static void install() throws Exception { TestRecipes.manager(); }

    private static final SuperflatWorldSource GEN = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

    /** 반경 6 청크 슈퍼플랫, 시간·규칙·난수를 정할 수 있는 레벨. */
    static class PatrolLevel extends ChunkLevel {
        final Map<Long, ChunkColumn> chunks = new HashMap<>();
        long day = 5 * 24000L + 1000L;
        Set<String> offRules = Set.of();
        final RandomSource rnd;

        PatrolLevel(long seed) {
            super(1000000);
            rnd = RandomSource.create(seed);
        }

        @Override public RandomSource random() { return rnd; }

        @Override
        protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > 6 || Math.abs(z) > 6) return null;
            return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> GEN.generate(x, z));
        }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return day; }
                @Override public int difficulty() { return 2; }
                @Override public boolean gameRule(String name) { return !offRules.contains(name) && super.gameRule(name); }
            };
        }
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        return p;
    }

    /** 첫 틱에 순찰대가 나는 시드: nextInt(1200) 다음 nextInt(5) == 0. */
    private static long successSeed() {
        for (long s = 1; ; s++) {
            RandomSource r = RandomSource.create(s);
            r.nextInt(1200);
            if (r.nextInt(5) == 0) return s;
        }
    }

    private static List<Pillager> pillagers(ChunkLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof Pillager).map(e -> (Pillager) e).toList();
    }

    @Test
    void spawnsPatrolInVanillaRandomOrder() {
        long seed = successSeed();
        var level = new PatrolLevel(seed);
        var spawner = new PatrolSpawner();
        int n = spawner.tick(level, List.of(player(GameMode.SURVIVAL)), true);

        // 같은 시드로 바닐라 호출 순서를 그대로 따라 한다
        RandomSource m = RandomSource.create(seed);
        int next = 12000 + m.nextInt(1200);
        assertEquals(0, m.nextInt(5));
        assertEquals(0, m.nextInt(1), "플레이어 1명 고르기");
        int dx = (24 + m.nextInt(24)) * (m.nextBoolean() ? -1 : 1);
        int dz = (24 + m.nextInt(24)) * (m.nextBoolean() ? -1 : 1);
        int x = 8 + dx, z = 8 + dz;
        int size = (int) Math.ceil(level.getCurrentDifficultyAt(new Pos(x, -60, z)).effectiveDifficulty()) + 1;

        assertEquals(next - 1, spawner.nextTick(), "0 에서 한 번 줄고(-1) 더한다");
        assertEquals(size, n);
        List<Pillager> list = pillagers(level);
        assertEquals(size, list.size(), "슈퍼플랫 풀밭에서는 모두 선다");
        Pillager leader = list.stream().filter(Raider::isPatrolLeader).findFirst().orElseThrow();
        assertEquals(1, list.stream().filter(Raider::isPatrolLeader).count());
        assertEquals(x, leader.x(), 1e-9, "지도자는 정수 좌표 setPos");
        assertEquals(z, leader.z(), 1e-9);
        assertEquals(level.getMotionBlockingNoLeavesHeight(x, z), leader.y(), 1e-9);
        assertTrue(leader.hasPatrolTarget());
        Pos t = leader.getPatrolTarget();
        assertTrue(Math.abs(t.x()) <= 500 && Math.abs(t.z()) <= 500, "목표는 원점 기준 ±500(setPos 전에 정함)");
        assertEquals("minecraft:white_banner", ItemTools.itemName(leader.getItemBySlot(EquipmentSlot.HEAD)));
        assertEquals(2.0F, leader.getEquipmentDropChance(EquipmentSlot.HEAD));
        for (Pillager p : list) {
            assertTrue(p.isPatrolling());
            assertTrue(p.canJoinRaid());
            if (p != leader) assertFalse(p.hasPatrolTarget(), "대원은 지도자가 목표를 나눠 줄 때까지 목표 없음");
        }
    }

    @Test
    void conditionsBlockSpawn() {
        long seed = successSeed();
        Player survivor = player(GameMode.SURVIVAL);

        var early = new PatrolLevel(seed);
        early.day = 4 * 24000L + 1000L;
        assertEquals(0, new PatrolSpawner().tick(early, List.of(survivor), true), "5일째 전에는 없다");

        var night = new PatrolLevel(seed);
        night.day = 5 * 24000L + 18000L;
        assertEquals(0, new PatrolSpawner().tick(night, List.of(survivor), true), "밤에는 없다");

        var rule = new PatrolLevel(seed);
        rule.offRules = Set.of("doPatrolSpawning");
        var off = new PatrolSpawner();
        assertEquals(0, off.tick(rule, List.of(survivor), true), "doPatrolSpawning 꺼짐");
        assertEquals(0, off.nextTick(), "규칙이 꺼지면 nextTick 도 안 줄어든다");

        assertEquals(0, new PatrolSpawner().tick(new PatrolLevel(seed), List.of(survivor), false), "적대 몹 스폰 꺼짐");
        assertEquals(0, new PatrolSpawner().tick(new PatrolLevel(seed), List.of(), true), "플레이어 없음");
        assertEquals(0, new PatrolSpawner().tick(new PatrolLevel(seed), List.of(player(GameMode.SPECTATOR)), true), "관전자");
        assertEquals(0, new PatrolSpawner(p -> true).tick(new PatrolLevel(seed), List.of(survivor), true), "마을 근처");

        var interval = new PatrolLevel(seed);
        var sp = new PatrolSpawner();
        sp.tick(interval, List.of(survivor), true);
        int before = pillagers(interval).size();
        for (int i = 1; i < 12000; i++) sp.tick(interval, List.of(survivor), true);
        assertEquals(before, pillagers(interval).size(), "다음 시도까지 12000 틱 이상 쉰다");
    }

    @Test
    void patrolStateRoundTrips() {
        var level = new PatrolLevel(1);
        var p = new Pillager(level, level.nextEntityId());
        p.moveTo(3.5, -60, 4.5, 0, 0);
        p.setPatrolLeader(true);
        p.setPatrolTarget(new Pos(120, 64, -77));
        NbtCompound tag = p.saveWithoutId(new NbtCompound());
        assertArrayEquals(new int[] {120, 64, -77}, (int[]) tag.tags().get("patrol_target"));
        assertTrue(tag.getBoolean("PatrolLeader"));
        assertTrue(tag.getBoolean("Patrolling"));
        var back = new Pillager(level, level.nextEntityId());
        back.load(tag);
        assertEquals(new Pos(120, 64, -77), back.getPatrolTarget());
        assertTrue(back.isPatrolLeader());
        assertTrue(back.isPatrolling());

        var plain = new Pillager(level, level.nextEntityId());
        var plainBack = new Pillager(level, level.nextEntityId());
        plainBack.load(plain.saveWithoutId(new NbtCompound()));
        assertFalse(plainBack.hasPatrolTarget());
        assertFalse(plainBack.isPatrolling());
    }

    @Test
    void naturalSpawnRollsLeaderButPatrolDoesNot() {
        var level = new PatrolLevel(1);
        // 첫 nextFloat 가 0.06 미만인 시드
        long s = 1;
        while (RandomSource.create(s).nextFloat() >= 0.06F) s++;
        var v = new Vindicator(level, level.nextEntityId());
        v.finalizeSpawn(RandomSource.create(s), level.getCurrentDifficultyAt(new Pos(0, -60, 0)));
        assertTrue(v.isPatrolLeader(), "자연 스폰 6% 굴림");
        assertEquals("minecraft:white_banner", ItemTools.itemName(v.getItemBySlot(EquipmentSlot.HEAD)));
        assertFalse(v.isPatrolling());

        var r = new Ravager(level, level.nextEntityId());
        r.finalizeSpawn(RandomSource.create(s), level.getCurrentDifficultyAt(new Pos(0, -60, 0)));
        assertFalse(r.isPatrolLeader(), "약탈수는 지도자가 될 수 없다");

        var p = new Vindicator(level, level.nextEntityId());
        p.setFinalizeReason(SpawnReason.PATROL);
        p.finalizeSpawn(RandomSource.create(s), level.getCurrentDifficultyAt(new Pos(0, -60, 0)));
        assertFalse(p.isPatrolLeader(), "순찰 스폰은 굴리지 않는다");
        assertTrue(p.isPatrolling());
    }

    @Test
    void longDistancePatrolGoalOnlyWhenPatrollingWithTarget() {
        var level = new PatrolLevel(1);
        var p = new Pillager(level, level.nextEntityId());
        var goal = new Raider.LongDistancePatrolGoal(p, 0.7, 0.595);
        assertFalse(goal.canUse());
        p.setPatrolTarget(new Pos(100, -60, 100));
        assertTrue(goal.canUse());
        // 동료가 없으면 순찰을 그만둔다
        goal.tick();
        assertFalse(p.isPatrolling());
    }

    /**
     * 사용자 버그 C4: 145일째 낮에 시간을 흘려 보내면 12000+nextInt(1200) 틱마다 판정하고, 1/5 판정이 맞는 주기에 순찰대가 난다.
     * 판정 사이(대기 중)에는 난수를 쓰지 않는다.
     */
    @Test
    void patrolAttemptsEveryCycleAfterTimePasses() {
        var level = new PatrolLevel(42L);
        level.day = 145 * 24000L + 6000L;   // 145일째 정오
        var spawner = new PatrolSpawner(p -> false);
        List<Player> players = List.of(player(GameMode.SURVIVAL));
        RandomSource m = RandomSource.create(42L);
        int checks = 0, spawned = 0;
        long next = 0;
        for (int t = 0; t < 20 * 13200 && spawned == 0; t++) {
            int n = spawner.tick(level, players, true);
            next--;
            if (next <= 0) {   // 이 틱에 판정: 대기 다시 걸기 + 1/5
                checks++;
                next += 12000 + m.nextInt(1200);
                boolean roll = m.nextInt(5) == 0;
                assertEquals(next, spawner.nextTick(), "대기 다시 걸기(" + checks + "번째 판정)");
                if (roll) {
                    assertTrue(n > 0, "1/5 판정이 맞으면 순찰대가 난다");
                    spawned = n;
                } else {
                    assertEquals(0, n);
                }
            } else {
                assertEquals(0, n, "대기 중에는 내지 않는다");
            }
        }
        assertTrue(spawned > 0, "판정 " + checks + "번 안에 순찰대");
        assertEquals(spawned, pillagers(level).size());
        assertTrue(checks >= 1);
    }
}
