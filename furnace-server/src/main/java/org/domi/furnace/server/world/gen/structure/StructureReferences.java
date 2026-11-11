// 구조물 시작점의 references(바닐라 StructureStart.references — 탐험 지도가 이미 가리킨 구조물을 건너뛰는 표시, A06-06).
// 바닐라는 시작 청크를 STRUCTURE_STARTS 까지 불러 그 시작점 객체에 더하고 청크 NBT `starts.<id>.references` 로 남긴다.
// Furnace 는 시작 청크를 만들지 않고 찾으므로 (구조물 id, 시작 청크) → 값을 차원마다 여기 모아 두고,
// 청크를 저장할 때 그 값을 시작점 NBT 에 쓴다. 아직 만들어지지 않은 시작 청크의 값은 data/furnace_structure_references.dat 에 남긴다.
package org.domi.furnace.server.world.gen.structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StructureReferences {
    private static final Logger LOG = LoggerFactory.getLogger(StructureReferences.class);
    private static final int DATA_VERSION = 3955;

    /** 바닐라 StructureStart.getMaxReferences(): 1. canBeReferenced = references < 1. */
    public static final int MAX_REFERENCES = 1;

    /** 디스크의 시작 청크 NBT 에서 그 구조물 시작점의 references 를 읽는다. 저장된 시작점이 없으면 -1(바닐라 StructureCheck 의 "없음"). */
    public interface Lookup { int stored(String structureId, int chunkX, int chunkZ); }

    private record Key(String id, long chunk) {}

    /** 알려진 값. 0 도 담는다(디스크를 이미 읽었다는 표시). -1 = 저장된 시작점이 없음. */
    private final Map<Key, Integer> known = new ConcurrentHashMap<>();
    private volatile Lookup disk;
    private volatile Path file;
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    /** 디스크 월드에 묶는다: 저장된 청크 읽기와 data 파일(있으면 읽는다). */
    public void bind(Path dataFile, Lookup disk) {
        this.disk = disk;
        this.file = dataFile;
        load();
    }

    /**
     * 그 시작점의 references. 알려진 값이 없으면 디스크의 시작 청크를 읽는다(바닐라 StructureCheck.tryLoadFromStorage).
     * 저장된 시작점이 없으면 0(새로 만들어질 시작점은 references 0 으로 생긴다).
     */
    public int get(String structureId, int chunkX, int chunkZ) {
        Key k = new Key(structureId, ChunkStructures.chunkLong(chunkX, chunkZ));
        Integer v = known.get(k);
        if (v == null) {
            Lookup d = disk;
            int stored = d == null ? -1 : d.stored(structureId, chunkX, chunkZ);
            v = known.merge(k, stored, Math::max);
        }
        return Math.max(0, v);
    }

    /** 이미 알려진 값만(없으면 -1). 저장할 때 디스크를 다시 읽지 않는다. */
    public int knownOrMinus(String structureId, int chunkX, int chunkZ) {
        Integer v = known.get(new Key(structureId, ChunkStructures.chunkLong(chunkX, chunkZ)));
        return v == null ? -1 : v;
    }

    /**
     * 바닐라 ChunkGenerator.tryAddReference: canBeReferenced(references < 1)면 StructureManager.addReference(+1)하고 참.
     * 여러 스레드가 같은 시작점을 동시에 잡으면 하나만 성공한다.
     */
    public boolean tryAddReference(String structureId, int chunkX, int chunkZ) {
        get(structureId, chunkX, chunkZ);   // 디스크 값을 먼저 들인다
        boolean[] added = {false};
        known.compute(new Key(structureId, ChunkStructures.chunkLong(chunkX, chunkZ)), (k, v) -> {
            int cur = v == null ? 0 : Math.max(0, v);
            if (cur >= MAX_REFERENCES) return v;
            added[0] = true;
            return cur + 1;
        });
        if (added[0]) dirty = true;
        return added[0];
    }

    /** 청크를 불러올 때 그 청크 NBT 의 시작점 값을 들인다(큰 값을 남긴다). */
    public void seed(String structureId, int chunkX, int chunkZ, int references) {
        known.merge(new Key(structureId, ChunkStructures.chunkLong(chunkX, chunkZ)), references, Math::max);
    }

    // ------------------------------------------------------------------ 저장

    private void load() {
        Path f = file;
        if (f == null || !Files.isRegularFile(f)) return;
        try (InputStream in = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(in);
            NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
            if (data.tags().get("References") instanceof NbtList list) {
                for (Object o : list.items()) {
                    if (o instanceof NbtCompound c && c.tags().get("id") instanceof String id && c.tags().get("Pos") instanceof long[] pos
                            && c.tags().get("references") instanceof Number n) {
                        known.merge(new Key(id, pos.length > 0 ? pos[0] : 0L), n.intValue(), Math::max);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("{} 읽기 실패 — 탐험 지도 표시 없이 시작", f.getFileName(), e);
        }
    }

    /** 바뀐 것이 있으면 references 가 1 이상인 시작점만 쓴다. */
    public void saveIfDirty() {
        Path f = file;
        if (f == null || !dirty) return;
        synchronized (writeLock) {
            dirty = false;
            NbtList list = new NbtList(NbtIo.TAG_COMPOUND);
            for (var e : known.entrySet()) {
                if (e.getValue() <= 0) continue;
                NbtCompound c = new NbtCompound();
                c.putString("id", e.getKey().id());
                c.putLongArray("Pos", new long[] {e.getKey().chunk()});
                c.putInt("references", e.getValue());
                list.add(c);
            }
            NbtCompound data = new NbtCompound();
            data.putList("References", list);
            NbtCompound root = new NbtCompound();
            root.putCompound("data", data);
            root.putInt("DataVersion", DATA_VERSION);
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                try (OutputStream os = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(os, root, "");
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                dirty = true;
                LOG.error("{} 저장 실패", f.getFileName(), e);
            }
        }
    }
}
