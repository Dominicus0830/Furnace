// 디스크(.mca) 우선 로드, 없으면 base WorldSource로 생성하는 WorldSource. 로드 청크는 clean, 새로 생성한 청크는 dirty(저장 대상).
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AnvilWorldSource implements WorldSource {

    private final Path worldDir;
    private final RegionFileStore store;
    private final AnvilChunkCodec codec;
    private final WorldSource base; // 디스크 없을 때 폴백 생성

    public AnvilWorldSource(Path worldDir, RegionFileStore store, AnvilChunkCodec codec, WorldSource base) {
        this.worldDir = worldDir;
        this.store = store;
        this.codec = codec;
        this.base = base;
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        try {
            return withPoi(chunkX, chunkZ, fromBytesOrGenerate(chunkX, chunkZ, store.readChunk(chunkX, chunkZ)));
        } catch (IOException e) {
            throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
        }
    }

    /** 동기 로드. 읽기만 ioLane 에서 하고(같은 파일의 앞선 저장 뒤에 줄 선다) 그 결과를 기다려 이 스레드에서 디코드·생성한다. */
    public ChunkColumn generate(int chunkX, int chunkZ, Executor ioLane) {
        byte[] bytes;
        try {
            bytes = CompletableFuture.supplyAsync(() -> {
                try {
                    return store.readChunk(chunkX, chunkZ);
                } catch (IOException e) {
                    throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
                }
            }, ioLane).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
        try {
            return withPoi(chunkX, chunkZ, fromBytesOrGenerate(chunkX, chunkZ, bytes));
        } catch (IOException e) {
            throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
        }
    }

    private ChunkColumn fromBytesOrGenerate(int chunkX, int chunkZ, byte[] nbtBytes) throws IOException {
        if (nbtBytes != null) {
            NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
            ChunkColumn col = codec.fromNbt(nbt);
            if (col != null) {
                col.clearDirty();
                return col;
            }
            // 완성되지 않은 청크(바닐라 월드 가장자리의 proto 청크) — 새로 생성한다.
        }
        ChunkColumn col = generateFallback(chunkX, chunkZ);
        col.markDirty();   // 바닐라처럼 새로 생성한 청크도 디스크에 남긴다
        return col;
    }

    public CompletableFuture<ChunkColumn> loadChunkAsync(int chunkX, int chunkZ, Executor ioExecutor, Executor genExecutor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return store.readChunk(chunkX, chunkZ);
            } catch (IOException e) {
                throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
            }
        }, ioExecutor).thenCompose(nbtBytes -> {
            ChunkColumn decoded = nbtBytes != null ? decodeChunk(nbtBytes) : null;
            if (decoded != null) {
                return CompletableFuture.supplyAsync(() -> withPoi(chunkX, chunkZ, decoded), ioExecutor);
            }
            return CompletableFuture.supplyAsync(() -> {
                ChunkColumn col = generateFallback(chunkX, chunkZ);
                col.markDirty();
                // 노이즈 생성기만 청크 생성 때 동물을 놓는다(NoiseBasedChunkGenerator.spawnOriginalMobs — 평지 생성기는 없다)
                if (base instanceof NoiseWorldSource) col.freshlyGenerated(true);
                return col;
            }, genExecutor);
        });
    }

    private ChunkColumn generateFallback(int chunkX, int chunkZ) {
        if (base instanceof NoiseWorldSource noise) {
            return noise.generateWithFeatures(chunkX, chunkZ);
        }
        return base.generate(chunkX, chunkZ);
    }

    private ChunkColumn decodeChunk(byte[] nbtBytes) {
        try {
            NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
            ChunkColumn col = codec.fromNbt(nbt);
            if (col != null) col.clearDirty();
            return col;
        } catch (IOException e) {
            throw new UncheckedIOException("청크 디코드 실패", e);
        }
    }

    public RegionFileStore store() { return store; }
    /** 디스크에 없을 때 쓰는 생성기. */
    public WorldSource base() { return base; }
    public AnvilChunkCodec codec() { return codec; }

    // ------------------------------------------------------------------ POI(poi/ 지역 파일 — 바닐라 PoiManager 의 SectionStorage)

    private volatile RegionFileStore poiStore;

    // C-5(F-01 후속): 저장소 전체 잠금 대신 청크별(줄무늬) 잠금. 같은 청크의 읽기·쓰기·덧붙이기(읽고·쓰기)만 서로 배제하면 되고,
    // 다른 청크끼리는 RegionFileStore 의 파일 잠금이 섹터 기록을 지킨다 — NBT 인코딩·deflate 가 레인끼리 줄 서지 않는다.
    private static final int LOCK_STRIPES = 256;
    private final Object[] poiLocks = stripes(), entityLocks = stripes();

    private static Object[] stripes() {
        Object[] a = new Object[LOCK_STRIPES];
        for (int i = 0; i < a.length; i++) a[i] = new Object();
        return a;
    }

    private static int stripe(int chunkX, int chunkZ) {
        int h = chunkX * 0x9E3779B1 ^ chunkZ * 0x85EBCA6B;
        return (h ^ (h >>> 16)) & (LOCK_STRIPES - 1);
    }

    /** 그 청크 POI 묶음의 읽기·쓰기 잠금. */
    public Object poiLock(int chunkX, int chunkZ) { return poiLocks[stripe(chunkX, chunkZ)]; }

    /** 그 청크 엔티티 묶음의 읽기·쓰기·덧붙이기 잠금. */
    public Object entityLock(int chunkX, int chunkZ) { return entityLocks[stripe(chunkX, chunkZ)]; }

    public void poiStore(RegionFileStore s) { this.poiStore = s; }

    public RegionFileStore poiStore() { return poiStore; }

    /** 디스크의 POI 묶음을 청크에 붙인다(복구는 올릴 때). 읽기 실패는 경고만 — 블록에서 다시 만든다. */
    private ChunkColumn withPoi(int chunkX, int chunkZ, ChunkColumn col) {
        RegionFileStore ps = poiStore;
        if (ps == null || col == null) return col;
        try {
            byte[] bytes;
            synchronized (poiLock(chunkX, chunkZ)) {
                bytes = ps.readChunk(chunkX, chunkZ);
            }
            if (bytes != null) col.poiNbt(NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes))));
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(AnvilWorldSource.class).warn("[POI] {},{} 읽기 실패 — 블록에서 다시 만든다", chunkX, chunkZ, e);
        }
        return col;
    }

    public void writePoi(int chunkX, int chunkZ, NbtCompound root) throws IOException {
        RegionFileStore ps = poiStore;
        if (ps == null) return;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        NbtIo.write(new java.io.DataOutputStream(bos), root, "");
        synchronized (poiLock(chunkX, chunkZ)) {
            ps.writeChunk(chunkX, chunkZ, bos.toByteArray());
        }
    }

    // ------------------------------------------------------------------ 엔티티(entities/ 지역 파일 — 바닐라 EntityStorage)

    private volatile RegionFileStore entityStore;

    public void entityStore(RegionFileStore s) { this.entityStore = s; }

    public RegionFileStore entityStore() { return entityStore; }

    /** 그 청크의 엔티티 묶음({DataVersion, Position, Entities}). 없으면 null. IO 스레드에서. */
    public NbtCompound readEntities(int chunkX, int chunkZ) throws IOException {
        RegionFileStore es = entityStore;
        if (es == null) return null;
        byte[] bytes = es.readChunk(chunkX, chunkZ);
        return bytes == null ? null : NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    /**
     * 디스크의 그 청크 엔티티 묶음에 덧붙인다(읽고 → 더하고 → 쓰기). 호출자가 {@link #entityLock} 을 잡는다.
     * 디스크의 Entities 가 빈 리스트면 원소 종류가 TAG_END 로 읽힌다 — 그대로 compound 를 넣으면 쓰기에서 터지므로 compound 리스트로 새로 만든다.
     */
    public void appendEntities(int chunkX, int chunkZ, java.util.List<NbtCompound> tags) throws IOException {
        NbtCompound root = readEntities(chunkX, chunkZ);
        if (root == null) {
            root = new NbtCompound();
            root.putInt("DataVersion", org.domi.furnace.server.world.anvil.AnvilChunkCodec.DATA_VERSION);
            root.putIntArray("Position", new int[] {chunkX, chunkZ});
        }
        NbtList list = root.tags().get("Entities") instanceof NbtList l && l.size() > 0 && l.elementType() == NbtIo.TAG_COMPOUND
                ? l : new NbtList(NbtIo.TAG_COMPOUND);
        for (NbtCompound t : tags) list.add(t);
        root.putList("Entities", list);
        writeEntities(chunkX, chunkZ, root);
    }

    public void writeEntities(int chunkX, int chunkZ, NbtCompound root) throws IOException {
        RegionFileStore es = entityStore;
        if (es == null) return;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        NbtIo.write(new java.io.DataOutputStream(bos), root, "");
        es.writeChunk(chunkX, chunkZ, bos.toByteArray());
    }
}
