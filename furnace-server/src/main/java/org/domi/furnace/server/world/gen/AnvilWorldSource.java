// 디스크(.mca) 우선 로드, 없으면 base WorldSource로 생성하는 WorldSource. 로드 청크는 clean.
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
                col.clearDirty();
                return col;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("청크 로드 실패 " + chunkX + "," + chunkZ, e);
        }
        ChunkColumn col = base.generate(chunkX, chunkZ);
        col.clearDirty();
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
            if (nbtBytes != null) {
                return CompletableFuture.completedFuture(decodeChunk(nbtBytes));
            }
            return CompletableFuture.supplyAsync(() -> {
                ChunkColumn col = base.generate(chunkX, chunkZ);
                col.clearDirty();
                return col;
            }, genExecutor);
        });
    }

    private ChunkColumn decodeChunk(byte[] nbtBytes) {
        try {
            NbtCompound nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(nbtBytes)));
            ChunkColumn col = codec.fromNbt(nbt);
            col.clearDirty();
            return col;
        } catch (IOException e) {
            throw new UncheckedIOException("청크 디코드 실패", e);
        }
    }

    public RegionFileStore store() { return store; }
    public AnvilChunkCodec codec() { return codec; }
}
