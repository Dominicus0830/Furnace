// P-O14-10: 저장 스레드에서 도는 save-all. flush 가 아니면 청크를 IO 레인에 넣는 데까지만(바닐라 IOWorker 처럼 디스크 기록은 기다리지 않는다),
// flush 면 레인 기록이 다 끝나고 지역 파일을 force(true) 한 뒤 완료된다. 스냅샷은 부른 스레드에서, 파일 쓰기는 저장 스레드에서.
package org.domi.furnace.server.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerSaveTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final ItemRegistry ITEMS = ItemRegistry.load("items/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
    private static final int PLAINS = BIOMES.indexOf("minecraft:plains");
    private static final int N = 4;

    @TempDir Path dir;
    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private ExecutorService playerIo;
    private ServerSave save;

    @AfterEach
    void tearDown() throws Exception {
        if (save != null) save.shutdown(10, TimeUnit.SECONDS);
        if (io != null) io.shutdownNow();
        if (playerIo != null) playerIo.shutdownNow();
        if (sched != null) sched.close();
    }

    private World world() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        io = Executors.newSingleThreadExecutor();
        playerIo = Executors.newSingleThreadExecutor();
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir), new AnvilChunkCodec(REG, ITEMS, BIOMES, PLAINS,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT), new SuperflatWorldSource(REG, PLAINS));
        src.entityStore(new RegionFileStore(dir, "entities"));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, REG.directBits(), 7, io);
        w.setRegistries(REG, ITEMS);
        w.configurePersistence(1_000_000, 1_000_000);
        w.ioBacklogLimit(100_000);   // 막힌 레인에서도 역압 없이 다 넣는다(flush 없는 저장이 레인을 기다리지 않는 것을 보려고)
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) w.awaitChunk(new ChunkPos(x, z));
        return w;
    }

    private static final class Calls implements ServerSave.Hooks {
        final World w;
        final ExecutorService playerIo;
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Thread> snapshotThreads = new CopyOnWriteArrayList<>();
        final List<Thread> writeThreads = new CopyOnWriteArrayList<>();
        final CompletableFuture<Void> playerSave = new CompletableFuture<>();

        Calls(World w, ExecutorService playerIo) { this.w = w; this.playerIo = playerIo; }

        @Override public List<World> worlds() { return List.of(w); }
        @Override public List<CompletableFuture<Void>> requestPlayerSaves() {
            events.add("players");
            return new ArrayList<>(List.of(playerSave));
        }
        @Override public java.util.concurrent.Executor playerIo() { return playerIo; }
        @Override public void snapshotLevelData() {
            events.add("snapshot");
            snapshotThreads.add(Thread.currentThread());
        }
        @Override public void writeLevelData() {
            events.add("write");
            writeThreads.add(Thread.currentThread());
        }
    }

    private static int onDisk(Path dir) throws Exception {
        RegionFileStore check = new RegionFileStore(dir);
        int n = 0;
        for (int x = 0; x < N; x++) for (int z = 0; z < N; z++) if (check.readChunk(x, z) != null) n++;
        check.closeAll();
        return n;
    }

    /** flush 없음: IO 레인이 막혀 있어도 청크를 레인에 넣고 플레이어·level.dat 을 쓰면 끝난다(바닐라도 IOWorker 기록을 기다리지 않는다). */
    @Test
    void plainSaveDoesNotWaitForDiskWrites() throws Exception {
        World w = world();
        Calls calls = new Calls(w, playerIo);
        save = new ServerSave(calls);
        CountDownLatch gate = new CountDownLatch(1);
        io.execute(() -> { try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        CompletableFuture<Void> f = save.save(false);
        assertEquals(List.of("players", "snapshot"), calls.events, "스냅샷은 부른 스레드에서 바로 뜬다");
        assertEquals(Thread.currentThread(), calls.snapshotThreads.get(0));
        Thread.sleep(200);
        assertFalse(f.isDone(), "플레이어 저장이 끝나기 전에는 끝나지 않는다(바닐라 PlayerList.saveAll 은 동기 기록)");
        calls.playerSave.complete(null);
        f.get(30, TimeUnit.SECONDS);
        assertEquals(List.of("players", "snapshot", "write"), calls.events);
        assertNotEquals(Thread.currentThread(), calls.writeThreads.get(0));
        assertEquals("Furnace Save Thread", calls.writeThreads.get(0).getName());
        assertEquals(0, onDisk(dir), "레인이 막혀 있으니 아직 디스크에는 없다 — flush 없는 저장은 그것을 기다리지 않았다");
        gate.countDown();
        w.ioBarrier().get(30, TimeUnit.SECONDS);
        assertEquals(N * N, onDisk(dir));
    }

    /** flush: 레인 기록이 다 끝나고 지역 파일을 force 한 뒤에야 끝난다. */
    @Test
    void flushWaitsForDiskAndForcesFiles() throws Exception {
        World w = world();
        Calls calls = new Calls(w, playerIo);
        calls.playerSave.complete(null);
        save = new ServerSave(calls);
        CountDownLatch gate = new CountDownLatch(1);
        io.execute(() -> { try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        long forcedBefore = w.regionFileStores().get(0).flushedFiles();

        CompletableFuture<Void> f = save.save(true);
        Thread.sleep(300);
        assertFalse(f.isDone(), "flush 는 IO 레인 기록이 끝날 때까지 기다린다");
        gate.countDown();
        f.get(30, TimeUnit.SECONDS);
        assertEquals(N * N, onDisk(dir), "flush 가 끝나면 모든 청크가 디스크에 있다");
        assertTrue(w.regionFileStores().get(0).flushedFiles() > forcedBefore, "지역 파일을 force(true) 했다");
        assertEquals(List.of("players", "snapshot", "write"), calls.events);
    }

    /** 저장은 한 번에 하나씩 차례로(저장 스레드 하나). 닫은 뒤의 요청은 실패로 끝난다(종료 저장이 대신한다). */
    @Test
    void savesRunOneAtATimeAndRejectAfterShutdown() throws Exception {
        World w = world();
        Calls calls = new Calls(w, playerIo);
        calls.playerSave.complete(null);
        save = new ServerSave(calls);
        CompletableFuture<Void> a = save.save(false);
        CompletableFuture<Void> b = save.save(true);
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        assertEquals(List.of("players", "snapshot", "players", "snapshot", "write", "write"), calls.events);
        assertTrue(save.shutdown(10, TimeUnit.SECONDS));
        assertTrue(save.save(false).isCompletedExceptionally());
    }

    /**
     * 교차 검토 필수 조건(RCON 백업 스크립트 `save-off; save-all flush; 복사`): RCON 응답은 명령 컨텍스트가 끝날 때 돌아오므로,
     * save-all flush 의 응답이 온 시점에는 모든 청크가 디스크에 있어야 한다. 기록 레인이 막혀 있는 동안은 응답하지 않는다.
     * flush 없는 save-all 은 바닐라처럼 디스크 기록을 기다리지 않고 응답한다.
     */
    @Test
    void rconSaveAllFlushRespondsOnlyAfterChunksAreOnDisk() throws Exception {
        World w = world();
        Calls calls = new Calls(w, playerIo);
        calls.playerSave.complete(null);
        save = new ServerSave(calls);
        org.domi.furnace.server.ServerCore core = org.domi.furnace.server.TestServers.core(dir.resolve("server"), sched);
        core.onSaveAll(save::save);
        CountDownLatch gate = new CountDownLatch(1);
        io.execute(() -> { try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });

        // flush 없음: 레인이 막혀 있어도 응답한다(디스크에는 아직 없다)
        String plain = core.runRconCommand("save-all");
        assertTrue(plain.contains("Saved the game"), plain);
        assertEquals(0, onDisk(dir));

        CompletableFuture<String> resp = CompletableFuture.supplyAsync(() -> {
            try {
                return core.runRconCommand("save-all flush");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(400);
        assertFalse(resp.isDone(), "기록 레인이 막혀 있는 동안 RCON save-all flush 는 응답하지 않는다");
        assertEquals(42, sched.global().submit(() -> 42).get(5, TimeUnit.SECONDS), "그동안 전역 스레드는 돈다");
        gate.countDown();
        String text = resp.get(60, TimeUnit.SECONDS);
        assertEquals(N * N, onDisk(dir), "응답이 온 때 모든 청크가 디스크에 있다 — 바로 복사해도 된다");
        assertTrue(text.startsWith("Saving the game (this may take a moment!)") && text.contains("Saved the game"), text);
    }

    @Test
    void storageNameIsDimensionFolder() {
        World w = world();
        assertEquals("world", ServerSave.storageName(w), "오버월드 = 월드 폴더 이름(바닐라 ChunkMap.getStorageName)");
    }
}
