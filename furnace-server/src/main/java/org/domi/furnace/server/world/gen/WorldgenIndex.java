// 월드젠 레지스트리(설정된 지형지물·배치된 지형지물·구조물) 항목 목록 — 클래스패스 worldgen JSON 에서 처음 물을 때 한 번 만든다(NMS 호환 shim 용).
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class WorldgenIndex {

    private static final String BASE = "worldgen/1.21.1/worldgen/";
    private static final Map<String, String> DIRS = Map.of(
            "minecraft:worldgen/configured_feature", "configured_feature",
            "minecraft:worldgen/placed_feature", "placed_feature",
            "minecraft:worldgen/structure", "structure");
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private WorldgenIndex() {}

    /**
     * 항목 id("minecraft:oak") → configured_feature·structure 는 "type", placed_feature 는 가리키는 configured_feature id(직접 적었으면 빈 문자열). id 순.
     * 모르는 레지스트리면 빈 맵.
     */
    public static Map<String, String> entries(String registry) {
        String dir = DIRS.get(registry);
        if (dir == null) return Map.of();
        return CACHE.computeIfAbsent(dir, WorldgenIndex::load);
    }

    private static Map<String, String> load(String dir) {
        ClassLoader cl = WorldgenIndex.class.getClassLoader();
        List<String> ids = listIds(cl, BASE + dir);
        Collections.sort(ids);
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : ids) {
            try (var in = cl.getResourceAsStream(BASE + dir + "/" + id + ".json")) {
                if (in == null) continue;
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonElement e = JsonParser.parseReader(r);
                    out.put("minecraft:" + id, field(e, dir.equals("placed_feature") ? "feature" : "type"));
                }
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static String field(JsonElement e, String name) {
        if (!e.isJsonObject()) return "";
        JsonObject o = e.getAsJsonObject();
        JsonElement v = o.get(name);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    /** 폴더 아래 모든 .json 의 폴더 기준 경로(확장자 없이, 예 "oak", "ore/x"). 폴더 파일이든 jar 안이든 읽는다. */
    private static List<String> listIds(ClassLoader cl, String resourceDir) {
        URL url = cl.getResource(resourceDir);
        if (url == null) return new ArrayList<>();
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                } catch (java.nio.file.FileSystemAlreadyExistsException ex) {
                    fs = FileSystems.getFileSystem(uri);
                }
                return walk(fs.getPath(resourceDir.startsWith("/") ? resourceDir : "/" + resourceDir));
            }
            return walk(Path.of(uri));
        } catch (URISyntaxException | IOException ex) {
            throw new IllegalStateException("월드젠 목록을 읽지 못했다: " + resourceDir, ex);
        }
    }

    private static List<String> walk(Path root) throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                out.add(rel.substring(0, rel.length() - 5));
            });
        }
        return out;
    }
}
