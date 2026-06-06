// 소유 스레드 가드(Owned)의 접근 허용/차단 동작 테스트
package org.domi.furnace.api.concurrent;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OwnedTest {

    private static ThreadOwner fixedOwner(boolean isOwner) {
        return new ThreadOwner() {
            public boolean isOwnerThread() { return isOwner; }
            public String ownerName() { return "test-owner"; }
        };
    }

    @Test
    void get_returnsValue_whenCalledByOwner() {
        Owned<String> owned = new Owned<>("hello", fixedOwner(true));
        assertEquals("hello", owned.get());
    }

    @Test
    void get_throwsAssertionError_whenCalledByNonOwner() {
        Owned<String> owned = new Owned<>("hello", fixedOwner(false));
        assertThrows(AssertionError.class, owned::get);
    }

    @Test
    void owner_isExposed() {
        ThreadOwner owner = fixedOwner(true);
        Owned<String> owned = new Owned<>("hello", owner);
        assertSame(owner, owned.owner());
    }
}
