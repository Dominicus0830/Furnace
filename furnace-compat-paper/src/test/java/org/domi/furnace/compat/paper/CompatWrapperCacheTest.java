package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-073 — 어댑터 엔티티·플레이어 감싸개 캐시 정리. 살아 있는 감싸개가 1024 를 넘어도 놓칠 때마다 전체를 훑지 않고(회수 큐로 죽은 칸만 걷고),
 * 관측되는 동작(같은 엔티티 = 같은 Bukkit 객체, 회수된 칸은 {@code cachedEntity} 에서 null, 핸들이 바뀌면 새 감싸개)은 그대로다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompatWrapperCacheTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    static org.domi.furnace.api.entity.Entity fake(UUID id, String type) {
        return (org.domi.furnace.api.entity.Entity) Proxy.newProxyInstance(CompatWrapperCacheTest.class.getClassLoader(),
                new Class<?>[] {org.domi.furnace.api.entity.Entity.class},
                (p, m, a) -> switch (m.getName()) {
                    case "uniqueId" -> id;
                    case "type" -> type;
                    case "snapshotStamp" -> 0L;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> p == a[0];
                    default -> null;
                });
    }

    static org.domi.furnace.api.entity.Entity fake(UUID id) { return fake(id, "minecraft:arrow"); }

    /** 회수가 끝날 때까지(최대 몇 초) GC 를 재촉하며 조건을 기다린다. */
    static boolean await(java.util.function.BooleanSupplier ok) throws InterruptedException {
        for (int i = 0; i < 100 && !ok.getAsBoolean(); i++) {
            System.gc();
            Thread.sleep(30);
        }
        return ok.getAsBoolean();
    }

    // ------------------------------------------------------------------ 동일성·회수 (결과 동일)

    @Test
    void sameEntitySameWrapperAndFreshSnapshotKept() {
        UUID id = UUID.randomUUID();
        var f = fake(id);
        Entity a = Compat.entity(f);
        assertSame(a, Compat.entity(f));
        assertSame(a, Compat.cachedEntity(id));
        assertSame(a, Compat.entity(fake(id)));   // 같은 UUID 의 새 스냅샷도 같은 감싸개
    }

    @Test
    void kindChangeReplacesWrapperAndOldReferenceDoesNotRemoveNewOne() throws Exception {
        UUID id = UUID.randomUUID();
        Entity arrow = Compat.entity(fake(id, "minecraft:arrow"));
        Entity pig = Compat.entity(fake(id, "minecraft:item"));   // 형(CraftEntity → CraftItem)이 달라지면 새 감싸개로 교체
        assertNotSame(arrow, pig);
        assertSame(pig, Compat.cachedEntity(id));
        // 교체된 옛 감싸개가 회수돼 큐에 나와도 새 칸은 지워지지 않는다(remove(key, ref) 는 그 참조일 때만)
        WeakReference<Entity> oldRef = new WeakReference<>(arrow);
        arrow = null;
        assertTrue(await(() -> oldRef.get() == null), "옛 감싸개가 회수돼야 한다");
        for (int i = 0; i < 3; i++) Compat.entity(fake(UUID.randomUUID()));   // 놓침 → 큐를 걷는다
        assertSame(pig, Compat.cachedEntity(id));
        assertSame(pig, Compat.entity(fake(id, "minecraft:item")));
    }

    @Test
    void collectedWrappersAreSweptOnMissEvenBelowOldThreshold() throws Exception {
        int base = Compat.entityCacheSize();
        List<UUID> ids = new ArrayList<>();
        List<WeakReference<Entity>> refs = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            refs.add(new WeakReference<>(Compat.entity(fake(id))));
        }
        assertTrue(Compat.entityCacheSize() >= base + 200);
        assertTrue(await(() -> refs.stream().allMatch(r -> r.get() == null)), "감싸개가 회수돼야 한다");
        // 회수된 칸은 읽기에서 이미 null (옛 구현과 같은 관측)
        for (UUID id : ids) assertNull(Compat.cachedEntity(id));
        // 새로 놓치면 죽은 칸을 걷는다(옛 구현은 1024 를 넘어야 걷었다)
        assertTrue(await(() -> {
            Compat.entity(fake(UUID.randomUUID()));
            return Compat.entityCacheSize() < base + 200;
        }), "회수된 칸이 걷혀야 한다");
        // 걷은 UUID 를 다시 감싸면 새 감싸개
        Entity again = Compat.entity(fake(ids.get(0)));
        assertNotNull(again);
        assertSame(again, Compat.cachedEntity(ids.get(0)));
    }

    @Test
    void heldWrappersAreNeverDropped() throws Exception {
        List<Entity> keep = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            keep.add(Compat.entity(fake(id)));
        }
        for (int i = 0; i < 500; i++) Compat.entity(fake(UUID.randomUUID()));   // 놓침들
        System.gc();
        for (int i = 0; i < ids.size(); i++) assertSame(keep.get(i), Compat.cachedEntity(ids.get(i)));
        assertSame(keep.get(7), Compat.entity(fake(ids.get(7))));
    }

    // ------------------------------------------------------------------ 놓침 비용이 N 에 비례하지 않는다

    /** 붙잡힌 감싸개 n 개일 때 새 감싸개 하나를 놓치는 평균 비용(ns) — 세 번 중 최소. */
    static double missNanos(int held) {
        List<Object> keep = new ArrayList<>();
        for (int i = 0; i < held; i++) keep.add(Compat.entity(fake(UUID.randomUUID())));
        double best = Double.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            List<org.domi.furnace.api.entity.Entity> more = new ArrayList<>();
            for (int i = 0; i < 1000; i++) more.add(fake(UUID.randomUUID()));
            long t = System.nanoTime();
            for (var f : more) keep.add(Compat.entity(f));
            best = Math.min(best, (System.nanoTime() - t) / 1000.0);
        }
        keep.clear();
        return best;
    }

    @Test
    void missCostDoesNotGrowWithHeldCount() {
        missNanos(500);   // 예열
        double small = missNanos(1000);
        double big = missNanos(20000);
        System.out.printf("T-073 miss cost: held=1000 %.0f ns, held=20000 %.0f ns%n", small, big);
        // 옛 구현: 1000 개 ≈ 9µs, 20000 개 ≈ 144µs(16배). 새 구현은 같은 자리수 — 여유를 크게 둔다
        assertTrue(big < small * 6 + 5_000, "놓침 비용이 붙잡힌 수에 비례해 늘었다: held=1000 " + small + "ns, held=20000 " + big + "ns");
    }

    // ------------------------------------------------------------------ 플레이어 표

    @Test
    void playerWrapperIdentityAndHandleSwapUnchanged() {
        var tp = server.join("CacheA");
        server.tick(2);
        var a = Compat.player(tp.player());
        assertSame(a, Compat.player(tp.player()));
        assertEquals(tp.player().uniqueId(), a.getUniqueId());
        assertTrue(Compat.playerCacheSize() > 0);
        assertEquals(1024, Compat.playerPruneAt(), "작은 표에서는 문턱이 기본값이다");
    }
}
