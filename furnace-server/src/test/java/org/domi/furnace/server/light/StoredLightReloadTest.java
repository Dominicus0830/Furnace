// T-012: 디스크 빛을 읽을 때의 두 안전장치.
//  1) 이웃 빛만 바뀐 청크도 저장 대상(dirty) — 바닐라 ChunkHolder.sectionLightChanged → setUnsaved.
//  2) 내려간 청크로 막힌 감소는 그 청크(와 그 청크를 거쳐 빛이 닿았을 수 있는 대각 이웃)를 표시 → 다시 올릴 때 저장 빛 대신 새로 계산.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class StoredLightReloadTest {

    /** 슈퍼플랫(풀 위는 모두 공기) — 빛 번짐을 손으로 셀 수 있다. */
    static class FlatLevel extends LightSceneIdentityTest.SceneLevel {
        FlatLevel() { super(0); }

        @Override ChunkColumn terrain(int cx, int cz) { return flat.generate(cx, cz); }
    }

    private static final int GLOW = LightSceneIdentityTest.REG.defaultStateId("minecraft:glowstone");
    private static final int AIR = LightSceneIdentityTest.REG.defaultStateId("minecraft:air");

    /** Region.tick 의 빛 자리(끄면 그 자리 계산, 빛 작업 모드면 묶음을 넘기고 기다려 게시) → 방송. */
    private static void settle(FlatLevel lv) {
        lv.lightTickEnd();
        lv.awaitLightIdle();
        lv.broadcastChanges();
    }

    private static FlatLevel loaded(int minX, int maxX, int minZ, int maxZ) { return loaded(minX, maxX, minZ, maxZ, null); }

    private static FlatLevel loaded(int minX, int maxX, int minZ, int maxZ, java.util.concurrent.Executor lightExec) {
        FlatLevel lv = new FlatLevel();
        lv.lightExec = lightExec;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) lv.load(x, z);
        settle(lv);
        lv.saveAll();
        return lv;
    }

    private static ChunkColumn col(FlatLevel lv, int x, int z) { return lv.loaded.get(FlatLevel.key(x, z)); }

    private static ChunkColumn read(FlatLevel lv, int x, int z) {
        try {
            return lv.codec.fromNbt(NbtIo.read(lv.disk.get(FlatLevel.key(x, z))));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void neighbourLightChange_marksChunkDirty() { neighbourLightChange_marksChunkDirty(null); }

    /** 빛 작업 모드(T-064): 빛 스레드가 바꾼 빛도 게시 때 저장 대상으로. */
    @Test
    void neighbourLightChange_marksChunkDirty_asyncLight() { withLightThread(this::neighbourLightChange_marksChunkDirty); }

    private void neighbourLightChange_marksChunkDirty(java.util.concurrent.Executor exec) {
        FlatLevel lv = loaded(-1, 2, -1, 1, exec);
        assertFalse(col(lv, 1, 0).dirty());
        LightSceneIdentityTest.put(lv, 15, 0, 8, GLOW);   // (0,0) 동쪽 끝 → (1,0) 로 번진다
        settle(lv);
        assertEquals(14, col(lv, 1, 0).light().blockAt(16, 0, 8));
        assertTrue(col(lv, 1, 0).dirty(), "블록은 그대로여도 빛이 바뀐 청크는 저장 대상");
        assertFalse(col(lv, -1, 0).dirty(), "빛이 닿지 않은 청크는 그대로");
    }

    @Test
    void decreaseIntoUnloadedChunk_marksItAndNearDiagonals_thenReloadRecomputes() throws Exception {
        decreaseIntoUnloadedChunk(null);
    }

    /** 빛 작업 모드(T-064): 빛 스레드에서 막힌 감소도 게시 때 표시. */
    @Test
    void decreaseIntoUnloadedChunk_asyncLight() { withLightThread(this::decreaseIntoUnloadedChunk); }

    private static void withLightThread(java.util.function.Consumer<java.util.concurrent.Executor> body) {
        var exec = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            body.accept(exec);
        } finally {
            exec.shutdownNow();
        }
    }

    private void decreaseIntoUnloadedChunk(java.util.concurrent.Executor exec) {
        FlatLevel lv = loaded(-1, 2, -1, 1, exec);
        LightSceneIdentityTest.put(lv, 15, 0, 8, GLOW);
        settle(lv);
        lv.saveAll();                                     // (1,0)·(1,1) 은 번진 빛을 담아 저장
        assertEquals(14, read(lv, 1, 0).light().blockAt(16, 0, 8));
        lv.unload(1, 0);
        lv.unload(1, 1);
        lv.unload(2, 0);
        lv.unload(1, -1);
        LightSceneIdentityTest.put(lv, 15, 0, 8, AIR);    // 광원 치움 → 감소가 내려간 (1,0) 에 막힌다
        settle(lv);
        var stale = lv.stale();
        assertTrue(stale.contains(1, 0), "감소가 막힌 청크");
        assertTrue(stale.contains(1, 1), "그 청크를 거쳐 빛이 닿았을 수 있는 대각(거리 ≤ 14)");
        assertTrue(stale.contains(1, -1));
        assertFalse(stale.contains(2, 0), "15 칸 넘게 떨어진 청크는 표시 안 함");

        lv.load(1, 0);
        settle(lv);
        assertEquals(0, col(lv, 1, 0).light().blockAt(16, 0, 8), "저장 빛(14)이 아니라 새로 센 빛");
        assertTrue(col(lv, 1, 0).dirty(), "고친 빛을 다시 저장한다");
        assertTrue(stale.contains(1, 0), "고친 빛이 디스크에 써지기 전에는 표시를 지우지 않는다(그 전에 죽어도 남게)");
        lv.saveAll();
        assertFalse(stale.contains(1, 0), "써진 뒤 지운다");
        assertTrue(stale.contains(1, 1), "아직 안 올린 청크의 표시는 그대로");
    }

    @Test
    void syncLoadPath_withoutGenPoolCheck_alsoRecomputesStaleChunk() throws Exception {
        FlatLevel lv = loaded(-1, 2, -1, 1);
        lv.genPoolStaleCheck = false;   // 생성 풀을 안 거친 동기 올리기: lightNewChunk 가 저장 빛을 버리고 다시 계산
        LightSceneIdentityTest.put(lv, 15, 0, 8, GLOW);
        settle(lv);
        lv.saveAll();
        lv.unload(1, 0);
        LightSceneIdentityTest.put(lv, 15, 0, 8, AIR);
        settle(lv);
        assertTrue(lv.stale().contains(1, 0));
        lv.load(1, 0);
        settle(lv);
        assertEquals(0, col(lv, 1, 0).light().blockAt(16, 0, 8));
        assertTrue(col(lv, 1, 0).dirty());
    }

    @Test
    void remarkWhileLoaded_survivesTheHealingSave() {
        FlatLevel lv = loaded(-1, 2, -1, 1);
        LightSceneIdentityTest.put(lv, 15, 0, 8, GLOW);
        settle(lv);
        lv.saveAll();
        lv.unload(1, 0);
        LightSceneIdentityTest.put(lv, 15, 0, 8, AIR);
        settle(lv);
        lv.load(1, 0);                                   // 다시 계산 — 지울 표시 번호를 잡아 둠
        settle(lv);
        lv.unload(2, 0);
        LightSceneIdentityTest.put(lv, 30, 0, 8, GLOW);  // (1,0) 안 광원 → (2,0) 쪽으로 막히고, 치우면 (1,0) 도 다시 표시(올라와 있는 이웃)
        settle(lv);
        LightSceneIdentityTest.put(lv, 30, 0, 8, AIR);
        settle(lv);
        lv.saveAll();
        assertTrue(lv.stale().contains(1, 0), "다시 계산한 뒤 새로 붙은 표시는 그 저장으로 지우지 않는다");
    }

    @Test
    void lightThatOnlyReachedADiagonalChunk_throughUnloadedSides_isMarkedStale() throws Exception {
        FlatLevel lv = loaded(-1, 2, -1, 2);
        LightSceneIdentityTest.put(lv, 15, 0, 13, GLOW);   // (0,0) 남동 모서리 근처 → (1,0)·(0,1) 을 거쳐 대각 (1,1) 까지
        settle(lv);
        lv.saveAll();
        assertEquals(11, lv.codec.fromNbt(NbtIo.read(lv.disk.get(FlatLevel.key(1, 1)))).light().blockAt(16, 0, 16));
        lv.unload(1, 0);
        lv.unload(0, 1);
        lv.unload(1, 1);   // (0,0) 칸과 면을 맞대지 않는다 — 감소가 직접 닿지 못하는 대각
        LightSceneIdentityTest.put(lv, 15, 0, 13, AIR);
        settle(lv);
        assertTrue(lv.stale().contains(1, 0) && lv.stale().contains(0, 1));
        assertTrue(lv.stale().contains(1, 1), "막힌 감소가 옆 청크를 거쳐 갔을 대각 청크도 표시");
        lv.load(1, 1);
        settle(lv);
        assertEquals(0, col(lv, 1, 1).light().blockAt(16, 0, 16), "대각 청크의 지운 빛이 되살아나지 않는다");
    }

    @Test
    void cleanStoredReload_keepsStoredLight_withoutDirtying() throws Exception {
        FlatLevel lv = loaded(-1, 1, -1, 1);
        LightSceneIdentityTest.put(lv, 15, 0, 8, GLOW);
        settle(lv);
        lv.saveAll();
        lv.unload(1, 0);
        lv.load(1, 0);
        settle(lv);
        ChunkColumn c = col(lv, 1, 0);
        assertEquals(14, c.light().blockAt(16, 0, 8));
        assertFalse(c.dirty(), "저장 빛 그대로면 다시 저장할 것이 없다");
        assertEquals(0, lv.stale().size());
    }
}
