// T-055 독립 검토 R4: 바닐라 1.21.1 LevelTicks·LevelChunkTicks 의 동작을 그대로 옮긴 모델(이 파일의 VLevel·VChunk)과 BlockTicks 를 맞댄다.
// 빠른 길(이번 시각만·한도 안)과 병합 길 경계, 지난 예약, 틱 범위 술어, 실행 중 예약(지연 0 포함)·조회, 청크 꺼내기·다시 걸기.
// 시드 수: -Dfurnace.identity.vanillaModelSeeds (기본 1000).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;

class BlockTicksVanillaSourceModelTest {

    record ST(long pos, int type, long trigger, int prio, long sub) {}

    static final Comparator<ST> DRAIN = (a, b) -> {
        int c = Long.compare(a.trigger, b.trigger);
        if (c != 0) return c;
        c = Integer.compare(a.prio, b.prio);
        return c != 0 ? c : Long.compare(a.sub, b.sub);
    };
    static final Comparator<ST> INTRA = (a, b) -> {
        int c = Integer.compare(a.prio, b.prio);
        return c != 0 ? c : Long.compare(a.sub, b.sub);
    };

    static String key(ST t) { return t.pos + ":" + t.type; }

    /** LevelChunkTicks. */
    static final class VChunk {
        final PriorityQueue<ST> q = new PriorityQueue<>(DRAIN);
        final Set<String> per = new HashSet<>();
        VLevel owner;
        long ck;
        ST peek() { return q.peek(); }
        ST poll() { ST t = q.poll(); if (t != null) per.remove(key(t)); return t; }
        void schedule(ST t) {
            if (per.add(key(t))) {
                q.add(t);
                if (owner != null && t.equals(q.peek())) owner.nextTick.put(ck, t.trigger);
            }
        }
    }

    /** LevelTicks (allContainers 는 모든 청크가 로드된 것으로 — 처음 쓸 때 addContainer). */
    static final class VLevel {
        final Map<Long, VChunk> all = new HashMap<>();
        final LinkedHashMap<Long, Long> nextTick = new LinkedHashMap<>();
        final PriorityQueue<VChunk> toTick = new PriorityQueue<>((a, b) -> INTRA.compare(a.peek(), b.peek()));
        final ArrayDeque<ST> run = new ArrayDeque<>();
        final Set<String> runSet = new HashSet<>();

