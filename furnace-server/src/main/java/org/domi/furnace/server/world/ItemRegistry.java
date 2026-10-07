// 아이템 숫자 id ↔ 이름(레지스트리). items/<버전>.txt(라인 "<name> <id>") 로드.
package org.domi.furnace.server.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ItemRegistry {

    private final Map<Integer, String> nameById;
    private final Map<String, Integer> idByName = new HashMap<>();

    private ItemRegistry(Map<Integer, String> nameById) {
        this.nameById = nameById;
        for (var e : nameById.entrySet()) idByName.put(e.getValue(), e.getKey());
    }

    public static ItemRegistry load(String resourcePath) {
        Map<Integer, String> map = new HashMap<>();
        ClassLoader cl = ItemRegistry.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalStateException("아이템 리소스 없음: " + resourcePath);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                map.put(Integer.parseInt(line.substring(sp + 1).strip()), line.substring(0, sp));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ItemRegistry(map);
    }

    /** 아이템 id → 이름. 미지원 id는 "minecraft:air". */
    public String nameOf(int itemId) {
        return nameById.getOrDefault(itemId, "minecraft:air");
    }

    /** 아이템 이름 → id. 알 수 없는 이름이면 -1. */
    public int idOf(String name) {
        return idByName.getOrDefault(name, -1);
    }

    /** 등록된 아이템 id인가(공기 0 제외). */
    public boolean isValid(int itemId) {
        return itemId > 0 && nameById.containsKey(itemId);
    }
}
