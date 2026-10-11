// 클라이언트로 태그를 보내는 레지스트리(바닐라 Update Tags 와 같은 13개)와, 엔트리 이름 → 클라이언트 숫자 id.
//  - 내장 레지스트리: registries/builtin-1.21.1.txt (protocol_id 순서)
//  - 동기화되는 동적 레지스트리: registries/1.21.1.txt (Furnace 가 보내는 순서)
package org.domi.furnace.server.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.net.registry.RegistrySet;

public final class TagRegistries {

    /** Update Tags 에 실리는 레지스트리(바닐라 1.21.1 순서와 무관 — 패킷은 레지스트리 키로 구분된다). */
    public static final List<String> SYNCED = List.of(
        "minecraft:banner_pattern", "minecraft:block", "minecraft:cat_variant", "minecraft:damage_type", "minecraft:enchantment",
        "minecraft:entity_type", "minecraft:fluid", "minecraft:game_event", "minecraft:instrument", "minecraft:item",
        "minecraft:painting_variant", "minecraft:point_of_interest_type", "minecraft:worldgen/biome");

    private final Map<String, Map<ResourceId, Integer>> ids = new HashMap<>();

    public TagRegistries(RegistrySet dynamic) {
        try (InputStream in = TagRegistries.class.getClassLoader().getResourceAsStream("registries/builtin-1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("registries/builtin-1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                Map<ResourceId, Integer> m = ids.computeIfAbsent(line.substring(0, sp), k -> new LinkedHashMap<>());
                m.put(ResourceId.parse(line.substring(sp + 1)), m.size());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (String reg : SYNCED) {
            if (ids.containsKey(reg)) continue;
            Map<ResourceId, Integer> m = new LinkedHashMap<>();
            for (String e : dynamic.entries(reg)) m.put(ResourceId.parse(e), m.size());
            ids.put(reg, m);
        }
    }

    /** 이름 → 숫자 id(없으면 null). */
    public Integer id(String registry, ResourceId element) {
        Map<ResourceId, Integer> m = ids.get(registry);
        return m == null ? null : m.get(element);
    }

    /** 숫자 id 순서의 엔트리 이름(없는 레지스트리면 빈 목록). */
    public List<ResourceId> names(String registry) {
        Map<ResourceId, Integer> m = ids.get(registry);
        return m == null ? List.of() : List.copyOf(m.keySet());
    }

    /** 켜진 팩들의 태그 전부(레지스트리 → 태그 → 숫자 id 배열). */
    public Map<String, Map<String, int[]>> loadAll(ResourceManager rm) {
        Map<String, Map<String, int[]>> out = new LinkedHashMap<>();
        for (String reg : SYNCED) out.put(reg, TagLoader.load(rm, reg, e -> id(reg, e)));
        return out;
    }
}
