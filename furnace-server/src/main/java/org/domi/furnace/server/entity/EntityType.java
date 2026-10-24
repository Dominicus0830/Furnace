// 바닐라 EntityType 의 서버 쪽 속성: 레지스트리 번호·크기·눈높이·추적 거리(청크)·갱신 주기·속도 추적·불 면역.
package org.domi.furnace.server.entity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public record EntityType(String name, int id, float width, float height, float eyeHeight,
                         int clientTrackingRange, int updateInterval, boolean trackDeltas, boolean fireImmune) {

    private static final Map<String, Integer> IDS = loadIds();

    /** EntityType.ITEM: sized(0.25, 0.25).eyeHeight(0.2125).clientTrackingRange(6).updateInterval(20). */
    public static final EntityType ITEM = of("minecraft:item", 0.25F, 0.25F, 0.2125F, 6, 20, true, false);

    /** EntityType.EXPERIENCE_ORB: sized(0.5, 0.5).clientTrackingRange(6).updateInterval(20). */
    public static final EntityType EXPERIENCE_ORB = of("minecraft:experience_orb", 0.5F, 0.5F, 0.5F * 0.85F, 6, 20, true, false);

    /** EntityType.VILLAGER: sized(0.6, 1.95).eyeHeight(1.62).clientTrackingRange(10). */
    public static final EntityType VILLAGER = of("minecraft:villager", 0.6F, 1.95F, 1.62F, 10, 3, true, false);

    /** EntityType.ZOMBIE: sized(0.6, 1.95).eyeHeight(1.74).clientTrackingRange(8). */
    public static final EntityType ZOMBIE = of("minecraft:zombie", 0.6F, 1.95F, 1.74F, 8, 3, true, false);

    private static EntityType of(String name, float w, float h, float eye, int range, int interval, boolean deltas, boolean fireImmune) {
        Integer id = IDS.get(name);
        if (id == null) throw new IllegalStateException("엔티티 종류 없음: " + name);
        return new EntityType(name, id, w, h, eye, range, interval, deltas, fireImmune);
    }

    private static Map<String, Integer> loadIds() {
        Map<String, Integer> ids = new HashMap<>();
        var in = EntityType.class.getClassLoader().getResourceAsStream("registries/entity_types-1.21.1.txt");
        if (in == null) throw new IllegalStateException("registries/entity_types-1.21.1.txt 없음");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) ids.put(line.trim(), n++);
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return ids;
    }

    /** EntityDimensions.makeBoundingBox(x, y, z). */
    public AABB makeBoundingBox(double x, double y, double z) {
        double hw = width / 2.0F;
        return new AABB(x - hw, y, z - hw, x + hw, y + height, z + hw);
    }
}
