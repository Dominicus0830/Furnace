// T-055 결과 동일 차분: 청크별 컨테이너 BlockTicks 와 옛 단일 큐 BlockTicks(LegacyBlockTicks, dec5d2c7 사본)를 무작위 작업 대본으로 맞댄다.
// 대본: 예약(지난 시각·같은 칸 겹침·우선순위 섞음), 틱 범위 술어·한도(1~5 또는 65536)를 바꿔 가며 틱, 틱 안에서 willTickThisTick·hasScheduledTick
// 조회와 새 예약, 청크 저장 목록·꺼내기, region 합치기(시계 차이)·나누기, /clone 복사. 기록: 실행 순서·조회 답·저장 목록·개수.
// 순번은 늘 서로 달라(같은 (우선순위, 순번) 동점 없음) 두 구현 모두 결정적이다 — 동점의 순서는 바닐라에서도 해시 순회에 달려 정의되지 않는다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.LongPredicate;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.identity.legacy.blockticks.LegacyBlockTicks;
import org.junit.jupiter.api.Test;

class BlockTicksIdentityTest {

    /** 두 구현을 같은 모양으로 부르는 얇은 겉. */
    interface Ticks {
        void schedule(Pos pos, int type, long trigger, int prio, long sub);
        boolean has(Pos pos, int type);
        boolean will(Pos pos, int type);
        int size();
        void tick(long now, int max, LongPredicate ticking, BlockTicks.Ticker ticker);
        List<String> chunk(int cx, int cz, boolean remove);
        void moveTo(Ticks target, LongPredicate chunkFilter, long offset);
        void copyFrom(Ticks src, int[] box, int dx, int dy, int dz);
    }

    static String fmt(long pos, int type, long trigger, int prio, long sub) {
        Pos p = Pos.of(pos);
        return p.x() + "," + p.y() + "," + p.z() + "#" + type + "@" + trigger + "/" + prio + "/" + sub;
    }

    static final class NewTicks implements Ticks {
        final BlockTicks t = new BlockTicks();
        public void schedule(Pos pos, int type, long trigger, int prio, long sub) { t.schedule(pos, type, trigger, prio, sub); }
        public boolean has(Pos pos, int type) { return t.hasScheduledTick(pos, type); }
        public boolean will(Pos pos, int type) { return t.willTickThisTick(pos, type); }
        public int size() { return t.size(); }
        public void tick(long now, int max, LongPredicate ticking, BlockTicks.Ticker ticker) { t.tick(now, max, ticking, ticker); }
        public List<String> chunk(int cx, int cz, boolean remove) {
            List<String> out = new ArrayList<>();
            if (!remove) out.add("has=" + t.hasChunkTicks(cx, cz));
            List<BlockTicks.Scheduled> list = new ArrayList<>(remove ? t.removeChunk(cx, cz) : t.chunkTicks(cx, cz));
            // 저장 목록은 이제 바닐라처럼 컨테이너 큐 배열 순서다(옛 구현은 정렬) — 순서는 바닐라 모델 대조(DeferredBlockTicksTest)가 보고, 여기서는 내용만 맞댄다
            list.sort(java.util.Comparator.comparingLong(BlockTicks.Scheduled::triggerTick).thenComparingInt(BlockTicks.Scheduled::priority)
                    .thenComparingLong(BlockTicks.Scheduled::subTickOrder));
            for (var s : list) out.add(fmt(s.pos(), s.type(), s.triggerTick(), s.priority(), s.subTickOrder()));
            return out;
        }
        public void moveTo(Ticks target, LongPredicate chunkFilter, long offset) { t.moveTo(((NewTicks) target).t, chunkFilter, offset); }
        public void copyFrom(Ticks src, int[] b, int dx, int dy, int dz) { t.copyAreaFrom(((NewTicks) src).t, b[0], b[1], b[2], b[3], b[4], b[5], dx, dy, dz); }
    }

