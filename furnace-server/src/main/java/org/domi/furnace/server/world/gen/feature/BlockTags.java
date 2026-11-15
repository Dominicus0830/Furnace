// 바닐라 블록 태그(이름 목록) 리소스 로더. 월드젠 JSON의 `#minecraft:...` 참조를 블록 이름으로 푼다.
package org.domi.furnace.server.world.gen.feature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockTags {

    private static final String RESOURCE = "worldgen/1.21.1/block_tags.txt";
    private static volatile BlockTags INSTANCE;

    private final Map<String, List<String>> tags;

    private BlockTags(Map<String, List<String>> tags) {
        this.tags = tags;
    }

    public static BlockTags get() {
        BlockTags t = INSTANCE;
        if (t == null) {
            synchronized (BlockTags.class) {
                t = INSTANCE;
                if (t == null) INSTANCE = t = load();
            }
        }
        return t;
    }

    private static BlockTags load() {
        Map<String, List<String>> map = new HashMap<>();
        try (InputStream in = BlockTags.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("블록 태그 리소스 없음: " + RESOURCE);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(" ");
                    map.put(parts[0], List.of(parts).subList(1, parts.length));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BlockTags(map);
    }

    /** 한 태그 표(데이터 팩 리로드마다 새 객체)에서 풀어 둔 태그별 블록 이름 목록. */
    private record LiveCache(org.domi.furnace.server.net.registry.TagSet src, java.util.concurrent.ConcurrentHashMap<String, List<String>> byTag) {}

    private static volatile LiveCache liveCache = new LiveCache(null, new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * 태그에 속한 블록 이름들. 데이터 팩(·플러그인)이 바꾼 블록 태그가 걸려 있으면 그것을 따른다(바닐라 레지스트리 태그와 같다) —
     * 그 표에 없는 태그는 빈 태그다(필수 참조가 빠진 태그는 로드되지 않는다). 데이터 팩 표가 없거나(시험) 어디에도 없는 이름이면
     * jar 의 바닐라 태그를 읽고, 거기에도 없으면 예외 — 조용히 다른 집합으로 대체하지 않는다.
     */
    public List<String> blocksOf(String tag) {
        var live = org.domi.furnace.server.item.ItemContext.tagSet();
        if (live != null) {
            String full = tag.indexOf(':') < 0 ? "minecraft:" + tag : tag;
            LiveCache c = liveCache;
            if (c.src != live) liveCache = c = new LiveCache(live, new java.util.concurrent.ConcurrentHashMap<>());
            List<String> r = c.byTag.get(full);
            if (r == null) {
                int[] ids = c.src.ids("minecraft:block", full);
                if (ids != null) {
                    var blocks = org.domi.furnace.server.block.Blocks.get();
                    List<String> names = new java.util.ArrayList<>(ids.length);
                    for (int id : ids) if (id >= 0 && id < blocks.blockCount()) names.add(blocks.name(id));
                    r = List.copyOf(names);
                } else if (tags.containsKey(full)) {
                    r = List.of();
                } else {
                    throw new IllegalArgumentException("알 수 없는 블록 태그: " + tag);
                }
                c.byTag.put(full, r);
            }
            return r;
        }
        List<String> r = tags.get(tag);
        if (r == null) throw new IllegalArgumentException("알 수 없는 블록 태그: " + tag);
        return r;
    }
}
