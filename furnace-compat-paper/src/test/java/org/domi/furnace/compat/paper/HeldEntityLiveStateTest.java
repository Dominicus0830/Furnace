package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A16-01: 몹(플레이어가 아닌 엔티티)의 위치·체력·생사는 Furnace 핸들 스냅샷이라, 플러그인이 쥐고 있는 Bukkit 객체가 낡은 값을 돌려 줬다.
 * CraftBukkit 의 NMS 엔티티는 살아 있는 객체라 {@code setHealth} 직후 {@code getHealth}, {@code teleport} 직후 {@code getLocation},
 * {@code remove} 직후 {@code isValid} 가 곧바로 바뀐 값을 준다. 소유 스레드에서는 지금 상태를 읽고, 그 밖의 스레드에서는 핸들 시점 값을 준다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HeldEntityLiveStateTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Lia");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    Entity spawnPig(World world, double dx, double dz) {
        return owner(() -> world.spawnEntity(new Location(world, at.x() + dx, at.y() + 1, at.z() + dz), EntityType.PIG));
    }

    @Test
    void setHealthIsVisibleToGetHealthRightAway() {
        World world = player.getWorld();
        Entity pig = spawnPig(world, 3.5, 0.5);
        try {
            double[] seen = owner(() -> {
                LivingEntity le = (LivingEntity) pig;
                double before = le.getHealth();
                le.setHealth(3);
                return new double[]{before, le.getHealth(), le.getMaxHealth()};
            });
            assertEquals(10.0, seen[0], 1e-6, "돼지 기본 체력");
            assertEquals(3.0, seen[1], 1e-6, "setHealth 직후 getHealth 가 낡은 값을 줬다");
            assertEquals(10.0, seen[2], 1e-6);
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void teleportIsVisibleToGetLocationRightAway() {
        World world = player.getWorld();
        Entity pig = spawnPig(world, 3.5, 0.5);
        try {
            Location to = new Location(world, at.x() + 6.5, at.y() + 1, at.z() + 0.5);
            double x = owner(() -> {
                assertTrue(pig.teleport(to));
                return pig.getLocation().getX();
            });
            assertEquals(to.getX(), x, 1e-6, "teleport 직후 getLocation 이 낡은 위치를 줬다");
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    /** 지금 위치를 읽어도 Paper 처럼 yaw 는 머리 방향이다(핸들 시점 경로와 같은 규칙) — 베이스 병합 때 두 경로가 합쳐진 자리. */
    @Test
    void liveLocationYawIsHeadYaw() {
        World world = player.getWorld();
        Entity pig = spawnPig(world, 3.5, 0.5);
        try {
            float[] yaw = owner(() -> {
                assertTrue(pig.teleport(new Location(world, at.x() + 6.5, at.y() + 1, at.z() + 0.5, 90f, 10f)));
                return new float[]{pig.getLocation().getYaw(), pig.getLocation().getPitch()};
            });
            assertEquals(90f, yaw[0], 1e-3f, "지금 위치의 yaw 가 머리 방향이 아니다");
            assertEquals(10f, yaw[1], 1e-3f);
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void removeIsVisibleToIsValidRightAway() {
        World world = player.getWorld();
        Entity pig = spawnPig(world, 2.5, 2.5);
        boolean[] seen = owner(() -> {
            boolean before = pig.isValid();
            pig.remove();
            return new boolean[]{before, pig.isValid(), pig.isDead()};
        });
        assertTrue(seen[0]);
        assertFalse(seen[1], "remove 직후 isValid 가 계속 true 였다");
        assertTrue(seen[2], "remove 직후 isDead 가 계속 false 였다");
    }

    /** 소유 스레드가 아니면 예외 없이 핸들 시점 값을 준다(Furnace 규칙). */
    @Test
    void offOwnerThreadFallsBackToSnapshot() {
        World world = player.getWorld();
        Entity pig = spawnPig(world, 3.5, 0.5);
        try {
            assertEquals(10.0, ((LivingEntity) pig).getHealth(), 1e-6);
            assertEquals(at.x() + 3.5, pig.getLocation().getX(), 1e-6);
            assertTrue(pig.isValid());
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }
}
