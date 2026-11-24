package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        int[] released = {0};
        SynchedData.Watcher w = new SynchedData.Watcher() {
            @Override public void accept(int i) { published.add(i); }
            @Override public void release() { released[0]++; }
        };
        assertSame(w, d.watch(() -> w));
        assertSame(w, d.watch(() -> new SynchedData.Watcher() {
            @Override public void accept(int i) { }
            @Override public void release() { }
        }), "두 번째 보기는 처음 것을 준다");

        assertFalse(d.set(1, 5, false), "같은 값: 훅도 갱신도 없고 거짓");
        assertTrue(d.set(1, 6, false));
        assertTrue(d.set(0, (byte) 1, true));
        d.setQuiet(1, 7);
        assertEquals(List.of(1, 0, 1), published);
        assertEquals(List.of(0, 1, 0), hooks, "훅은 바닐라대로(값 → 훅 → 더러움) 그대로");

        // 엔티티가 빠지면(setRemoved) 갱신을 멈추고 갱신기가 쥔 것을 놓는다
        d.unwatch();
        assertEquals(1, released[0]);
        d.set(1, 8);
        assertEquals(List.of(1, 0, 1), published, "빠진 뒤로는 사본 갱신 없음");
    }
}