    static final class OldTicks implements Ticks {
        final LegacyBlockTicks t = new LegacyBlockTicks();
        public void schedule(Pos pos, int type, long trigger, int prio, long sub) { t.schedule(pos, type, trigger, prio, sub); }
        public boolean has(Pos pos, int type) { return t.hasScheduledTick(pos, type); }
        public boolean will(Pos pos, int type) { return t.willTickThisTick(pos, type); }
        public int size() { return t.size(); }
        public void tick(long now, int max, LongPredicate ticking, BlockTicks.Ticker ticker) { t.tick(now, max, ticking, ticker::tick); }
        public List<String> chunk(int cx, int cz, boolean remove) {
            List<String> out = new ArrayList<>();
            // 옛 ChunkLevel.hasChunkTicks 는 chunkTicks 가 비었는지로 물었다
            if (!remove) out.add("has=" + !t.chunkTicks(cx, cz).isEmpty());
            List<LegacyBlockTicks.Scheduled> list = remove ? new ArrayList<>(t.removeChunk(cx, cz)) : t.chunkTicks(cx, cz);
            // 옛 removeChunk 는 미뤄 둔 것이 없으면 전역 힙을 훑은 순서 그대로(정렬 안 함)였다 — 그 순서는 없어진 전역 힙의 모양이라 옮길 수 없다.
            // 새 구현은 실행 순서로 정렬해 돌려준다. 목록 순서만 다르고 내용은 같아야 하므로 여기서는 정렬해 맞댄다(장면 금 파일 갱신 절 참고).
            list.sort(java.util.Comparator.comparingLong(LegacyBlockTicks.Scheduled::triggerTick).thenComparingInt(LegacyBlockTicks.Scheduled::priority)
                    .thenComparingLong(LegacyBlockTicks.Scheduled::subTickOrder));
            for (LegacyBlockTicks.Scheduled s : list) out.add(fmt(s.pos(), s.type(), s.triggerTick(), s.priority(), s.subTickOrder()));
            return out;
        }
        public void moveTo(Ticks target, LongPredicate chunkFilter, long offset) {
            // 옛 ChunkLevel 은 칸 → 청크로 바꿔 같은 조건을 넘겼다
            t.moveTo(((OldTicks) target).t, packed -> chunkFilter.test(BlockTicks.chunkKeyOfPos(packed)), offset);
        }
        public void copyFrom(Ticks src, int[] b, int dx, int dy, int dz) { t.copyAreaFrom(((OldTicks) src).t, b[0], b[1], b[2], b[3], b[4], b[5], dx, dy, dz); }
    }

    /** 대본 하나 = 시드. 두 구현이 같은 시드로 같은 대본을 만든다(구현의 답에 따라 갈라지지 않는 결정만 난수로). */
    record Script(long seed, int steps) {
        String summary() { return "seed=" + Long.toHexString(seed) + " steps=" + steps; }
    }

    static final int CHUNKS_X = 4, CHUNKS_Z = 3;

    static Pos randomPos(SplittableRandom r) {
        int cx = r.nextInt(CHUNKS_X) - 1, cz = r.nextInt(CHUNKS_Z) - 1;
        // 칸을 좁게 골라 같은 (칸, 종류) 겹침이 자주 나게
        return new Pos(cx * 16 + (r.nextBoolean() ? r.nextInt(3) : 13 + r.nextInt(3)), r.nextInt(3) - 1, cz * 16 + r.nextInt(3));
    }

