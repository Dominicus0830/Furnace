// PR #304 검토 재현: 저장 빛을 믿게 되면서 기준 코드는 다시 올릴 때 고쳐지던 빛이 영구히 남는 두 경우.
// 기준 코드(48adaafad = 기준 + 장면 시험)에서는 통과, PR 머리(218bec61b)에서는 실패해야 한다.
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class ReviewPr304ReproTest {

    static class Flat extends LightSceneIdentityTest.SceneLevel {
        Flat() { super(0); }

        @Override ChunkColumn terrain(int cx, int cz) { return flat.generate(cx, cz); }
    }

    static final int GLOW = LightSceneIdentityTest.REG.defaultStateId("minecraft:glowstone");
    static final int AIR = LightSceneIdentityTest.REG.defaultStateId("minecraft:air");

    static void settle(Flat lv) { lv.runLightUpdates(); lv.broadcastChanges(); }

    static Flat loaded(int lo, int hi) {
        Flat lv = new Flat();
        for (int x = lo; x <= hi; x++) for (int z = lo; z <= hi; z++) lv.load(x, z);
        settle(lv);
        lv.saveAll();
        return lv;
    }

    static ChunkColumn col(Flat lv, int x, int z) { return lv.loaded.get(Flat.key(x, z)); }

    /**
     * R1: 막힌 감소가 "올라와 있는" 대각 청크에 남긴 지운 빛. 기준 코드는 그 청크를 다시 올릴 때 새로 계산해 없앴다.
     * PR 은 올라온 이웃을 표시하지 않고(decreaseBlocked 의 col != null 건너뛰기) 저장 빛을 믿어 영구히 남는다.
     */
    @Test
    void loadedDiagonalPhantom_isHealedOnReload() {
        Flat lv = loaded(-1, 2);
        LightSceneIdentityTest.put(lv, 15, 0, 13, GLOW);   // (0,0) 남동 모서리 → (1,0)·(0,1) 을 거쳐 대각 (1,1)
        settle(lv);
        lv.saveAll();
        assertEquals(11, col(lv, 1, 1).light().blockAt(16, 0, 16));
        lv.unload(1, 0);
        lv.unload(0, 1);                                    // (1,1) 은 올라와 있다
        LightSceneIdentityTest.put(lv, 15, 0, 13, AIR);
        settle(lv);
        // 여기까지는 기준·PR 같음: (1,1) 메모리 빛 11 이 남는다(이미 있던 문제, context-notes 5번)
        lv.unload(1, 1);
        lv.load(1, 1);
        settle(lv);
        assertEquals(0, col(lv, 1, 1).light().blockAt(16, 0, 16), "다시 올리면 지운 빛이 사라져야 한다(기준 코드 동작, 바닐라 고정점)");
        lv.load(1, 0);
        lv.load(0, 1);
        settle(lv);
        assertEquals(0, col(lv, 1, 0).light().blockAt(16, 0, 15), "표시된 옆 청크도 되살아난 빛을 다시 받지 않는다");
    }

    /**
     * R2: 블록은 바뀌었는데 빛 검사가 아직 안 돈 채(틱의 플레이어 처리 뒤 — 종료 저장·같은 틱 내리기) 저장된 청크.
     * 기준 코드는 다시 올릴 때 새로 계산해 맞췄다. PR 은 낡은 저장 빛을 믿고, 그 칸을 다시 검사할 일이 없어 영구히 남는다.
     */
    @Test
    void savedWithPendingLightCheck_isCorrectAfterRestart() {
        Flat lv = loaded(-1, 1);
        LightSceneIdentityTest.put(lv, 8, 0, 8, GLOW);
        settle(lv);
        lv.saveAll();
        LightSceneIdentityTest.put(lv, 8, 0, 8, AIR);      // 플레이어 처리에서 치움 — runLightUpdates 는 다음 틱
        lv.saveAll();                                        // 종료 flushAll(빛 갱신을 돌리지 않는다)
        Flat restarted = new Flat();
        restarted.disk.putAll(lv.disk);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) restarted.load(x, z);
        settle(restarted);
        assertEquals(0, col(restarted, 0, 0).light().blockAt(8, 0, 8), "광원을 치운 칸");
        assertEquals(0, col(restarted, 0, 0).light().blockAt(12, 0, 8), "이웃 칸");
    }
}
