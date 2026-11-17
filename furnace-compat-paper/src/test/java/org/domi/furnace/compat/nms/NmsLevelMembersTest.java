package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 2026-11-17 NMS shim 점검: Level 에 더한 바닐라 멤버(random·getRandom·getSeaLevel·dimensionType)가 월드마다 하나씩, 바닐라 값으로 보이는가. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsLevelMembersTest {

    FurnaceTestServer server;
    ServerLevel level;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Levely");
        server.tick(3);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void randomIsOnePerLevel() {
        assertNotNull(level.random);
        assertSame(level.random, level.getRandom());
    }

    @Test
    void seaLevelIs63OnEveryDimensionInVanilla121() {
        assertEquals(63, level.getSeaLevel());
    }

    @Test
    void dimensionTypeIsTheRegistrationValue() {
        assertSame(level.dimensionTypeRegistration(), level.dimensionTypeRegistration());
        assertSame(level.dimensionTypeRegistration().value(), level.dimensionType());
        assertEquals(level.getMinBuildHeight(), level.dimensionType().minY());
    }
}
