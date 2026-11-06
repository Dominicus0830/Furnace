// 플러그인별 시간 프로파일러 구현(/timings). 구간 하나는 LongAdder 묶음이라 여러 region 스레드가 잠금 없이 동시에 기록한다.
// 겹친 구간(리스너 안에서 다른 플러그인의 이벤트가 도는 등)은 스레드마다 둔 틀 스택으로 자기 시간을 나눈다 — 플러그인 합계는 자기 시간의 합이라
// 같은 시간을 두 번 세지 않는다. 꺼져 있으면 start() 는 volatile 읽기 하나로 음수를 돌려주고 stop 은 바로 돌아간다(스레드 로컬도 만지지 않는다).
// 스레드 종류(region·전역·그 밖)는 그 스레드가 처음 잴 때 한 번 정해 틀 스택에 둔다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.server.sched.RegionThread;
import org.domi.furnace.server.world.Regionizer;

public final class PluginProfilerImpl implements PluginProfiler {

    /** 틱 하나의 예산(초과 횟수 기준). */
    public static final long TICK_NANOS = 50_000_000L;
    /** 겹친 구간을 따로 셀 깊이. 더 깊은 구간은 시간은 재되 자식 시간을 빼지 않는다(잘못 쓴 사용자 구간이 스택을 키우지 못하게). */
    static final int MAX_DEPTH = 64;
    /** nanoTime 은 음수일 수 있어 기준점을 빼 늘 0 이상으로 만든다(음수 토큰 = 재지 않음). */
    private static final long ORIGIN = System.nanoTime();

    private record Key(FurnacePlugin plugin, Kind kind, String name) {}

    private final ConcurrentHashMap<Key, Section> sections = new ConcurrentHashMap<>();
    private final BooleanSupplier globalThread;
    private final ThreadLocal<Frames> frames;
    private volatile boolean running;
    /** 켜져 있던 시간: 앞서 끝난 구간들의 합 + (켜져 있으면) 지금 구간의 시작. this 잠금 안에서만 바꾼다. */
    private long measured;
    private long runningSince;

    /** @param globalThread 지금 스레드가 전역 틱 스레드인가 */
    public PluginProfilerImpl(BooleanSupplier globalThread) {
        this.globalThread = globalThread == null ? () -> false : globalThread;
        this.frames = ThreadLocal.withInitial(() -> new Frames(threadKind()));
    }

    /** 기동 때 켤지: -Dfurnace.profiler=true. */
    public static boolean enabledByConfig() { return Boolean.getBoolean("furnace.profiler"); }

    static long now() { return System.nanoTime() - ORIGIN; }

    private ThreadKind threadKind() {
        if (RegionThread.isRegionThread() || Regionizer.isRegionWorkerThread()) return ThreadKind.REGION;
        if (globalThread.getAsBoolean()) return ThreadKind.GLOBAL;
        return ThreadKind.OTHER;
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public synchronized void start() {
        if (running) return;
        runningSince = now();
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        measured += now() - runningSince;
    }

    @Override
    public synchronized void reset() {
        measured = 0;
        if (running) runningSince = now();
        // 꺼진 플러그인의 구간은 버린다(다시 쓰일 일이 없다). 나머지는 손잡이를 쥔 쪽이 계속 쓰므로 값만 0 으로.
        sections.entrySet().removeIf(e -> !e.getKey().plugin().isEnabled());
        for (Section s : sections.values()) s.clear();
    }

    @Override
    public Section timing(FurnacePlugin plugin, Kind kind, String name) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        return sections.computeIfAbsent(new Key(plugin, kind, name), k -> new Section(this, k));
    }

    synchronized long measuredNanos() { return measured + (running ? now() - runningSince : 0); }

