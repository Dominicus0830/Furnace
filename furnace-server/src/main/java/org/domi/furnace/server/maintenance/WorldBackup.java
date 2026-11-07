// 실행 중 월드 백업(주기·명령). 핵심은 region 스레드마다 따로 도는 월드에서 "한 시점" 을 뜨는 것:
//   1) 기준점 잡기(짧게 틱을 붙잡는다): 전역·모든 차원 region 게임 틱을 붙잡고 지금 도는 틱이 끝나기를 기다린다 → 그 순간의 상태를
//      저장(dirty 청크·엔티티·POI·플레이어·level.dat 등)하고 → IO 레인마다 기준점 marker 를 넣고 → 작은 파일(level.dat, data/ …)을 복사한 뒤 놓는다.
//      붙잡은 동안 엔티티·플레이어 넘겨주기(틱에서만 생긴다)가 없으므로 region 사이에서 엔티티가 둘이 되거나 사라지지 않는다.
//   2) 복사(틱은 다시 돈다): 지역 파일(.mca/.mcc)은 쓰기 전 복사(RegionFileStore.Snapshot) — 기준점 뒤 첫 쓰기가 오면 그 파일만 먼저 뜨고,
//      나머지는 복사기가 하나씩 뜬다. 플레이어 데이터는 그 실행기(직렬) 차례에서 복사한다(기준점 저장 뒤·이후 저장 앞).
//   3) 묶기: zip 또는 폴더로 옮기고 오래된 백업을 지운다.
// 설계 문서: docs/feature-parity/backup-idle-pause-o4-2026-11-07.md
package org.domi.furnace.server.maintenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.RegionFileStore;

