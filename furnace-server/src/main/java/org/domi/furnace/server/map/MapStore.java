// 지도 저장소 — 바닐라 DimensionDataStorage 의 map_<id> 와 idcounts: world/data/map_<id>.dat(gzip NBT {data, DataVersion}),
// world/data/idcounts.dat({data:{map:<마지막 번호>}}). 지도는 처음 쓸 때 읽어 메모리에 두고, 바뀐 것(dirty)만 저장한다.
// 번호는 0 부터(idcounts 에 map 이 없으면 -1 에서 시작해 +1). 파일이 없는 번호는 null(바닐라 MapItem.getSavedData 도 같다).
package org.domi.furnace.server.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapStore {

    private static final Logger LOG = LoggerFactory.getLogger(MapStore.class);
    private static final int DATA_VERSION = 3955;

    private static volatile Path dataDir;
    private static final Map<Integer, MapData> CACHE = new ConcurrentHashMap<>();
    private static final Set<Integer> MISSING = ConcurrentHashMap.newKeySet();
    private static final Object ID_LOCK = new Object();
    private static int lastId = -1;
    private static boolean idsLoaded, idsDirty;

    private MapStore() { }

    /** 월드를 열 때 world/data 를 건다. */
    public static void install(Path worldDir) {
        dataDir = worldDir.resolve("data");
        reset();
    }

    public static void uninstall() {
        dataDir = null;
        reset();
    }

    private static void reset() {
        CACHE.clear();
        MISSING.clear();
        synchronized (ID_LOCK) {
            lastId = -1;
            idsLoaded = false;
            idsDirty = false;
        }
    }

    /** 지도 번호의 데이터(파일이 없거나 읽지 못하면 null). */
    public static MapData get(int id) {
        MapData d = CACHE.get(id);
        if (d != null) return d;
        Path dir = dataDir;
        if (dir == null || MISSING.contains(id)) return null;
        Path f = dir.resolve("map_" + id + ".dat");
        try {
            if (!Files.isRegularFile(f)) {
                MISSING.add(id);
                return null;
            }
            try (InputStream in = Files.newInputStream(f)) {
                NbtCompound root = NbtIo.readGzip(in);
                NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
                MapData loaded = MapData.load(data);
                MapData prev = CACHE.putIfAbsent(id, loaded);
                return prev != null ? prev : loaded;
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("지도 데이터 읽기 실패 map_{}", id, e);
            MISSING.add(id);   // 매 틱 다시 읽으며 로그를 쏟지 않게
            return null;
        }
    }

    /** MapItem.getSavedData(stack): 스택의 map_id 로 찾는다. */
    public static MapData of(ItemStack s) {
        Integer id = MapItems.mapId(s);
        return id == null ? null : get(id);
    }

    /** Level.setMapData(id, data): 새 지도 데이터를 두고 저장 대상으로 올린다. */
    public static void put(int id, MapData data) {
        MISSING.remove(id);
        CACHE.put(id, data);
        data.setDirty();
    }

    /** Level.getFreeMapId: idcounts 의 map 을 하나 올려 새 번호(첫 번호 0)를 준다. */
    public static int nextId() {
        synchronized (ID_LOCK) {
            loadIdsLocked();
            lastId++;
            idsDirty = true;
            return lastId;
        }
    }

    private static void loadIdsLocked() {
        if (idsLoaded) return;
        idsLoaded = true;
        Path dir = dataDir;
        if (dir == null) return;
        Path f = dir.resolve("idcounts.dat");
        try {
            if (!Files.isRegularFile(f)) return;
            try (InputStream in = Files.newInputStream(f)) {
                NbtCompound root = NbtIo.readGzip(in);
                NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
                if (data.containsNumber("map")) lastId = data.getIntOr0("map");
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("idcounts.dat 읽기 실패", e);
        }
    }

    /** 바뀐 지도와 idcounts 를 저장한다(자동 저장·/save-all·종료). 어느 스레드에서든. */
    public static void saveDirty() {
        Path dir = dataDir;
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.warn("data 폴더를 만들지 못함", e);
            return;
        }
        boolean writeIds;
        int ids;
        synchronized (ID_LOCK) {
            writeIds = idsDirty;
            ids = lastId;
            idsDirty = false;
        }
        if (writeIds) {
            NbtCompound data = new NbtCompound();
            data.putInt("map", ids);
            write(dir.resolve("idcounts.dat"), data);
        }
        for (Map.Entry<Integer, MapData> e : CACHE.entrySet()) {
            MapData d = e.getValue();
            if (!d.isDirty()) continue;
            d.setDirty(false);   // 쓰는 도중 바뀌면 다시 dirty 가 된다
            write(dir.resolve("map_" + e.getKey() + ".dat"), d.save());
        }
    }

    /** DimensionDataStorage 의 저장: {data, DataVersion}. 임시 파일에 쓰고 옮긴다. */
    private static void write(Path file, NbtCompound data) {
        NbtCompound root = new NbtCompound();
        root.putCompound("data", data);
        root.putInt("DataVersion", DATA_VERSION);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tmp)) {
                NbtIo.writeGzip(os, root, "");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("지도 데이터 저장 실패 {}", file, e);
        }
    }
}
