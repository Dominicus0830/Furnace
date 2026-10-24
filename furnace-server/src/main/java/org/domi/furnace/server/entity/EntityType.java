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

    /** HUSK: sized(0.6, 1.95).eyeHeight(1.74).clientTrackingRange(8). */
    public static final EntityType HUSK = of("minecraft:husk", 0.6F, 1.95F, 1.74F, 8, 3, true, false);

    /** GIANT: sized(3.6, 12.0).eyeHeight(10.44).clientTrackingRange(10). */
    public static final EntityType GIANT = of("minecraft:giant", 3.6F, 12.0F, 10.44F, 10, 3, true, false);

    /** ZOMBIE_VILLAGER: sized(0.6, 1.95).eyeHeight(1.74).clientTrackingRange(8). */
    public static final EntityType ZOMBIE_VILLAGER = of("minecraft:zombie_villager", 0.6F, 1.95F, 1.74F, 8, 3, true, false);

    /** ZOMBIFIED_PIGLIN: fireImmune, sized(0.6, 1.95).eyeHeight(1.79).clientTrackingRange(8). */
    public static final EntityType ZOMBIFIED_PIGLIN = of("minecraft:zombified_piglin", 0.6F, 1.95F, 1.79F, 8, 3, true, true);

    /** ARROW: sized(0.5, 0.5).eyeHeight(0.13).clientTrackingRange(4).updateInterval(20). */
    public static final EntityType ARROW = of("minecraft:arrow", 0.5F, 0.5F, 0.13F, 4, 20, true, false);

    /** SKELETON/STRAY/BOGGED: sized(0.6, 1.99).eyeHeight(1.74).clientTrackingRange(8). */
    public static final EntityType SKELETON = of("minecraft:skeleton", 0.6F, 1.99F, 1.74F, 8, 3, true, false);
    public static final EntityType STRAY = of("minecraft:stray", 0.6F, 1.99F, 1.74F, 8, 3, true, false);
    public static final EntityType BOGGED = of("minecraft:bogged", 0.6F, 1.99F, 1.74F, 8, 3, true, false);

    /** WITHER_SKELETON: fireImmune, sized(0.7, 2.4).eyeHeight(2.1).clientTrackingRange(8). */
    public static final EntityType WITHER_SKELETON = of("minecraft:wither_skeleton", 0.7F, 2.4F, 2.1F, 8, 3, true, true);

    /** TNT: fireImmune, sized(0.98, 0.98).eyeHeight(0.15).clientTrackingRange(10).updateInterval(10). */
    public static final EntityType TNT = of("minecraft:tnt", 0.98F, 0.98F, 0.15F, 10, 10, true, true);

    /** CREEPER: sized(0.6, 1.7).clientTrackingRange(8) (눈높이 1.445). */
    public static final EntityType CREEPER = of("minecraft:creeper", 0.6F, 1.7F, 1.7F * 0.85F, 8, 3, true, false);

    /** SPIDER: sized(1.4, 0.9).eyeHeight(0.65).clientTrackingRange(8). CAVE_SPIDER: sized(0.7, 0.5).eyeHeight(0.45).clientTrackingRange(8). */
    public static final EntityType SPIDER = of("minecraft:spider", 1.4F, 0.9F, 0.65F, 8, 3, true, false);
    public static final EntityType CAVE_SPIDER = of("minecraft:cave_spider", 0.7F, 0.5F, 0.45F, 8, 3, true, false);

    /** SLIME: sized(0.52, 0.52).eyeHeight(0.325).clientTrackingRange(10). MAGMA_CUBE: fireImmune, 같은 크기, clientTrackingRange(8). */
    public static final EntityType SLIME = of("minecraft:slime", 0.52F, 0.52F, 0.325F, 10, 3, true, false);
    public static final EntityType MAGMA_CUBE = of("minecraft:magma_cube", 0.52F, 0.52F, 0.325F, 8, 3, true, true);

    /** ENDERMAN: sized(0.6, 2.9).eyeHeight(2.55).clientTrackingRange(8). */
    public static final EntityType ENDERMAN = of("minecraft:enderman", 0.6F, 2.9F, 2.55F, 8, 3, true, false);

    /** SILVERFISH / ENDERMITE: sized(0.4, 0.3).eyeHeight(0.13).clientTrackingRange(8). */
    public static final EntityType SILVERFISH = of("minecraft:silverfish", 0.4F, 0.3F, 0.13F, 8, 3, true, false);
    public static final EntityType ENDERMITE = of("minecraft:endermite", 0.4F, 0.3F, 0.13F, 8, 3, true, false);

    /** POTION: sized(0.25, 0.25).clientTrackingRange(4).updateInterval(10). */
    public static final EntityType POTION = of("minecraft:potion", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** WITCH: sized(0.6, 1.95).eyeHeight(1.62).clientTrackingRange(8). */
    public static final EntityType WITCH = of("minecraft:witch", 0.6F, 1.95F, 1.62F, 8, 3, true, false);

    private static final org.domi.furnace.server.net.registry.TagSet TAGS = org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt");

    /** 엔티티 종류 태그(entity_type 태그)에 들어 있나. */
    public boolean is(String tag) {
        int[] ids = TAGS.ids("minecraft:entity_type", tag);
        if (ids == null) return false;
        for (int i : ids) if (i == this.id) return true;
        return false;
    }

    /** EntityDimensions.makeBoundingBox(x, y, z). */
    public AABB makeBoundingBox(double x, double y, double z) {
        double hw = width / 2.0F;
        return new AABB(x - hw, y, z - hw, x + hw, y + height, z + hw);
    }
}
