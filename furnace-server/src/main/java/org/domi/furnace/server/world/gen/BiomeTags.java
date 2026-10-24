// 바이옴 태그(data/minecraft/tags/worldgen/biome/*.json) 조회: 태그 이름과 바이옴 이름으로 소속 여부를 본다(# 참조는 재귀로 푼다).
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BiomeTags {
    private static final Map<String, Set<String>> CACHE = new ConcurrentHashMap<>();

    private BiomeTags() { }

    /** biome(예 "minecraft:desert") 가 tag(예 "minecraft:snow_golem_melts")에 속하나. */
    public static boolean is(String biome, String tag) { return biome != null && CACHE.computeIfAbsent(tag, BiomeTags::load).contains(biome); }

    private static Set<String> load(String tag) {
        Set<String> out = new HashSet<>();
        add(out, tag);
        return Set.copyOf(out);
    }

    private static void add(Set<String> out, String tag) {
        String path = "worldgen/1.21.1/tags/worldgen/biome/" + tag.substring("minecraft:".length()) + ".json";
        try (InputStream in = BiomeTags.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("바이옴 태그 없음: " + tag);
            JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement v : j.getAsJsonArray("values")) {
                String s = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                if (s.startsWith("#")) add(out, s.substring(1));
                else out.add(s);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
