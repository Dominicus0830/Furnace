// 미리 생성(T-175, /furnace pregen): 정한 사각형 안의 청크를 가운데에서 바깥으로 돌며 만들어 저장해 둔다.
//  - 생성 요청은 생성 대기열(GenQueue) 맨 뒤에 선다(GenQueue.Ticket.forBackground) — 플레이어·플러그인 요청이 하나라도 있으면 그 뒤라
//    플레이어 차례·전역 예산(T-170)을 건드리지 않는다. 플레이어가 미리 생성이 걸어 둔 청크를 원하면 그 요청은 맨 뒤에서 내려온다(promote).
//  - 이미 디스크에 있는 청크는 IO 레인에서 위치표만 보고 건너뛴다(올리지 않는다).
//  - 만든 청크는 티켓 없이 올라와 구독자가 없으면 유예(chunk-eviction-grace) 뒤 평소처럼 저장·내려간다 — 생성·저장 경로는 플레이어가
//    걸어가 만든 청크와 같다(결과 동일). 저장 역압(World.ioBackedUp)이 걸리거나 내려가길 기다리는 청크가 많으면 새 요청을 멈춘다.
//  - 한 차원에 하나만. 진행 위치는 차원 data/furnace_pregen.properties 에 남겨 멈춘 뒤·재기동 뒤 이어 한다(자동으로 다시 시작하지는 않는다).
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Pregen {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Pregen.class);

    /** 진행 파일 이름(차원 data 폴더). */
    public static final String FILE = "furnace_pregen.properties";
    /** 기본 진행률 보고 간격. */
    public static final long DEFAULT_REPORT_SECONDS = Long.getLong("furnace.pregen.reportSeconds", 10L);
    /** 내려가길 기다리는 미리 생성 청크가 이만큼이면 새 요청을 멈춘다(저장이 생성을 못 따라갈 때 메모리가 늘지 않게). */
    static final int MAX_RESIDENT = Integer.getInteger("furnace.pregen.maxResident", 1024);
    /** 만든 지 이만큼 지나도 올라와 있는 청크는 다른 누군가(플레이어·티켓)가 붙잡은 것으로 보고 더 세지 않는다. */
    static final long RESIDENT_FORGET_NANOS = TimeUnit.SECONDS.toNanos(30);
    /** 역압으로 멈췄을 때 다시 보는 간격. */
    static final long RETRY_MILLIS = 200L;

    /**
     * 미리 생성할 청크 사각형(양 끝 포함)과 도는 가운데 청크. label 은 보고·진행 파일용("반경 1000" / "월드 경계").
     */
    public record Area(int centerX, int centerZ, int minX, int minZ, int maxX, int maxZ, String label) {
        public Area {
            if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("빈 영역");
        }

        /** 블록 가운데 (bx, bz) 둘레 반경 r 블록 정사각형 [b − r, b + r) 에 걸치는 청크. */
        public static Area ofRadius(double bx, double bz, double radius, String label) {
            int minX = (int) Math.floor(bx - radius) >> 4, maxX = ((int) Math.ceil(bx + radius) - 1) >> 4;
            int minZ = (int) Math.floor(bz - radius) >> 4, maxZ = ((int) Math.ceil(bz + radius) - 1) >> 4;
            return new Area((int) Math.floor(bx) >> 4, (int) Math.floor(bz) >> 4, minX, minZ, Math.max(minX, maxX), Math.max(minZ, maxZ), label);
        }

        public long total() { return (long) (maxX - minX + 1) * (maxZ - minZ + 1); }

        boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

        /** 가운데에서 가장 먼 모서리까지의 체비셰프 거리(돌 고리 수). */
        int rings() {
            return Math.max(Math.max(centerX - minX, maxX - centerX), Math.max(centerZ - minZ, maxZ - centerZ));
        }
    }

    /**
     * 가운데에서 바깥으로 고리마다 도는 순서(고리 k 는 둘레 8k 칸: 위 변 +x, 오른 변 +z, 아래 변 −x, 왼 변 −z). 영역 밖 칸은 건너뛰고
     * 번호(index)는 영역 안 칸만 센다 — 진행 파일은 이 번호 하나로 이어 한다. 고리 순서라 이웃 청크를 잇따라 만들어 이웃 원형(7×7)을 함께 쓴다.
     */
    static final class Spiral {
        private final Area a;
        private final int rings;
        private int k, p;
        private long index;
        private boolean done;

        Spiral(Area a) {
            this.a = a;
            this.rings = a.rings();
            if (!a.contains(a.centerX(), a.centerZ())) advance();   // 가운데가 영역 밖이면(있을 수 없지만) 첫 안쪽 칸으로
        }

        boolean hasNext() { return !done; }

        long index() { return index; }

        int x() { return cellX(); }

        int z() { return cellZ(); }

        /** 지금 칸을 넘기고 다음 영역 안 칸으로. */
        void next() {
            index++;
            advance();
        }

        /** n 칸 건너뛴다(이어 하기). */
        void skip(long n) {
            while (n-- > 0 && !done) next();
        }

        private void advance() {
            while (true) {
                if (k == 0 || ++p >= 8 * k) {
                    k++;
                    p = 0;
                    if (k > rings) { done = true; return; }
                }
                if (a.contains(cellX(), cellZ())) return;
            }
        }

        private int cellX() {
            if (k == 0) return a.centerX();
            int s = p / (2 * k), o = p % (2 * k);
            return switch (s) {
                case 0 -> a.centerX() - k + o;
                case 1 -> a.centerX() + k;
                case 2 -> a.centerX() + k - o;
                default -> a.centerX() - k;
            };
        }

        private int cellZ() {
            if (k == 0) return a.centerZ();
            int s = p / (2 * k), o = p % (2 * k);
            return switch (s) {
                case 0 -> a.centerZ() - k;
                case 1 -> a.centerZ() - k + o;
                case 2 -> a.centerZ() + k;
                default -> a.centerZ() + k - o;
            };
        }
    }

    public enum State { RUNNING, STOPPED, DONE }

    /** 진행 상황 사본(명령·보고용). */
    public record Progress(String dimension, Area area, State state, long processed, long total, long generated, long skipped, long failed,
                           long elapsedNanos, double rate, int inflight, boolean waiting) {
        public double percent() { return total == 0 ? 100.0 : processed * 100.0 / total; }

        /** 남은 시간(초) — 이번 실행의 평균 속도로. 모르면 −1. */
        public long etaSeconds() { return rate <= 0 ? -1 : (long) Math.ceil((total - processed) / rate); }

        public String line() {
            long eta = etaSeconds();
            return String.format(Locale.ROOT, "[미리 생성] %s %s: %d/%d 청크 (%.2f%%), 새로 만든 %d · 건너뛴 %d%s, %.1f 청크/초, 남은 시간 %s%s",
                    dimension, area.label(), processed, total, percent(), generated, skipped, failed > 0 ? " · 실패 " + failed : "",
                    rate, eta < 0 ? "?" : hms(eta), waiting ? " (저장 대기)" : "");
        }
    }

    static String hms(long s) {
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    // ------------------------------------------------------------------ 작업 하나(차원 하나)

    public final class Job {
        final World world;
        final Area area;
        private final Spiral spiral;
        private final int window;
        private final Consumer<String> out;
        /** 진행 중인 번호(가장 작은 것 = 이어 할 곳). */
        private final TreeSet<Long> inflight = new TreeSet<>();
        /** 진행 중인 청크 키 → 번호(멈출 때 시작 전 요청 취소용). */
        private final Map<Long, Long> inflightKeys = new ConcurrentHashMap<>();
        /** 이번 실행에서 만든 청크(내려가길 기다리는 것 셈): {키, 끝난 때}. */
        private final ArrayDeque<long[]> resident = new ArrayDeque<>();
        private long processed, generated, skipped, failed;
        private final long processedAtStart;
        private final long startNanos = System.nanoTime();
        private final long elapsedBefore;
        /** volatile: region 스레드가 요청을 걸기 전·후에 본다(멈춤과 엇갈려도 시작 전 요청이 새지 않게, process). */
        private volatile State state = State.RUNNING;
        private boolean waiting, retryScheduled, pumping;
        private java.util.concurrent.ScheduledFuture<?> reporter;

        Job(World world, Area area, long startIndex, long[] counts, long elapsedBefore, int window, Consumer<String> out) {
            this.world = world;
            this.area = area;
            this.spiral = new Spiral(area);
            spiral.skip(startIndex);
            this.processed = startIndex;
            this.processedAtStart = startIndex;
            if (counts != null) { generated = counts[0]; skipped = counts[1]; failed = counts[2]; }
            this.elapsedBefore = elapsedBefore;
            this.window = Math.max(1, window);
            this.out = out;
        }

        public synchronized Progress progress() {
            long elapsedRun = System.nanoTime() - startNanos;
            double rate = elapsedRun <= 0 ? 0 : (processed - processedAtStart) / (elapsedRun / 1e9);
            return new Progress(world.dimension(), area, state, processed, area.total(), generated, skipped, failed,
                    elapsedBefore + (state == State.RUNNING ? elapsedRun : 0), rate, inflight.size(), waiting);
        }

        public State state() { return state; }

        boolean running() { return state == State.RUNNING; }

        /** 이어 할 번호: 진행 중인 것 가운데 가장 작은 것, 없으면 다음에 꺼낼 번호. */
        synchronized long resumeIndex() { return inflight.isEmpty() ? spiral.index() : inflight.first(); }

        void begin(long reportSeconds) {
            if (reportSeconds > 0) {
                reporter = timer.scheduleAtFixedRate(this::report, reportSeconds, reportSeconds, TimeUnit.SECONDS);
            }
            pump();
        }

        private void report() {
            Progress pr;
            synchronized (this) {
                if (state != State.RUNNING) return;
                pr = progress();
                saveQuietly();
            }
            say(pr.line());
        }

        private void say(String line) {
            LOG.info(line);
            if (out != null) {
                try {
                    out.accept(line);
                } catch (RuntimeException e) {
                    LOG.debug("[PREGEN] 보고 실패", e);
                }
            }
        }

        /** 창이 빌 때까지 다음 청크를 건다. 역압이면 잠시 뒤 다시. 안에서 바로 끝난 청크(이미 올라와 있음)가 다시 부르면 바깥 고리가 잇는다. */
        synchronized void pump() {
            if (pumping) return;
            pumping = true;
            try {
                pumpLocked();
            } finally {
                pumping = false;
            }
        }

        private void pumpLocked() {
            if (state != State.RUNNING) return;
            if (world.isUnloaded()) {
                end(State.STOPPED, "월드가 내려가 멈춘다");
                return;
            }
            while (inflight.size() < window && spiral.hasNext()) {
                if (backedUp()) {
                    waiting = true;
                    retryLater();
                    return;
                }
                waiting = false;
                long idx = spiral.index();
                ChunkPos cp = new ChunkPos(spiral.x(), spiral.z());
                spiral.next();
                inflight.add(idx);
                long key = Regionizer.chunkKey(cp.x(), cp.z());
                inflightKeys.put(key, idx);
                submit(idx, key, cp, 0);
            }
            if (inflight.isEmpty() && !spiral.hasNext()) end(State.DONE, null);
        }

        /** 저장 역압이거나 내려가길 기다리는 미리 생성 청크가 너무 많다. */
        private boolean backedUp() {
            if (world.ioBackedUp()) return true;
            long now = System.nanoTime();
            for (var it = resident.iterator(); it.hasNext(); ) {
                long[] r = it.next();
                if (now - r[1] >= RESIDENT_FORGET_NANOS || world.loadedChunk(Regionizer.chunkX(r[0]), Regionizer.chunkZ(r[0])) == null) it.remove();
            }
            return resident.size() >= MAX_RESIDENT;
        }

        private void retryLater() {
            if (retryScheduled) return;
            retryScheduled = true;
            try {
                timer.schedule(() -> {
                    synchronized (this) { retryScheduled = false; }
                    pump();
                }, RETRY_MILLIS, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException e) {   // 관리자가 닫혔다(서버 종료) — 멈춘다
                end(State.STOPPED, "서버 종료");
            }
        }

        private void submit(long idx, long key, ChunkPos cp, int attempt) {
            CompletableFuture<Boolean> f;
            try {
                f = process(world, cp, this::running);
            } catch (RuntimeException e) {
                f = CompletableFuture.failedFuture(e);
            }
            f.whenComplete((made, err) -> finished(idx, key, cp, made, err, attempt));
        }

        /** 같은 청크를 다시 거는 횟수 상한(취소된 옛 요청에 붙은 경우). */
        static final int MAX_RETRY = 3;

        private void finished(long idx, long key, ChunkPos cp, Boolean made, Throwable err, int attempt) {
            synchronized (this) {
                if (state != State.RUNNING) {   // 멈춘 뒤 끝난 것은 세지 않는다(이어 할 때 다시 본다 — 디스크에 있으면 건너뛴다)
                    inflight.remove(idx);
                    inflightKeys.remove(key, idx);
                    return;
                }
                Throwable root = err;
                while (root instanceof java.util.concurrent.CompletionException && root.getCause() != null) root = root.getCause();
                if (root instanceof java.util.concurrent.CancellationException) {
                    GenQueue q = world.genQueue();
                    if ((q == null || !q.isShutdown()) && attempt < MAX_RETRY) {
                        // 멈췄다 이어 한 직후 등: 취소된 옛 미리 생성 요청(아직 끝나지 않은 것)에 붙었다 — 그것이 끝났으니 다시 건다
                        submit(idx, key, cp, attempt + 1);
                        return;
                    }
                    // 생성 대기열이 닫힌다(서버 종료) — 이 청크부터 이어 하게 남기고 멈춘다
                    end(State.STOPPED, "생성 대기열이 닫혀 멈춘다");
                    inflight.remove(idx);
                    inflightKeys.remove(key, idx);
                    return;
                }
                inflight.remove(idx);
                inflightKeys.remove(key, idx);
                processed++;
                if (err != null) {
                    failed++;
                    LOG.warn("[PREGEN] 청크 {} 생성 실패 — 건너뛴다", cp, err);
                } else if (Boolean.TRUE.equals(made)) {
                    generated++;
                    resident.addLast(new long[] {key, System.nanoTime()});
                } else {
                    skipped++;
                }
            }
            pump();
        }

        /** 멈춤(이어 할 수 있게 진행 파일을 남긴다). 시작 전인 미리 생성 요청은 취소한다. */
        synchronized boolean stop(String why) {
            if (state != State.RUNNING) return false;
            end(State.STOPPED, why);
            return true;
        }

        private void end(State s, String why) {
            long resume = resumeIndex();
            Progress pr = progress();
            state = s;
            if (reporter != null) reporter.cancel(false);
            for (Long key : inflightKeys.keySet()) {
                GenQueue.Ticket t = world.genTicket(key);
                if (t != null) t.cancelBackground();
            }
            jobs.remove(world, this);
            if (s == State.DONE) {
                deleteQuietly(world);
                say(pr.line());
                say("[미리 생성] " + world.dimension() + " 끝: " + area.total() + " 청크, 새로 만든 " + generated + " · 건너뛴 " + skipped
                        + (failed > 0 ? " · 실패 " + failed : "") + ", 걸린 시간 " + hms(pr.elapsedNanos() / 1_000_000_000L));
            } else {
                saveAt(resume, pr.elapsedNanos());
                say("[미리 생성] " + world.dimension() + " 멈춤" + (why != null ? "(" + why + ")" : "") + ": " + resume + "/" + area.total()
                        + " 청크까지 — '/furnace pregen resume' 으로 이어 한다");
            }
        }

        private void saveQuietly() {
            saveAt(resumeIndex(), progress().elapsedNanos());
        }

        private void saveAt(long resume, long elapsed) {
            Path f = file(world);
            if (f == null) return;
            Properties p = new Properties();
            p.setProperty("centerX", Integer.toString(area.centerX()));
            p.setProperty("centerZ", Integer.toString(area.centerZ()));
            p.setProperty("minX", Integer.toString(area.minX()));
            p.setProperty("minZ", Integer.toString(area.minZ()));
            p.setProperty("maxX", Integer.toString(area.maxX()));
            p.setProperty("maxZ", Integer.toString(area.maxZ()));
            p.setProperty("label", area.label());
            p.setProperty("next", Long.toString(resume));
            p.setProperty("generated", Long.toString(generated));
            p.setProperty("skipped", Long.toString(skipped));
            p.setProperty("failed", Long.toString(failed));
            p.setProperty("elapsedMs", Long.toString(elapsed / 1_000_000L));
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(FILE + ".tmp");
                try (OutputStream o = Files.newOutputStream(tmp)) {
                    p.store(o, "Furnace /furnace pregen 진행 위치");
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOG.warn("[PREGEN] 진행 파일 저장 실패 {}", f, e);
            }
        }
    }

    /**
     * 청크 하나: 올라와 있으면 끝(거짓), 디스크에 있으면 건너뜀(거짓), 없으면 생성 대기열 맨 뒤에 걸어 만든다(참).
     * 디스크 확인은 그 청크의 IO 레인에서 — 앞서 넣은 저장 뒤에 보므로 방금 저장한 청크도 있다고 본다.
     */
    static CompletableFuture<Boolean> process(World w, ChunkPos cp, java.util.function.BooleanSupplier alive) {
        if (w.loadedChunk(cp.x(), cp.z()) != null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> onDisk;
        if (w.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource anvil) {
            onDisk = CompletableFuture.supplyAsync(() -> {
                try {
                    return anvil.store().hasChunk(cp.x(), cp.z());
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }, w.ioLane(cp.x(), cp.z()));
        } else {
            onDisk = CompletableFuture.completedFuture(false);
        }
        return onDisk.thenCompose(has -> {
            if (has) return CompletableFuture.completedFuture(false);
            return w.submitAtChunk(cp, r -> {
                if (!alive.getAsBoolean()) return CompletableFuture.<ChunkColumn>completedFuture(null);
                CompletableFuture<ChunkColumn> f = r.loadChunkBackground(cp);
                // 멈춤(상태 쓰기 → 요청 훑기)과 엇갈렸으면 여기서 취소한다 — 둘 중 하나는 반드시 상대를 본다
                if (!alive.getAsBoolean()) {
                    GenQueue.Ticket t = w.genTicket(Regionizer.chunkKey(cp.x(), cp.z()));
                    if (t != null) t.cancelBackground();
                }
                return f;
            }).thenCompose(x -> x).thenApply(col -> col != null);
        });
    }

    // ------------------------------------------------------------------ 관리자(서버 하나)

    private final Map<World, Job> jobs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timer;

    public Pregen() {
        timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "furnace-pregen");
            t.setDaemon(true);
            return t;
        });
    }

    /** 그 차원에서 돌고 있는 작업(없으면 null). */
    public Job job(World w) { return jobs.get(w); }

    /** 결과: 시작했으면 job, 아니면 거절 사유. */
    public record Started(Job job, String refused) {}

    /** 새로 시작한다(그 차원의 진행 파일은 새것으로 덮는다). 이미 돌고 있으면 거절. */
    public synchronized Started start(World w, Area area, Consumer<String> out) {
        return begin(w, area, 0, null, 0, out);
    }

    /** 진행 파일에서 이어 한다. 파일이 없거나 이미 돌고 있으면 거절. */
    public synchronized Started resume(World w, Consumer<String> out) {
        Saved s = loadSaved(w);
        if (s == null) return new Started(null, "이어 할 미리 생성이 없다");
        return begin(w, s.area(), s.next(), new long[] {s.generated(), s.skipped(), s.failed()}, s.elapsedNanos(), out);
    }

    private Started begin(World w, Area area, long next, long[] counts, long elapsed, Consumer<String> out) {
        if (jobs.containsKey(w)) return new Started(null, "이 차원에서 이미 미리 생성 중이다 — '/furnace pregen status' 로 보고, 멈추려면 '/furnace pregen stop'");
        if (w.isUnloaded()) return new Started(null, "내려간 월드다");
        Job j = new Job(w, area, Math.min(next, area.total()), counts, elapsed, window(w), out);
        jobs.put(w, j);
        synchronized (j) {
            j.saveQuietly();
        }
        j.say("[미리 생성] " + w.dimension() + " " + area.label() + " 시작: 청크 (" + area.minX() + "," + area.minZ() + ")~(" + area.maxX() + ","
                + area.maxZ() + "), " + area.total() + " 청크" + (next > 0 ? ", " + next + " 번째부터 이어 함" : "")
                + " — 플레이어 요청이 늘 먼저다");
        j.begin(DEFAULT_REPORT_SECONDS);
        return new Started(j, null);
    }

    /** 멈춘다(이어 할 수 있다). 돌고 있지 않으면 거짓. */
    public boolean stop(World w) {
        Job j = jobs.get(w);
        return j != null && j.stop("명령");
    }

    /** 멈추고 진행 파일도 지운다. 무엇이든 지웠으면 참. */
    public synchronized boolean cancel(World w) {
        Job j = jobs.get(w);
        boolean any = j != null && j.stop("취소");
        Path f = file(w);
        if (f != null && Files.exists(f)) any = true;
        deleteQuietly(w);
        return any;
    }

    /** 서버 종료: 모두 멈추고 진행 파일을 남긴다. */
    public void shutdown() {
        for (Job j : jobs.values()) j.stop("서버 종료");
        timer.shutdownNow();
    }

    /** 차원 하나의 동시 진행 수: 생성 스레드 × 2(디스크 확인·빛·설치 사이에도 생성 스레드가 놀지 않게). */
    static int window(World w) {
        int n = Integer.getInteger("furnace.pregen.window", 0);
        if (n > 0) return n;
        GenQueue q = w.genQueue();
        return Math.max(2, (q != null ? q.threads() : Runtime.getRuntime().availableProcessors()) * 2);
    }

    // ------------------------------------------------------------------ 진행 파일

    /** 진행 파일에서 읽은 것. */
    public record Saved(Area area, long next, long generated, long skipped, long failed, long elapsedNanos) {}

    static Path file(World w) {
        if (w.worldDir() == null || !(w.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource)) return null;
        return w.dimensionType().storageDir(w.levelRoot()).resolve("data").resolve(FILE);
    }

    /** 진행 파일(없거나 못 읽으면 null). */
    public static Saved loadSaved(World w) {
        Path f = file(w);
        if (f == null || !Files.isRegularFile(f)) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            Area a = new Area(Integer.parseInt(p.getProperty("centerX")), Integer.parseInt(p.getProperty("centerZ")),
                    Integer.parseInt(p.getProperty("minX")), Integer.parseInt(p.getProperty("minZ")),
                    Integer.parseInt(p.getProperty("maxX")), Integer.parseInt(p.getProperty("maxZ")), p.getProperty("label", "?"));
            return new Saved(a, Long.parseLong(p.getProperty("next", "0")), Long.parseLong(p.getProperty("generated", "0")),
                    Long.parseLong(p.getProperty("skipped", "0")), Long.parseLong(p.getProperty("failed", "0")),
                    TimeUnit.MILLISECONDS.toNanos(Long.parseLong(p.getProperty("elapsedMs", "0"))));
        } catch (IOException | RuntimeException e) {
            LOG.warn("[PREGEN] 진행 파일을 읽지 못했다 {}", f, e);
            return null;
        }
    }

    private static void deleteQuietly(World w) {
        Path f = file(w);
        if (f == null) return;
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            LOG.warn("[PREGEN] 진행 파일 지우기 실패 {}", f, e);
        }
    }
}
