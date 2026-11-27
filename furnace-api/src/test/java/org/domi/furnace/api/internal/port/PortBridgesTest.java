package org.domi.furnace.api.internal.port;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 다리 찾기: 그 인터페이스의 로더로 하나만, 없거나 둘이면 그 자리에서 IllegalStateException. */
class PortBridgesTest {

    public interface One { int v(); }

    public interface None {}

    public interface Two {}

    public static final class OneImpl implements One { public int v() { return 7; } }

    public static final class TwoA implements Two {}

    public static final class TwoB implements Two {}

    @Test
    void findsTheSingleImplementationOnce() {
        One a = PortBridges.get(One.class);
        assertTrue(a instanceof OneImpl);
        assertSame(a, PortBridges.get(One.class));
    }

    @Test
    void missingImplementationIsALoudError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PortBridges.get(None.class));
        assertTrue(e.getMessage().contains("without the Furnace server"), e.getMessage());
    }

    @Test
    void twoImplementationsAreAnError() {
        assertThrows(IllegalStateException.class, () -> PortBridges.get(Two.class));
    }

    @Test
    void usesTheInterfaceLoaderNotTheContextLoader() {
        Thread t = Thread.currentThread();
        ClassLoader before = t.getContextClassLoader();
        t.setContextClassLoader(new ClassLoader(null) {});   // 아무것도 못 보는 문맥 로더 — 쓰면 못 찾는다
        try {
            assertTrue(PortBridges.load(One.class, One.class.getClassLoader()) instanceof OneImpl);
        } finally {
            t.setContextClassLoader(before);
        }
    }
}
