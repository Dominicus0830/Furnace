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

    // ------------------------------------------------------------------ 타입 있는 속성

    /** 이 상태가 그 속성을 적어 두었는가(적지 않은 속성은 기본값이라는 뜻이라 false 일 수 있다 — 정확한 값은 서버가 채운 읽기 결과에서 본다). */
    public boolean has(BlockProperty<?> property) { return properties.containsKey(property.name()); }

    /** 속성 값, 적혀 있지 않으면 empty. 값 형식이 속성 종류와 맞지 않으면 IllegalArgumentException. */
    public <T> java.util.Optional<T> property(BlockProperty<T> property) {
        String v = properties.get(property.name());
        return v == null ? java.util.Optional.empty() : java.util.Optional.of(property.parse(v));
    }

    /** 속성 값. 적혀 있지 않으면 NoSuchElementException. 서버가 읽어 준 상태(World.blockNow …)는 속성을 모두 적고 있다. */
    public <T> T get(BlockProperty<T> property) {
        return property(property).orElseThrow(() -> new java.util.NoSuchElementException("속성 없음: " + property.name() + " (" + this + ")"));
    }

    /** 속성을 바꾼 새 상태. 그 블록이 가진 속성인지와 값 범위는 쓸 때(setBlock) 서버가 확인한다. */
    public <T> BlockData with(BlockProperty<T> property, T value) { return with(property.name(), property.format(value)); }

    /** 속성을 지운 새 상태(기본값으로 돌아간다). */
    public BlockData without(BlockProperty<?> property) {
        if (!properties.containsKey(property.name())) return this;
        TreeMap<String, String> m = new TreeMap<>(properties);
        m.remove(property.name());
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
