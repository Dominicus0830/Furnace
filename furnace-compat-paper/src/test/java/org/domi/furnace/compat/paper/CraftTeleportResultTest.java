package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/** P4: Bukkit {@code Entity#teleport} 가 늘 true 가 아니라 Paper 처럼 진짜 결과를 돌려준다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftTeleportResultTest {
    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Tele");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    @Test
    void entityTeleportOnTheOwnerThreadReportsTheRealResult() {
        World world = player.getWorld();
        Entity pig = owner(() -> world.spawnEntity(new Location(world, at.x() + 3.5, at.y() + 1, at.z() + 0.5), EntityType.PIG));
        try {
            Location near = new Location(world, at.x() + 5.5, at.y() + 1, at.z() + 0.5);
            assertTrue(owner(() -> pig.teleport(near)), "같은 region 안은 옮기고 true");
            server.tick(2);
            Entity fresh = owner(() -> world.getNearbyEntities(near, 1, 1, 1).stream().filter(e -> e.getUniqueId().equals(pig.getUniqueId())).findFirst().orElseThrow());
            assertEquals(near.getX(), fresh.getLocation().getX(), 1e-6);
            Location far = new Location(world, at.x() + 16 * 40 + 0.5, at.y() + 1, at.z() + 0.5);
            assertFalse(owner(() -> pig.teleport(far)), "다른 region 으로의 이동은 아직 못 옮긴다 — 거짓 true 가 아니라 false");
            server.tick(2);
            Entity still = owner(() -> world.getNearbyEntities(near, 1, 1, 1).stream().filter(e -> e.getUniqueId().equals(pig.getUniqueId())).findFirst().orElseThrow());
            assertEquals(near.getX(), still.getLocation().getX(), 1e-6, "거절하면 제자리");
            assertThrows(IllegalArgumentException.class, () -> pig.teleport(null));
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
        server.tick(2);
        assertFalse(owner(() -> pig.teleport(new Location(world, at.x() + 1.5, at.y() + 1, at.z() + 0.5))), "지워진 엔티티는 false");
    }

    @Test
    void playerTeleportReportsTrueAndSendsThePlayerThere() {
        World world = player.getWorld();
        Location to = new Location(world, at.x() + 2.5, at.y() + 3, at.z() + 2.5, 45f, 10f);
        assertTrue(owner(() -> player.teleport(to)));
        tp.sync();
        server.tick(5);
        tp.sync();
        var now = tp.player().location();
        assertEquals(to.getX(), now.x(), 1e-6);
        assertEquals(to.getZ(), now.z(), 1e-6);
    }
}
