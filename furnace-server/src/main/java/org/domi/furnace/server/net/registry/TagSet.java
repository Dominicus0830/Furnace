// 바닐라 태그 리소스(tags-<버전>.txt) 로더. 줄 형식: `<레지스트리> <태그 id> <엔트리 숫자 id>...`
// 숫자 id는 클라이언트가 보는 레지스트리 id(내장: 바닐라 protocol_id, 동적: 우리가 보내는 레지스트리 순서).
package org.domi.furnace.server.net.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TagSet {

    // 레지스트리 → (태그 id → 엔트리 id 배열). 삽입 순서 유지.
    private final Map<String, Map<String, int[]>> tags;

    /** 데이터 팩에서 불러온 태그로 만든다. */
    public static TagSet of(Map<String, Map<String, int[]>> tags) { return new TagSet(tags); }

    private TagSet(Map<String, Map<String, int[]>> tags) {
        this.tags = tags;
    }

    public static TagSet load(String resourcePath) {
        Map<String, Map<String, int[]>> map = new LinkedHashMap<>();
        try (InputStream in = TagSet.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("태그 리소스 없음: " + resourcePath);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split(" ");
                    int[] ids = new int[parts.length - 2];
                    for (int i = 2; i < parts.length; i++) ids[i - 2] = Integer.parseInt(parts[i]);
                    map.computeIfAbsent(parts[0], k -> new LinkedHashMap<>()).put(parts[1], ids);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TagSet(map);
    }

    public Set<String> registries() {
        return tags.keySet();
    }

    public Map<String, int[]> tagsOf(String registry) {
        return tags.getOrDefault(registry, Map.of());
    }

    public int[] ids(String registry, String tag) {
        return tagsOf(registry).get(tag);
    }

    public int tagCount() {
        return tags.values().stream().mapToInt(Map::size).sum();
    }
}
