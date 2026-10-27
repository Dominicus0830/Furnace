// 데이터 팩 태그(바닐라 TagLoader + TagEntry + TagFile).
//  - tags/<레지스트리>/<…>.json 을 팩 아래→위로 쌓되 replace: true 면 앞의 것을 비운다.
//  - 엔트리: "id" · "#태그" · {"id": …, "required": false}. 필수 참조가 없으면 그 태그는 실리지 않는다(오류 로그).
//  - 결과는 첫 등장 순서·중복 제거(바닐라 ImmutableSet), 중첩 태그는 의존 순서로 먼저 풀어 둔다.
package org.domi.furnace.server.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagLoader {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 태그 엔트리 하나와 어느 팩에서 왔는지. */
    record Entry(ResourceId id, boolean tag, boolean required, String pack) {
        @Override public String toString() {
            String s = (tag ? "#" : "") + id;
            return (required ? s : s + "?") + " (from " + pack + ")";
        }
    }

    private TagLoader() {}

    /** 바닐라 Registries.tagsDirPath — minecraft 레지스트리는 "tags/<path>", 아니면 "tags/<ns>/<path>". */
    public static String tagsDir(String registry) {
        ResourceId r = ResourceId.parse(registry);
        return "tags/" + (r.namespace().equals("minecraft") ? r.path() : r.namespace() + "/" + r.path());
    }

    /**
     * 한 레지스트리의 태그를 읽어 엔트리 번호(elementId: 이름 → 클라이언트 숫자 id, 없으면 null)로 푼다.
     * 결과: 태그 id(문자열) → 숫자 id 배열, 태그 id 순.
     */
    public static Map<String, int[]> load(ResourceManager rm, String registry, Function<ResourceId, Integer> elementId) {
        Map<ResourceId, List<Entry>> raw = read(rm, tagsDir(registry));
        Map<ResourceId, int[]> built = build(raw, elementId);
        Map<String, int[]> out = new LinkedHashMap<>();
        for (var e : new TreeMap<>(built).entrySet()) out.put(e.getKey().toString(), e.getValue());
        return out;
    }

    /**
     * 숫자 id 가 없는 레지스트리(함수 등)용: 이름 → 원소 표로 태그를 푼다. 없는 원소를 필수로 참조하면 그 태그는 빠진다(바닐라와 같다).
     * 결과의 원소 순서는 첫 등장 순·중복 제거.
     */
    public static <T> Map<ResourceId, List<T>> loadNamed(ResourceManager rm, String registry, Map<ResourceId, T> elements) {
        List<T> byIndex = new ArrayList<>(elements.values());
        Map<ResourceId, Integer> index = new HashMap<>();
        int i = 0;
        for (ResourceId id : elements.keySet()) index.put(id, i++);
        Map<ResourceId, int[]> built = build(read(rm, tagsDir(registry)), index::get);
        Map<ResourceId, List<T>> out = new LinkedHashMap<>();
        for (var e : new TreeMap<>(built).entrySet()) {
            List<T> list = new ArrayList<>(e.getValue().length);
            for (int x : e.getValue()) list.add(byIndex.get(x));
            out.put(e.getKey(), List.copyOf(list));
        }
        return out;
    }

    static Map<ResourceId, List<Entry>> read(ResourceManager rm, String dir) {
        Map<ResourceId, List<Entry>> out = new HashMap<>();
        String prefix = dir + "/";
        for (var e : rm.listResourceStacks(dir, id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            ResourceId tagId = file.withPath(file.path().substring(prefix.length(), file.path().length() - ".json".length()));
            for (ResourceManager.Resource r : e.getValue()) {
                try (InputStream in = r.open().get()) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    List<Entry> list = out.computeIfAbsent(tagId, k -> new ArrayList<>());
                    List<Entry> parsed = new ArrayList<>();
                    for (JsonElement v : o.getAsJsonArray("values")) parsed.add(parseEntry(v, r.packId()));
                    if (o.has("replace") && o.get("replace").getAsBoolean()) list.clear();
                    list.addAll(parsed);
                } catch (Exception ex) {
                    log.error("Couldn't read tag list {} from {} in data pack {}", tagId, file, r.packId(), ex);
                }
            }
        }
        return out;
    }

    private static Entry parseEntry(JsonElement v, String pack) {
        String s;
        boolean required = true;
        if (v.isJsonObject()) {
            s = v.getAsJsonObject().get("id").getAsString();
            if (v.getAsJsonObject().has("required")) required = v.getAsJsonObject().get("required").getAsBoolean();
        } else {
            s = v.getAsString();
        }
        boolean tag = s.startsWith("#");
        ResourceId id = ResourceId.parse(tag ? s.substring(1) : s);
        return new Entry(id, tag, required, pack);
    }

    /** 의존 순서로 풀어 숫자 id 집합을 만든다(바닐라 TagLoader.build + DependencySorter). */
    static Map<ResourceId, int[]> build(Map<ResourceId, List<Entry>> raw, Function<ResourceId, Integer> elementId) {
        Map<ResourceId, int[]> done = new HashMap<>();
        Set<ResourceId> visiting = new HashSet<>();
        Set<ResourceId> visited = new HashSet<>();
        for (ResourceId id : raw.keySet()) visit(id, raw, elementId, done, visiting, visited);
        return done;
    }

    private static void visit(ResourceId id, Map<ResourceId, List<Entry>> raw, Function<ResourceId, Integer> elementId,
                              Map<ResourceId, int[]> done, Set<ResourceId> visiting, Set<ResourceId> visited) {
        if (visited.contains(id) || !raw.containsKey(id)) return;
        if (!visiting.add(id)) return;   // 순환 — 바닐라 DependencySorter 도 순환 쪽은 풀지 못한다
        for (Entry e : raw.get(id)) if (e.tag) visit(e.id, raw, elementId, done, visiting, visited);
        visiting.remove(id);
        visited.add(id);
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        List<Entry> missing = new ArrayList<>();
        for (Entry e : raw.get(id)) {
            if (e.tag) {
                int[] sub = done.get(e.id);
                if (sub == null) { if (e.required) missing.add(e); continue; }
                for (int x : sub) values.add(x);
            } else {
                Integer x = elementId.apply(e.id);
                if (x == null) { if (e.required) missing.add(e); continue; }
                values.add(x);
            }
        }
        if (!missing.isEmpty()) {
            log.error("Couldn't load tag {} as it is missing following references: {}", id,
                missing.stream().map(Object::toString).collect(Collectors.joining(", ")));
            return;
        }
        done.put(id, values.stream().mapToInt(Integer::intValue).toArray());
    }
}
