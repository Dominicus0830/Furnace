// 엔티티 종류별 자연 스폰 정보(EntityType 의 category·canSummon·canSpawnFarFromPlayer·fireImmune) — entities/entity_meta-1.21.1.txt 에서 읽는다.
package org.domi.furnace.server.entity.spawn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class EntityMeta {
    public record Info(MobCategory category, boolean canSummon, boolean canSpawnFarFromPlayer, boolean fireImmune) {}

    private static final Map<String, Info> BY_NAME = new HashMap<>();

    static {
        try (var in = EntityMeta.class.getClassLoader().getResourceAsStream("entities/entity_meta-1.21.1.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.trim().split(" ");
                if (p.length != 5) continue;
                BY_NAME.put("minecraft:" + p[0], new Info(MobCategory.byKey(p[1]), p[2].equals("1"), p[3].equals("1"), p[4].equals("1")));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private EntityMeta() {}

    private static final Info MISC = new Info(MobCategory.MISC, true, true, false);

    public static Info of(String typeName) { return BY_NAME.getOrDefault(typeName, MISC); }

    public static MobCategory category(String typeName) { return of(typeName).category(); }
}
