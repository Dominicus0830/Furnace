// Ctrl+C 확인 창: 첫 입력은 안내, 제한 시간 안의 두 번째 입력에서 종료.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InterruptConfirmTest {

    @Test
    void needsSecondPressInWindow() {
        AtomicLong now = new AtomicLong();
        var c = new InterruptConfirm(5, now::get);
        assertFalse(c.onInterrupt());
        now.set(4_000_000_000L);
        assertTrue(c.onInterrupt());
        now.set(5_000_000_000L);
        assertFalse(c.onInterrupt(), "종료한 뒤에는 다시 처음부터");
    }

    @Test
    void windowExpires() {
        AtomicLong now = new AtomicLong();
        var c = new InterruptConfirm(5, now::get);
        assertFalse(c.onInterrupt());
        now.set(6_000_000_000L);
        assertFalse(c.onInterrupt(), "시간이 지나면 이 입력이 새 첫 입력");
        now.set(7_000_000_000L);
        assertTrue(c.onInterrupt());
    }
}
