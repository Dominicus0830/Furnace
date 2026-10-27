// 바닐라 CommandStorage — /data storage 의 저장소. 이름공간마다 data/command_storage_<ns>.dat
// (gzip {data:{contents:{<경로>:{…}}}, DataVersion}). 빈 compound 를 넣으면 그 항목을 지운다.
// 어느 스레드에서든 쓴다(명령 스레드). 저장은 World.flushAll(저장·종료)에서.
package org.domi.furnace.server.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandStorage {
    private static final Logger LOG = LoggerFactory.getLogger(CommandStorage.class);
    private static final int DATA_VERSION = 3955;

    private final Path dataDir;
    /** 이름공간 → (경로 → 내용). 읽어 본 이름공간만 들어 있다. */
    private final Map<String, Map<String, NbtCompound>> namespaces = new HashMap<>();
    private final Set<String> dirty = new HashSet<>();

    /** @param dataDir world/data 폴더. null 이면 메모리에서만. */
    public CommandStorage(Path dataDir) { this.dataDir = dataDir; }

    private Map<String, NbtCompound> namespace(String ns, boolean create) {
        Map<String, NbtCompound> m = namespaces.get(ns);
        if (m != null) return m;
        m = load(ns);
        if (m == null) {
            if (!create) return null;
            m = new HashMap<>();
        }
        namespaces.put(ns, m);
        return m;
    }

    private Path file(String ns) { return dataDir.resolve("command_storage_" + ns + ".dat"); }

    private Map<String, NbtCompound> load(String ns) {
        if (dataDir == null) return null;
        Path f = file(ns);
        if (!Files.isRegularFile(f)) return null;
        try (InputStream in = Files.newInputStream(f)) {
            NbtCompound root = NbtIo.readGzip(in);
            NbtCompound data = root.tags().get("data") instanceof NbtCompound d ? d : new NbtCompound();
            NbtCompound contents = data.tags().get("contents") instanceof NbtCompound c ? c : new NbtCompound();
            Map<String, NbtCompound> m = new HashMap<>();
            for (var e : contents.tags().entrySet()) m.put(e.getKey(), e.getValue() instanceof NbtCompound c ? c : new NbtCompound());
            return m;
        } catch (IOException | RuntimeException e) {
            LOG.warn("{} 읽기 실패", f, e);
            return null;
        }
    }

    /** CommandStorage.get: 없으면 빈 compound(복사본). */
    public synchronized NbtCompound get(String id) {
        String[] p = split(id);
        Map<String, NbtCompound> m = namespace(p[0], false);
        NbtCompound c = m == null ? null : m.get(p[1]);
        return c != null ? c.copy() : new NbtCompound();
    }

    /** CommandStorage.set: 빈 compound 면 지운다. */
    public synchronized void set(String id, NbtCompound tag) {
        String[] p = split(id);
        Map<String, NbtCompound> m = namespace(p[0], true);
        if (tag.tags().isEmpty()) m.remove(p[1]);
        else m.put(p[1], tag.copy());
        dirty.add(p[0]);
    }

    /** keys: 들어 있는 모든 id(제안용). */
    public synchronized List<String> keys() {
        List<String> out = new ArrayList<>();
        for (var e : namespaces.entrySet()) for (String k : e.getValue().keySet()) out.add(e.getKey() + ":" + k);
        return out;
    }

    private static String[] split(String id) {
        int i = id.indexOf(':');
        return i < 0 ? new String[] {"minecraft", id} : new String[] {id.substring(0, i), id.substring(i + 1)};
    }

    /** 바뀐 이름공간 파일을 쓴다. */
    public void saveIfDirty() {
        Map<String, NbtCompound> toWrite = new HashMap<>();
        synchronized (this) {
            for (String ns : dirty) {
                NbtCompound contents = new NbtCompound();
                for (var e : namespaces.get(ns).entrySet()) contents.put(e.getKey(), e.getValue().copy());
                NbtCompound data = new NbtCompound();
                data.put("contents", contents);
                NbtCompound root = new NbtCompound();
                root.put("data", data);
                root.putInt("DataVersion", DATA_VERSION);
                toWrite.put(ns, root);
            }
            dirty.clear();
        }
        if (dataDir == null) return;
        for (var e : toWrite.entrySet()) {
            Path f = file(e.getKey());
            try {
                Files.createDirectories(dataDir);
                Path tmp = Files.createTempFile(dataDir, "command_storage_" + e.getKey(), ".dat");
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    NbtIo.writeGzip(out, e.getValue(), "");
                }
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                LOG.error("{} 저장 실패", f, ex);
            }
        }
    }
}
