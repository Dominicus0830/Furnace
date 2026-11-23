package org.domi.furnace.compat.vdiff;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.domi.furnace.compat.vdiff.DiffRunner.MemberResult;
import org.domi.furnace.compat.vdiff.DiffRunner.Status;
import org.junit.jupiter.api.Test;

/** 틀 자체 점검: 일부러 틀린 구현은 "다름", 같은 구현은 "같음"이어야 한다(틀이 언제나 같음을 내는 고장을 막는다). */
class HarnessCanaryTest {

    /** 바닐라 Mth.floor 와 다르다 — 음수에서 0 쪽으로 자른다. */
    public static final class Wrong {
        public static int floor(double v) {
            return (int) v;
        }
    }

    /** 바닐라 Mth.floor 와 같은 규칙(자른 뒤 원래 값보다 크면 하나 뺀다 — 아주 큰 음수에서 넘침까지 같다). */
    public static final class Right {
        public static int floor(double v) {
            int t = (int) v;
            return t > v ? t - 1 : t;
        }
    }

    private static MemberResult run(Class<?> shim) throws Exception {
        VanillaRuntime rt = VanillaRuntime.get();
        Class<?> vc = Class.forName("net.minecraft.util.Mth", true, rt.loader());
        Method floor = vc.getMethod("floor", double.class);
        DiffRunner r = new DiffRunner(rt.loader(), HarnessCanaryTest.class.getClassLoader(), 1L);
        try {
            return r.run(vc, shim, floor, 2_000);
        } finally {
            r.close();
        }
    }

    @Test
    void wrongImplementationIsCaught() throws Exception {
        assertEquals(Status.DIFF, run(Wrong.class).status());
    }

    @Test
    void equivalentImplementationPasses() throws Exception {
        MemberResult m = run(Right.class);
        assertEquals(Status.SAME, m.status(), String.valueOf(m));
    }
}
