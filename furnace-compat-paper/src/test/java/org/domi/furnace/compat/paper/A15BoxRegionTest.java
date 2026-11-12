package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 긴 광선·큰 상자에서의 엔티티 탐색 region 규칙: region 스레드는 자기 region, 전역 빌림 구간은 겹치는 region 을 모두 빌려 합친다(PR #258 검토 R1). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
/** (엔티티는 AI 가 없는 갑옷 거치대 — 몹은 느린 실행기에서 틱 사이에 움직여 광선에서 벗어날 수 있다) */
class A15BoxRegionTest {

    FurnaceTestServer server;
    TestPlayer tp;
    World world;
    BlockPos origin;
    BlockPos far;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Rhea");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 40, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        // 멀리 떨어진 청크 하나(다른 region)
        far = new BlockPos(origin.x() + 40 * 16, origin.y(), origin.z());
        server.loadChunk(far.chunkX(), far.chunkZ());
        server.tick(2);
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    Location at(double dx, double dy, double dz) { return new Location(world, origin.x() + 0.5 + dx, origin.y() + dy, origin.z() + 0.5 + dz); }

    Entity spawn(BlockPos base, Location at, EntityType type) {
        return server.onRegion(base, () -> {
            Entity e = world.spawnEntity(at, type);
            e.setGravity(false);
            return e;
        });
    }

    /** 결과 또는 던진 예외 이름. */
    static String run(Supplier<Object> s) {
        try {
            Object o = s.get();
            return "OK " + o;
        } catch (Throwable t) {
            return "THROW " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    @Test
    void longRayFromOwnRegion() {
        Entity zombie = spawn(origin, at(3, 0, 0), EntityType.ARMOR_STAND);
        List<String> out = new ArrayList<>();
        for (double d : new double[] {50, 150, 300, 1000}) {
            out.add(d + " entities: " + owner(() -> run(() -> {
                RayTraceResult r = world.rayTraceEntities(at(0, 1, 0), new Vector(1, 0, 0), d);
                return r == null ? "null" : r.getHitEntity().getType();
            })));
            out.add(d + " rayTrace: " + owner(() -> run(() -> {
                RayTraceResult r = world.rayTrace(at(0, 1, 0), new Vector(1, 0, 0), d, FluidCollisionMode.NEVER, false, 0.0, null);
                return r == null ? "null" : r.getHitEntity() != null ? r.getHitEntity().getType() : r.getHitBlock();
            })));
        }
        // Paper: 어느 거리든 바로 앞 받침대(갑옷 거치대)를 맞춘다.
        out.forEach(System.out::println);
        owner(() -> { zombie.remove(); return null; });
        for (String s : out) assertEquals(true, s.contains("ARMOR_STAND"), s);
    }

    @Test
    void boxSpanningTwoRegions() {
        Entity near = spawn(origin, at(1, 0, 0), EntityType.ARMOR_STAND);
        Location farLoc = new Location(world, far.x() + 0.5, far.y(), far.z() + 0.5);
        Entity farPig = spawn(far, farLoc, EntityType.ARMOR_STAND);
        // 가운데가 origin 쪽인 큰 상자(x 로 ±700) — region 스레드는 자기 region 의 갑옷 거치대만 본다(Folia 처럼 남의 region 은 못 만진다)
        BoundingBox box = new BoundingBox(origin.x() - 700, origin.y() - 2, origin.z() - 2, origin.x() + 700, origin.y() + 4, origin.z() + 3);
        String a = owner(() -> run(() -> world.getNearbyEntities(box, e -> e.getType() == EntityType.ARMOR_STAND).size()));
        // 가운데가 far 쪽 region 인 상자를 origin 스레드에서
        BoundingBox box2 = new BoundingBox(origin.x() - 2, origin.y() - 2, origin.z() - 2, far.x() + 40, origin.y() + 4, origin.z() + 3);
        String b = owner(() -> run(() -> world.getNearbyEntities(box2, e -> e.getType() == EntityType.ARMOR_STAND).size()));
        System.out.println("centerOrigin=" + a + " centerFarSide=" + b);
        owner(() -> { near.remove(); return null; });
        server.onRegion(far, () -> { farPig.remove(); return null; });
        assertEquals("OK 1", a, "자기 region 의 갑옷 거치대만");
        assertEquals("OK 1", b, "상자 가운데가 남의 region 이어도 자기 region 의 갑옷 거치대는 보인다 — 예외·누락 없음");
    }

    /** 옛 Bukkit 동기 작업(전역 스레드 + region 빌림 구간)에서: Paper 의 "주 스레드" 호출에 해당. */
    @Test
    void legacySyncBorrowScope() {
        Entity zombie = spawn(origin, at(3, 0, 0), EntityType.ARMOR_STAND);
        Location farLoc = new Location(world, far.x() + 0.5, far.y(), far.z() + 0.5);
        Entity farPig = spawn(far, farLoc, EntityType.ARMOR_STAND);
        BoundingBox box = new BoundingBox(origin.x() - 700, origin.y() - 2, origin.z() - 2, origin.x() + 700, origin.y() + 4, origin.z() + 3);
        String[] r = server.onGlobal(() -> {
            try (var access = org.domi.furnace.api.Furnace.server().scheduler().global().openRegionAccess()) {
                String ray = run(() -> {
                    RayTraceResult h = world.rayTraceEntities(at(0, 1, 0), new Vector(1, 0, 0), 1000);
                    return h == null ? "null" : h.getHitEntity().getType();
                });
                String nearby = run(() -> world.getNearbyEntities(box, e -> e.getType() != EntityType.PLAYER).size());
                return new String[] {ray, nearby};
            }
        });
        System.out.println("legacy ray=" + r[0] + " nearby=" + r[1]);
        owner(() -> { zombie.remove(); return null; });
        server.onRegion(far, () -> { farPig.remove(); return null; });
        assertEquals("OK ARMOR_STAND", r[0]);
        assertEquals("OK 2", r[1]);
    }
}
