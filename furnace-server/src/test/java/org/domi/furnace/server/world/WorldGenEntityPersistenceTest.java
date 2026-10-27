// 월드젠 엔티티는 한 번만 들어간다: Anvil 저장 → 퇴출 → 재로드를 되풀이해도 늘지 않는다(청크 파일의 "entities" 를 지우고 다시 쓴다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldGenEntityPersistenceTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos CP = new ChunkPos(1, 1);

    private static NbtCompound villager() {
        NbtCompound t = new NbtCompound();
        t.putString("id", "minecraft:villager");
        NbtList pos = new NbtList((byte) 6);
        pos.add(20.5);
        pos.add(-60.0);
        pos.add(20.5);
        t.put("Pos", pos);
        return t;
    }

    /** 청크를 올리고 엔티티 묶음까지 읽힌 뒤 주민 수를 센다. 그다음 저장·퇴출하고 IO 를 기다린다. */
    private static int loadCountAndEvict(World w, FurnaceSchedulerImpl sched) throws Exception {
        sched.region(R00).execute(() -> w.regionAt(R00).chunkAtBlocking(CP)).get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            boolean loaded = sched.region(R00).submit(() -> {
                ChunkColumn c = w.loadedChunk(CP.x(), CP.z());
                return c != null && c.entitiesLoaded();
            }).get();
            if (loaded) break;
            assertTrue(System.nanoTime() < deadline, "엔티티 묶음 읽기 대기 시간 초과");
            Thread.onSpinWait();
        }
        int n = sched.region(R00).submit(() -> {
            Region r = w.regionAt(R00);
            int c = (int) r.level().entities().tickSnapshot().stream().filter(e -> e instanceof Villager && !e.isRemoved()).count();
            for (int i = 0; i < 10; i++) r.tick();   // 구독 없음 → 저장 후 퇴출(유예 3틱)
            assertFalse(r.chunkLoaded(CP), "퇴출돼야 다시 디스크에서 읽는다");
            return c;
        }).get();
        // 저장·엔티티 쓰기는 청크 IO 레인 — 다음 로드도 같은 레인을 지나므로 순서가 지켜진다
        return n;
    }

    private static World world(Path dir, WorldSource base, BlockRegistry reg, FurnaceSchedulerImpl sched, java.util.concurrent.ExecutorService io) {
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir), new AnvilChunkCodec(reg, 0), base);
        src.entityStore(new RegionFileStore(dir.resolve("entities")));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 3);
        return w;
    }

    @Test
    void freshVillageChunkSpawnsOnceAcrossReloads(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var flat = new SuperflatWorldSource(reg, 0);
        WorldSource base = (cx, cz) -> {
            ChunkColumn c = flat.generate(cx, cz);
            if (cx == CP.x() && cz == CP.z()) c.addGeneratedEntity(villager());
            return c;
        };
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        var io = Executors.newFixedThreadPool(2);
        World w = world(dir, base, reg, sched, io);
        for (int i = 0; i < 3; i++) assertEquals(1, loadCountAndEvict(w, sched), "로드 " + i + " 회째에도 주민 하나");
        io.shutdown();
        sched.close();
    }

    @Test
    void legacyDiskChunkWithLeftoverEntitiesSpawnsOnce(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var flat = new SuperflatWorldSource(reg, 0);
        // 이 수정 이전에 저장돼 청크 파일에 월드젠 엔티티("entities")가 남은 청크
        ChunkColumn old = flat.generate(CP.x(), CP.z());
        old.addGeneratedEntity(villager());
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), codec.toNbt(old, CP.x(), CP.z()), "");
        RegionFileStore pre = new RegionFileStore(dir);
        pre.writeChunk(CP.x(), CP.z(), bos.toByteArray());
        pre.closeAll();

        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        var io = Executors.newFixedThreadPool(2);
        World w = world(dir, flat, reg, sched, io);
        for (int i = 0; i < 3; i++) assertEquals(1, loadCountAndEvict(w, sched), "로드 " + i + " 회째에도 주민 하나");
        io.shutdown();
        sched.close();
    }
}
