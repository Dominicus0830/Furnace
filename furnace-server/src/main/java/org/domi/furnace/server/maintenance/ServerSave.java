// /save-all·빈 서버 일시정지의 저장(바닐라 MinecraftServer.saveEverything(suppressLogs=true, flush, forced=true)).
// 기다리는 일은 전용 저장 스레드에서 한다 — 예전엔 부른 스레드(/save-all 이면 전역 스레드)가 차원마다 최대 60초를 기다려
// 시간·날씨·스케줄러 심박·콘솔·RCON 이 멈추고, 길어지면 워치독(max-tick-time)이 서버를 껐다(P-O14-10).
// 스냅샷(플레이어 저장 요청·level.dat 값 옮기기)은 부른 스레드에서 바로 뜬다 — /save-all 이면 전역 스레드라 전역 상태를 안전하게 읽는다.
package org.domi.furnace.server.maintenance;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerSave implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ServerSave.class);

    /** 차원 하나의 청크를 IO 레인에 다 넣기까지의 마감(flush 가 아닐 때, 예전 값 그대로). */
    static final long CHUNK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(60);
    /** 플레이어 데이터 저장 대기(예전 값 그대로). */
    static final long PLAYER_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30);
    /**
     * 저장 하나의 전체 마감. /save-all 지연 결과 한도(10분)보다 짧아야 한다 — 마감에 걸리면 실패로 끝나 명령이 한도 전에 답한다.
     * flush 는 이 안에서 남은 청크가 0 이 되고 레인이 빌 때까지 기다린다(save-off 상태면 자동 저장이 남은 청크를 대신 써 주지 않는다).
     */
    static final long TOTAL_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(8);

    /** flush 저장이 마감 안에 디스크까지 끝내지 못함 — /save-all 은 commands.save.failed 로 끝난다. */
    public static final class IncompleteSaveException extends RuntimeException {
        IncompleteSaveException(String message) { super(message, null, false, false); }
    }

    /** 서버가 채운다. */
    public interface Hooks {
        /** 저장할 차원(기본·추가 월드 모두). 부른 스레드에서(/save-all 이면 전역 — 월드 내리기와 순서가 정해진다). */
        List<World> worlds();

        /** 접속 중 플레이어 저장 요청 + 잡힌 퇴장 저장(바닐라 PlayerList.saveAll). 부른 스레드에서. */
        List<CompletableFuture<Void>> requestPlayerSaves();

        /** 플레이어 데이터 직렬 실행기 — 넣은 저장이 다 끝났는지 확인한다. */
        Executor playerIo();

        /** level.dat 에 쓸 런타임 값(시간·날씨·보더·게임 규칙 등)을 옮긴다. 부른 스레드에서(/save-all 이면 전역). */
        void snapshotLevelData();

        /** level.dat 파일 쓰기(기본·추가 월드). 저장 스레드에서. */
        void writeLevelData();
    }

    private final Hooks hooks;
    private final ExecutorService exec;
    /** 종료 중: 돌던 저장은 남은 단계를 건너뛴다(종료 저장이 대신 한다 — 검토 R5). */
    private volatile boolean stopping;
    /** 시험용: 전체 마감(나노초). */
    volatile long totalTimeoutNanos = TOTAL_TIMEOUT_NANOS;

    public ServerSave(Hooks hooks) {
        this.hooks = hooks;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Furnace Save Thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 저장을 시작하고 곧바로 돌아온다. 돌려주는 future 는 바닐라 saveEverything 이 돌아오는 시점에 완료된다:
     * 플레이어 파일·level.dat 기록, 청크는 IO 레인에 다 넣은 뒤(flush 가 아니면 디스크 기록은 기다리지 않는다 — 바닐라 IOWorker 와 같다),
     * flush 면 IO 레인의 기록이 다 끝나고 지역 파일을 force(true) 한 뒤. 디스크 오류는 바닐라처럼 로그만 남기고 성공으로 끝난다.
     * flush 인데 전체 마감(8분) 안에 디스크까지 끝내지 못하면 {@link IncompleteSaveException} 으로 실패한다.
     * 저장은 한 번에 하나씩 차례로 돈다.
     */
    public CompletableFuture<Void> save(boolean flush) {
        List<CompletableFuture<Void>> players;
        List<World> worlds;
        try {
            // 월드 목록도 부른 스레드(/save-all 이면 전역)에서 뜬다 — 추가 월드 내리기(전역에서 목록에서 뗌)와 순서가 정해진다(검토 R2)
            worlds = List.copyOf(hooks.worlds());
            players = hooks.requestPlayerSaves();
            hooks.snapshotLevelData();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Void> out = new CompletableFuture<>();
        try {
            exec.execute(() -> {
                try {
                    run(flush, worlds, players);
                    out.complete(null);
                } catch (Throwable t) {
                    if (t instanceof InterruptedException) Thread.currentThread().interrupt();
                    out.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            out.completeExceptionally(e);   // 종료 중 — 종료 저장이 대신한다
        }
        return out;
    }

    /** 끝날 때까지 기다리는 판(유지보수 스레드 전용 — 전역·region 스레드에서 부르지 않는다). */
    public void saveAndWait(boolean flush) {
        save(flush).join();
    }

    /** 저장 스레드에 넣은 빈 작업 — 그 앞에 들어간 저장이 모두 끝나면 완료(추가 월드 내리기가 저장소를 닫기 전에 기다린다). */
    public CompletableFuture<Void> barrier() {
        try {
            return CompletableFuture.runAsync(() -> { }, exec);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(null);   // 닫혔다 — 종료 저장 차례
        }
    }

    private void run(boolean flush, List<World> worlds, List<CompletableFuture<Void>> players) throws InterruptedException {
        long deadline = System.nanoTime() + totalTimeoutNanos;
        String incomplete = null;
        // 바닐라 순서: 플레이어(동기 기록) → 차원마다 청크 → level.dat. 플레이어 기록은 전용 실행기에서 함께 돌고 맨 끝에서 기다린다.
        for (World w : worlds) {
            if (stopping) throw new IncompleteSaveException("서버 종료 중");
            if (w.isUnloaded()) continue;   // 그 사이 내린 추가 월드(내리기가 저장했다)
            long budget = Math.max(1, deadline - System.nanoTime());
            int left = w.flushAllDrained(flush ? budget : Math.min(CHUNK_TIMEOUT_NANOS, budget));
            if (left != 0) {
                if (flush) {
                    if (incomplete == null) incomplete = w.dimension() + " 청크 " + (left < 0 ? "?" : left) + "개를 IO 레인에 넣지 못했다";
                } else {
                    log.warn("save-all: {} 청크 {}개는 자동 저장으로 넘어갔다(IO 레인이 마감 안에 비지 않았다, -1 = 수 모름)", w.dimension(), left);
                }
            }
        }
        if (flush) {
            if (stopping) throw new IncompleteSaveException("서버 종료 중");
            String f = flushToDisk(worlds, deadline);
            if (incomplete == null) incomplete = f;
        }
        if (stopping) throw new IncompleteSaveException("서버 종료 중");
        hooks.writeLevelData();
        try {
            long wait = Math.max(1, Math.min(PLAYER_TIMEOUT_NANOS, deadline - System.nanoTime()));
            CompletableFuture.allOf(players.toArray(new CompletableFuture[0])).get(wait, TimeUnit.NANOSECONDS);
            wait = Math.max(1, Math.min(PLAYER_TIMEOUT_NANOS, deadline - System.nanoTime()));
            CompletableFuture.runAsync(() -> { }, hooks.playerIo()).get(wait, TimeUnit.NANOSECONDS);
        } catch (TimeoutException | ExecutionException | RejectedExecutionException e) {
            log.warn("save-all: 플레이어 데이터 저장이 마감 안에 끝나지 않았다", e);
        }
        if (flush) {
            if (incomplete != null) {
                // 바닐라 "All chunks are saved" 로그는 거짓이 되므로 찍지 않고 실패로 끝낸다(백업 스크립트가 덜 쓴 월드를 복사하지 않게 — 검토 R1)
                log.error("save-all flush: 마감 안에 디스크까지 끝내지 못했다 — {}", incomplete);
                throw new IncompleteSaveException(incomplete);
            }
            // 바닐라 MinecraftServer.saveAllChunks(flush) 의 로그(이름은 ChunkMap.getStorageName = 차원 저장 폴더 이름)
            for (World w : worlds) if (!w.isUnloaded()) log.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", storageName(w));
            log.info("ThreadedAnvilChunkStorage: All dimensions are saved");
        }
    }

    /**
     * 바닐라 ChunkMap.flushWorker → IOWorker.synchronize(true): 넣은 기록이 다 끝나길 기다린 뒤 지역 파일을 디스크에 내린다.
     * 마감 안에 레인이 비지 않으면 그 사유를 돌려준다(없으면 null). force 실패는 바닐라처럼 로그만.
     */
    private static String flushToDisk(List<World> worlds, long deadline) throws InterruptedException {
        String incomplete = null;
        for (World w : worlds) {
            if (w.isUnloaded()) continue;
            try {
                w.ioBarrier().get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                if (incomplete == null) incomplete = w.dimension() + " IO 레인이 마감 안에 비지 않았다";
                continue;
            } catch (ExecutionException | RejectedExecutionException e) {
                if (incomplete == null) incomplete = w.dimension() + " IO 레인 대기 실패: " + e;
                continue;
            }
            for (RegionFileStore st : w.regionFileStores()) {
                try {
                    st.flush();
                } catch (java.io.IOException e) {
                    log.warn("Failed to synchronize chunks", e);   // 바닐라 IOWorker 문구
                }
            }
        }
        return incomplete;
    }

    /** 바닐라 ChunkMap.getStorageName: 차원 저장 폴더 이름(오버월드 = 월드 폴더, 네더 DIM-1, 엔드 DIM1). */
    static String storageName(World w) {
        try {
            java.nio.file.Path dir = w.dimensionType().storageDir(w.levelRoot()).toAbsolutePath().normalize();
            java.nio.file.Path name = dir.getFileName();
            return name != null ? name.toString() : w.dimension();
        } catch (RuntimeException e) {
            return w.dimension();
        }
    }

    /** 새 저장을 받지 않고, 돌고 있는 저장이 끝나길 기다린다(종료 저장 앞). 끝났으면 true. 끊지 않는다 — force 중 인터럽트는 파일 채널을 닫는다. */
    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        exec.shutdown();
        boolean done = exec.awaitTermination(timeout, unit);
        if (!done) stopping = true;   // 기다려도 안 끝났다: 남은 단계는 건너뛰게 하고 종료 저장이 대신 한다(R5)
        return done;
    }

    @Override
    public void close() {
        exec.shutdown();
    }
}
