package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T-204 1단계: {@code LevelChunk#getFullStatus}·{@code ChunkHolder#getFullStatus} = 바닐라 청크 홀더 단계(티켓 레벨 ≤31 ENTITY_TICKING, 32 BLOCK_TICKING,
 * 33 FULL, 그 밖 INACCESSIBLE — 플레이어 시야·/forceload·포털·플러그인·스폰 티켓, 설계 docs/feature-parity/compat-nms-t204-side-effects-design-2026-11-16.md 4장). FAWE NativeAccess 의
 * {@code isChunkTicking}(= {@code isOrAfter(BLOCK_TICKING)})이 이 값으로 블록 변경을 보낼지 고른다.
 */
class NmsFullChunkStatusTest {

    private static final int VIEW = 3;
    private static FurnaceTestServer server;
    private static ServerLevel level;
    private static int pcx, pcz;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().failFast(false).viewDistance(VIEW).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        var tp = server.join("Status");
        server.tick(3);
        var loc = tp.player().location();
        pcx = (int) Math.floor(loc.x()) >> 4;
        pcz = (int) Math.floor(loc.z()) >> 4;
        level = ((CraftWorld) org.domi.furnace.compat.paper.Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    static void stop() { server.close(); }

    private static FullChunkStatus statusOnOwner(int cx, int cz) {
        server.loadChunk(cx, cz);
        return server.onRegion(new BlockPos(cx << 4, 64, cz << 4), () -> {
            LevelChunk c = level.getChunk(cx, cz);
            FullChunkStatus s = c.getFullStatus();
            assertEquals(s, level.getChunkSource().chunkMap.getVisibleChunkIfPresent(net.minecraft.world.level.ChunkPos.asLong(cx, cz)).getFullStatus(),
                    "ChunkHolder#getFullStatus 도 같은 값");
            return s;
        });
    }

    /** 플레이어 시야 티켓(시야 안 모든 청크에 레벨 31): 시야 안 ENTITY_TICKING, +1 BLOCK_TICKING, +2 FULL, 그 밖 INACCESSIBLE(바닐라 — Paper 는 시뮬레이션 거리 기준). */
    @Test
    void playerTicketLevels() {
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(pcx, pcz));
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(pcx + VIEW, pcz));
        assertEquals(FullChunkStatus.BLOCK_TICKING, statusOnOwner(pcx + VIEW + 1, pcz));
        assertEquals(FullChunkStatus.FULL, statusOnOwner(pcx, pcz - VIEW - 2));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(pcx - VIEW - 3, pcz));
        assertTrue(FullChunkStatus.BLOCK_TICKING.isOrAfter(FullChunkStatus.FULL));
        assertTrue(FullChunkStatus.BLOCK_TICKING.isOrAfter(FullChunkStatus.BLOCK_TICKING));
        assertTrue(!FullChunkStatus.FULL.isOrAfter(FullChunkStatus.BLOCK_TICKING));
    }

    /** /forceload(레벨 31): 그 청크 ENTITY_TICKING, 둘레 1 BLOCK_TICKING, 둘레 2 FULL, 그 밖 INACCESSIBLE. */
    @Test
    void forcedChunkLevels() {
        int fx = 100, fz = 100;
        server.execute("forceload add " + (fx << 4) + " " + (fz << 4));
        server.tick(2);
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(fx, fz));
        assertEquals(FullChunkStatus.BLOCK_TICKING, statusOnOwner(fx + 1, fz + 1));
        assertEquals(FullChunkStatus.FULL, statusOnOwner(fx - 2, fz));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(fx + 3, fz));
        server.execute("forceload remove " + (fx << 4) + " " + (fz << 4));
        server.tick(2);
    }

    /** 포털 티켓(addTicket 거리 3 = 가운데 레벨 30): 거리 ≤1 ENTITY_TICKING, 2 BLOCK_TICKING, 3 FULL, 4 INACCESSIBLE. */
    @Test
    void portalTicketLevels() {
        int fx = 60, fz = -60;
        server.internals().world().addTicket(fx, fz, 3, 6000);
        server.tick(2);
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(fx, fz));
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(fx + 1, fz - 1));
        assertEquals(FullChunkStatus.BLOCK_TICKING, statusOnOwner(fx - 2, fz));
        assertEquals(FullChunkStatus.FULL, statusOnOwner(fx + 3, fz + 3));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(fx, fz + 4));
    }

    /** 플러그인 티켓: 틱 없는 것은 그 청크만 FULL, 틱 있는 것(레벨 31)은 가운데 ENTITY_TICKING·둘레 1 BLOCK_TICKING·둘레 2 FULL. */
    @Test
    void pluginTicketLevels() {
        var w = server.internals().world();
        int hx = -60, hz = -60;
        server.await(w.addPluginTicket(hx, hz, hx, hz, false));
        server.tick(2);
        assertEquals(FullChunkStatus.FULL, statusOnOwner(hx, hz));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(hx + 1, hz));
        int tx = -60, tz = 60;
        server.await(w.addPluginTicket(tx, tz, tx, tz, true));
        server.tick(2);
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(tx, tz));
        assertEquals(FullChunkStatus.BLOCK_TICKING, statusOnOwner(tx - 1, tz + 1));
        assertEquals(FullChunkStatus.FULL, statusOnOwner(tx, tz - 2));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(tx + 3, tz));
        w.removePluginTicket(hx, hz, hx, hz, false);
        w.removePluginTicket(tx, tz, tx, tz, true);
        server.tick(2);
    }

    /** 스폰 청크(START, 게임 규칙 spawnChunkRadius 2 → 거리 3): 거리 ≤1 ENTITY_TICKING, 2 BLOCK_TICKING, 3 FULL, 4 INACCESSIBLE. 플레이어 시야 밖으로 스폰을 옮겨 본다. */
    @Test
    void spawnChunkLevels() {
        var w = server.internals().world();
        int sx = 200, sz = 200;
        server.execute("setworldspawn " + (sx << 4) + " 64 " + (sz << 4));
        server.tick(2);
        assertEquals(sx, Math.floorDiv(w.defaultSpawn().x(), 16), "스폰이 옮겨졌다");
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(sx, sz));
        assertEquals(FullChunkStatus.ENTITY_TICKING, statusOnOwner(sx - 1, sz + 1));
        assertEquals(FullChunkStatus.BLOCK_TICKING, statusOnOwner(sx + 2, sz));
        assertEquals(FullChunkStatus.FULL, statusOnOwner(sx, sz - 3));
        assertEquals(FullChunkStatus.INACCESSIBLE, statusOnOwner(sx + 4, sz));
    }

    /** 틱 스레드가 아닌 스레드(FAWE 일꾼)는 주인을 기다려 같은 값, 다른 region 스레드는 교착을 피해 ThreadOwnershipException. */
    @Test
    void otherThreads() throws Exception {
        int fx = -100, fz = 40;
        server.execute("forceload add " + (fx << 4) + " " + (fz << 4));
        server.tick(3);
        var holder = new FullChunkStatus[1];
        var err = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                LevelChunk c = (LevelChunk) level.getChunkSource().getChunk(fx, fz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
                holder[0] = c.getFullStatus();
            } catch (Throwable e) {
                err[0] = e;
            }
        }, "worker");
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) {
            server.tick();
            t.join(20);
        }
        t.join(5_000);
        if (err[0] != null) throw new AssertionError(err[0]);
        assertEquals(FullChunkStatus.ENTITY_TICKING, holder[0]);
        LevelChunk far = server.onRegion(new BlockPos(fx << 4, 64, fz << 4), () -> level.getChunk(fx, fz));
        assertThrows(ThreadOwnershipException.class,
                () -> server.onRegion(new BlockPos(pcx << 4, 64, pcz << 4), () -> far.getFullStatus()), "다른 region 스레드");
        server.execute("forceload remove " + (fx << 4) + " " + (fz << 4));
        server.tick(2);
    }
}
