// O00 기준선: JFR 파일 하나를 모듈별 CPU·할당 지분, 상위 프레임, 스레드별 CPU, GC 요약으로 묶는다.
// 사용: java JfrAgg.java <file.jfr> [상위 N=30] [시작 초 건너뛰기=0]
import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class JfrAgg {
    static final String ROOT = "org.domi.furnace.";

    // 패키지 → 묶음 이름(서버는 server. 뒤 1~2단계, 호환·api 는 모듈 이름)
    static String bucketOf(String cls) {
        if (!cls.startsWith(ROOT)) return null;
        String rest = cls.substring(ROOT.length());
        String[] p = rest.split("\\.");
        if (p[0].equals("server")) {
            if (p.length <= 2) return "server(root):" + p[p.length - 1].replaceAll("\\$.*", "");
            String a = p[1];
            if (p.length >= 4 && (a.equals("world") || a.equals("entity") || a.equals("block") || a.equals("net") || a.equals("command") || a.equals("plugin")))
                return a + "." + p[2];
            if (a.equals("world") && p.length == 3) {
                String c = p[2].replaceAll("\\$.*", "");
                if (c.startsWith("Region") || c.equals("SharedScheduler") || c.equals("TickState") || c.equals("LiveGrid")) return "world(root):sched-ish";
                if (c.startsWith("Chunk") || c.startsWith("Palette") || c.equals("BitStorage") || c.startsWith("PaletteStorage")) return "world(root):chunk";
                return "world(root):other";
            }
            return a + "(root)";
        }
        return p[0]; // api, compat ...
    }

    record Acc(Map<String, Long> m) {
        Acc() { this(new HashMap<>()); }
        void add(String k, long w) { m.merge(k, w, Long::sum); }
        long total() { return m.values().stream().mapToLong(Long::longValue).sum(); }
    }

    static String frameName(RecordedFrame f) {
        return f.getMethod().getType().getName() + "." + f.getMethod().getName();
    }

    static String classify(RecordedStackTrace st, RecordedThread th) {
        if (st != null) for (RecordedFrame f : st.getFrames()) {
            if (!f.isJavaFrame()) continue;
            String b = bucketOf(f.getMethod().getType().getName());
            if (b != null) return b;
        }
        String tn = th == null ? "?" : th.getJavaName();
        return "외부[" + threadGroup(tn) + "]";
    }

    static String threadGroup(String n) {
        if (n == null) return "?";
        return n.replaceAll("[0-9]+", "#").replaceAll("#(-#)+", "#");
    }

    public static void main(String[] a) throws Exception {
        Path file = Path.of(a[0]);
        int topN = a.length > 1 ? Integer.parseInt(a[1]) : 30;
        double skipSec = a.length > 2 ? Double.parseDouble(a[2]) : 0;
        Acc incl = new Acc(), inclRegion = new Acc(); long[] regionN = {0};
        Acc cpu = new Acc(), alloc = new Acc(), self = new Acc(), firstF = new Acc(), allocCls = new Acc(), allocSite = new Acc(), cpuThread = new Acc();
        Map<String, double[]> thLoad = new HashMap<>();
        long gcCount = 0; double gcPauseSum = 0, gcPauseMax = 0; long heapAfterMax = 0, heapAfterSum = 0, heapAfterN = 0;
        List<Double> pauses = new ArrayList<>();
        java.time.Instant t0 = null;
        double machineTotal = 0, jvmUser = 0, jvmSys = 0; int cpuN = 0;
        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                if (t0 == null) t0 = e.getStartTime();
                if (skipSec > 0 && java.time.Duration.between(t0, e.getStartTime()).toMillis() < skipSec * 1000) continue;
                String n = e.getEventType().getName();
                switch (n) {
                    case "jdk.ExecutionSample" -> {
                        RecordedStackTrace st = e.getStackTrace();
                        RecordedThread th = e.getThread("sampledThread");
                        cpu.add(classify(st, th), 1);
                        cpuThread.add(threadGroup(th == null ? "?" : th.getJavaName()), 1);
                        if (st != null) {
                            boolean reg = th != null && th.getJavaName() != null && th.getJavaName().startsWith("furnace-region");
                            if (reg) regionN[0]++;
                            java.util.HashSet<String> seen = new java.util.HashSet<>();
                            for (RecordedFrame f : st.getFrames()) { String fn = frameName(f); if (fn.startsWith(ROOT) && seen.add(fn)) { incl.add(fn, 1); if (reg) inclRegion.add(fn, 1); } }
                        }
                        if (st != null && !st.getFrames().isEmpty()) {
                            self.add(frameName(st.getFrames().get(0)), 1);
                            for (RecordedFrame f : st.getFrames()) if (f.getMethod().getType().getName().startsWith(ROOT)) { firstF.add(frameName(f), 1); break; }
                        }
                    }
                    case "jdk.ObjectAllocationSample" -> {
                        long w = e.getLong("weight");
                        RecordedStackTrace st = e.getStackTrace();
                        alloc.add(classify(st, e.getThread("eventThread")), w);
                        allocCls.add(e.getClass("objectClass").getName(), w);
                        if (st != null) for (RecordedFrame f : st.getFrames()) if (f.getMethod().getType().getName().startsWith(ROOT)) { allocSite.add(frameName(f), w); break; }
                    }
                    case "jdk.ThreadCPULoad" -> {
                        RecordedThread th = e.getThread("eventThread");
                        String g = threadGroup(th == null ? "?" : th.getJavaName());
                        double[] v = thLoad.computeIfAbsent(g, k -> new double[2]);
                        v[0] += e.getFloat("user") + e.getFloat("system"); v[1] += 1;
                    }
                    case "jdk.CPULoad" -> { machineTotal += e.getFloat("machineTotal"); jvmUser += e.getFloat("jvmUser"); jvmSys += e.getFloat("jvmSystem"); cpuN++; }
                    case "jdk.GarbageCollection" -> {
                        gcCount++; double s = e.getDuration("sumOfPauses").toNanos() / 1e6; double l = e.getDuration("longestPause").toNanos() / 1e6;
                        gcPauseSum += s; gcPauseMax = Math.max(gcPauseMax, l); pauses.add(l);
                    }
                    case "jdk.GCHeapSummary" -> {
                        if ("After GC".equals(e.getString("when"))) { long u = e.getLong("heapUsed"); heapAfterMax = Math.max(heapAfterMax, u); heapAfterSum += u; heapAfterN++; }
                    }
                    default -> {}
                }
            }
        }
        System.out.println("# " + file.getFileName());
        if (cpuN > 0) System.out.printf("CPU(평균, 코어 비율 0~1): JVM user %.3f sys %.3f, 기계 전체 %.3f (표본 %d)%n", jvmUser / cpuN, jvmSys / cpuN, machineTotal / cpuN, cpuN);
        pct("CPU 표본 — 모듈별(가장 위 Furnace 프레임의 패키지)", cpu, topN, 1);
        pct("CPU 표본 — 스레드 묶음별", cpuThread, topN, 1);
        pct("CPU 표본 — 자기 프레임 상위", self, topN, 1);
        pct("CPU 표본 — 가장 위 Furnace 프레임 상위", firstF, topN, 1);
        pct("CPU 표본 — 포함(inclusive) 상위, 전체 기준", incl, 60, 1, cpu.total());
        pct("CPU 표본 — region 스레드만, 포함 상위(region 스레드 표본 기준)", inclRegion, 60, 1, regionN[0]);
        pct("할당(바이트 가중) — 모듈별", alloc, topN, 1 << 20);
        pct("할당 — 클래스 상위", allocCls, 15, 1 << 20);
        pct("할당 — 가장 위 Furnace 프레임 상위", allocSite, topN, 1 << 20);
        System.out.println("\n## 스레드 묶음별 평균 CPU(ThreadCPULoad, 스레드당 평균×스레드 수 아님: 합/표본)");
        thLoad.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0])).limit(20)
                .forEach(en -> System.out.printf("  %-45s 합 %.2f 표본 %.0f%n", en.getKey(), en.getValue()[0], en.getValue()[1]));
        Collections.sort(pauses);
        System.out.printf("%nGC: %d회, 멈춤 합 %.1f ms, 최장 %.1f ms, p50 %.1f p99 %.1f, GC 뒤 힙 평균 %.0f MB 최대 %.0f MB%n",
                gcCount, gcPauseSum, gcPauseMax, q(pauses, .5), q(pauses, .99), heapAfterN == 0 ? 0 : heapAfterSum / heapAfterN / 1048576.0, heapAfterMax / 1048576.0);
    }

    static double q(List<Double> l, double p) { return l.isEmpty() ? 0 : l.get(Math.min(l.size() - 1, (int) (l.size() * p))); }

    static void pct(String title, Acc acc, int topN, long unit) { pct(title, acc, topN, unit, acc.total()); }
    static void pct(String title, Acc acc, int topN, long unit, long tot) {
        System.out.printf("%n## %s (합 %s)%n", title, unit == 1 ? tot : String.format("%.0f MB", tot / (double) unit));
        acc.m().entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(topN)
                .forEach(en -> System.out.printf("  %5.1f%%  %s%s%n", 100.0 * en.getValue() / Math.max(1, tot), en.getKey(),
                        unit == 1 ? "" : String.format(" (%.0f MB)", en.getValue() / (double) unit)));
    }
}