    @Override
    public Report report() {
        long measuredNs = measuredNanos();
        boolean on = running;
        Map<FurnacePlugin, List<Section>> byPlugin = new IdentityHashMap<>();
        for (Section s : sections.values()) byPlugin.computeIfAbsent(s.key.plugin(), k -> new ArrayList<>()).add(s);
        List<PluginStats> out = new ArrayList<>();
        for (Map.Entry<FurnacePlugin, List<Section>> e : byPlugin.entrySet()) {
            List<SectionStats> list = new ArrayList<>();
            long self = 0, calls = 0, region = 0, global = 0, other = 0;
            for (Section s : e.getValue()) {
                SectionStats st = s.stats();
                if (st.calls() == 0) continue;
                list.add(st);
                self += st.selfNanos();
                calls += st.calls();
                region += s.byThread[ThreadKind.REGION.ordinal()].sum();
                global += s.byThread[ThreadKind.GLOBAL.ordinal()].sum();
                other += s.byThread[ThreadKind.OTHER.ordinal()].sum();
            }
            if (list.isEmpty()) continue;
            list.sort(Comparator.comparingLong(SectionStats::selfNanos).reversed().thenComparing(SectionStats::name));
            out.add(new PluginStats(pluginName(e.getKey()), self, calls, region, global, other, List.copyOf(list)));
        }
        out.sort(Comparator.comparingLong(PluginStats::selfNanos).reversed().thenComparing(PluginStats::plugin));
        return new Report(measuredNs, on, TICK_NANOS, List.copyOf(out));
    }

    private static String pluginName(FurnacePlugin p) {
        try {
            return p.name();
        } catch (RuntimeException e) {   // 초기화 전 객체여도 보고가 끊기지 않게
            return p.getClass().getName();
        }
    }

    /** 스레드 하나의 겹친 구간 스택: 깊이마다 그 안에서 끝난 자식 구간의 시간 합. */
    static final class Frames {
        final ThreadKind kind;
        long[] child = new long[8];
        int depth;

        Frames(ThreadKind kind) { this.kind = kind; }
    }

    /** 측정 구간 하나. */
    public static final class Section implements Timing {
        private final PluginProfilerImpl owner;
        final Key key;
        private final LongAdder calls = new LongAdder();
        private final LongAdder total = new LongAdder();
        private final LongAdder self = new LongAdder();
        private final AtomicLong max = new AtomicLong();
        private final LongAdder over = new LongAdder();
        /** 자기 시간을 스레드 종류별로. */
        final LongAdder[] byThread = {new LongAdder(), new LongAdder(), new LongAdder()};

        Section(PluginProfilerImpl owner, Key key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public long start() {
            if (!owner.running) return -1;
            Frames f = owner.frames.get();
            int d = f.depth++;
            if (d < MAX_DEPTH) {
                if (d == f.child.length) f.child = java.util.Arrays.copyOf(f.child, Math.min(MAX_DEPTH, d * 2));
                f.child[d] = 0;
            }
            return now();
        }

        @Override
        public void stop(long token) {
            if (token < 0) return;
            long dt = Math.max(0, now() - token);
            Frames f = owner.frames.get();
            long child = 0;
            if (f.depth > 0) {   // 다른 스레드에서 받은 토큰이면 짝이 없다 — 자식 없이 센다
                int d = --f.depth;
                if (d < MAX_DEPTH) child = f.child[d];
                if (d > 0 && d - 1 < MAX_DEPTH) f.child[d - 1] += dt;
            }
            record(dt, Math.max(0, dt - child), f.kind);
        }

        void record(long totalNs, long selfNs, ThreadKind thread) {
            calls.increment();
            total.add(totalNs);
            self.add(selfNs);
            byThread[thread.ordinal()].add(selfNs);
            if (totalNs > max.get()) max.accumulateAndGet(totalNs, Math::max);
            if (totalNs > TICK_NANOS) over.increment();
        }

        void clear() {
            calls.reset();
            total.reset();
            self.reset();
            max.set(0);
            over.reset();
            for (LongAdder a : byThread) a.reset();
        }

        SectionStats stats() {
            return new SectionStats(key.kind(), key.name(), calls.sum(), total.sum(), self.sum(), max.get(), over.sum());
        }

        @Override
        public String toString() { return "Timing{" + pluginName(key.plugin()) + " " + key.kind() + " " + key.name() + "}"; }
    }
}
