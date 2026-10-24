// 지도 저장 데이터(world/data/map_<id>.dat, gzip NBT)에서 읽기만 한다: 배율·잠금. 지도 만들기·색 갱신은 아직 — TODO(지도).
// 파일이 없으면 null(바닐라 MapItem.getSavedData 도 같다).
package org.domi.furnace.server.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;

public final class MapStore {

    /** 지도 한 장의 메타: 배율(0..4), 잠금. */
    public record Meta(int scale, boolean locked) { }

    private static volatile Path dataDir;
    private static final Map<Integer, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(long modified, Meta meta) { }

    private MapStore() { }

    /** 월드를 열 때 world/data 를 건다. */
    public static void install(Path worldDir) {
        dataDir = worldDir.resolve("data");
        CACHE.clear();
    }

    public static void uninstall() {
        dataDir = null;
        CACHE.clear();
    }

    /** 지도 번호의 메타(파일이 없거나 읽지 못하면 null). */
    public static Meta get(int id) {
        Path dir = dataDir;
        if (dir == null) return null;
        Path f = dir.resolve("map_" + id + ".dat");
        try {
            if (!Files.isRegularFile(f)) return null;
            long mod = Files.getLastModifiedTime(f).toMillis();
            Cached c = CACHE.get(id);
            if (c != null && c.modified() == mod) return c.meta();
            try (InputStream in = Files.newInputStream(f)) {
                NbtCompound root = NbtIo.readGzip(in);
                NbtCompound data = root.contains("data") ? root.getCompound("data") : root;
                Meta m = new Meta(data.getIntOr0("scale"), data.getIntOr0("locked") != 0);
                CACHE.put(id, new Cached(mod, m));
                return m;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** MapItem.getSavedData(stack): 스택의 map_id 로 찾는다. */
    public static Meta of(ItemStack s) {
        return ItemTools.component(s, "minecraft:map_id") instanceof Number n ? get(n.intValue()) : null;
    }
}
