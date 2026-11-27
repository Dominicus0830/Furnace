package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

/** NMS WorldBorder shim 이 서버 경계의 값을 읽고 바꾸는가(바닐라 WorldBorder 와 같은 값·같은 식). */
class NmsWorldBorderTest {

    @Test
    void readsAndWritesTheServerBorder() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).start()) {
            Bukkit.setServer(CraftServer.INSTANCE);
            var tp = server.join("Border");
            server.tick(3);
            WorldBorder b = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle().getWorldBorder();
            b.setCenter(10.0, -20.0);
            b.setSize(100.0);
            b.setDamagePerBlock(0.5);
            b.setDamageSafeZone(3.0);
            b.setWarningBlocks(7);
            b.setWarningTime(9);
            assertEquals(10.0, b.getCenterX());
            assertEquals(-20.0, b.getCenterZ());
            assertEquals(100.0, b.getSize());
            assertEquals(-40.0, b.getMinX());
            assertEquals(60.0, b.getMaxX());
            assertEquals(-70.0, b.getMinZ());
            assertEquals(30.0, b.getMaxZ());
            assertEquals(0.5, b.getDamagePerBlock());
            assertEquals(3.0, b.getDamageSafeZone());
            assertEquals(7, b.getWarningBlocks());
            assertEquals(9, b.getWarningTime());
            assertEquals(BorderStatus.STATIONARY, b.getStatus());
            // 안쪽 (10, -20): 네 변까지 50 — 가장 가까운 변. 바깥은 음수
            assertEquals(50.0, b.getDistanceToBorder(10.0, -20.0));
            assertEquals(-5.0, b.getDistanceToBorder(65.0, -20.0));
            assertTrue(b.isWithinBounds(59.9, 0.0));
            assertFalse(b.isWithinBounds(60.0, 0.0));
            assertTrue(b.isWithinBounds(62.0, 0.0, 3.0));   // margin 만큼 넓힌 안
            b.lerpSizeBetween(100.0, 200.0, 10_000L);
            assertEquals(BorderStatus.GROWING, b.getStatus());
            assertEquals(200.0, b.getLerpTarget());
            assertTrue(b.getLerpSpeed() > 0.0);
            assertEquals(4259712, b.getStatus().getColor());
            b.setSize(100.0);   // 시험 뒤 정리
        }
    }
}
