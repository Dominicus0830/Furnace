// T-012: 저장 빛을 믿으면 안 되는 청크(<차원 폴더>/data/furnace_light_stale.dat, gzip {data:{Stale:[long…]}}).
//
// Furnace 는 바닐라와 달리 이웃이 내려가 있어도 청크를 편집할 수 있다. 그때 빛을 줄이는 전파(광원 치움·막음)는 내려간 청크에 닿지 못해,
// 그 청크의 디스크 빛에는 지운 빛이 남는다. 바닐라는 편집되는 청크의 이웃이 늘 올라와 있어(티켓 단계) 이런 일이 없고, 그 청크를 다시
// 올리면 디스크 빛을 그대로 쓴다. 그래서 감소 전파가 막힌 청크를 여기 적어 두고, 다음에 올릴 때 저장 빛 대신 다시 계산한다(그리고
// 고친 빛을 저장하도록 dirty). 바닐라 파일이 아니라 Furnace 전용이다 — 바닐라는 이 파일을 모르고 무시한다.
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

public final class LightStaleChunks {
    private static final Logger LOG = LoggerFactory.getLogger(LightStaleChunks.class);
    public static final String FILE = "furnace_light_stale.dat";

    private final Path file;
    private final Set<Long> chunks = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    private LightStaleChunks(Path file) { this.file = file; }

    /** 파일 없이 메모리에서만(시험 레벨·저장소 없는 월드). */
    public static LightStaleChunks detached() { return new LightStaleChunks(null); }

    /** <차원 폴더>/data/furnace_light_stale.dat 을 읽는다(없거나 깨졌으면 빈 집합). */
    public static LightStaleChunks open(Path dimensionDir) {
        LightStaleChunks f = new LightStaleChunks(dimensionDir.resolve("data").resolve(FILE));
        f.load();
        return f;
    }

    private static long key(int x, int z) { return ForcedChunks.asLong(x, z); }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readGzip(in);
            NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
            if (data.tags().get("Stale") instanceof long[] stale) for (long k : stale) chunks.add(k);
        } catch (IOException | RuntimeException e) {
            // 표시를 잃으면 그 청크들은 낡은 저장 빛을 쓸 수 있다 — 크게 남긴다
            LOG.error("{} 읽기 실패 — 저장 빛을 믿으면 안 되는 청크 표시를 잃었다", file, e);
        }
    }

    /** 그 청크의 저장 빛을 믿지 않게 표시. */
    public void mark(int x, int z) {
        if (chunks.add(key(x, z))) dirty = true;
    }

    public boolean contains(int x, int z) { return !chunks.isEmpty() && chunks.contains(key(x, z)); }

    /** 표시가 있었으면 지우고 true(청크를 올릴 때 한 번). */
    public boolean take(int x, int z) {
        if (chunks.isEmpty() || !chunks.remove(key(x, z))) return false;
        dirty = true;
        return true;
    }

    public int size() { return chunks.size(); }

    /** 바뀐 것이 있으면 파일을 쓴다(비었으면 파일을 지운다). */
    public void saveIfDirty() {
        if (file == null || !dirty) return;
        synchronized (writeLock) {
            dirty = false;
            long[] keys = chunks.stream().mapToLong(Long::longValue).sorted().toArray();
            try {
                if (keys.length == 0) {
                    Files.deleteIfExists(file);
                    return;
                }
                NbtCompound data = new NbtCompound();
                data.putLongArray("Stale", keys);
                NbtCompound root = new NbtCompound();
                root.putCompound("data", data);
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(os, root, "");
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                dirty = true;
                LOG.error("{} 저장 실패", file, e);
            }
        }
    }
}
