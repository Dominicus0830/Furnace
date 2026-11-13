package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-078: 어댑터 감싸개의 "지금 값" 읽기(getType·getName·getLocation·getWorld·getHealth)를 할당 없이 정리해도 결과가 같아야 한다.
 * 옛 구현(종류는 매번 {@code EntityType.fromKey}, 이름은 매번 문자열 자르기)을 시험 안에 그대로 두고 모든 종류 키로 전수 비교한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityHotReadsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Hot");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    /** 종류 키만 아는 핸들(소유 스레드가 아닌 것처럼 동작) — getType·getName 은 이 값만 쓴다. */
    static CraftEntity fake(String type) {
        var h = (org.domi.furnace.api.entity.Entity) Proxy.newProxyInstance(EntityHotReadsTest.class.getClassLoader(),
                new Class<?>[]{org.domi.furnace.api.entity.Entity.class}, (p, m, a) -> switch (m.getName()) {
                    case "type" -> type;
                    case "uniqueId" -> UUID.nameUUIDFromBytes(type.getBytes());
                    case "customName" -> null;
                    case "isOwnedByCurrentThread" -> false;
                    case "stateNow" -> throw new ThreadOwnershipException("not owner");
                    case "hashCode" -> 1;
                    case "equals" -> p == a[0];
                    case "toString" -> "fake:" + type;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        return new CraftEntity(h);
    }

    // 옛 구현(커밋 전 CraftEntity)
    static EntityType oldType(String key) { return EntityType.fromKey(key); }

    static String oldName(String type) {
        String path = type.substring(type.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }

    @Test
    void typeTableMatchesFromKeyForEveryEntityType() {
        int n = 0;
        for (EntityType t : EntityType.values()) {
            if (t == EntityType.UNKNOWN) continue;
            String key = "minecraft:" + t.name().toLowerCase(Locale.ROOT);
            CraftEntity e = fake(key);
            assertSame(oldType(key), e.getType(), key);
            assertSame(t, e.getType(), key);
            assertSame(e.getType(), e.getType(), "두 번째 호출도 같은 값");
            assertEquals(oldName(key), e.getName(), key);
            assertEquals(oldName(key), e.getName(), "두 번째 호출도 같은 이름: " + key);
            n++;
        }
        assertTrue(n > 100, "종류를 거의 다 돌았다: " + n);
    }

    @Test
    void unknownKindKeepsThrowingEveryCall() {
        CraftEntity e = fake("somemod:thing");
        for (int i = 0; i < 3; i++) assertThrows(UnsupportedFeature.class, e::getType);
        assertEquals("Thing", e.getName());
    }

    @Test
    void liveReadsFollowTeleportAcrossFreshHandles() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 0.5), EntityType.PIG));
        try {
            Object[] seen = owner(() -> {
                Location to = new Location(world, at.x() + 6.5, at.y() + 1, at.z() + 0.5, 45f, 7f);
                pig.teleport(to);
                Location got = pig.getLocation();
                return new Object[]{got.getX(), got.getYaw(), got.getPitch(), got.getWorld(), pig.getWorld(), pig.getType(), pig.getName(),
                        ((LivingEntity) pig).getHealth(), ((LivingEntity) pig).getMaxHealth()};
            });
            assertEquals(at.x() + 6.5, (double) seen[0], 1e-6);
            assertEquals(45f, (float) seen[1], 1e-3f);
            assertEquals(7f, (float) seen[2], 1e-3f);
            assertSame(world, seen[3]);
            assertSame(world, seen[4]);
            assertSame(EntityType.PIG, seen[5]);
            assertEquals("Pig", seen[6]);
            assertEquals(10.0, (double) seen[7], 1e-6);
            assertEquals(10.0, (double) seen[8], 1e-6);
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void customNameWinsAndClearsLive() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 2.5), EntityType.PIG));
        try {
            String[] names = owner(() -> {
                String d = pig.getName();
                pig.setCustomName("Bacon");
                String c = pig.getName();
                pig.setCustomName(null);
                return new String[]{d, c, pig.getName(), pig.getCustomName()};
            });
            assertEquals("Pig", names[0]);
            assertEquals("Bacon", names[1]);
            assertEquals("Pig", names[2]);
            assertEquals(null, names[3]);
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void removedMobReadsFallBackToSnapshot() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 4.5), EntityType.PIG));
        Object[] seen = owner(() -> {
            pig.remove();
            return new Object[]{pig.getLocation(), pig.getWorld(), ((LivingEntity) pig).getHealth(), pig.getName(), pig.getType()};
        });
        assertEquals(at.x() + 3.5, ((Location) seen[0]).getX(), 1e-6, "사라진 몹은 핸들 시점 위치");
        assertSame(world, seen[1]);
        assertEquals(10.0, (double) seen[2], 1e-6);
        assertEquals("Pig", seen[3]);
        assertSame(EntityType.PIG, seen[4]);
    }

    /** 소유 스레드가 아니어도(핸들 시점 값 경로) 같은 값. */
    @Test
    void offOwnerReadsMatch() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 6.5), EntityType.PIG));
        try {
            assertEquals(at.x() + 3.5, pig.getLocation().getX(), 1e-6);
            assertSame(world, pig.getWorld());
            assertSame(EntityType.PIG, pig.getType());
            assertEquals("Pig", pig.getName());
            assertEquals(10.0, ((LivingEntity) pig).getHealth(), 1e-6);
            assertNotNull(pig.getLocation().getWorld());
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    /** getType·getName(기본 이름)은 두 번째 호출부터 할당이 없다(스레드별 할당량으로 잰다). */
    @Test
    void typeAndDefaultNameAllocateNothingAfterFirstCall() {
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemorySupported()) return;
        CraftEntity e = fake("minecraft:iron_golem");
        e.getType();
        e.getName();
        for (int i = 0; i < 20_000; i++) { e.getType(); e.getName(); }   // 예열
        long id = Thread.currentThread().threadId();
        long before = mx.getThreadAllocatedBytes(id);
        EntityType last = null;
        for (int i = 0; i < 100_000; i++) { last = e.getType(); e.getName(); }
        long bytes = mx.getThreadAllocatedBytes(id) - before;
        assertSame(EntityType.IRON_GOLEM, last);
        assertTrue(bytes < 100_000, "10만 번 호출이 " + bytes + " 바이트를 할당했다(옛 구현은 호출당 100바이트 넘게)");
    }
}
