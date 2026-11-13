// T-032: 이벤트 붙임 칸(EventSlot)·게으른 이벤트 내용(LazyValue)이 옛 구현과 같은 결과를 내는지.
//  - 다리 칸: 옛 synchronizedMap(WeakHashMap) 다리(LegacyBridgeLiveMap)와 무작위 조회 순서에서 "어느 조회가 어느 객체를 받는가"가 같다(IdentityKit 차분).
//  - 동시성: 여러 스레드가 같은 이벤트에 처음 붙이기를 겨뤄도 칸마다 객체 하나만 남고 모두 그 객체를 받는다.
//  - 물리 이벤트: 모든 블록 상태에서 게으른 block()·changedType() = 미리 만든 값.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventSlot;
import org.domi.furnace.api.event.LazyValue;
import org.domi.furnace.api.event.block.BlockPhysicsEvent;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.legacy.plugin.LegacyBridgeLiveMap;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class EventSlotIdentityTest {

    private static final class E extends Event {}

    /** 이벤트 n 개·짝(열쇠) k 개·건너뛰는 (이벤트, 짝) 표·조회 순서. */
    record Ops(int events, int keys, boolean[][] skip, int[] seq) {
        String summary() {
            StringBuilder sb = new StringBuilder("events=" + events + " keys=" + keys + " seq=");
            for (int i = 0; i < seq.length; i++) sb.append(seq[i] / keys).append(':').append(seq[i] % keys).append(' ');
            return sb.toString();
        }
    }

    private static Ops gen(java.util.SplittableRandom r) {
        int events = 1 + r.nextInt(6), keys = 1 + r.nextInt(4);
        boolean[][] skip = new boolean[events][keys];
        for (boolean[] row : skip) for (int k = 0; k < keys; k++) row[k] = r.nextInt(4) == 0;
        int[] seq = new int[1 + r.nextInt(40)];
        for (int i = 0; i < seq.length; i++) seq[i] = r.nextInt(events * keys);
        return new Ops(events, keys, skip, seq);
    }

    /** 조회마다 받은 객체의 "만든 순번"(null 은 -1)과 만들기 호출 수 — 정체 해시는 쓰지 않는다. */
    private interface Lookup { Object bukkitFor(int key, Event e); }

    private static List<Integer> run(Ops in, Lookup lookup, Event[] evs, Map<Object, Integer> madeOrder) {
        List<Integer> out = new ArrayList<>();
        for (int s : in.seq()) {
            Object o = lookup.bukkitFor(s % in.keys(), evs[s / in.keys()]);
            out.add(o == null ? -1 : madeOrder.get(o));
        }
        out.add(madeOrder.size());
        return out;
    }

    private static Event[] events(int n) {
        Event[] evs = new Event[n];
        for (int i = 0; i < n; i++) evs[i] = new E();
        return evs;
    }

    private static List<Integer> legacy(Ops in) {
        Event[] evs = events(in.events());
        Map<Object, Integer> made = new IdentityHashMap<>();
        Map<Event, Integer> idx = new IdentityHashMap<>();
        for (int i = 0; i < evs.length; i++) idx.put(evs[i], i);
        LegacyBridgeLiveMap[] maps = new LegacyBridgeLiveMap[in.keys()];
        java.util.function.Function<java.util.function.Function<Event, Object>, LegacyBridgeLiveMap> legacyMap = LegacyBridgeLiveMap::new;
        for (int k = 0; k < in.keys(); k++) {
            int key = k;
            maps[k] = legacyMap.apply(f -> {
                if (in.skip()[idx.get(f)][key]) return null;
                Object b = new Object();
                made.put(b, made.size());
                return b;
            });
        }
        return run(in, (k, e) -> maps[k].bukkitFor(e), evs, made);
    }

    private static final Object SKIP = new Object();

    /** 새 다리(EventBridge.Mapping#bukkitFor)와 같은 꼴: 짝마다 붙임 칸 하나, 건너뛰면 SKIP 표지. */
    private static List<Integer> slots(Ops in, boolean mutantAlwaysNew) {
        Event[] evs = events(in.events());
        Map<Object, Integer> made = new IdentityHashMap<>();
        Map<Event, Integer> idx = new IdentityHashMap<>();
        for (int i = 0; i < evs.length; i++) idx.put(evs[i], i);
        List<EventSlot<Object>> keys = new ArrayList<>();
        for (int k = 0; k < in.keys(); k++) keys.add(new EventSlot<>("k" + k));
        return run(in, (k, e) -> {
            java.util.function.Function<Event, Object> make = f -> {
                if (in.skip()[idx.get(f)][k]) return SKIP;
                Object b = new Object();
                made.put(b, made.size());
                return b;
            };
            Object o = mutantAlwaysNew ? make.apply(e) : keys.get(k).computeIfAbsent(e, make);
            return o == SKIP ? null : o;
        }, evs, made);
    }

    @Test
    void bridgeSlotsMatchTheLegacyWeakMapBridge() {
        var diff = Differential.<Ops, List<Integer>>of("t032-bridge-slots", EventSlotIdentityTest::gen)
                .seed(0x7032L).iterations(2000).describe(Ops::summary);
        diff.check(EventSlotIdentityTest::legacy, in -> slots(in, false));
        // 변이 점검: 칸을 쓰지 않고 매번 새로 만들면(= 처리기마다 다른 Bukkit 객체) 잡아야 한다
        diff.assertDetects(EventSlotIdentityTest::legacy, in -> slots(in, true));
    }

    @Test
    void slotBasics() {
        EventSlot<String> a = new EventSlot<>("a"), b = new EventSlot<>("b");
        Event e = new E(), other = new E();
        assertNull(a.get(e));
        AtomicInteger calls = new AtomicInteger();
        assertEquals("x", a.computeIfAbsent(e, ev -> { calls.incrementAndGet(); return "x"; }));
        assertEquals("x", a.computeIfAbsent(e, ev -> { calls.incrementAndGet(); return "y"; }));
        assertEquals(1, calls.get(), "붙은 뒤에는 만들기를 부르지 않는다");
        assertNull(b.get(e), "다른 열쇠의 칸과 섞이지 않는다");
        assertNull(a.get(other), "다른 이벤트와 섞이지 않는다");
        assertEquals("z", b.computeIfAbsent(e, ev -> "z"));
        assertEquals("x", a.get(e));
        assertThrows(NullPointerException.class, () -> a.computeIfAbsent(other, ev -> null));
        assertNull(a.get(other), "null 은 붙지 않는다");
        // 만들기 안에서 같은 이벤트에 다른 칸을 붙여도 둘 다 남는다
        Event n = new E();
        assertEquals("outer", a.computeIfAbsent(n, ev -> { b.computeIfAbsent(ev, x -> "inner"); return "outer"; }));
        assertEquals("inner", b.get(n));
        assertEquals("outer", a.get(n));
    }

    /** 8 스레드가 같은 이벤트·열쇠 4개에 동시에 처음 붙이기를 겨룬다 — 열쇠마다 모두 같은 객체를 받는다. */
    @RepeatedTest(20)
    void racingFirstAttachKeepsOneObjectPerSlot() throws Exception {
        int threads = 8, keys = 4, rounds = 200;
        List<EventSlot<Object>> slots = new ArrayList<>();
        for (int k = 0; k < keys; k++) slots.add(new EventSlot<>("k" + k));
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                Event e = new E();
                CyclicBarrier go = new CyclicBarrier(threads);
                List<Future<Object[]>> fs = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    int start = t;
                    fs.add(ex.submit(() -> {
                        go.await();
                        Object[] got = new Object[keys];
                        for (int i = 0; i < keys; i++) {
                            int k = (start + i) % keys;   // 스레드마다 다른 순서로 — 배열 바꿔 끼우기 경합
                            got[k] = slots.get(k).computeIfAbsent(e, ev -> new Object());
                        }
                        return got;
                    }));
                }
                Object[] first = fs.get(0).get(10, TimeUnit.SECONDS);
                for (var f : fs) {
                    Object[] got = f.get(10, TimeUnit.SECONDS);
                    for (int k = 0; k < keys; k++) assertSame(first[k], got[k], "열쇠 " + k + " 라운드 " + round);
                }
                for (int k = 0; k < keys; k++) assertSame(first[k], slots.get(k).get(e));
            }
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    void lazyValueConvertsOnceAndKeepsTheFirstResult() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LazyValue<String> v = LazyValue.of(41, s -> { calls.incrementAndGet(); return "v" + (s + 1); });
        assertFalse(v.isResolved());
        assertEquals(0, calls.get(), "만들 때는 변환하지 않는다");
        String first = v.get();
        assertEquals("v42", first);
        assertSame(first, v.get());
        assertEquals(1, calls.get());
        LazyValue<String> n = LazyValue.of("x", s -> null);
        assertNull(n.get());
        assertTrue(n.isResolved(), "null 결과도 고정된다");
        Object ready = new Object();
        assertSame(ready, LazyValue.ready(ready).get());
        assertTrue(LazyValue.ready(null).isResolved());

        // 동시에 처음 읽기: 모두 같은 객체
        ExecutorService ex = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 200; round++) {
                LazyValue<Object> lv = LazyValue.of(round, s -> new Object());
                CyclicBarrier go = new CyclicBarrier(8);
                List<Future<Object>> fs = new ArrayList<>();
                for (int t = 0; t < 8; t++) fs.add(ex.submit(() -> { go.await(); return lv.get(); }));
                Object a = fs.get(0).get(10, TimeUnit.SECONDS);
                for (var f : fs) assertSame(a, f.get(10, TimeUnit.SECONDS));
            }
        } finally {
            ex.shutdownNow();
        }
    }

    /** 모든 블록 상태: 게으른 물리 이벤트의 block()·changedType() = 옛 서버가 미리 만들던 값. */
    @Test
    void lazyPhysicsEventEqualsEagerForEveryState() {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        int states = B.BLOCKS.stateCount();
        assertEquals(blocks.totalStateCount(), states);
        BlockPos pos = new BlockPos(1, 2, 3), src = new BlockPos(1, 3, 3);
        int checked = 0;
        for (int s = 0; s < states; s++) {
            int sourceBlock = B.block((s * 7919) % states);   // 갱신을 일으킨 블록도 두루
            var eager = new BlockPhysicsEvent("minecraft:overworld", pos, ApiConvert.block(s, blocks), src,
                    ApiConvert.block(B.defaultState(sourceBlock), blocks).type());
            int state = s;
            var lazy = new BlockPhysicsEvent("minecraft:overworld", pos, LazyValue.of(state, x -> ApiConvert.block(x, blocks)), src,
                    LazyValue.of(sourceBlock, b -> ApiConvert.block(B.defaultState(b), blocks).type()));
            assertEquals(eager.block(), lazy.block(), "상태 " + s);
            assertEquals(eager.block().properties(), lazy.block().properties(), "상태 " + s);
            assertEquals(eager.changedType(), lazy.changedType(), "원인 블록 " + sourceBlock);
            assertSame(lazy.block(), lazy.block(), "두 번 읽어도 같은 객체");
            checked++;
        }
        assertEquals(states, checked);
    }
}