public final class WorldBackup {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorldBackup.class);
    static final String STAGING_PREFIX = ".staging-";
    private static final java.time.format.DateTimeFormatter STAMP = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", java.util.Locale.ROOT);

    /** 서버가 채우는 입구. */
    public interface Hooks {
        /** 지금 살아 있는 차원·월드(오버월드 먼저). */
        List<World> worlds();

        /** 전역 게임 틱을 붙잡는다 / 놓는다. */
        void holdGlobal(boolean on);

        /** 전역 스레드가 지금 도는 틱을 끝내면 완료. */
        CompletableFuture<Void> quiesceGlobal();

        /** 접속자 저장 요청(각 future 는 저장이 플레이어 데이터 실행기에 들어가면 완료). */
        List<CompletableFuture<Void>> requestPlayerSaves();

        /** level.dat(주 월드·추가 월드) 저장 — 그 자리에서 끝낸다. */
        void saveLevelData();

        /** 플레이어 데이터 직렬 실행기. */
        java.util.concurrent.Executor playerIo();

        /** 플레이어 데이터(playerdata·stats·advancements)가 있는 주 월드 폴더. */
        Path mainWorldDir();

        /** 지금 접속자가 있는가. */
        boolean anyoneOnline();

        /** 지금까지의 접속 횟수(늘었으면 그 사이 누가 들어왔다). */
        long joinCount();
    }

    /** 백업 한 번의 결과. */
    public record Result(boolean ok, String message, Path output, int files, long bytes, long holdMillis, int copiedOnWrite, long totalMillis) {}

    private final MaintenanceOptions options;
    private final Hooks hooks;
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();
    /** 지난 백업 때의 접속 횟수(backup.skip-if-no-players). -1 = 아직 없음(기동 뒤 첫 주기는 백업한다). */
    private volatile long joinsAtLast = -1;
    private volatile Result last;
    /** 시험용: 기준점을 놓은 직후(복사 전)에 부른다. */
    private volatile Runnable afterRelease;

    /** 시험용: 기준점을 놓은 직후·복사 전에 돌 일(기준점 뒤 쓰기 흉내). */
    public void afterReleaseForTest(Runnable r) { this.afterRelease = r; }

    private final java.util.concurrent.Executor executor;

    /** executor: 백업을 돌릴 스레드(유지보수 스레드 — 전역·region 스레드가 아니어야 한다). */
    public WorldBackup(MaintenanceOptions options, Hooks hooks, java.util.concurrent.Executor executor) {
        this.options = options;
        this.hooks = hooks;
        this.executor = executor;
    }

    /** 유지보수 스레드에서 백업한다(명령용 — 부르는 스레드는 기다리지 않는다). */
    public CompletableFuture<Result> runAsync(String reason) {
        if (running.get()) return CompletableFuture.completedFuture(new Result(false, "이미 백업 중", null, 0, 0, 0, 0, 0));
        return CompletableFuture.supplyAsync(() -> run(reason), executor);
    }

    public MaintenanceOptions options() { return options; }

    public boolean running() { return running.get(); }

    public Result last() { return last; }

    /** 남은 임시 폴더(지난번에 백업 도중 꺼졌을 때)를 지운다. */
    public void cleanStaleStaging() {
        Path dir = options.backupDirectory();
        if (!Files.isDirectory(dir)) return;
        try (var ds = Files.newDirectoryStream(dir, STAGING_PREFIX + "*")) {
            for (Path p : ds) {
                log.info("[BACKUP] 지난번에 끝나지 않은 임시 폴더를 지운다: {}", p);
                deleteTree(p);
            }
        } catch (IOException e) {
            log.warn("[BACKUP] 임시 폴더 정리 실패", e);
        }
    }

    /** 주기 백업(유지보수 스레드에서). 접속자가 없었으면(설정) 건너뛴다. */
    public void runScheduled() {
        if (options.backupSkipIdle() && joinsAtLast >= 0 && joinsAtLast == hooks.joinCount() && !hooks.anyoneOnline()) {
            log.debug("[BACKUP] 지난 백업 뒤 접속자가 없어 건너뛴다");
            return;
        }
        run("주기");
    }

    /** 지금 백업한다(이 스레드에서 끝까지 — 전역·region 스레드에서 부르지 않는다). 이미 도는 중이면 실패 결과. */
    public Result run(String reason) {
        if (!running.compareAndSet(false, true)) return new Result(false, "이미 백업 중", null, 0, 0, 0, 0, 0);
        long t0 = System.nanoTime();
        Result r;
        long joins = hooks.joinCount();
        try {
            r = runLocked(reason, t0);
            joinsAtLast = joins;
        } catch (Exception e) {
            log.error("[BACKUP] 실패", e);
            r = new Result(false, "실패: " + e, null, 0, 0, 0, 0, (System.nanoTime() - t0) / 1_000_000L);
        } finally {
            running.set(false);
        }
        last = r;
        return r;
    }

    private Result runLocked(String reason, long t0) throws Exception {
        Path root = options.backupDirectory();
        Files.createDirectories(root);
        String name = STAMP.format(java.time.LocalDateTime.now());
        Path staging = root.resolve(STAGING_PREFIX + name);
        if (Files.exists(staging)) throw new IOException("임시 폴더가 이미 있음: " + staging);
        log.info("[BACKUP] 시작({}): {}", reason, name);
        List<World> worlds = hooks.worlds();
        // 백업할 월드 폴더(주 월드 + 추가 월드). 네더·엔드는 주 월드 폴더 안(DIM-1, DIM1)이다.
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
        roots.add(hooks.mainWorldDir().toAbsolutePath().normalize());
        for (World w : worlds) roots.add(w.levelRoot().toAbsolutePath().normalize());
        Path absRoot = root.toAbsolutePath().normalize();
        for (Path r : roots) {
            if (absRoot.startsWith(r)) throw new IOException("backup.directory 가 월드 폴더 안에 있다: " + absRoot);
        }
        java.util.Map<Path, Path> destOf = new java.util.HashMap<>();
        for (Path r : roots) destOf.put(r, staging.resolve(r.getFileName().toString()));
        java.util.function.Function<Path, Path> dest = p -> {
            Path a = p.toAbsolutePath().normalize();
            for (Path r : roots) if (a.startsWith(r)) return destOf.get(r).resolve(r.relativize(a).toString());
            throw new IllegalStateException("월드 폴더 밖: " + a);
        };
        // 지역 파일 폴더(복사기가 따로 뜬다)와 플레이어 데이터 폴더(그 실행기 차례에 뜬다)는 작은 파일 복사에서 뺀다
        java.util.Set<Path> skip = new java.util.HashSet<>();
        for (World w : worlds) for (RegionFileStore s : w.regionFileStores()) skip.add(s.regionDir().toAbsolutePath().normalize());
        Path main = hooks.mainWorldDir().toAbsolutePath().normalize();
        List<Path> playerDirs = List.of(main.resolve("playerdata"), main.resolve("stats"), main.resolve("advancements"));
        skip.addAll(playerDirs);

        List<RegionFileStore.Snapshot> snapshots = new ArrayList<>();
        List<CompletableFuture<Void>> markers = new ArrayList<>();
        CompletableFuture<Void> playerCopy;
        long holdStart = System.nanoTime(), holdMillis;
        long timeout = TimeUnit.SECONDS.toNanos(options.backupHoldTimeoutSeconds());
        long deadline = holdStart + timeout;
        int smallFiles;
        long[] smallBytes = new long[1];
        // ---- 1) 기준점(틱 붙잡기)
        hooks.holdGlobal(true);
        for (World w : worlds) w.holdTicks(true);
        try {
            List<CompletableFuture<Void>> quiet = new ArrayList<>();
            quiet.add(hooks.quiesceGlobal());
            for (World w : worlds) quiet.add(w.quiesce());
            await(quiet, deadline, "틱 멈춤");
            for (World w : worlds) w.snapshotSaving(true);
            try {
                List<CompletableFuture<Void>> saves = new ArrayList<>(hooks.requestPlayerSaves());
                for (World w : worlds) saves.addAll(w.flushAll());
                await(saves, deadline, "기준점 저장");
            } finally {
                for (World w : worlds) w.snapshotSaving(false);
            }
            hooks.saveLevelData();
            // 레인 기준점: 위 저장이 모두 레인에 들어간 뒤, 틱을 놓기 전에(이후 저장은 marker 뒤로 간다)
            for (World w : worlds) markers.add(w.beginSnapshot(dest, snapshots));
            // 플레이어 데이터: 기준점 저장(위에서 넣었다) 뒤, 놓은 뒤의 저장 앞 — 같은 직렬 실행기 차례
            playerCopy = CompletableFuture.runAsync(() -> {
                for (Path d : playerDirs) {
                    try {
                        copyTree(d, dest.apply(d), java.util.Set.of(), smallBytes);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }, hooks.playerIo());
            // 작은 파일(level.dat, data/, 데이터 팩 …) — 붙잡은 동안 쓰는 곳이 없다
            smallFiles = 0;
            for (Path r : roots) smallFiles += copyTree(r, destOf.get(r), skip, smallBytes);
        } catch (Exception e) {
            for (var s : snapshots) s.end();
            throw e;
        } finally {
            for (World w : worlds) w.holdTicks(false);
            hooks.holdGlobal(false);
            holdMillis = (System.nanoTime() - holdStart) / 1_000_000L;
        }
        Runnable hook = afterRelease;
        if (hook != null) hook.run();
        // ---- 2) 복사(틱은 돈다)
        int files = smallFiles, cow = 0;
        long bytes = 0;
        try {
            CompletableFuture.allOf(markers.toArray(new CompletableFuture[0])).get(10, TimeUnit.MINUTES);
            for (var s : snapshots) s.copyRemaining();
            playerCopy.get(10, TimeUnit.MINUTES);
        } catch (Exception e) {
            deleteQuietly(staging);
            throw e;
        } finally {
            for (var s : snapshots) {
                s.end();
                files += s.files();
                bytes += s.bytes();
                cow += s.copiedOnWrite();
            }
        }
        for (var s : snapshots) {
            if (s.failure() != null) {
                deleteQuietly(staging);
                throw s.failure();
            }
        }
        bytes += smallBytes[0];
        // ---- 3) 묶기
        Path out;
        if (options.backupFormat() == MaintenanceOptions.BackupFormat.ZIP) {
            out = root.resolve(name + ".zip");
            Path part = root.resolve("." + name + ".zip.part");
            zip(staging, part);
            Files.move(part, out, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            deleteTree(staging);
        } else {
            out = root.resolve(name);
            Files.move(staging, out, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
        prune(root);
        long total = (System.nanoTime() - t0) / 1_000_000L;
        String msg = String.format(java.util.Locale.ROOT, "%s (파일 %d개, %.1f MB, 기준점 정지 %d ms, 쓰기 전 복사 %d개, 전체 %.1f s)",
                out.getFileName(), files, bytes / 1048576.0, holdMillis, cow, total / 1000.0);
        log.info("[BACKUP] 완료: {}", msg);
        return new Result(true, msg, out, files, bytes, holdMillis, cow, total);
    }

    private static void await(List<CompletableFuture<Void>> fs, long deadline, String what) throws Exception {
        try {
            CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IOException(what + " 이(가) backup.hold-timeout-seconds 안에 끝나지 않았다 — 이번 백업을 포기한다");
        }
    }

    /** from 아래 파일을 to 로 복사(skip 폴더·session.lock·*.tmp 는 뺀다). 복사한 파일 수. 도중에 사라진 파일은 건너뛴다. */
    static int copyTree(Path from, Path to, java.util.Set<Path> skip, long[] bytes) throws IOException {
        if (!Files.isDirectory(from)) return 0;
        int[] n = new int[1];
        Files.walkFileTree(from, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (skip.contains(dir.toAbsolutePath().normalize())) return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                String fn = file.getFileName().toString();
                if (fn.equals("session.lock") || fn.endsWith(".tmp")) return java.nio.file.FileVisitResult.CONTINUE;
                try {
                    Path target = to.resolve(from.relativize(file).toString());
                    Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    bytes[0] += attrs.size();
                    n[0]++;
                } catch (java.nio.file.NoSuchFileException gone) {
                    // 원자적 교체 도중의 임시 파일 등
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                if (exc instanceof java.nio.file.NoSuchFileException) return java.nio.file.FileVisitResult.CONTINUE;
                throw exc;
            }
        });
        return n[0];
    }

    /** dir 내용을 zip 으로(경로 구분자 '/'). 지역 파일은 이미 압축돼 있어 빠른 단계로. */
    static void zip(Path dir, Path zipFile) throws IOException {
        try (var out = new java.util.zip.ZipOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(zipFile), 1 << 16))) {
            out.setLevel(java.util.zip.Deflater.BEST_SPEED);
            List<Path> all = new ArrayList<>();
            try (var st = Files.walk(dir)) {
                st.filter(Files::isRegularFile).sorted().forEach(all::add);
            }
            for (Path p : all) {
                String entry = dir.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                out.putNextEntry(new java.util.zip.ZipEntry(entry));
                Files.copy(p, out);
                out.closeEntry();
            }
        }
    }

    /** 이름(시각)순으로 오래된 백업을 지워 keep 개만 남긴다. */
    void prune(Path root) throws IOException {
        int keep = options.backupKeep();
        if (keep <= 0) return;
        List<Path> backups = new ArrayList<>();
        try (var ds = Files.newDirectoryStream(root)) {
            for (Path p : ds) {
                String n = p.getFileName().toString();
                String base = n.endsWith(".zip") ? n.substring(0, n.length() - 4) : n;
                if (isStamp(base) && (n.endsWith(".zip") ? Files.isRegularFile(p) : Files.isDirectory(p))) backups.add(p);
            }
        }
        backups.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        for (int i = 0; i < backups.size() - keep; i++) {
            log.info("[BACKUP] 오래된 백업 지움: {}", backups.get(i).getFileName());
            deleteTree(backups.get(i));
        }
    }

    static boolean isStamp(String s) {
        if (s.length() != 15 || s.charAt(8) != '-') return false;
        for (int i = 0; i < s.length(); i++) if (i != 8 && !Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var st = Files.walk(p)) {
            for (Path q : st.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(q);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            deleteTree(p);
        } catch (IOException e) {
            log.warn("[BACKUP] 임시 폴더 지우기 실패 {}", p, e);
        }
    }
}
