// 실제 World/Region(autosave·퇴출·종료flush 경로)으로 "편집 없는 청크는 저장되지 않는다"와 재시작 후 바이옴/블록을 검증하는 일회성 도구
package org.domi.furnace.server.world;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.*;
import org.domi.furnace.server.world.gen.*;

public class WorldPersistCheck {
    static int savedChunks(Path dir) throws Exception {
        Path reg = dir.resolve("region");
        if (!Files.exists(reg)) return 0;
        int total = 0;
        try (var s = Files.list(reg)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                byte[] f = Files.readAllBytes(p);
                for (int i = 0; i < 1024 && f.length >= 4096; i++) if (f[i*4] != 0 || f[i*4+1] != 0 || f[i*4+2] != 0) total++;
            }
        }
        return total;
    }
    static int[] biomes(ChunkSnapshot s) { int[] o = new int[1536]; int k = 0; for (var sec : s.sections()) for (int b : sec.biomes()) o[k++] = b; return o; }
    static int[] blocks(ChunkColumn c) { int[] o = new int[16*16*384]; int k = 0; for (int y = -64; y < 320; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) o[k++] = c.getBlock(x, y, z); return o; }

    record Env(World w, FurnaceSchedulerImpl sched, RegionFileStore store, ExecutorService io) {}

    static Env boot(Path dir, long seed, BlockRegistry reg, RegistrySet r, NoiseWorldSource noise) {
        int plains = Math.max(0, r.indexOf("minecraft:worldgen/biome", "minecraft:plains"));          // FurnaceMain 과 동일
        int biomeBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(r.entries("minecraft:worldgen/biome").size() - 1));
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, plains), noise);
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), biomeBits, io, io);
        w.configurePersistence(3, 2);     // autosave 3틱, 퇴출 grace 2틱 → 짧게 돌려 모든 저장 경로를 태운다
        w.worldDir(dir);
        return new Env(w, sched, store, io);
    }

    /** region 스레드서 25청크(0..4,0..4) 비동기 로드 → 스냅샷/블록 수집 */
    static Map<Integer, Object[]> loadAll(Env e, RegionPos rp) throws Exception {
        List<CompletableFuture<ChunkColumn>> fs = new ArrayList<>();
        e.sched.region(rp).submit(() -> {
            Region region = e.w.regionAt(rp);
            for (int cz = 0; cz < 5; cz++) for (int cx = 0; cx < 5; cx++) fs.add(region.loadChunk(new ChunkPos(cx, cz)));
            return null;
        }).get();
        Map<Integer, Object[]> out = new LinkedHashMap<>();
        int i = 0;
        for (int cz = 0; cz < 5; cz++) for (int cx = 0; cx < 5; cx++) {
            ChunkColumn c = fs.get(i++).get(120, TimeUnit.SECONDS);
            out.put(cz * 5 + cx, new Object[]{ biomes(ChunkSnapshot.of(cx, cz, c)), blocks(c) });
        }
        return out;
    }

    public static void main(String[] a) throws Exception {
        long seed = Long.parseLong(a[0]);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet r = RegistrySet.load("registries/1.21.1.txt");
        List<String> names = r.entries("minecraft:worldgen/biome");
        NoiseWorldSource noise = NoiseWorldSource.overworld(seed, reg, r);
        Path dir = Files.createTempDirectory("furnace-world");
        RegionPos rp = new RegionPos(0, 0);

        // ---- 1차 실행: 편집 없이 로드 → autosave/퇴출/종료 flush 전부 태움 ----
        Env e1 = boot(dir, seed, reg, r, noise);
        Map<Integer, Object[]> run1 = loadAll(e1, rp);
        e1.sched.region(rp).submit(() -> { Region reg0 = e1.w.regionAt(rp); for (int i = 0; i < 8; i++) reg0.tick(); reg0.saveDirtyChunks(); return null; }).get();
        Thread.sleep(800);
        System.out.println("[phase A] 편집 없이 25청크 로드 + tick(autosave·퇴출) + 종료flush 후 디스크 저장 청크 수 = " + savedChunks(dir));

        // ---- 같은 실행에서 청크 (0,0) 한 블록 설치(플레이어 편집) ----
        e1.sched.region(rp).submit(() -> {
            Region region = e1.w.regionAt(rp);
            // 퇴출로 비워졌을 수 있으니 다시 로드 후 편집 (chunkAt 은 동기 generate 폴백)
            region.placeBlock(0, 319, 0, reg.defaultStateId("minecraft:stone"));
            region.saveDirtyChunks();
            return null;
        }).get();
        Thread.sleep(800);
        System.out.println("[phase B] 청크 (0,0)에 블록 1개 설치 + 저장 후 디스크 저장 청크 수 = " + savedChunks(dir));
        e1.io.shutdown(); e1.store.closeAll(); e1.sched.close();

        // ---- 서버 재시작: 새 World/AnvilWorldSource 로 같은 25청크 로드 ----
        Env e2 = boot(dir, seed, reg, r, noise);
        Map<Integer, Object[]> run2 = loadAll(e2, rp);
        for (int idx = 0; idx < 25; idx++) {
            int[] b1 = (int[]) run1.get(idx)[0], b2 = (int[]) run2.get(idx)[0];
            int[] k1 = (int[]) run1.get(idx)[1], k2 = (int[]) run2.get(idx)[1];
            int bc = 0, kc = 0; for (int i = 0; i < b1.length; i++) if (b1[i] != b2[i]) bc++; for (int i = 0; i < k1.length; i++) if (k1[i] != k2[i]) kc++;
            Set<String> d2 = new TreeSet<>(); for (int v : b2) d2.add(names.get(v));
            if (bc > 0 || kc > 0 || idx == 1 || idx == 6) System.out.printf("[phase C] chunk(%d,%d) 재시작 후 바이옴 변경 %d/1536, 블록 변경 %d (재시작 후 바이옴 종류=%s)%n", idx % 5, idx / 5, bc, kc, d2);
        }
        int changedBiomeChunks = 0, changedBlockChunks = 0;
        for (int idx = 0; idx < 25; idx++) {
            if (!Arrays.equals((int[]) run1.get(idx)[0], (int[]) run2.get(idx)[0])) changedBiomeChunks++;
            if (!Arrays.equals((int[]) run1.get(idx)[1], (int[]) run2.get(idx)[1])) changedBlockChunks++;
        }
        System.out.println("[phase C] 25청크 중 바이옴이 바뀐 청크 수=" + changedBiomeChunks + "  블록이 바뀐 청크 수=" + changedBlockChunks);
        e2.io.shutdown(); e2.store.closeAll(); e2.sched.close();
        System.exit(0);
    }
}
