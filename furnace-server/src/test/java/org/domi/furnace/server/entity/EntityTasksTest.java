// 플러그인 EntityScheduler 의 서버 대기열: 지연·순서·취소·반복·retire(남은 작업 대신 retired 한 번, 이후 거부)·엔티티 참조 놓기.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityTasksTest {

    @Test
    void delaysRunInOrderAfterTheirTicks() {
        List<String> log = new ArrayList<>();
        var t = new EntityTasks("e");
        t.schedule(() -> log.add("a0"), null, 0, 0);
        t.schedule(() -> log.add("b1"), null, 1, 0);
        t.schedule(() -> log.add("c3"), null, 3, 0);
        t.schedule(() -> log.add("d0"), null, 0, 0);
        assertTrue(EntityTasks.anyPending());
        t.tick();
        assertEquals(List.of("a0", "b1", "d0"), log, "0·1 은 다음 틱, 같은 틱 안에서는 넣은 순서");
        t.tick();
        assertEquals(3, log.size());
        t.tick();
        assertEquals(List.of("a0", "b1", "d0", "c3"), log);
        assertEquals(0, t.pendingCount());
    }

    @Test
    void cancelAndRepeatingAndExceptionsKeepRepeating() {
        int[] runs = {0};
        var t = new EntityTasks("e");
        var cancelled = t.schedule(() -> fail("취소된 작업이 돌았다"), null, 1, 0);
        cancelled.cancel();
        var rep = t.schedule(() -> {
            runs[0]++;
            throw new RuntimeException("작업 고장");
        }, null, 1, 2);
        t.tick();                  // 1회
        t.tick();
        t.tick();                  // 2회(주기 2)
        assertEquals(2, runs[0], "예외가 나도 반복은 이어진다");
        rep.cancel();
        t.tick();
        t.tick();
        assertEquals(2, runs[0]);
        assertEquals(0, t.pendingCount());
    }

    @Test
    void retireRunsRetiredOnceForPendingAndRejectsLater() {
        List<String> log = new ArrayList<>();
        var t = new EntityTasks("entity");
        t.schedule(() -> log.add("task"), () -> log.add("retired-1"), 5, 0);
        var c = t.schedule(() -> log.add("task"), () -> log.add("retired-cancelled"), 5, 0);
        c.cancel();
        t.schedule(() -> log.add("task"), () -> log.add("retired-2"), 1, 3);
        assertEquals("entity", t.owner());
        t.retire();
        t.retire();
        assertEquals(List.of("retired-1", "retired-2"), log, "취소된 것은 retired 도 없다, 두 번째 retire 는 아무것도 안 한다");
        assertNull(t.owner(), "retire 하면 엔티티 참조를 놓는다");
        assertTrue(t.isRetired());
        assertNull(t.schedule(() -> log.add("late"), () -> log.add("late-retired"), 0, 0), "retire 뒤에는 받지 않는다");
        t.tick();
        assertEquals(2, log.size());
    }

    @Test
    void repeatingTaskThatRemovesItsEntityGetsRetired() {
        List<String> log = new ArrayList<>();
        var t = new EntityTasks("e");
        t.schedule(() -> {
            log.add("run");
            t.retire();   // 작업 안에서 엔티티가 사라졌다
        }, () -> log.add("retired"), 1, 1);
        t.tick();
        assertEquals(List.of("run", "retired"), log);
    }
}
