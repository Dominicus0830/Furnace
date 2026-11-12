// 게임룰 spectatorsGenerateChunks: 꺼져 있으면 관전자의 시야가 청크를 새로 만들게 하지 않는다(바닐라 ChunkMap.skipPlayer) — 이미 로드된 청크만 받는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class SpectatorsGenerateChunksTest {

    private static final int LEVEL_CHUNK = 0x27;

    private record Rig(FurnaceSchedulerImpl sched, World w, EmbeddedChannel ch, Player p, java.util.concurrent.ExecutorService io) {
        void close() { io.shutdown(); sched.close(); }
    }

    private static Rig rig(GameMode mode, boolean spectatorsGenerate) { return rig(mode, spectatorsGenerate, false); }

    /** auto = chunks-per-tick=auto(서버 기본값, 준비 안 된 청크는 미리 불러오기를 건다). */
    private static Rig rig(GameMode mode, boolean spectatorsGenerate, boolean auto) {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        if (auto) w.configureStreaming(w.spawnChunkRadius(), org.domi.furnace.server.config.Config.CHUNKS_PER_TICK_AUTO);
        w.levelServices(new LevelServices.Delegating(w.levelServices()) {
            @Override public boolean gameRule(String name) {
                return name.equals("spectatorsGenerateChunks") ? spectatorsGenerate : super.gameRule(name);
            }
        });
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.vitals().loadGameTypes(null, mode, true);
        p.applyMove(8, -60, 8, 0, 0, true);
        return new Rig(sched, w, ch, p, io);
    }

    private static int tickAndCountChunks(Rig r, int ticks) throws Exception {
        RegionPos rp = new RegionPos(0, 0);
        r.sched.region(rp).execute(() -> r.w.regionAt(rp).adopt(r.p)).get();
        int chunks = 0;
        for (int t = 0; t < ticks; t++) {
            r.sched.region(rp).execute(() -> r.w.regionAt(rp).tick()).get();
            for (int i = 0; i < 20; i++) {
                r.ch.runPendingTasks();
                Object o;
                while ((o = r.ch.readOutbound()) != null) {
                    ByteBuf b = (ByteBuf) o;
                    if (Types.readVarInt(b) == LEVEL_CHUNK) chunks++;
                    b.release();
                }
                Thread.sleep(5);
            }
        }
        return chunks;
    }

    @Test
    void spectatorWithRuleOff_generatesNothing() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, false);
        try {
            assertEquals(0, tickAndCountChunks(r, 6), "꺼져 있으면 관전자는 청크를 받지 못한다");
            assertEquals(0, r.w.loadedChunkCount(), "청크가 만들어지지 않는다");
        } finally {
            r.close();
        }
    }

    @Test
    void spectatorWithRuleOff_receivesAlreadyLoadedChunksOnly() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, false);
        try {
            r.w.awaitChunk(new ChunkPos(0, 0));   // 다른 이유로 로드된 청크
            assertEquals(1, tickAndCountChunks(r, 6), "로드된 청크 하나만 받는다");
            assertTrue(r.w.loadedChunkCount() >= 1);
        } finally {
            r.close();
        }
    }

    @Test
    void spectatorWithRuleOn_andSurvivalWithRuleOff_generate() throws Exception {
        Rig on = rig(GameMode.SPECTATOR, true);
        try {
            assertEquals(ChunkTrackingViewTest.vanillaCount(2), tickAndCountChunks(on, 30), "켜져 있으면 관전자도 시야(바닐라 둥근 모양 45칸)를 만든다");
        } finally {
            on.close();
        }
        Rig surv = rig(GameMode.SURVIVAL, false);
        try {
            assertEquals(ChunkTrackingViewTest.vanillaCount(2), tickAndCountChunks(surv, 30), "생존 플레이어는 규칙과 상관없다");
        } finally {
            surv.close();
        }
    }

    // chunks-per-tick=auto(기본값)는 준비 안 된 청크에 미리 불러오기를 건다 — 관전자 규칙이 이 경로에서도 지켜져야 한다
    @Test
    void autoRate_spectatorWithRuleOff_generatesNothing() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, false, true);
        try {
            assertEquals(0, tickAndCountChunks(r, 6), "auto 에서도 꺼져 있으면 관전자는 청크를 받지 못한다");
            assertEquals(0, r.w.loadedChunkCount(), "auto 미리 불러오기가 청크를 만들지 않는다");
        } finally {
            r.close();
        }
    }

    @Test
    void autoRate_spectatorWithRuleOff_receivesAlreadyLoadedChunksOnly() throws Exception {
        Rig r = rig(GameMode.SPECTATOR, false, true);
        try {
            r.w.awaitChunk(new ChunkPos(0, 0));
            assertEquals(1, tickAndCountChunks(r, 6), "auto 에서도 로드된 청크 하나만 받는다");
        } finally {
            r.close();
        }
    }

    @Test
    void autoRate_survival_generates() throws Exception {
        Rig r = rig(GameMode.SURVIVAL, false, true);
        try {
            assertEquals(ChunkTrackingViewTest.vanillaCount(2), tickAndCountChunks(r, 30), "auto 의 생존 플레이어는 시야(바닐라 둥근 모양 45칸)를 받는다");
        } finally {
            r.close();
        }
    }
}
