// 블록 종류 + 속성 — 이름 기반 불변 값. 숫자 state id 는 노출하지 않는다.
// 문자열 형식은 바닐라 명령과 같다: minecraft:oak_stairs[facing=north,half=top]
package org.domi.furnace.api.world;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record BlockData(String type, Map<String, String> properties) {

    public static final BlockData AIR = new BlockData("minecraft:air", Map.of());

    public BlockData {
        Objects.requireNonNull(type, "type");
        if (type.indexOf(':') < 0) type = "minecraft:" + type;
        properties = properties.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    public static BlockData of(String type) { return new BlockData(type, Map.of()); }

    /** "minecraft:oak_stairs[facing=north]" 형식. 네임스페이스는 생략할 수 있다. */
    public static BlockData parse(String text) {
        Objects.requireNonNull(text, "text");
        int b = text.indexOf('[');
        if (b < 0) return of(text.strip());
        if (!text.endsWith("]")) throw new IllegalArgumentException("닫는 ] 가 없다: " + text);
        TreeMap<String, String> props = new TreeMap<>();
        String body = text.substring(b + 1, text.length() - 1);
        if (!body.isBlank()) {
            for (String part : body.split(",")) {
                int eq = part.indexOf('=');
                if (eq <= 0 || eq == part.length() - 1) throw new IllegalArgumentException("속성 형식 오류: " + part);
                if (props.put(part.substring(0, eq).strip(), part.substring(eq + 1).strip()) != null) {
                    throw new IllegalArgumentException("속성 중복: " + part);
                }
            }
        }
        return new BlockData(text.substring(0, b).strip(), props);
    }

    public BlockData with(String key, String value) {
        TreeMap<String, String> m = new TreeMap<>(properties);
        m.put(key, value);
        return new BlockData(type, m);
    }

    public boolean isAir() { return type.equals("minecraft:air") || type.equals("minecraft:cave_air") || type.equals("minecraft:void_air"); }

    @Override
    public String toString() {
        if (properties.isEmpty()) return type;
        StringBuilder sb = new StringBuilder(type).append('[');
        boolean first = true;
        for (var e : properties.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
