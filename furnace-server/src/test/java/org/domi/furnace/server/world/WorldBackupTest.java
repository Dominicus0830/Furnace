// O4 주기 백업: 기준점 뒤의 변경(청크 저장·플레이어 데이터·level.dat)은 백업에 들어가지 않고, 기준점 직전의 변경(아직 저장 안 한 dirty 청크)은
// 들어간다. 틱 붙잡기 동안 region 은 게임 틱을 돌지 않는다. zip 보관 수(keep)를 넘으면 오래된 것부터 지운다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.maintenance.MaintenanceOptions;
import org.domi.furnace.server.maintenance.WorldBackup;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldBackupTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos CP = new ChunkPos(1, 1);
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
    private static final int PLAINS = BIOMES.indexOf("minecraft:plains");

    private final List<AutoCloseable> closers = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (int i = closers.size() - 1; i >= 0; i--) closers.get(i).close();
    }

    private World world(Path dir) {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io = Executors.newFixedThreadPool(2);
        closers.add(sched::close);
        closers.add(io::shutdownNow);
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), BIOMES, PLAINS, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir), codec, new SuperflatWorldSource(REG, PLAINS));
        src.entityStore(new RegionFileStore(dir, "entities"));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, REG.directBits(), 7, io);
        w.setRegistries(REG, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 1_000_000);
        w.worldDir(dir);
        closers.add(() -> {
            for (var st : w.regionFileStores()) st.closeAll();
        });
        return w;
    }

    private static <T> T onRegion(World w, java.util.function.Function<Region, T> f) throws Exception {
        return w.submitAtChunk(CP, f).get(30, TimeUnit.SECONDS);
    }

    private static void setBlock(World w, String block) throws Exception {
        onRegion(w, r -> {
            ChunkColumn c = r.chunkAtBlocking(CP);
            c.setBlock(3, -50, 4, B.defaultState(block));
            r.markChunkDirty(CP);
            return null;
        });
    }

    private static int blockAt(World w) throws Exception {
        w.awaitChunk(CP);
        return onRegion(w, r -> r.chunkAtBlocking(CP).getBlock(3, -50, 4));
    }

    private WorldBackup backup(World w, Path worldDir, MaintenanceOptions o, ExecutorService playerIo) {
        return new WorldBackup(o, new WorldBackup.Hooks() {
            @Override public List<World> worlds() { return List.of(w); }
            @Override public void holdGlobal(boolean on) { }
            @Override public CompletableFuture<Void> quiesceGlobal() { return CompletableFuture.completedFuture(null); }
            @Override public List<CompletableFuture<Void>> requestPlayerSaves() {
                // 접속자 저장: 플레이어 데이터 실행기에 넣고 완료(실제 기록은 뒤따른다)
                CompletableFuture.runAsync(() -> write(worldDir.resolve("playerdata/p.dat"), "P-cut"), playerIo);
                return List.of(CompletableFuture.completedFuture(null));
            }
            @Override public void saveLevelData() { write(worldDir.resolve("level.dat"), "L-cut"); }
            @Override public java.util.concurrent.Executor playerIo() { return playerIo; }
            @Override public Path mainWorldDir() { return worldDir; }
            @Override public boolean anyoneOnline() { return false; }
            @Override public long joinCount() { return 0; }
        }, Runnable::run);
    }

    private static void write(Path p, String s) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, s);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Path unzip(Path zip, Path to) throws Exception {
        try (var in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            java.util.zip.ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                Path p = to.resolve(e.getName());
                Files.createDirectories(p.getParent());
                Files.copy(in, p);
            }
        }
        return to;
    }

    @Test
    void backupHoldsCutState_whileLaterWritesGoOn(@TempDir Path tmp) throws Exception {
        Path worldDir = tmp.resolve("world");
        World w = world(worldDir);
        write(worldDir.resolve("level.dat"), "L0");
        write(worldDir.resolve("data/plugin_custom.dat"), "R0");
        w.awaitChunk(CP);
        setBlock(w, "oak_planks");   // 아직 저장 안 함 — 기준점 저장이 실어야 한다
        ExecutorService playerIo = Executors.newSingleThreadExecutor();
        closers.add(playerIo::shutdownNow);
        var opts = new MaintenanceOptions(0, true, 0, tmp.resolve("backups"), 10, MaintenanceOptions.BackupFormat.ZIP, true, 10);
        WorldBackup b = backup(w, worldDir, opts, playerIo);
        // 기준점을 놓은 직후(복사 전): 청크를 바꿔 저장하고, 플레이어 데이터·level.dat 도 다시 쓴다
        b.afterReleaseForTest(() -> {
            try {
                setBlock(w, "stone");
                onRegion(w, r -> { r.saveDirtyChunks(); return null; });
                w.ioBarrier().get(30, TimeUnit.SECONDS);
                CompletableFuture.runAsync(() -> write(worldDir.resolve("playerdata/p.dat"), "P-later"), playerIo).get(30, TimeUnit.SECONDS);
                write(worldDir.resolve("level.dat"), "L-later");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        var r = b.run("시험");
        assertTrue(r.ok(), r.message());
        assertTrue(r.copiedOnWrite() >= 1, "기준점 뒤 저장이 먼저 와서 쓰기 전 복사가 일어났다: " + r.message());
        assertTrue(Files.isRegularFile(r.output()));

        Path out = unzip(r.output(), tmp.resolve("restored"));
        Path restored = out.resolve("world");
        assertEquals("L-cut", Files.readString(restored.resolve("level.dat")));
        assertEquals("R0", Files.readString(restored.resolve("data/plugin_custom.dat")));
        assertEquals("P-cut", Files.readString(restored.resolve("playerdata/p.dat")));
        try (var st = Files.list(tmp.resolve("backups"))) {
            assertTrue(st.noneMatch(p -> p.getFileName().toString().startsWith(".")), "임시 폴더·조각 파일은 남기지 않는다");
        }

        assertEquals(B.defaultState("stone"), blockAt(w), "원본은 기준점 뒤 상태");
        World back = world(restored);
        assertEquals(B.defaultState("oak_planks"), blockAt(back), "백업은 기준점 상태(기준점 직전 dirty 청크 포함, 뒤 변경 제외)");
    }

    @Test
    void heldWorld_runsNoGameTicks(@TempDir Path tmp) throws Exception {
        World w = world(tmp.resolve("world"));
        w.awaitChunk(CP);
        w.startTicking();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.regionizer().tickTimes().samples() < 3) {
            assertTrue(System.nanoTime() < deadline, "틱이 돌아야 한다");
            Thread.sleep(10);
        }
        w.holdTicks(true);
        w.quiesce().get(10, TimeUnit.SECONDS);
        int held = w.regionizer().tickTimes().samples();
        Thread.sleep(300);
        assertEquals(held, w.regionizer().tickTimes().samples(), "붙잡힌 동안 게임 틱 없음");
        assertEquals(Boolean.TRUE, onRegion(w, r -> true), "작업 큐는 돈다");
        w.holdTicks(false);
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.regionizer().tickTimes().samples() <= held) {
            assertTrue(System.nanoTime() < deadline, "놓으면 다시 돈다");
            Thread.sleep(10);
        }
    }

    @Test
    void keepPrunesOldest(@TempDir Path tmp) throws Exception {
        Path worldDir = tmp.resolve("world");
        World w = world(worldDir);
        w.awaitChunk(CP);
        Path dir = tmp.resolve("backups");
        Files.createDirectories(dir);
        for (String old : List.of("20200101-000000.zip", "20200102-000000.zip", "20200103-000000.zip")) Files.writeString(dir.resolve(old), "x");
        Files.writeString(dir.resolve("my-notes.txt"), "keep me");
        ExecutorService playerIo = Executors.newSingleThreadExecutor();
        closers.add(playerIo::shutdownNow);
        var opts = new MaintenanceOptions(0, true, 0, dir, 2, MaintenanceOptions.BackupFormat.ZIP, true, 10);
        var r = backup(w, worldDir, opts, playerIo).run("시험");
        assertTrue(r.ok(), r.message());
        try (var st = Files.list(dir)) {
            var names = st.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of("20200103-000000.zip", r.output().getFileName().toString(), "my-notes.txt"), names);
        }
    }

    @Test
    void backupDirectoryInsideWorld_isRefused(@TempDir Path tmp) throws Exception {
        Path worldDir = tmp.resolve("world");
        World w = world(worldDir);
        ExecutorService playerIo = Executors.newSingleThreadExecutor();
        closers.add(playerIo::shutdownNow);
        var opts = new MaintenanceOptions(0, true, 0, worldDir.resolve("backups"), 2, MaintenanceOptions.BackupFormat.ZIP, true, 10);
        var r = backup(w, worldDir, opts, playerIo).run("시험");
        assertFalse(r.ok());
        assertFalse(w.ticksHeld(), "실패해도 틱을 놓는다");
    }
}
