// 스폰 정책(E3): 기본은 바닐라 상수 그대로, 월드별·플레이어별 상한, 설정(furnace.properties) 바탕값, 필터 정리, 설정 파싱·경고.
package org.domi.furnace.server.entity.spawn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SpawnPolicyStateTest {

    @AfterEach
    void restoreConfig() { SpawnConfig.install(null); }

    private static Player player(int id) {
        Player p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.publishPosition(8.5, -60, 8.5, 0, 0, true);
        p.drainPending();
        return p;
    }

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    void defaultIsVanillaAndZeroCost() {
        var wsp = new WorldSpawnPolicy();
        assertSame(SpawnPolicyState.VANILLA, wsp.state(), "아무것도 걸지 않으면 같은 불변 칸");
        for (MobCategory c : MobCategory.values()) {
            if (!SpawnConfig.spawns(c)) continue;
            assertEquals(c.max(), wsp.state().cap(c), c.key());
            assertEquals(c.max(), wsp.state().playerCap(c), c.key());
            assertEquals(c.max(), wsp.state().playerCap(UUID.randomUUID(), c), c.key());
        }
        assertFalse(wsp.state().hasFilters());
        assertEquals(70, MobCategory.MONSTER.max());
        // 걸었다가 모두 지우면 다시 그 칸으로 돌아온다
        UUID u = UUID.randomUUID();
        wsp.setCap(MobCategory.MONSTER, 5);
        wsp.setPlayerCap(MobCategory.CREATURE, 4);
        wsp.setPlayerCap(u, MobCategory.AMBIENT, 3);
        Runnable off = wsp.addFilter("p", () -> true, (c, t, d, x, y, z, b, n) -> true);
        assertNotSame(SpawnPolicyState.VANILLA, wsp.state());
        wsp.resetCap(MobCategory.MONSTER);
        wsp.resetPlayerCap(MobCategory.CREATURE);
        wsp.resetPlayerCap(u, MobCategory.AMBIENT);
        off.run();
        assertSame(SpawnPolicyState.VANILLA, wsp.state());
    }

    @Test
    void resolutionOrderIsPlayerThenWorldThenConfigThenVanilla() {
        SpawnConfig.install(SpawnConfig.from(props("spawn.limit.monster", "50", "spawn.player-limit.monster", "40"), w -> {}));
        var wsp = new WorldSpawnPolicy();
        UUID u = UUID.randomUUID();
        assertEquals(50, wsp.state().cap(MobCategory.MONSTER), "설정이 바닐라 위");
        assertEquals(40, wsp.state().playerCap(u, MobCategory.MONSTER));
        assertEquals(10, wsp.state().cap(MobCategory.CREATURE), "다른 종류는 바닐라");
        wsp.setCap(MobCategory.MONSTER, 20);
        wsp.setPlayerCap(MobCategory.MONSTER, 15);
        assertEquals(20, wsp.state().cap(MobCategory.MONSTER), "월드별 값이 설정 위");
        assertEquals(15, wsp.state().playerCap(u, MobCategory.MONSTER));
        wsp.setPlayerCap(u, MobCategory.MONSTER, 2);
        assertEquals(2, wsp.state().playerCap(u, MobCategory.MONSTER), "그 사람 값이 맨 위");
        assertEquals(15, wsp.state().playerCap(UUID.randomUUID(), MobCategory.MONSTER), "다른 사람은 기본 플레이어 상한");
        wsp.resetCap(MobCategory.MONSTER);
        assertEquals(50, wsp.state().cap(MobCategory.MONSTER), "지우면 설정으로 돌아간다");
        // 0 은 유효한 값(그 종류는 스폰 안 함)이고 "정하지 않음"과 다르다
        wsp.setCap(MobCategory.MONSTER, 0);
        assertEquals(0, wsp.state().cap(MobCategory.MONSTER));
    }

    @Test
    void validation() {
        var wsp = new WorldSpawnPolicy();
        assertThrows(IllegalArgumentException.class, () -> wsp.setCap(MobCategory.MONSTER, -1));
        assertThrows(IllegalArgumentException.class, () -> wsp.setCap(MobCategory.MONSTER, SpawnConfig.MAX_CAP + 1));
        assertThrows(IllegalArgumentException.class, () -> wsp.setCap(MobCategory.MISC, 5), "MISC 는 자연 스폰이 없다");
        assertThrows(IllegalArgumentException.class, () -> wsp.setPlayerCap(UUID.randomUUID(), MobCategory.MISC, 5));
        wsp.setCap(MobCategory.MONSTER, SpawnConfig.MAX_CAP);
        assertEquals(SpawnConfig.MAX_CAP, wsp.state().cap(MobCategory.MONSTER));
    }

    @Test
    void localCapsFollowPolicy() {
        Player a = player(1), b = player(2);
        var wsp = new WorldSpawnPolicy();
        wsp.setPlayerCap(MobCategory.MONSTER, 3);
        wsp.setPlayerCap(b.uuid(), MobCategory.MONSTER, 1);
        // a 만 근처에 있으면 기본 플레이어 상한 3
        NaturalSpawner.SpawnState sa = new NaturalSpawner.SpawnState(1_000_000, List.of(a), wsp);
        for (int i = 0; i < 3; i++) {
            assertTrue(sa.canSpawnForCategory(MobCategory.MONSTER, 0, 0), "a 의 " + i + "번째");
            sa.addMobToLocalCaps(0, 0, MobCategory.MONSTER);
        }
        assertFalse(sa.canSpawnForCategory(MobCategory.MONSTER, 0, 0), "플레이어 상한 3 에 닿았다");
        assertTrue(sa.canSpawnForCategory(MobCategory.CREATURE, 0, 0), "다른 종류는 그대로");
        // b 에게는 1
        NaturalSpawner.SpawnState sb = new NaturalSpawner.SpawnState(1_000_000, List.of(b), wsp);
        assertTrue(sb.canSpawnForCategory(MobCategory.MONSTER, 0, 0));
        sb.addMobToLocalCaps(0, 0, MobCategory.MONSTER);
        assertFalse(sb.canSpawnForCategory(MobCategory.MONSTER, 0, 0), "b 만의 상한 1");
        // 둘 다 근처면 하나만 여유 있어도 스폰된다(바닐라 LocalMobCapCalculator: 근처 플레이어 중 하나라도 상한 아래면 허용)
        NaturalSpawner.SpawnState sab = new NaturalSpawner.SpawnState(1_000_000, List.of(a, b), wsp);
        sab.addMobToLocalCaps(0, 0, MobCategory.MONSTER);
        assertTrue(sab.canSpawnForCategory(MobCategory.MONSTER, 0, 0), "a 는 아직 1/3");
        // 플레이어 상한 0: 근처에 아무도 안 셌어도 못 난다(바닐라 상수는 늘 양수라 영향 없음)
        var zero = new WorldSpawnPolicy();
        zero.setPlayerCap(a.uuid(), MobCategory.MONSTER, 0);
        assertFalse(new NaturalSpawner.SpawnState(1_000_000, List.of(a), zero).canSpawnForCategory(MobCategory.MONSTER, 0, 0));
        assertTrue(new NaturalSpawner.SpawnState(1_000_000, List.of(a, b), zero).canSpawnForCategory(MobCategory.MONSTER, 0, 0), "다른 근처 플레이어가 여유 있으면 난다");
        // 정책 없이(바닐라) 70
        NaturalSpawner.SpawnState vanilla = new NaturalSpawner.SpawnState(1_000_000, List.of(a));
        for (int i = 0; i < 69; i++) vanilla.addMobToLocalCaps(0, 0, MobCategory.MONSTER);
        assertTrue(vanilla.canSpawnForCategory(MobCategory.MONSTER, 0, 0));
        vanilla.addMobToLocalCaps(0, 0, MobCategory.MONSTER);
        assertFalse(vanilla.canSpawnForCategory(MobCategory.MONSTER, 0, 0));
    }

    @Test
    void worldCapScalesWithSpawnableChunks() {
        var wsp = new WorldSpawnPolicy();
        wsp.setCap(MobCategory.CREATURE, 0);
        NaturalSpawner.SpawnState s = new NaturalSpawner.SpawnState(289, List.of(player(3)), wsp);
        assertFalse(s.canSpawnForCategory(MobCategory.CREATURE, 0, 0), "월드 상한 0 이면 못 난다");
        assertTrue(s.canSpawnForCategory(MobCategory.MONSTER, 0, 0));
        // 289 청크에서 상한 = max 그대로, 청크가 그 절반이면 절반
        var half = new WorldSpawnPolicy();
        half.setCap(MobCategory.MONSTER, 10);
        assertTrue(new NaturalSpawner.SpawnState(289, List.of(player(4)), half).canSpawnForCategory(MobCategory.MONSTER, 0, 0));
        assertFalse(new NaturalSpawner.SpawnState(28, List.of(player(5)), half).canSpawnForCategory(MobCategory.MONSTER, 0, 0), "10*28/289 = 0");
    }

    @Test
    void filtersRunInOrderAndDropWhenBrokenOrOwnerDisabled() {
        var level = new BlockPlacementTest.TestLevel();
        Player p = player(6);
        var wsp = new WorldSpawnPolicy();
        List<String> calls = new ArrayList<>();
        AtomicBoolean ownerOn = new AtomicBoolean(true);
        wsp.addFilter("first", () -> true, (c, t, d, x, y, z, b, n) -> {
            calls.add("first:" + t + "@" + x + "," + y + "," + z + ":" + c.key() + ":" + (n != null && n.equals(p.uuid())));
            return true;
        });
        wsp.addFilter("owner", ownerOn::get, (c, t, d, x, y, z, b, n) -> {
            calls.add("owner");
            return !t.equals("minecraft:zombie");
        });
        var s = new NaturalSpawner.SpawnState(289, List.of(p), wsp);
        assertTrue(s.filtersAllow(MobCategory.CREATURE, "minecraft:sheep", level, new Pos(1, -60, 2), p));
        assertEquals(List.of("first:minecraft:sheep@1,-60,2:creature:true", "owner"), calls);
        calls.clear();
        assertFalse(s.filtersAllow(MobCategory.MONSTER, "minecraft:zombie", level, new Pos(1, -60, 2), p), "하나라도 false 면 막는다");
        // 주인이 꺼지면 다음 호출에서 빠지고 통과한다
        ownerOn.set(false);
        assertTrue(s.filtersAllow(MobCategory.MONSTER, "minecraft:zombie", level, new Pos(1, -60, 2), p));
        assertEquals(1, wsp.filterCount());
        // 예외를 던지면 빠지고(로그) 그 틱은 통과
        AtomicInteger boom = new AtomicInteger();
        wsp.addFilter("broken", () -> true, (c, t, d, x, y, z, b, n) -> {
            boom.incrementAndGet();
            throw new IllegalStateException("고장");
        });
        var s2 = new NaturalSpawner.SpawnState(289, List.of(p), wsp);
        assertTrue(s2.filtersAllow(MobCategory.MONSTER, "minecraft:zombie", level, new Pos(1, -60, 2), p));
        assertTrue(s2.filtersAllow(MobCategory.MONSTER, "minecraft:zombie", level, new Pos(1, -60, 2), p));
        assertEquals(1, boom.get(), "한 번 던지면 다시 불리지 않는다");
        assertEquals(1, wsp.filterCount());
    }

    // ------------------------------------------------------------------ furnace.properties

    @Test
    void configDefaultsToVanillaAndParsesValues() {
        assertEquals(70, SpawnConfig.DEFAULT.cap(MobCategory.MONSTER));
        List<String> warns = new ArrayList<>();
        var c = SpawnConfig.from(props("spawn.limit.monster", " 40 ", "spawn.player-limit.creature", "6", "spawn.limit.water_ambient", "0"), warns::add);
        assertEquals(40, c.cap(MobCategory.MONSTER));
        assertEquals(70, c.playerCap(MobCategory.MONSTER), "안 쓴 키는 바닐라");
        assertEquals(6, c.playerCap(MobCategory.CREATURE));
        assertEquals(0, c.cap(MobCategory.WATER_AMBIENT));
        assertEquals(1, warns.size(), "0 은 경고: " + warns);
        assertTrue(warns.get(0).contains("spawn.limit.water_ambient"), warns.get(0));
    }

    @Test
    void configWarnsOnRiskyAndUnknownKeysAndRejectsBadValues() {
        List<String> warns = new ArrayList<>();
        SpawnConfig.from(props("spawn.limit.monster", "500", "spawn.limit.monsterz", "5"), warns::add);
        assertEquals(2, warns.size(), warns.toString());
        assertTrue(warns.stream().anyMatch(w -> w.contains("3배") && w.contains("spawn.limit.monster")), warns.toString());
        assertTrue(warns.stream().anyMatch(w -> w.contains("알 수 없는") && w.contains("monsterz")), warns.toString());
        assertThrows(IllegalArgumentException.class, () -> SpawnConfig.from(props("spawn.limit.monster", "-1"), w -> {}));
        assertThrows(IllegalArgumentException.class, () -> SpawnConfig.from(props("spawn.limit.monster", "abc"), w -> {}));
        assertThrows(IllegalArgumentException.class, () -> SpawnConfig.from(props("spawn.player-limit.creature", "10001"), w -> {}));
        assertTrue(SpawnConfig.knownKeys().contains("spawn.limit.underground_water_creature"));
        assertFalse(SpawnConfig.knownKeys().contains("spawn.limit.misc"));
    }
}