    static List<String> run(Script s, java.util.function.Supplier<Ticks> make) {
        SplittableRandom r = new SplittableRandom(s.seed);
        Ticks[] lv = {make.get(), make.get()};   // region 둘
        long[] now = {100, 100 + r.nextInt(7) - 3};
        long[] order = {0};
        int[] clones = {0};
        List<String> out = new ArrayList<>();
        for (int step = 0; step < s.steps; step++) {
            int which = r.nextInt(2);
            Ticks t = lv[which];
            int op = r.nextInt(100);
            if (op < 45) {   // 예약 여러 개
                int n = 1 + r.nextInt(6);
                for (int i = 0; i < n; i++) {
                    Pos p = randomPos(r);
                    int type = 1 + r.nextInt(3), prio = r.nextInt(7) - 3;
                    long trig = now[which] + r.nextInt(8) - (r.nextInt(6) == 0 ? 4 : 0);   // 가끔 이미 지난 시각(불러온 예약처럼)
                    t.schedule(p, type, trig, prio, order[0]++);
                }
                out.add("size" + which + "=" + t.size());
            } else if (op < 80) {   // 틱
                long time = ++now[which];
                int max = r.nextInt(4) == 0 ? 1 + r.nextInt(5) : 65536;
                LongPredicate ticking;
                if (r.nextInt(3) == 0) {
                    ticking = null;
                } else {
                    long mask = r.nextLong();
                    ticking = k -> (mask >>> Math.floorMod((int) (k >> 32) * 7 + (int) k, 64) & 1) != 0;
                }
                long qseed = r.nextLong();
                SplittableRandom q = new SplittableRandom(qseed);   // 틱 안 결정은 이 난수열로(실행 순서가 같으면 같은 결정)
                StringBuilder ran = new StringBuilder("tick" + which + "@" + time + " max=" + max + ":");
                t.tick(time, max, ticking, (p, type) -> {
                    ran.append(' ').append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append('#').append(type);
                    Pos other = randomPos(q);
                    int ot = 1 + q.nextInt(3);
                    ran.append(t.will(other, ot) ? "W" : "w").append(t.has(other, ot) ? "H" : "h").append(t.will(p, type) ? "S" : "s");
                    int k = q.nextInt(4);
                    if (k == 0) t.schedule(p, type, time + q.nextInt(3), q.nextInt(3) - 1, order[0]++);   // 같은 칸 다시
                    else if (k == 1) t.schedule(other, ot, time + q.nextInt(4), q.nextInt(5) - 2, order[0]++);
                });
                out.add(ran.toString());
                out.add("size" + which + "=" + t.size());
            } else if (op < 87) {   // 청크 저장 목록(꺼내지 않음 / 꺼냄)
                int cx = r.nextInt(CHUNKS_X) - 1, cz = r.nextInt(CHUNKS_Z) - 1;
                boolean remove = r.nextInt(3) == 0;
                out.add((remove ? "remove" : "save") + which + " " + cx + "," + cz + " " + t.chunk(cx, cz, remove));
                out.add("size" + which + "=" + t.size());
            } else if (op < 93) {   // region 합치기(전부, 시계 차이) 또는 나누기(청크 일부)
                int other = 1 - which;
                if (r.nextBoolean()) {
                    long offset = now[other] - now[which];
                    t.moveTo(lv[other], k -> true, offset);
                    out.add("absorb " + which + "->" + other + " offset=" + offset);
                } else {
                    long mask = r.nextLong();
                    t.moveTo(lv[other], k -> (mask >>> Math.floorMod((int) (k >> 32) * 5 + (int) k * 3, 64) & 1) != 0, now[other] - now[which]);
                    out.add("split " + which + "->" + other);
                }
                out.add("size=" + lv[0].size() + "," + lv[1].size());
            } else if (op < 97) {   // /clone 복사(같은 레벨 안)
                int x0 = r.nextInt(48) - 16, z0 = r.nextInt(32) - 16;
                int[] box = {x0, -1, z0, x0 + r.nextInt(20), 1, z0 + r.nextInt(20)};
                int dx = r.nextInt(9) - 4, dz = r.nextInt(9) - 4;
                // 상자 안에 지금 가장 큰 순번의 예약을 하나 둔다 — 복사본 순번(최대 + 1 부터)이 상자 밖 예약의 순번과 겹치지 않게(동점 없음)
                t.schedule(new Pos(box[0], 0, box[2]), 10 + clones[0]++, now[which] + r.nextInt(4), 0, order[0]++);
                t.copyFrom(t, box, dx, 0, dz);
                order[0] = 2 * order[0] + 2;   // 복사본 순번은 2·최대 − 최소 + 1 ≤ 2·order 이하 — 뒤 예약의 순번과 겹치지 않게
                out.add("clone" + which + " size=" + t.size());
            } else {   // 조회만
                Pos p = randomPos(r);
                int type = 1 + r.nextInt(3);
                out.add("query" + which + " " + t.has(p, type) + "," + t.will(p, type));
            }
        }
        for (int i = 0; i < 2; i++) {
            for (int cx = -1; cx < CHUNKS_X - 1 + 1; cx++)
                for (int cz = -1; cz < CHUNKS_Z - 1 + 1; cz++) out.add("final" + i + " " + cx + "," + cz + " " + lv[i].chunk(cx, cz, false));
        }
        return out;
    }

    private static Differential<Script, List<String>> diff() {
        return Differential.<Script, List<String>>of("block-ticks-containers", r -> new Script(r.nextLong(), 50 + r.nextInt(300)))
                .seed(0x7055_0001L).iterations(400).describe(Script::summary);
    }

