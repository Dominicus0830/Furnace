// T-012: 저장 빛을 믿으면 안 되는 청크(<차원 폴더>/data/furnace_light_stale.dat, gzip {data:{Stale:[long…]}}).
//
// Furnace 는 바닐라와 달리 이웃이 내려가 있어도 청크를 편집할 수 있다. 그때 빛을 줄이는 전파(광원 치움·막음)는 내려간 청크에 닿지 못해,
// 그 청크의 디스크 빛에는 지운 빛이 남는다. 바닐라는 편집되는 청크의 이웃이 늘 올라와 있어(티켓 단계) 이런 일이 없고, 그 청크를 다시
// 올리면 디스크 빛을 그대로 쓴다. 그래서 감소 전파가 막힌 청크를 여기 적어 두고, 다음에 올릴 때 저장 빛 대신 다시 계산한다(그리고
// 고친 빛을 저장하도록 dirty). 바닐라 파일이 아니라 Furnace 전용이다 — 바닐라는 이 파일을 모르고 무시한다.
//
// 표시는 다시 계산한 빛이 디스크에 실제로 써진 뒤에야 지운다(clearIfSeq — 그 전에 죽어도 표시가 남는다). 표시마다 번호를 매겨, 그 사이
// 다시 표시된 청크의 새 표시는 지우지 않는다. 파일을 못 읽으면 이 차원은 저장 빛을 하나도 믿지 않는다(TrustNone — 예전 다시 계산 경로).
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LightStaleChunks {
    private static final Logger LOG = LoggerFactory.getLogger(LightStaleChunks.class);
    public static final String FILE = "furnace_light_stale.dat";

    private final Path file;
    /** 청크 키 → 표시 번호(메모리에서만 — 파일에는 키만 쓴다). */
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> chunks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong();
    /** 표시 파일을 못 읽었다 — 어떤 저장 빛도 믿지 않는다(파일에도 남겨 다음 실행도 그렇게). */
    private volatile boolean trustNone;
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    private LightStaleChunks(Path file) { this.file = file; }

    /** 파일 없이 메모리에서만(시험 레벨·저장소 없는 월드). */
    public static LightStaleChunks detached() { return new LightStaleChunks(null); }

    /** <차원 폴더>/data/furnace_light_stale.dat 을 읽는다(없으면 빈 집합, 깨졌으면 아무 저장 빛도 믿지 않음). */
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
            if (data.tags().get("Stale") instanceof long[] stale) for (long k : stale) chunks.put(k, seq.incrementAndGet());
            if (data.tags().get("TrustNone") instanceof Byte b && b != 0) trustNone = true;
        } catch (IOException | RuntimeException e) {
            // 표시를 잃으면 그 청크들이 지운 빛이 남은 저장 빛을 믿게 된다 — 대신 이 차원은 저장 빛을 하나도 믿지 않는다(예전처럼 다시 계산)
            LOG.error("{} 읽기 실패 — 이 차원은 저장된 빛을 믿지 않고 모두 다시 계산한다(파일을 지우면 다시 믿는다: 그 전에 지운 빛이 남은 청크가 있을 수 있다)",
                    file, e);
            chunks.clear();
            trustNone = true;
            dirty = true;
        }
    }

    /** 그 청크의 저장 빛을 믿지 않게 표시(이미 있으면 번호만 새로 — 앞서 잡아 둔 지우기 번호가 이 표시를 지우지 못하게). */
    public void mark(int x, int z) {
        if (chunks.put(key(x, z), seq.incrementAndGet()) == null) dirty = true;
    }

    /** 그 청크의 저장 빛을 믿으면 안 되는가. */
    public boolean contains(int x, int z) { return trustNone || !chunks.isEmpty() && chunks.containsKey(key(x, z)); }

    /** 지금 표시 번호(표시가 없으면 0). 다시 계산할 때 잡아 두고, 그 빛이 디스크에 써진 뒤 {@link #clearIfSeq} 로 지운다. */
    public long markSeq(int x, int z) {
        if (chunks.isEmpty()) return 0;
        Long s = chunks.get(key(x, z));
        return s == null ? 0 : s;
    }

    /** 표시 번호가 그대로면(그 사이 다시 표시되지 않았으면) 지운다. 아무 스레드에서나(IO 레인의 저장 끝). */
    public void clearIfSeq(int x, int z, long s) {
        if (s != 0 && chunks.remove(key(x, z), s)) dirty = true;
    }

    public int size() { return chunks.size(); }

    public boolean trustNone() { return trustNone; }

    /** 바뀐 것이 있으면 파일을 쓴다(비었으면 파일을 지운다). */
    public void saveIfDirty() {
        if (file == null || !dirty) return;
        synchronized (writeLock) {
            dirty = false;
            long[] keys = chunks.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
            try {
                if (keys.length == 0 && !trustNone) {
                    Files.deleteIfExists(file);
                    return;
                }
                NbtCompound data = new NbtCompound();
                data.putLongArray("Stale", keys);
                if (trustNone) data.put("TrustNone", (byte) 1);
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
