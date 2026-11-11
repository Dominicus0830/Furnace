// A03-03 검토 반영: /tick step 중 병합 미룸 판정(Regionizer.stepCompatible). 미룸은 "같은 박자의 한 걸음 차이"에 한 번만 걸고,
// 양보한 region 이 끼면 미루지 않는다(양보한 쪽은 다시 돌지 않아 영영 같아지지 않는다 — 정지가 풀릴 때까지 멈춤).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StepCompatibleTest {

    static final int G = 7, REQ = 10;

    /** 둘 다 이번 세대를 보고 참여한 region: 남은 걸음 r, o. */
    private static boolean joined(int r, int o) {
        return Regionizer.stepCompatible(true, G, REQ, G, true, r, false, G, true, o, false);
    }

    @Test
    void sameStepsMergeAndOneBehindInPhaseDefersOnce() {
        assertTrue(joined(5, 5));
        assertFalse(joined(4, 5), "방금 돈 쪽이 하나 적다 — 상대가 이번 주기 틱을 돌면 같아진다");
        assertTrue(joined(4, 6), "두 걸음 이상 차이는 미뤄도 step 끝까지 안 같아진다 — 곧바로 합친다");
        assertTrue(joined(5, 4), "검사하는 쪽이 더 남으면 상대가 한 주기 앞섰다 — 둘 다 걸음을 쓰는 동안 차이가 줄지 않으므로 미루지 않는다");
        assertTrue(joined(0, 0));
        assertFalse(joined(0, 1));
    }

    @Test
    void notFrozenOrNonParticipantNeverDefers() {
        assertTrue(Regionizer.stepCompatible(false, G, REQ, G, true, 4, false, G, true, 5, false), "정지가 아니면 걸음을 쓰지 않는다");
        assertTrue(Regionizer.stepCompatible(true, G, REQ, G, false, 0, false, G, true, 1, false), "step 도중 생긴 region(참여 안 함)");
        assertTrue(Regionizer.stepCompatible(true, G, REQ, G, true, 1, false, G, false, 0, false));
    }

    @Test
    void unseenGenerationCountsAsAllRequestedSteps() {
        // r 은 이번 세대에서 한 걸음 썼고(REQ−1), o 는 아직 세대를 못 봤다(곧 틱을 돌아 REQ−1 이 된다)
        assertFalse(Regionizer.stepCompatible(true, G, REQ, G, true, REQ - 1, false, G - 1, false, 0, false));
        assertTrue(Regionizer.stepCompatible(true, G, REQ, G - 1, false, 3, false, G - 1, true, 0, false), "둘 다 못 봤으면 둘 다 REQ");
    }

    @Test
    void yieldedRegionNeverDefers() {
        // 틱 중인 상대의 낡은 요약으로 호환이라 보고 양보했는데, 상대 틱이 걸음을 써 하나 적어진 경우 — 미루면 양보한 쪽이 영영 멈춘다
        assertTrue(Regionizer.stepCompatible(true, G, REQ, G, true, 4, false, G, true, 5, true));
        assertTrue(Regionizer.stepCompatible(true, G, REQ, G, true, 4, true, G, true, 5, false));
    }
}
