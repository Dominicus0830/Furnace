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

    /** 태그에 속한 블록 이름들(없는 태그면 예외 — 조용히 다른 집합으로 대체하지 않는다). */
    public List<String> blocksOf(String tag) {
        List<String> r = tags.get(tag);
        if (r == null) throw new IllegalArgumentException("알 수 없는 블록 태그: " + tag);
        return r;
    }
}