    @Test
    void containerTicksMatchLegacySingleQueue() {
        diff().check(s -> run(s, OldTicks::new), s -> run(s, NewTicks::new));
    }

    /** 변이 점검: 생성기가 청크 사이 우선순위 끼어들기(INTRA_TICK)를 실제로 만든다 — 청크 순서로만 꺼내면 잡혀야 한다. */
    @Test
    void generatorDetectsCrossChunkInterleaving() {
        diff().assertDetects(s -> run(s, OldTicks::new), s -> run(s, () -> new ChunkMajorTicks()));
    }

    /** 일부러 틀린 구현: 새 구현과 같되 틱마다 꺼낸 것을 청크 키 순으로 다시 늘어놓는다. */
    static final class ChunkMajorTicks implements Ticks {
        final NewTicks in = new NewTicks();
        public void schedule(Pos pos, int type, long trigger, int prio, long sub) { in.schedule(pos, type, trigger, prio, sub); }
        public boolean has(Pos pos, int type) { return in.has(pos, type); }
        public boolean will(Pos pos, int type) { return in.will(pos, type); }
        public int size() { return in.size(); }
        public void tick(long now, int max, LongPredicate ticking, BlockTicks.Ticker ticker) {
            List<Pos> ps = new ArrayList<>();
            List<Integer> ts = new ArrayList<>();
            in.tick(now, max, ticking, (p, t) -> { ps.add(p); ts.add(t); });
            Integer[] idx = new Integer[ps.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, java.util.Comparator.comparingLong(i -> BlockTicks.chunkKeyOfPos(ps.get(i).asLong())));
            for (int i : idx) ticker.tick(ps.get(i), ts.get(i));
        }
        public List<String> chunk(int cx, int cz, boolean remove) { return in.chunk(cx, cz, remove); }
        public void moveTo(Ticks target, LongPredicate f, long o) { in.moveTo(((ChunkMajorTicks) target).in, f, o); }
        public void copyFrom(Ticks src, int[] b, int dx, int dy, int dz) { in.copyFrom(((ChunkMajorTicks) src).in, b, dx, dy, dz); }
    }

    /** 원시 (칸, 종류) 집합: 무작위 추가·삭제·조회를 HashSet 과 맞댄다(뒤로 당기기 삭제의 실수를 잡는다). */
    @Test
    void posTypeSetMatchesHashSet() {
        SplittableRandom r = new SplittableRandom(0x7055_0002L);
        BlockTicks.PosTypeSet set = new BlockTicks.PosTypeSet();
        java.util.Set<String> ref = new java.util.HashSet<>();
        for (int i = 0; i < 400_000; i++) {
            long p = new Pos(r.nextInt(40) - 20, r.nextInt(6) - 3, r.nextInt(40) - 20).asLong();
            int t = r.nextInt(4);
            String k = p + ":" + t;
            switch (r.nextInt(3)) {
                case 0 -> assertEquals(ref.add(k), set.add(p, t), "add " + i);
                case 1 -> assertEquals(ref.remove(k), set.remove(p, t), "remove " + i);
                default -> assertEquals(ref.contains(k), set.contains(p, t), "contains " + i);
            }
            assertEquals(ref.size(), set.size());
            if (i % 50_000 == 49_999) {   // 가끔 비우기(큰 배열 버리기 경로 포함)
                set.clear();
                ref.clear();
            }
        }
    }

    @Test
    void longMapMatchesHashMap() {
        SplittableRandom r = new SplittableRandom(0x7055_0003L);
        BlockTicks.LongMap<Integer> map = new BlockTicks.LongMap<>();
        java.util.Map<Long, Integer> ref = new java.util.HashMap<>();
        for (int i = 0; i < 300_000; i++) {
            long k = ChunkPredicateMemo.chunkKey(r.nextInt(60) - 30, r.nextInt(60) - 30);
            switch (r.nextInt(3)) {
                case 0 -> { map.put(k, i); ref.put(k, i); }
                case 1 -> { map.remove(k); ref.remove(k); }
                default -> assertEquals(ref.get(k), map.get(k), "get " + i);
            }
            assertEquals(ref.size(), map.size());
        }
        List<Integer> vals = new ArrayList<>();
        map.forEachValue(vals::add);
        assertEquals(new java.util.HashSet<>(ref.values()), new java.util.HashSet<>(vals));
    }
}