        VChunk c(long ck) {
            return all.computeIfAbsent(ck, k -> { VChunk c = new VChunk(); c.owner = this; c.ck = k; return c; });
        }
        void schedule(ST t) { c(BlockTicks.chunkKeyOfPos(t.pos)).schedule(t); }
        boolean has(long pos, int type) { VChunk c = all.get(BlockTicks.chunkKeyOfPos(pos)); return c != null && c.per.contains(pos + ":" + type); }
        boolean will(long pos, int type) {
            if (runSet.isEmpty() && !run.isEmpty()) for (ST t : run) runSet.add(key(t));
            return runSet.contains(pos + ":" + type);
        }
        void tick(long time, int max, LongPredicate check, java.util.function.Consumer<ST> f) {
            var it = nextTick.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue() <= time) {
                    VChunk c = all.get(e.getKey());
                    if (c == null) it.remove();
                    else {
                        ST t = c.peek();
                        if (t == null) it.remove();
                        else if (t.trigger > time) e.setValue(t.trigger);
                        else if (check.test(e.getKey())) { it.remove(); toTick.add(c); }
                    }
                }
            }
            VChunk c;
            while (run.size() < max && (c = toTick.poll()) != null) {
                run.add(c.poll());
                if (run.size() < max) {
                    VChunk n = toTick.peek();
                    ST nh = n != null ? n.peek() : null;
                    while (run.size() < max) {
                        ST t = c.peek();
                        if (t == null || t.trigger > time || nh != null && INTRA.compare(t, nh) > 0) break;
                        c.poll();
                        run.add(t);
                    }
                }
                ST nx = c.peek();
                if (nx != null) {
                    if (nx.trigger <= time && run.size() < max) toTick.add(c);
                    else nextTick.put(c.ck, nx.trigger);
                }
            }
            for (VChunk l : toTick) nextTick.put(l.ck, l.peek().trigger);
            while (!run.isEmpty()) {
                ST t = run.poll();
                if (!runSet.isEmpty()) runSet.remove(key(t));
                f.accept(t);
            }
            run.clear();
            toTick.clear();
            runSet.clear();
        }
        List<ST> remove(long ck) {
            VChunk c = all.remove(ck);
            nextTick.remove(ck);
            if (c == null) return List.of();
            List<ST> l = new ArrayList<>(c.q);
            l.sort(DRAIN);
            return l;
        }
        List<ST> sorted(long ck) {
            VChunk c = all.get(ck);
            if (c == null) return List.of();
            List<ST> l = new ArrayList<>(c.q);
            l.sort(DRAIN);
            return l;
        }
        int size() { int n = 0; for (VChunk c : all.values()) n += c.q.size(); return n; }
    }

    static Pos rpos(SplittableRandom r, int span) {
        return new Pos((r.nextInt(span) - span / 2) * 16 + r.nextInt(16), r.nextInt(2), (r.nextInt(span) - span / 2) * 16 + r.nextInt(16));
    }

    static String run(long seed, boolean vanilla) {
        SplittableRandom r = new SplittableRandom(seed);
        int span = 2 + r.nextInt(5);
        VLevel v = new VLevel();
        BlockTicks b = new BlockTicks();
        long[] sub = {0};
        long now = 10;
        StringBuilder out = new StringBuilder();
        int steps = 40 + r.nextInt(120);
        for (int s = 0; s < steps; s++) {
            int op = r.nextInt(100);
            if (op < 40) {
                int n = 1 + r.nextInt(r.nextInt(5) == 0 ? 200 : 12);
                for (int i = 0; i < n; i++) {
                    Pos p = rpos(r, span);
                    int type = 1 + r.nextInt(3), prio = r.nextInt(7) - 3;
                    long trig = now + 1 + r.nextInt(4) - (r.nextInt(8) == 0 ? 5 : 0);
                    ST t = new ST(p.asLong(), type, trig, prio, sub[0]++);
                    if (vanilla) v.schedule(t); else b.schedule(p, type, trig, prio, t.sub);
                }
            } else if (op < 88) {
                now += r.nextInt(6) == 0 ? 1 + r.nextInt(3) : 1;
                long time = now;
                // 한도: 작음 / 이번 시각 예약 수 근처(빠른 길 경계) / 큼
                int dueGuess = r.nextInt(60);
                int max = switch (r.nextInt(4)) { case 0 -> 1 + r.nextInt(6); case 1 -> Math.max(1, dueGuess); default -> 65536; };
                long mask = r.nextInt(3) == 0 ? -1L : r.nextLong();
                LongPredicate check = k -> (mask >>> Math.floorMod((int) (k >> 32) * 7 + (int) k * 13, 64) & 1) != 0;
                SplittableRandom q = new SplittableRandom(r.nextLong());
                out.append("T").append(time).append('/').append(max).append(':');
                java.util.function.BiConsumer<Long, Integer> body = (pos, type) -> {
                    out.append(' ').append(pos).append('#').append(type);
                    Pos o = rpos(q, span);
                    int ot = 1 + q.nextInt(3);
                    boolean w = vanilla ? v.will(o.asLong(), ot) : b.willTickThisTick(o, ot);
                    boolean h = vanilla ? v.has(o.asLong(), ot) : b.hasScheduledTick(o, ot);
                    out.append(w ? 'W' : 'w').append(h ? 'H' : 'h');
                    int k = q.nextInt(5);
                    if (k <= 1) {
                        long p2 = k == 0 ? pos : o.asLong();
                        int t2 = k == 0 ? type : ot;
                        ST t = new ST(p2, t2, time + q.nextInt(3), q.nextInt(5) - 2, sub[0]++);   // 지연 0 이면 다음 틱에 지난 예약
                        if (vanilla) v.schedule(t); else b.schedule(Pos.of(p2), t2, t.trigger, t.prio, t.sub);
                    }
                };
                if (vanilla) v.tick(time, max, check, t -> body.accept(t.pos, t.type));
                else b.tick(time, max, check, (p, t) -> body.accept(p.asLong(), t));
                out.append(" n=").append(vanilla ? v.size() : b.size()).append('\n');
            } else if (op < 96) {   // 청크 꺼냈다가(실행 순서) 곧바로 또는 나중에 다시 걸기
                int cx = r.nextInt(span) - span / 2, cz = r.nextInt(span) - span / 2;
                long ck = ChunkPredicateMemo.chunkKey(cx, cz);
                List<ST> l;
                if (vanilla) l = v.remove(ck);
                else {
                    l = new ArrayList<>();
                    for (var x : b.removeChunk(cx, cz)) l.add(new ST(x.pos(), x.type(), x.triggerTick(), x.priority(), x.subTickOrder()));
                }
                out.append("R").append(ck).append(' ').append(l).append('\n');
                for (ST t : l) {
                    ST nt = new ST(t.pos, t.type, t.trigger, t.prio, sub[0]++);
                    if (vanilla) v.schedule(nt); else b.schedule(Pos.of(nt.pos), nt.type, nt.trigger, nt.prio, nt.sub);
                }
            } else {
                Pos p = rpos(r, span);
                int type = 1 + r.nextInt(3);
                out.append("Q").append(vanilla ? v.has(p.asLong(), type) : b.hasScheduledTick(p, type)).append('\n');
            }
        }
        for (int cx = -span; cx <= span; cx++)
            for (int cz = -span; cz <= span; cz++) {
                long ck = ChunkPredicateMemo.chunkKey(cx, cz);
                List<ST> l;
                if (vanilla) l = v.sorted(ck);
                else {
                    l = new ArrayList<>();
                    for (var x : b.chunkTicks(cx, cz)) l.add(new ST(x.pos(), x.type(), x.triggerTick(), x.priority(), x.subTickOrder()));
                }
                if (!l.isEmpty()) out.append("F").append(ck).append(l).append('\n');
            }
        return out.toString();
    }

    @Test
    void matchesVanillaSourceModel() {
        int n = Integer.getInteger("furnace.identity.vanillaModelSeeds", 1000);
        long base = Long.getLong("furnace.identity.vanillaModelBase", 0);
        for (long seed = base; seed < base + n; seed++) {
            String a = run(seed, true), c = run(seed, false);
            if (!a.equals(c)) {
                String[] x = a.split("\n"), y = c.split("\n");
                int i = 0;
                while (i < Math.min(x.length, y.length) && x[i].equals(y[i])) i++;
                fail("seed " + seed + " line " + i + "\nvanilla: " + (i < x.length ? x[i] : "<end>") + "\nnew:     " + (i < y.length ? y[i] : "<end>"));
            }
        }
    }

    /** 빠른 길을 타는 장면(많은 청크·이번 시각만·큰 한도)도 바닐라 병합과 같은지. */
    @Test
    void fastPathHeavyScenes() {
        for (long seed = 0; seed < 300; seed++) {
            SplittableRandom r = new SplittableRandom(seed ^ 0x5eed);
            VLevel v = new VLevel();
            BlockTicks b = new BlockTicks();
            long sub = 0;
            List<String> va = new ArrayList<>(), bb = new ArrayList<>();
            for (int tick = 1; tick <= 30; tick++) {
                int n = r.nextInt(400);
                for (int i = 0; i < n; i++) {
                    Pos p = rpos(r, 8);
                    int type = 1 + r.nextInt(2), prio = r.nextInt(7) - 3;
                    long trig = tick + 1 + r.nextInt(3);
                    v.schedule(new ST(p.asLong(), type, trig, prio, sub));
                    b.schedule(p, type, trig, prio, sub++);
                }
                int max = r.nextBoolean() ? 65536 : 50 + r.nextInt(300);
                final int t0 = tick;
                v.tick(tick, max, k -> true, t -> va.add(t0 + ":" + t.pos + "#" + t.type));
                b.tick(tick, max, k -> true, (p, t) -> bb.add(t0 + ":" + p.asLong() + "#" + t));
            }
            assertEquals(va, bb, "seed " + seed);
        }
    }
}
