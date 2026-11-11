// (Fable 대기열 검증에서 받은 참조 모델 — DeferredBlockTicksTest.matchesVanillaLevelTicksOrderOnRandomScenes 가 쓴다.)
// 바닐라 1.21.1 LevelTicks(eyq) 꺼내기 순서를 바이트코드 그대로 옮긴 참조 모델과 Furnace BlockTicks 를 무작위 장면으로 맞대 본다.
// 모델 근거: eyq.a(J)(sortContainersToTick), eyq.a(JI)(drainContainers), eyq.a(Queue,eyo,J,I)(drainFromCurrentContainer),
// eyq.b()(rescheduleLeftoverContainers), eyt.a = DRAIN_ORDER(시각→우선순위→순번), eyt.b = INTRA_TICK(우선순위→순번).
package org.domi.furnace.server.block;

import java.util.*;
import java.util.function.LongPredicate;

public final class BlockTicksVanillaOrderModel {

    record T(long pos, int type, long trigger, int prio, long order) {}

    static final Comparator<T> DRAIN = Comparator.<T>comparingLong(T::trigger).thenComparingInt(T::prio).thenComparingLong(T::order);
    static final Comparator<T> INTRA = Comparator.<T>comparingInt(T::prio).thenComparingLong(T::order);

    /** 바닐라 LevelChunkTicks(eyo) + LevelTicks(eyq) 모델. */
    static final class Vanilla {
        static final class Container {
            final long chunk;
            final PriorityQueue<T> q = new PriorityQueue<>(DRAIN);
            final Set<Long> keys = new HashSet<>();   // (pos,type) — ticksPerPosition
            Container(long chunk) { this.chunk = chunk; }
            T peek() { return q.peek(); }
            T poll() { T t = q.poll(); if (t != null) keys.remove(key(t.pos, t.type)); return t; }
        }
        final Map<Long, Container> all = new HashMap<>();
        final Map<Long, Long> nextTick = new HashMap<>();   // nextTickForContainer
        final PriorityQueue<Container> toTick = new PriorityQueue<>((a, b) -> INTRA.compare(a.peek(), b.peek()));
        final ArrayDeque<T> toRun = new ArrayDeque<>();

        static long key(long pos, int type) { return pos * 31L + type; }

        void schedule(T t) {
            long ck = ChunkPredicateMemo.chunkKeyOfPos(t.pos);
            Container c = all.computeIfAbsent(ck, Container::new);
            if (!c.keys.add(key(t.pos, t.type))) return;
            c.q.add(t);
            if (t.equals(c.peek())) nextTick.put(ck, t.trigger);   // onTickAdded → updateContainerScheduling
        }

        List<T> collect(long gameTime, int max, LongPredicate ticking) {
            // sortContainersToTick
            for (var it = nextTick.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (e.getValue() > gameTime) continue;
                Container c = all.get(e.getKey());
                if (c == null) { it.remove(); continue; }
                T head = c.peek();
                if (head == null) { it.remove(); continue; }
                if (head.trigger > gameTime) { e.setValue(head.trigger); continue; }
                if (ticking.test(e.getKey())) { it.remove(); toTick.add(c); }
            }
            // drainContainers
            Container c;
            while (toRun.size() < max && (c = toTick.poll()) != null) {
                toRun.add(c.poll());
                // drainFromCurrentContainer — 다음 컨테이너 머리는 한 번만 본다
                if (toRun.size() < max) {
                    Container other = toTick.peek();
                    T otherHead = other == null ? null : other.peek();
                    while (toRun.size() < max) {
                        T t = c.peek();
                        if (t == null || t.trigger > gameTime) break;
                        if (otherHead != null && INTRA.compare(t, otherHead) > 0) break;
                        c.poll();
                        toRun.add(t);
                    }
                }
                T next = c.peek();
                if (next != null) {
                    if (next.trigger <= gameTime && toRun.size() < max) toTick.add(c);
                    else nextTick.put(c.chunk, next.trigger);
                }
            }
            // rescheduleLeftoverContainers + cleanup
            for (Container left : toTick) nextTick.put(left.chunk, left.peek().trigger);
            toTick.clear();
            List<T> out = new ArrayList<>(toRun);
            toRun.clear();
            return out;
        }
    }

    /** 한 시드 장면. 처음 어긋난 곳의 설명(없으면 null). */
    public static String firstMismatch(long seed) {
        Random setup = new Random(seed);
        int chunks = 2 + setup.nextInt(4);
        BlockTicks furnace = new BlockTicks();
        Vanilla vanilla = new Vanilla();
        long[] orderF = {0}, orderV = {0};
        boolean[] on = new boolean[chunks];
        Arrays.fill(on, true);
        Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);
        for (long now = 1; now <= 60; now++) {
            // 바깥에서 거는 예약(두 쪽에 똑같이)
            int n = rng.nextInt(6);
            for (int i = 0; i < n; i++) {
                int ch = rng.nextInt(chunks);
                long pos = new Pos(ch * 16 + rng.nextInt(3), 64, rng.nextInt(3)).asLong();
                int type = 1 + rng.nextInt(2), prio = rng.nextInt(3) - 1;
                long trig = now + rng.nextInt(4);
                furnace.schedule(Pos.of(pos), type, trig, prio, orderF[0]++);
                vanilla.schedule(new T(pos, type, trig, prio, orderV[0]++));
            }
            for (int i = 0; i < chunks; i++) if (rng.nextInt(5) == 0) on[i] = !on[i];
            boolean[] snap = on.clone();
            LongPredicate ticking = ck -> { for (int i = 0; i < chunks; i++) if (ck == ChunkPredicateMemo.chunkKey(i, 0)) return snap[i]; return false; };
            int max = rng.nextInt(4) == 0 ? 1 + rng.nextInt(4) : 65536;
            final long time = now;
            // 실행 중 새 예약: (칸, 종류, 시각)으로만 정해져 두 쪽이 같은 결정을 한다
            List<String> ranF = new ArrayList<>(), ranV = new ArrayList<>();
            furnace.tick(now, max, ticking, (p, type) -> {
                ranF.add(p.asLong() + ":" + type);
                long h = (p.asLong() * 1000003L + type * 7919L + time * 31L) & 0x7fffffffL;
                if (h % 3 == 0) furnace.schedule(p, type, time + 1 + h % 3, (int) (h % 2), orderF[0]++);
            });
            for (T t : vanilla.collect(now, max, ticking)) {
                ranV.add(t.pos + ":" + t.type);
                long h = (t.pos * 1000003L + t.type * 7919L + time * 31L) & 0x7fffffffL;
                if (h % 3 == 0) vanilla.schedule(new T(t.pos, t.type, time + 1 + h % 3, (int) (h % 2), orderV[0]++));
            }
            if (!ranF.equals(ranV)) return "seed " + seed + " tick " + now + " max " + max + "\n  vanilla=" + ranV + "\n  furnace=" + ranF;
        }
        return null;
    }

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 20000, bad = 0;
        String first = null;
        for (long s = 0; s < seeds; s++) {
            String m = firstMismatch(s);
            if (m != null) { bad++; if (first == null) first = m; }
        }
        System.out.println("seeds=" + seeds + " mismatch=" + bad);
        if (first != null) System.out.println(first);
    }
}
