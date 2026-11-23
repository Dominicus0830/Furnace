package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * T-260: NMS 밖 껍데기의 클래스 초기화 덫은 플러그인의 {@code Class.forName} 감지에서 "클래스 없음"(ClassNotFoundException)이어야 한다 —
 * 흔한 {@code catch (ClassNotFoundException e) { 대체 길 }} 이 그대로 대체 길로 가게. 다른 초기화 실패는 그대로 둔다.
 */
class ShellInitTrapTest {

    @Test
    void forNameOfAnInitTrappedShellIsClassNotFound() {
        assertTrue(NmsSymbols.RUNTIME.initTrapped("io/papermc/furnacetest/TrapHolder"));
        assertTrue(NmsSymbols.RUNTIME.isShellClass("io/papermc/furnacetest/TrapHolder"));
        // 첫 번째(ExceptionInInitializerError)와 그다음(NoClassDefFoundError) 모두
        assertThrows(ClassNotFoundException.class, () -> CraftNames.forName("io.papermc.furnacetest.TrapHolder", getClass(), "TestPlugin"));
        assertThrows(ClassNotFoundException.class, () -> CraftNames.forName("io.papermc.furnacetest.TrapHolder", getClass(), "TestPlugin"));
        assertThrows(ClassNotFoundException.class,
                () -> CraftNames.forName("io.papermc.furnacetest.TrapHolder", true, getClass().getClassLoader(), "TestPlugin"));
        // 초기화하지 않는 길은 클래스를 돌려준다(instanceof·서명용)
        assertTrue(assertDoesNotThrowForName("io.papermc.furnacetest.TrapHolder"));
    }

    @Test
    void otherInitFailuresAreNotHidden() {
        assertFalse(NmsSymbols.RUNTIME.initTrapped("org/domi/furnace/compat/nms/ShellInitTrapTest$Boom"));
        ExceptionInInitializerError e = assertThrows(ExceptionInInitializerError.class,
                () -> CraftNames.forName("org.domi.furnace.compat.nms.ShellInitTrapTest$Boom", getClass(), "TestPlugin"));
        assertSame(IllegalStateException.class, e.getCause().getClass());
    }

    private boolean assertDoesNotThrowForName(String n) {
        try {
            return CraftNames.forName(n, false, getClass().getClassLoader(), "TestPlugin").getName().equals(n);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static final class Boom {
        static {
            if (true) throw new IllegalStateException("boom");
        }
    }
}
