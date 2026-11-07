// 바닐라 /perf start|stop: 10초(또는 stop 까지) 틱 속도와 JVM 표본을 모아 debug/profiling/<날짜>-<월드>-<버전>.zip 보고서를 만든다.
// 바닐라는 MetricsRecorder 로 서버 틱 하나마다 표본(틱 소요·CPU·GC)을 모은다. Furnace 는 region 이 따로 틱하므로 틱 수는 전역 박동 기준이고,
// 차원별 region 틱은 따로 합산한다. 표본은 SAMPLE_MILLIS 마다 타이머 스레드가 읽기 전용 계측만 보고 적는다(틱 경로에 일을 더하지 않는다).
// 보고서 내용(바닐라와 다름): system.txt(JVM·OS), profiling.txt(요약), metrics/ticking.csv(표본별 틱 수·소요), metrics/jvm.csv(힙·CPU·GC·스레드).
package org.domi.furnace.server.profiling;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PerfRecorder {

    private static final Logger log = LoggerFactory.getLogger(PerfRecorder.class);

    /** 바닐라 ActiveMetricsRecorder: 시작 뒤 10초가 지나면 스스로 멈춘다. */
    public static final long MAX_NANOS = 10L * 1_000_000_000L;
    public static final long SAMPLE_MILLIS = 100;
    /** 바닐라 MetricsPersister.PROFILING_RESULTS_DIR. */
    public static final String RESULTS_DIR = "debug/profiling";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    /** 읽기 전용 계측의 누적값 — 서버가 채운다. */
    public interface Source {
        /** 전역 박동 누적 횟수·소요 합(ns). */
        long globalTicks();

        long globalTickNanos();

        /** 차원별 region 틱 누적(이름·횟수·소요 합 ns). */
        List<DimTicks> dimensions();

        int players();

        String levelName();

        /** 보고서 system.txt 에 덧붙일 서버 쪽 정보(줄 목록). */
        default List<String> serverInfo() { return List.of(); }
    }

    public record DimTicks(String dimension, long ticks, long nanos) {}

    /** 멈춘 뒤 알리는 요약(바닐라 commands.perf.stopped 의 값). */
    public record Result(double seconds, long ticks) {
        public double ticksPerSecond() { return seconds <= 0 ? 0 : ticks / seconds; }
    }

    private record Row(long atNanos, long globalTicks, long globalNanos, long heapUsed, double cpuLoad, long gcCount, long gcMillis, int threads) {}

    private final Source source;
    private final Path baseDir;
    private final ScheduledExecutorService timer;
    private final boolean ownsTimer;

    private boolean recording;
    private long startNanos;
    private List<DimTicks> startDims;
    private final List<Row> rows = new ArrayList<>();
    private ScheduledFuture<?> sampler, deadline;
    private Consumer<Result> onStopped;
    private Consumer<Path> onReport;
    private Consumer<Throwable> onReportFailed;

    public PerfRecorder(Source source, Path baseDir) {
        this(source, baseDir, Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Furnace Perf Recorder");
            t.setDaemon(true);
            return t;
        }), true);
    }

    PerfRecorder(Source source, Path baseDir, ScheduledExecutorService timer, boolean ownsTimer) {
        this.source = source;
        this.baseDir = baseDir;
        this.timer = timer;
        this.ownsTimer = ownsTimer;
    }

    public synchronized boolean isRecording() { return recording; }

    /** 시작한다. 이미 하는 중이면 false. 멈추면 onStopped(요약) 다음 onReport(보고서 경로) 또는 onReportFailed 를 부른다(타이머 스레드에서). */
    public synchronized boolean start(Consumer<Result> onStopped, Consumer<Path> onReport, Consumer<Throwable> onReportFailed) {
        if (recording) return false;
        recording = true;
        this.onStopped = onStopped;
        this.onReport = onReport;
        this.onReportFailed = onReportFailed;
        rows.clear();
        startNanos = System.nanoTime();
        startDims = source.dimensions();
        rows.add(row());
        sampler = timer.scheduleAtFixedRate(this::sample, SAMPLE_MILLIS, SAMPLE_MILLIS, TimeUnit.MILLISECONDS);
        deadline = timer.schedule(this::stop, MAX_NANOS, TimeUnit.NANOSECONDS);
        return true;
    }

    private synchronized void sample() {
        if (recording) rows.add(row());
    }

    private Row row() {
        long gcCount = 0, gcMillis = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
            if (gc.getCollectionTime() > 0) gcMillis += gc.getCollectionTime();
        }
        double cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os ? os.getProcessCpuLoad() : -1;
        return new Row(System.nanoTime(), source.globalTicks(), source.globalTickNanos(),
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), cpu, gcCount, gcMillis,
                ManagementFactory.getThreadMXBean().getThreadCount());
    }

    /** 멈춘다(이미 멈췄으면 아무것도 안 한다). 보고서는 호출한 스레드에서 쓴다. */
    public void stop() {
        Consumer<Result> stopped;
        Consumer<Path> report;
        Consumer<Throwable> failed;
        List<Row> snapshot;
        List<DimTicks> endDims;
        long start, end;
        synchronized (this) {
            if (!recording) return;
            recording = false;
            rows.add(row());
            end = System.nanoTime();
            start = startNanos;
            snapshot = new ArrayList<>(rows);
            endDims = source.dimensions();
            stopped = onStopped;
            report = onReport;
            failed = onReportFailed;
            if (sampler != null) sampler.cancel(false);
            if (deadline != null) deadline.cancel(false);
        }
        Row first = snapshot.get(0), last = snapshot.get(snapshot.size() - 1);
        Result result = new Result((end - start) / 1.0E9, last.globalTicks() - first.globalTicks());
        if (stopped != null) stopped.accept(result);
        try {
            Path zip = writeReport(snapshot, result, startDims, endDims);
            if (report != null) report.accept(zip);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to save profiling report", e);
            if (failed != null) failed.accept(e);
        }
    }

    public void shutdown() {
        stop();
        if (ownsTimer) timer.shutdownNow();
    }

    // ------------------------------------------------------------------ 보고서

    Path writeReport(List<Row> rows, Result result, List<DimTicks> startDims, List<DimTicks> endDims) throws IOException {
        Path dir = baseDir.resolve(RESULTS_DIR);
        Files.createDirectories(dir);
        String base = String.format(Locale.ROOT, "%s-%s-%s", FILE_STAMP.format(LocalDateTime.now()), source.levelName(), "1.21.1");
        Path zip = findAvailable(dir, base);
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(os)) {
            put(out, "system.txt", systemReport());
            put(out, "profiling.txt", summary(rows, result, startDims, endDims));
            put(out, "metrics/ticking.csv", tickingCsv(rows));
            put(out, "metrics/jvm.csv", jvmCsv(rows));
        }
        return zip;
    }

    /** 바닐라 FileUtil.findAvailableName: base.zip, 있으면 base_1.zip … */
    static Path findAvailable(Path dir, String base) throws IOException {
        Path p = dir.resolve(base + ".zip");
        for (int i = 1; Files.exists(p); i++) {
            if (i > 9999) throw new IOException("Too many files like " + base);
            p = dir.resolve(base + "_" + i + ".zip");
        }
        return p;
    }

    private static void put(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private String systemReport() {
        StringBuilder b = new StringBuilder("---- Furnace Performance Report ----\n");
        Runtime rt = Runtime.getRuntime();
        b.append("Minecraft Version: 1.21.1\n");
        b.append("Java Version: ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("java.vendor")).append('\n');
        b.append("JVM: ").append(System.getProperty("java.vm.name")).append(" (").append(System.getProperty("java.vm.info")).append(")\n");
        b.append("Operating System: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")\n");
        b.append("Available processors: ").append(rt.availableProcessors()).append('\n');
        b.append("Memory: ").append(rt.totalMemory() - rt.freeMemory()).append(" / ").append(rt.totalMemory()).append(" / ").append(rt.maxMemory()).append(" bytes (used / total / max)\n");
        b.append("JVM Flags: ").append(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())).append('\n');
        b.append("Players: ").append(source.players()).append('\n');
        for (String l : source.serverInfo()) b.append(l).append('\n');
        return b.toString();
    }

    private static String summary(List<Row> rows, Result r, List<DimTicks> startDims, List<DimTicks> endDims) {
        Row first = rows.get(0), last = rows.get(rows.size() - 1);
        long gcMs = last.gcMillis() - first.gcMillis();
        double mspt = r.ticks() == 0 ? 0 : (last.globalNanos() - first.globalNanos()) / 1.0E6 / r.ticks();
        StringBuilder b = new StringBuilder("---- Furnace Profiler Results ----\n");
        b.append(String.format(Locale.ROOT, "Duration: %.2f s%nGlobal ticks: %d (%.2f per second)%nGlobal tick average: %.3f ms%n",
                r.seconds(), r.ticks(), r.ticksPerSecond(), mspt));
        b.append(String.format(Locale.ROOT, "GC: %d collections, %d ms%n", last.gcCount() - first.gcCount(), gcMs));
        double cpuSum = 0;
        int cpuN = 0;
        for (Row row : rows) if (row.cpuLoad() >= 0) { cpuSum += row.cpuLoad(); cpuN++; }
        if (cpuN > 0) b.append(String.format(Locale.ROOT, "Process CPU load (average): %.1f %%%n", 100.0 * cpuSum / cpuN));
        b.append("\nRegion ticks per dimension:\n");
        for (DimTicks e : endDims) {
            DimTicks s = startDims.stream().filter(d -> d.dimension().equals(e.dimension())).findFirst().orElse(new DimTicks(e.dimension(), 0, 0));
            long ticks = e.ticks() - s.ticks();
            double avg = ticks == 0 ? 0 : (e.nanos() - s.nanos()) / 1.0E6 / ticks;
            b.append(String.format(Locale.ROOT, "  %s: %d region ticks, average %.3f ms%n", e.dimension(), ticks, avg));
        }
        return b.toString();
    }

    private static String tickingCsv(List<Row> rows) {
        StringBuilder b = new StringBuilder("elapsed_ms,global_ticks,global_tick_ms_total\n");
        long t0 = rows.get(0).atNanos();
        for (Row r : rows) {
            b.append((r.atNanos() - t0) / 1_000_000L).append(',').append(r.globalTicks() - rows.get(0).globalTicks()).append(',')
                    .append(String.format(Locale.ROOT, "%.3f", (r.globalNanos() - rows.get(0).globalNanos()) / 1.0E6)).append('\n');
        }
        return b.toString();
    }

    private static String jvmCsv(List<Row> rows) {
        StringBuilder b = new StringBuilder("elapsed_ms,heap_used_bytes,process_cpu_load,gc_count,gc_time_ms,threads\n");
        long t0 = rows.get(0).atNanos();
        for (Row r : rows) {
            b.append((r.atNanos() - t0) / 1_000_000L).append(',').append(r.heapUsed()).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", r.cpuLoad())).append(',').append(r.gcCount()).append(',').append(r.gcMillis()).append(',')
                    .append(r.threads()).append('\n');
        }
        return b.toString();
    }

    /** LongSupplier 두 개로 단순 Source 를 만든다(시험용). */
    static Source simple(LongSupplier ticks, LongSupplier nanos, String level) {
        return new Source() {
            public long globalTicks() { return ticks.getAsLong(); }
            public long globalTickNanos() { return nanos.getAsLong(); }
            public List<DimTicks> dimensions() { return List.of(); }
            public int players() { return 0; }
            public String levelName() { return level; }
        };
    }
}
