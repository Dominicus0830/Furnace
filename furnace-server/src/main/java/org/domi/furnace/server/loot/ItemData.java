// 아이템 기본값 — 기본 컴포넌트(items/components_1.21.1.json.gz, 바닐라 데이터 생성기 items 보고서)와
// 인챈트 수치(items/properties_1.21.1.txt — 1.21.1 은 컴포넌트가 아니라 Item.getEnchantmentValue 코드에 있다).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class ItemData {

    private static volatile ItemData vanilla;

    private final Map<String, Map<String, Object>> defaults = new HashMap<>();
    private final Map<String, Integer> enchantValue = new HashMap<>();

    public static ItemData vanilla() {
        ItemData d = vanilla;
        if (d == null) {
            synchronized (ItemData.class) {
                if (vanilla == null) vanilla = load();
                d = vanilla;
            }
        }
        return d;
    }

    private static ItemData load() {
        ItemData d = new ItemData();
        ClassLoader cl = ItemData.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream("items/components_1.21.1.json.gz")) {
            if (in == null) throw new IllegalStateException("items/components_1.21.1.json.gz 없음");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> comps = (Map<String, Object>) Components.fromJson(e.getValue());
                d.defaults.put(e.getKey(), comps);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (InputStream in = cl.getResourceAsStream("items/properties_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("items/properties_1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(" ");
                d.enchantValue.put(p[0], Integer.parseInt(p[3]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return d;
    }

    public boolean exists(String item) { return defaults.containsKey(item); }

    /** 기본 컴포넌트 값(없으면 null). */
    public Object defaultComponent(String item, String component) {
        Map<String, Object> m = defaults.get(item);
        return m == null ? null : m.get(component);
    }

    /** 아이템의 기본 컴포넌트 전부(보고서 JSON 꼴, 읽기 전용). 모르는 아이템이면 null. */
    public Map<String, Object> defaults(String item) {
        Map<String, Object> m = defaults.get(item);
        return m == null ? null : java.util.Collections.unmodifiableMap(m);
    }

    public int enchantmentValue(String item) { return enchantValue.getOrDefault(item, 0); }

}
