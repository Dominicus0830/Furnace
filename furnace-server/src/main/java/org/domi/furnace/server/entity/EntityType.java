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

    /** CHICKEN: sized(0.4, 0.7).eyeHeight(0.644).clientTrackingRange(10). */
    public static final EntityType CHICKEN = of("minecraft:chicken", 0.4F, 0.7F, 0.644F, 10, 3, true, false);

    /** COW: sized(0.9, 1.4).eyeHeight(1.3).clientTrackingRange(10). */
    public static final EntityType COW = of("minecraft:cow", 0.9F, 1.4F, 1.3F, 10, 3, true, false);

    /** PIG: sized(0.9, 0.9).clientTrackingRange(10). */
    public static final EntityType PIG = of("minecraft:pig", 0.9F, 0.9F, 0.9F * 0.85F, 10, 3, true, false);

    /** SHEEP: sized(0.9, 1.3).eyeHeight(1.235).clientTrackingRange(10). */
    public static final EntityType SHEEP = of("minecraft:sheep", 0.9F, 1.3F, 1.235F, 10, 3, true, false);

    /** MOOSHROOM: sized(0.9, 1.4).eyeHeight(1.3).clientTrackingRange(10). */
    public static final EntityType MOOSHROOM = of("minecraft:mooshroom", 0.9F, 1.4F, 1.3F, 10, 3, true, false);

    /** RABBIT: sized(0.4, 0.5).clientTrackingRange(8) (눈높이는 기본: 높이 × 0.85). */
    public static final EntityType RABBIT = of("minecraft:rabbit", 0.4F, 0.5F, 0.5F * 0.85F, 8, 3, true, false);

    /** WOLF: sized(0.6, 0.85).eyeHeight(0.68).passengerAttachments(0.8325).clientTrackingRange(10). */
    public static final EntityType WOLF = of("minecraft:wolf", 0.6F, 0.85F, 0.68F, 10, 3, true, false);

    /** OCELOT: sized(0.6, 0.7).clientTrackingRange(10) (눈높이 기본 0.595). */
    public static final EntityType OCELOT = of("minecraft:ocelot", 0.6F, 0.7F, 0.7F * 0.85F, 10, 3, true, false);

    /** CAT: sized(0.6, 0.7).clientTrackingRange(8). */
    public static final EntityType CAT = of("minecraft:cat", 0.6F, 0.7F, 0.7F * 0.85F, 8, 3, true, false);

    /** POLAR_BEAR: sized(1.4, 1.4).clientTrackingRange(10). */
    public static final EntityType POLAR_BEAR = of("minecraft:polar_bear", 1.4F, 1.4F, 1.4F * 0.85F, 10, 3, true, false);

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
