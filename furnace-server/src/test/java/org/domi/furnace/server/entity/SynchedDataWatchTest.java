package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 칸 보기(엔티티 데이터 설계 D5): 보기 전에는 갱신기가 없고, 본 뒤에는 값이 바뀐 칸마다(같은 값 쓰기는 빼고) 한 번씩 불린다. */
class SynchedDataWatchTest {

    @Test
    void publisherSeesEveryChangedSlotAfterWatch() {
        List<Integer> hooks = new ArrayList<>();
        SynchedData d = new SynchedData(hooks::add);
        d.define(0, 0, (byte) 0);
        d.define(1, 1, 5);
        d.set(0, (byte) 1);   // 보기 전: 갱신기 없음

        List<Integer> published = new ArrayList<>();
        java.util.function.IntConsumer w = published::add;
        assertSame(w, d.watch(() -> w));
        assertSame(w, d.watch(() -> i -> { }), "두 번째 보기는 처음 것을 준다");

        d.set(1, 5);          // 같은 값: 훅도 갱신도 없다
        d.set(1, 6);
        d.set(0, (byte) 1, true);
        d.setQuiet(1, 7);
        assertEquals(List.of(1, 0, 1), published);
        assertEquals(List.of(0, 1, 0), hooks, "훅은 바닐라대로(값 → 훅 → 더러움) 그대로");
    }
}
