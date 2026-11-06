// 바닐라 ForcedChunksSavedData("chunks" → <차원 폴더>/data/chunks.dat, gzip {data:{Forced:[long…]}, DataVersion}): /forceload 로 붙잡아 둔 청크.
// 키는 바닐라 ChunkPos.asLong(x 가 아래 32비트, z 가 위 32비트)이라 파일과 목록 정렬 순서가 바닐라와 같다.
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForcedChunks {
    private static final Logger LOG = LoggerFactory.getLogger(ForcedChunks.class);
    private static final int DATA_VERSION = 3955;

    private final Path file;
    private final Set<Long> chunks = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    private ForcedChunks(Path file) { this.file = file; }

    /** 파일 없이 메모리에서만. */
    public static ForcedChunks detached() { return new ForcedChunks(null); }

    /** <차원 폴더>/data/chunks.dat 을 읽는다(없거나 깨졌으면 빈 집합). */
    public static ForcedChunks open(Path dimensionDir) {
        ForcedChunks f = new ForcedChunks(dimensionDir.resolve("data").resolve("chunks.dat"));
        f.load();
        return f;
    }

    /** 바닐라 ChunkPos.asLong. */
    public static long asLong(int x, int z) { return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32; }

    public static int xOf(long key) { return (int) key; }

    public static int zOf(long key) { return (int) (key >> 32); }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readGzip(in);
            NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
            if (data.tags().get("Forced") instanceof long[] forced) for (long k : forced) chunks.add(k);
        } catch (IOException | RuntimeException e) {
            LOG.warn("chunks.dat 읽기 실패 — 강제 로드 청크 없이 시작", e);
        }
    }

    public boolean contains(int x, int z) { return chunks.contains(asLong(x, z)); }

    public int size() { return chunks.size(); }

    /** 정렬한 키 목록(바닐라 목록 출력 순서: 부호 있는 long 오름차순). */
    public long[] sortedKeys() {
        long[] a = chunks.stream().mapToLong(Long::longValue).toArray();
        java.util.Arrays.sort(a);
        return a;
    }

    /** 바닐라 ServerLevel.setChunkForced 의 집합 부분: 바뀌었으면 참. */
    public boolean set(int x, int z, boolean forced) {
        boolean changed = forced ? chunks.add(asLong(x, z)) : chunks.remove(asLong(x, z));
        if (changed) dirty = true;
        return changed;
    }

    /** 바뀐 것이 있으면 chunks.dat 을 쓴다. */
    public void saveIfDirty() {
        if (file == null || !dirty) return;
        synchronized (writeLock) {
            dirty = false;
            NbtCompound data = new NbtCompound();
            data.putLongArray("Forced", sortedKeys());
            NbtCompound root = new NbtCompound();
            root.putCompound("data", data);
            root.putInt("DataVersion", DATA_VERSION);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(os, root, "");
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                dirty = true;
                LOG.error("chunks.dat 저장 실패", e);
            }
        }
    }
}
