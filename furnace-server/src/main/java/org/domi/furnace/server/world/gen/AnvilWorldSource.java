// 디스크(.mca) 우선 로드, 없으면 base WorldSource로 생성하는 WorldSource. 로드 청크는 clean, 새로 생성한 청크는 dirty(저장 대상).
// 깨진 청크(읽기·압축 풀기·NBT·디코드 실패)는 바닐라 ChunkMap.handleChunkLoadFailure 처럼 로그 후 새로 생성한다(A05-01) — 덮어쓰기 전에
// 원본 바이트를 <region>_corrupt/ 에 남긴다(바닐라 밖 친절). 이 서버보다 새 판(DataVersion)으로 저장된 청크는 새로 생성하지 않고 거부한다
// (Paper "Do not let the server load chunks from newer versions" — 모르는 블록이 공기로 지워지지 않게, A05-03).
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.anvil.NewerDataVersionException;
import org.domi.furnace.server.world.anvil.OlderDataVersionException;
import org.domi.furnace.server.world.anvil.UnsupportedDataVersionException;
import org.domi.furnace.server.world.anvil.RegionFile;
import java.io.IOException;
import java.nio.file.Files;
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
        return generate(chunkX, chunkZ, Runnable::run);
    }

    /** 동기 로드. 읽기만 ioLane 에서 하고(같은 파일의 앞선 저장 뒤에 줄 선다) 그 결과를 기다려 이 스레드에서 디코드·생성한다. */
    public ChunkColumn generate(int chunkX, int chunkZ, Executor ioLane) {
        UnsupportedDataVersionException refused = refusedNewer.get(chunkKey(chunkX, chunkZ));
        if (refused != null) throw refused;
        Read read;
        try {
            read = CompletableFuture.supplyAsync(() -> readPayload(chunkX, chunkZ), ioLane).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            if (e.getCause() instanceof Error err) throw err;
            throw e;
        }
        ChunkColumn col = decode(chunkX, chunkZ, read);
        if (col != null) return withPoi(chunkX, chunkZ, col);
        col = generateFallback(chunkX, chunkZ);
        col.markDirty();   // 바닐라처럼 새로 생성한 청크도 디스크에 남긴다(깨진 청크면 다음 저장이 덮는다)
        return col;
    }

    /** 레인에서 읽은 압축 본문. 읽기 자체가 실패했으면 failure(본문 없음). */
    private record Read(RegionFile.Payload payload, Exception failure) {}

    /** 레인에서: 압축 본문만 읽는다. 실패는 던지지 않고 담아 넘긴다(디코드 쪽이 깨진 청크로 다룬다). */
    private Read readPayload(int chunkX, int chunkZ) {
        try {
            return new Read(store.readPayload(chunkX, chunkZ), null);
        } catch (IOException | RuntimeException e) {
            return new Read(null, e);
        }
    }

    /**
     * 압축 풀기·NBT·디코드. 디스크에 없거나 완성되지 않은 청크(바닐라 월드 가장자리 proto 청크)·깨진 청크면 null — 부르는 쪽이 새로 생성한다.
     * 깨진 청크는 바닐라 handleChunkLoadFailure 처럼 로그를 남기고(원본 바이트는 백업) 새로 생성으로 넘긴다. Error 와 새 판 청크는 던진다.
     */
    private ChunkColumn decode(int chunkX, int chunkZ, Read read) {
        if (read.failure() != null) {
            corrupt(chunkX, chunkZ, read.failure(), null, null);
            return null;
        }
        RegionFile.Payload payload = read.payload();
        if (payload == null) return null;
        byte[] nbtBytes = null;
        try {
            nbtBytes = payload.decompress();
            return decodeChunk(nbtBytes);
        } catch (UnsupportedDataVersionException e) {
            refusedNewer.put(chunkKey(chunkX, chunkZ), e);
            throw e;
        } catch (IOException | RuntimeException e) {
            corrupt(chunkX, chunkZ, e, payload, nbtBytes);
            return null;
        }
    }

    /** 바닐라 MinecraftServer.reportChunkLoadFailure 문구로 남기고, 덮어쓰이기 전에 원본을 백업한다. */
    private void corrupt(int chunkX, int chunkZ, Exception cause, RegionFile.Payload payload, byte[] nbtBytes) {
        Path backup = backupCorrupt(chunkX, chunkZ, payload, nbtBytes);
        LOG.error("Failed to load chunk {},{} — 깨진 청크라 새로 생성한다(다음 저장에서 덮인다). 원본: {}",
                chunkX, chunkZ, backup != null ? backup : "(백업할 본문 없음)", cause);
    }

    /**
     * 원본을 {@code <region 폴더>_corrupt/c.X.Z-<시각>.nbt}(압축 푼 NBT) 또는 {@code .bin}(압축 종류 1바이트 + 압축 본문, region 섹터의 길이 뒤와 같다)로 남긴다.
     * 실패해도 새로 생성은 계속한다.
     */
    private Path backupCorrupt(int chunkX, int chunkZ, RegionFile.Payload payload, byte[] nbtBytes) {
        if (payload == null && nbtBytes == null) return null;
        try {
            Path regionDir = store.regionDir();
            Path dir = regionDir.resolveSibling(regionDir.getFileName() + "_corrupt");
            Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
            String base = "c." + chunkX + "." + chunkZ + "-" + stamp;
            if (nbtBytes != null) {
                Path f = dir.resolve(base + ".nbt");
                Files.write(f, nbtBytes);
                return f;
            }
            Path f = dir.resolve(base + ".bin");
            byte[] data = payload.data();
            byte[] out = new byte[data.length + 1];
            out[0] = (byte) payload.type();
            System.arraycopy(data, 0, out, 1, data.length);
            Files.write(f, out);
            return f;
        } catch (IOException | RuntimeException e) {
            LOG.warn("깨진 청크 {},{} 원본 백업 실패", chunkX, chunkZ, e);
            return null;
        }
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AnvilWorldSource.class);

    /** 거부한 새 판 청크 — 서버가 도는 동안 다시 읽지 않는다(파일을 바꿨으면 다시 켠다). */
    private final java.util.concurrent.ConcurrentHashMap<Long, UnsupportedDataVersionException> refusedNewer = new java.util.concurrent.ConcurrentHashMap<>();

    private static long chunkKey(int chunkX, int chunkZ) { return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL); }

    public CompletableFuture<ChunkColumn> loadChunkAsync(int chunkX, int chunkZ, Executor ioExecutor, Executor genExecutor) {
        return loadChunkAsync(chunkX, chunkZ, ioExecutor, ioExecutor, genExecutor);
    }

    /**
     * ioExecutor(그 지역 파일 레인)에서는 압축 본문 읽기만 하고, 압축 풀기·NBT 파싱·디코드는 decodeExecutor 에서 한다.
     * 레인은 같은 .mca 의 읽기·쓰기 순서만 지키면 되므로, 같은 파일 청크 수백 개(접속·순간이동)도 디코드는 여러 IO 스레드가 나눠 한다.
     * POI 읽기는 예전처럼 디코드 뒤 레인에 다시 줄 선다.
     */
    public CompletableFuture<ChunkColumn> loadChunkAsync(int chunkX, int chunkZ, Executor ioExecutor, Executor decodeExecutor,
                                                         Executor genExecutor) {
        UnsupportedDataVersionException refused = refusedNewer.get(chunkKey(chunkX, chunkZ));
        if (refused != null) return CompletableFuture.failedFuture(refused);   // 시야 미리 불러오기가 틱마다 다시 걸어도 다시 읽지 않는다
        return CompletableFuture.supplyAsync(() -> readPayload(chunkX, chunkZ), ioExecutor).thenCompose(read -> {
            CompletableFuture<ChunkColumn> decoded = read.payload() == null && read.failure() == null
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.supplyAsync(() -> decode(chunkX, chunkZ, read), decodeExecutor);
            return decoded.thenCompose(col -> {
                if (col != null) return CompletableFuture.supplyAsync(() -> withPoi(chunkX, chunkZ, col), ioExecutor);
                return CompletableFuture.supplyAsync(() -> {
                    ChunkColumn gen = generateFallback(chunkX, chunkZ);
                    gen.markDirty();
                    // 노이즈 생성기만 청크 생성 때 동물을 놓는다(NoiseBasedChunkGenerator.spawnOriginalMobs — 평지 생성기는 없다)
                    if (base instanceof NoiseWorldSource n && !n.isFlat()) gen.freshlyGenerated(true);
                    return gen;
                }, genExecutor);
            });
        });
    }

    private ChunkColumn generateFallback(int chunkX, int chunkZ) {
        if (base instanceof NoiseWorldSource noise) {
            return noise.generateWithFeatures(chunkX, chunkZ);
        }
        return base.generate(chunkX, chunkZ);
    }

    private ChunkColumn decodeChunk(byte[] nbtBytes) throws IOException {
        NbtCompound nbt = NbtIo.read(nbtBytes);
        NewerDataVersionException.check(nbt);
        OlderDataVersionException.check(nbt, "청크 " + (nbt.tags().get("xPos") instanceof Number x ? x.intValue() : 0) + "," + (nbt.tags().get("zPos") instanceof Number z ? z.intValue() : 0));
        ChunkColumn col = codec.fromNbt(nbt);
        if (col != null) col.clearDirty();
        return col;
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
            if (bytes != null) col.poiNbt(NbtIo.read(bytes));
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(AnvilWorldSource.class).warn("[POI] {},{} 읽기 실패 — 블록에서 다시 만든다", chunkX, chunkZ, e);
        }
        return col;
    }

    public void writePoi(int chunkX, int chunkZ, NbtCompound root) throws IOException {
        RegionFileStore ps = poiStore;
        if (ps == null) return;
        byte[] bytes = NbtIo.toBytes(root, "");
        synchronized (poiLock(chunkX, chunkZ)) {
            ps.writeChunk(chunkX, chunkZ, bytes);
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
        return bytes == null ? null : NbtIo.read(bytes);
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
        es.writeChunk(chunkX, chunkZ, NbtIo.toBytes(root, ""));
    }
}
