// 엔티티 타입 이름 목록(선택자 type= 검증용). 리소스의 1.21.1 엔티티 레지스트리에서 읽는다.
package org.domi.furnace.server.command.arguments;

import java.util.Set;

public final class EntityTypes {

    private static final Set<String> KNOWN = load();

    private EntityTypes() {}

    public static boolean isKnown(String id) { return KNOWN.contains(id); }

    public static java.util.Set<String> all() { return KNOWN; }

    private static Set<String> load() {
        var in = EntityTypes.class.getClassLoader().getResourceAsStream("registries/entity_types-1.21.1.txt");
        if (in == null) return Set.of("minecraft:player");
        try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
            return Set.copyOf(r.lines().map(String::strip).filter(s -> !s.isEmpty()).toList());
        } catch (java.io.IOException e) {
            return Set.of("minecraft:player");
        }
    }
}
