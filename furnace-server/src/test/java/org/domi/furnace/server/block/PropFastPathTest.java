// X1: Prop 의 불리언·정수 빠른 길(파생 표)과 B.id 기억이 예전 문자열 경로와 모든 상태에서 같은 답을 내는지.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropFastPathTest {

    /** 예전 구현(문자열 경로) — Prop 의 공개 API(valueName·with(String))만 쓴다. */
    private static boolean refIs(Prop p, int state) { return "true".equals(p.valueName(state)); }

    private static int refWithBool(Prop p, int state, boolean v) { return p.with(state, v ? "true" : "false"); }

    private static Object refInt(Prop p, int state) {
        String v = p.valueName(state);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return "NFE";
        }
    }

    private static Object fastInt(Prop p, int state) {
        try {
            return p.intValue(state);
        } catch (NumberFormatException e) {
            return "NFE";
        }
    }

    @Test
    void allPropsAllStatesMatchTheStringPath() {
        Blocks bs = Blocks.get();
        List<Prop> all = new ArrayList<>(bs.props.values());
        assertTrue(all.size() > 50);
        long checks = 0;
        for (int st = 0; st < bs.stateCount(); st++) {
            final int state = st;
            int block = bs.blockOf(state);
            // 그 블록의 속성 전부 + 없는 속성 몇 개(없을 때의 답도 같아야 한다)
            List<Prop> ps = new ArrayList<>(List.of(bs.propsOf(block)));
            for (int k = 0; k < 3; k++) ps.add(all.get((state * 7 + k * 13) % all.size()));
            for (Prop prop : ps) {
                final Prop p = prop;
                assertEquals(refIs(p, state), p.is(state), () -> p + " is " + state);
                assertEquals(refWithBool(p, state, true), p.with(state, true), () -> p + " withTrue " + state);
                assertEquals(refWithBool(p, state, false), p.with(state, false), () -> p + " withFalse " + state);
                assertEquals(refInt(p, state), fastInt(p, state), () -> p + " intValue " + state);
                for (int v = -2; v <= 17; v++) {
                    final int vv = v;
                    assertEquals(p.with(state, Integer.toString(v)), p.withInt(state, v), () -> p + " withInt " + state + " " + vv);
                    checks++;
                }
                checks += 4;
            }
        }
        assertTrue(checks > 1_000_000, "검사 수 " + checks);
    }

    @Test
    void intPropsRoundTrip() {
        Prop power = B.prop("power");
        int wire = B.defaultState("redstone_wire");
        for (int v = 0; v <= 15; v++) assertEquals(v, power.intValue(power.withInt(wire, v)));
        assertEquals(wire, power.withInt(wire, 16), "범위 밖은 그대로");
        assertEquals(wire, power.withInt(wire, -1));
        Prop lit = B.prop("lit");
        int furnace = B.defaultState("furnace");
        assertFalse(lit.is(furnace));
        assertTrue(lit.is(lit.with(furnace, true)));
    }

    @Test
    void idCacheMatchesLookupForConstantsAndFreshStrings() {
        Blocks bs = Blocks.get();
        for (int b = 0; b < bs.blockCount(); b++) {
            String full = bs.name(b);
            String shortName = full.substring("minecraft:".length());
            assertEquals(b, B.id(full));
            assertEquals(b, B.id(shortName));
            assertEquals(b, B.id(new String(shortName.toCharArray())), "같은 내용의 다른 객체");
            assertEquals(b, B.id(shortName));
        }
        assertEquals(-1, B.id("no_such_block"));
        assertEquals(-1, B.id("no_such_block"));
        assertEquals(-1, B.id("other:thing"));
        // 해시가 같은 칸에 부딪히는 이름이 번갈아 와도 답이 틀리지 않는다
        for (int rep = 0; rep < 3; rep++) for (int b = 0; b < bs.blockCount(); b++) assertEquals(b, B.id(bs.name(b)));
    }
}
