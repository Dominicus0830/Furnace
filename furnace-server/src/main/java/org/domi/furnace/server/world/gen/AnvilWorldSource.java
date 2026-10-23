// 디스크(.mca) 우선 로드, 없으면 base WorldSource로 생성하는 WorldSource. 로드 청크는 clean, 새로 생성한 청크는 dirty(저장 대상).
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
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
            byte[] nbtBytes = store.readChunk(chunkX, chunkZ);
            if (nbtBytes != null) {
                NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
                ChunkColumn col = codec.fromNbt(nbt);
                if (col != null) {
                    col.clearDirty();
                    return col;
                }
                // 완성되지 않은 청크(바닐라 월드 가장자리의 proto 청크) — 새로 생성한다.
            }
        } catch (IOException e) {
            throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
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
                return CompletableFuture.completedFuture(decoded);
            }
            return CompletableFuture.supplyAsync(() -> {
                ChunkColumn col = generateFallback(chunkX, chunkZ);
                col.markDirty();
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
    public AnvilChunkCodec codec() { return codec; }

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

    public void writeEntities(int chunkX, int chunkZ, NbtCompound root) throws IOException {
        RegionFileStore es = entityStore;
        if (es == null) return;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        NbtIo.write(new java.io.DataOutputStream(bos), root, "");
        es.writeChunk(chunkX, chunkZ, bos.toByteArray());
    }
}
