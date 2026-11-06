// 수상한 스튜 효과(SuspiciousEffectHolder) — 꽃 아이템 → 효과 목록. 원본: items/stew_effects_1.21.1.txt (tools/vanilla-data/ExportStewEffects.java).
package org.domi.furnace.server.recipe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StewEffects {

    private StewEffects() { }

    private static volatile Map<String, List<Object>> table;

    private static Map<String, List<Object>> table() {
        Map<String, List<Object>> t = table;
        if (t != null) return t;
        t = new HashMap<>();
        try (InputStream in = StewEffects.class.getResourceAsStream("/items/stew_effects_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("items/stew_effects_1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                List<Object> effects = new ArrayList<>();
                for (int i = 1; i < p.length; i++) {
                    int c = p[i].lastIndexOf(':');
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("id", p[i].substring(0, c));
                    e.put("duration", Integer.parseInt(p[i].substring(c + 1)));
                    effects.add(e);
                }
                t.put(p[0], effects);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        table = t;
        return t;
    }

    /** 그 아이템의 수상한 스튜 효과(정규 값 목록), 해당 없으면 null. */
    public static List<Object> of(String item) { return item == null ? null : table().get(item); }
}
