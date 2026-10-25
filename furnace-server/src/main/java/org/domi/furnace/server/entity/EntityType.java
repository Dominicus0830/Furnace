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

    /** COD: sized(0.5, 0.3).eyeHeight(0.195).clientTrackingRange(4). */
    public static final EntityType COD = of("minecraft:cod", 0.5F, 0.3F, 0.195F, 4, 3, true, false);

    /** SALMON: sized(0.7, 0.4).eyeHeight(0.26).clientTrackingRange(4). */
    public static final EntityType SALMON = of("minecraft:salmon", 0.7F, 0.4F, 0.26F, 4, 3, true, false);

    /** PUFFERFISH: sized(0.7, 0.7).eyeHeight(0.455).clientTrackingRange(4). */
    public static final EntityType PUFFERFISH = of("minecraft:pufferfish", 0.7F, 0.7F, 0.455F, 4, 3, true, false);

    /** TROPICAL_FISH: sized(0.5, 0.4).eyeHeight(0.26).clientTrackingRange(4). */
    public static final EntityType TROPICAL_FISH = of("minecraft:tropical_fish", 0.5F, 0.4F, 0.26F, 4, 3, true, false);

    /** DOLPHIN: sized(0.9, 0.6).eyeHeight(0.3). */
    public static final EntityType DOLPHIN = of("minecraft:dolphin", 0.9F, 0.6F, 0.3F, 5, 3, true, false);

    /** SQUID: sized(0.8F, 0.8F).eyeHeight(0.4). */
    public static final EntityType SQUID = of("minecraft:squid", 0.8F, 0.8F, 0.4F, 8, 3, true, false);

    /** GLOW_SQUID: sized(0.8F, 0.8F).eyeHeight(0.4). */
    public static final EntityType GLOW_SQUID = of("minecraft:glow_squid", 0.8F, 0.8F, 0.4F, 10, 3, true, false);

    /** GUARDIAN: sized(0.85F, 0.85F).eyeHeight(0.425). */
    public static final EntityType GUARDIAN = of("minecraft:guardian", 0.85F, 0.85F, 0.425F, 8, 3, true, false);

    /** ELDER_GUARDIAN: sized(1.9975F, 1.9975F).eyeHeight(0.99875). */
    public static final EntityType ELDER_GUARDIAN = of("minecraft:elder_guardian", 1.9975F, 1.9975F, 0.99875F, 10, 3, true, false);

    /** TURTLE: sized(1.2F, 0.4F). */
    public static final EntityType TURTLE = of("minecraft:turtle", 1.2F, 0.4F, 0.4F * 0.85F, 10, 3, true, false);

    /** AXOLOTL: sized(0.75F, 0.42F).eyeHeight(0.2751). */
    public static final EntityType AXOLOTL = of("minecraft:axolotl", 0.75F, 0.42F, 0.2751F, 10, 3, true, false);

    /** TADPOLE: sized(0.4F, 0.3F).eyeHeight(0.19500001). */
    public static final EntityType TADPOLE = of("minecraft:tadpole", 0.4F, 0.3F, 0.19500001F, 10, 3, true, false);

    /** FROG: sized(0.5F, 0.5F). */
    public static final EntityType FROG = of("minecraft:frog", 0.5F, 0.5F, 0.5F * 0.85F, 10, 3, true, false);

    /** BAT: sized(0.5F, 0.9F).eyeHeight(0.45). */
    public static final EntityType BAT = of("minecraft:bat", 0.5F, 0.9F, 0.45F, 5, 3, true, false);

    /** BEE: sized(0.7F, 0.6F).eyeHeight(0.3). */
    public static final EntityType BEE = of("minecraft:bee", 0.7F, 0.6F, 0.3F, 8, 3, true, false);

    /** PARROT: sized(0.5F, 0.9F).eyeHeight(0.54). */
    public static final EntityType PARROT = of("minecraft:parrot", 0.5F, 0.9F, 0.54F, 8, 3, true, false);

    /** ALLAY: sized(0.35F, 0.6F).eyeHeight(0.36). */
    public static final EntityType ALLAY = of("minecraft:allay", 0.35F, 0.6F, 0.36F, 8, 2, true, false);

    /** PHANTOM: sized(0.9F, 0.5F).eyeHeight(0.175). */
    public static final EntityType PHANTOM = of("minecraft:phantom", 0.9F, 0.5F, 0.175F, 8, 3, true, false);

    /** GHAST: sized(4.0F, 4.0F).eyeHeight(2.6). */
    public static final EntityType GHAST = of("minecraft:ghast", 4.0F, 4.0F, 2.6F, 10, 3, true, true);

    /** BLAZE: sized(0.6F, 1.8F). */
    public static final EntityType BLAZE = of("minecraft:blaze", 0.6F, 1.8F, 1.8F * 0.85F, 8, 3, true, true);

    /** BREEZE: sized(0.6F, 1.77F).eyeHeight(1.3452). */
    public static final EntityType BREEZE = of("minecraft:breeze", 0.6F, 1.77F, 1.3452F, 10, 3, true, false);

    /** VEX: sized(0.4F, 0.8F).eyeHeight(0.51875). */
    public static final EntityType VEX = of("minecraft:vex", 0.4F, 0.8F, 0.51875F, 8, 3, true, true);

    /** ENDER_DRAGON: sized(16.0F, 8.0F). */
    public static final EntityType ENDER_DRAGON = of("minecraft:ender_dragon", 16.0F, 8.0F, 8.0F * 0.85F, 10, 3, true, true);

    /** WITHER: sized(0.9F, 3.5F). */
    public static final EntityType WITHER = of("minecraft:wither", 0.9F, 3.5F, 3.5F * 0.85F, 10, 3, true, true);

    /** HORSE: sized(1.3964844F, 1.6F).eyeHeight(1.52). */
    public static final EntityType HORSE = of("minecraft:horse", 1.3964844F, 1.6F, 1.52F, 10, 3, true, false);

    /** DONKEY: sized(1.3964844F, 1.5F).eyeHeight(1.425). */
    public static final EntityType DONKEY = of("minecraft:donkey", 1.3964844F, 1.5F, 1.425F, 10, 3, true, false);

    /** MULE: sized(1.3964844F, 1.6F).eyeHeight(1.52). */
    public static final EntityType MULE = of("minecraft:mule", 1.3964844F, 1.6F, 1.52F, 8, 3, true, false);

    /** SKELETON_HORSE: sized(1.3964844F, 1.6F).eyeHeight(1.52). */
    public static final EntityType SKELETON_HORSE = of("minecraft:skeleton_horse", 1.3964844F, 1.6F, 1.52F, 10, 3, true, false);

    /** ZOMBIE_HORSE: sized(1.3964844F, 1.6F).eyeHeight(1.52). */
    public static final EntityType ZOMBIE_HORSE = of("minecraft:zombie_horse", 1.3964844F, 1.6F, 1.52F, 10, 3, true, false);

    /** LLAMA: sized(0.9F, 1.87F).eyeHeight(1.7765). */
    public static final EntityType LLAMA = of("minecraft:llama", 0.9F, 1.87F, 1.7765F, 10, 3, true, false);

    /** TRADER_LLAMA: sized(0.9F, 1.87F).eyeHeight(1.7765). */
    public static final EntityType TRADER_LLAMA = of("minecraft:trader_llama", 0.9F, 1.87F, 1.7765F, 10, 3, true, false);

    /** CAMEL: sized(1.7F, 2.375F).eyeHeight(2.275). */
    public static final EntityType CAMEL = of("minecraft:camel", 1.7F, 2.375F, 2.275F, 10, 3, true, false);

    /** GOAT: sized(0.9F, 1.3F). */
    public static final EntityType GOAT = of("minecraft:goat", 0.9F, 1.3F, 1.3F * 0.85F, 10, 3, true, false);

    /** FOX: sized(0.6F, 0.7F).eyeHeight(0.4). */
    public static final EntityType FOX = of("minecraft:fox", 0.6F, 0.7F, 0.4F, 8, 3, true, false);

    /** PANDA: sized(1.3F, 1.25F). */
    public static final EntityType PANDA = of("minecraft:panda", 1.3F, 1.25F, 1.25F * 0.85F, 10, 3, true, false);

    /** ARMADILLO: sized(0.7F, 0.65F).eyeHeight(0.26). */
    public static final EntityType ARMADILLO = of("minecraft:armadillo", 0.7F, 0.65F, 0.26F, 10, 3, true, false);

    /** SNIFFER: sized(1.9F, 1.75F).eyeHeight(1.05). */
    public static final EntityType SNIFFER = of("minecraft:sniffer", 1.9F, 1.75F, 1.05F, 10, 3, true, false);

    /** STRIDER: sized(0.9F, 1.7F). */
    public static final EntityType STRIDER = of("minecraft:strider", 0.9F, 1.7F, 1.7F * 0.85F, 10, 3, true, true);

    /** IRON_GOLEM: sized(1.4F, 2.7F). */
    public static final EntityType IRON_GOLEM = of("minecraft:iron_golem", 1.4F, 2.7F, 2.7F * 0.85F, 10, 3, true, false);

    /** SNOW_GOLEM: sized(0.7F, 1.9F).eyeHeight(1.7). */
    public static final EntityType SNOW_GOLEM = of("minecraft:snow_golem", 0.7F, 1.9F, 1.7F, 8, 3, true, false);

    /** SHULKER: sized(1.0F, 1.0F).eyeHeight(0.5). */
    public static final EntityType SHULKER = of("minecraft:shulker", 1.0F, 1.0F, 0.5F, 10, 3, true, true);

    /** PIGLIN: sized(0.6F, 1.95F).eyeHeight(1.79). */
    public static final EntityType PIGLIN = of("minecraft:piglin", 0.6F, 1.95F, 1.79F, 8, 3, true, false);

    /** PIGLIN_BRUTE: sized(0.6F, 1.95F).eyeHeight(1.79). */
    public static final EntityType PIGLIN_BRUTE = of("minecraft:piglin_brute", 0.6F, 1.95F, 1.79F, 8, 3, true, false);

    /** HOGLIN: sized(1.3964844F, 1.4F). */
    public static final EntityType HOGLIN = of("minecraft:hoglin", 1.3964844F, 1.4F, 1.4F * 0.85F, 8, 3, true, false);

    /** ZOGLIN: sized(1.3964844F, 1.4F). */
    public static final EntityType ZOGLIN = of("minecraft:zoglin", 1.3964844F, 1.4F, 1.4F * 0.85F, 8, 3, true, true);

    /** PILLAGER: sized(0.6F, 1.95F). */
    public static final EntityType PILLAGER = of("minecraft:pillager", 0.6F, 1.95F, 1.95F * 0.85F, 8, 3, true, false);

    /** VINDICATOR: sized(0.6F, 1.95F). */
    public static final EntityType VINDICATOR = of("minecraft:vindicator", 0.6F, 1.95F, 1.95F * 0.85F, 8, 3, true, false);

    /** EVOKER: sized(0.6F, 1.95F). */
    public static final EntityType EVOKER = of("minecraft:evoker", 0.6F, 1.95F, 1.95F * 0.85F, 8, 3, true, false);

    /** ILLUSIONER: sized(0.6F, 1.95F). */
    public static final EntityType ILLUSIONER = of("minecraft:illusioner", 0.6F, 1.95F, 1.95F * 0.85F, 8, 3, true, false);

    /** RAVAGER: sized(1.95F, 2.2F). */
    public static final EntityType RAVAGER = of("minecraft:ravager", 1.95F, 2.2F, 2.2F * 0.85F, 10, 3, true, false);

    /** DROWNED: sized(0.6F, 1.95F).eyeHeight(1.74). */
    public static final EntityType DROWNED = of("minecraft:drowned", 0.6F, 1.95F, 1.74F, 8, 3, true, false);

    /** WARDEN: sized(0.9F, 2.9F). */
    public static final EntityType WARDEN = of("minecraft:warden", 0.9F, 2.9F, 2.9F * 0.85F, 16, 3, true, true);

    /** WANDERING_TRADER: sized(0.6F, 1.95F).eyeHeight(1.62). */
    public static final EntityType WANDERING_TRADER = of("minecraft:wandering_trader", 0.6F, 1.95F, 1.62F, 10, 3, true, false);


    /** SMALL_FIREBALL: sized(0.3125F, 0.3125F). */
    public static final EntityType SMALL_FIREBALL = of("minecraft:small_fireball", 0.3125F, 0.3125F, 0.3125F * 0.85F, 4, 10, true, false);

    /** FIREBALL: sized(1.0F, 1.0F). */
    public static final EntityType FIREBALL = of("minecraft:fireball", 1.0F, 1.0F, 1.0F * 0.85F, 4, 10, true, false);

    /** AREA_EFFECT_CLOUD: sized(6.0F, 0.5F). */
    public static final EntityType AREA_EFFECT_CLOUD = of("minecraft:area_effect_cloud", 6.0F, 0.5F, 0.5F * 0.85F, 10, Integer.MAX_VALUE, true, true);

    /** WITHER_SKULL: sized(0.3125F, 0.3125F). */
    public static final EntityType WITHER_SKULL = of("minecraft:wither_skull", 0.3125F, 0.3125F, 0.3125F * 0.85F, 4, 10, true, false);

    /** DRAGON_FIREBALL: sized(1.0F, 1.0F). */
    public static final EntityType DRAGON_FIREBALL = of("minecraft:dragon_fireball", 1.0F, 1.0F, 1.0F * 0.85F, 4, 10, true, false);

    /** LLAMA_SPIT: sized(0.25F, 0.25F). */
    public static final EntityType LLAMA_SPIT = of("minecraft:llama_spit", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** EGG: sized(0.25F, 0.25F). */
    public static final EntityType EGG = of("minecraft:egg", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** ENDER_PEARL: sized(0.25F, 0.25F). */
    public static final EntityType ENDER_PEARL = of("minecraft:ender_pearl", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** EXPERIENCE_BOTTLE: sized(0.25F, 0.25F). */
    public static final EntityType EXPERIENCE_BOTTLE = of("minecraft:experience_bottle", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** EYE_OF_ENDER: sized(0.25F, 0.25F). */
    public static final EntityType EYE_OF_ENDER = of("minecraft:eye_of_ender", 0.25F, 0.25F, 0.25F * 0.85F, 4, 4, true, false);

    /** SPECTRAL_ARROW: sized(0.5F, 0.5F).eyeHeight(0.13). */
    public static final EntityType SPECTRAL_ARROW = of("minecraft:spectral_arrow", 0.5F, 0.5F, 0.13F, 4, 20, true, false);

    /** FIREWORK_ROCKET: sized(0.25F, 0.25F). */
    public static final EntityType FIREWORK_ROCKET = of("minecraft:firework_rocket", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);

    /** WIND_CHARGE: sized(0.3125F, 0.3125F).eyeHeight(0.0). */
    public static final EntityType WIND_CHARGE = of("minecraft:wind_charge", 0.3125F, 0.3125F, 0.0F, 4, 10, true, false);

    /** BREEZE_WIND_CHARGE: sized(0.3125F, 0.3125F).eyeHeight(0.0). */
    public static final EntityType BREEZE_WIND_CHARGE = of("minecraft:breeze_wind_charge", 0.3125F, 0.3125F, 0.0F, 4, 10, true, false);

    /** END_CRYSTAL: sized(2.0F, 2.0F). */
    public static final EntityType END_CRYSTAL = of("minecraft:end_crystal", 2.0F, 2.0F, 2.0F * 0.85F, 16, Integer.MAX_VALUE, true, true);

    /** MARKER: sized(0.0F, 0.0F). */
    public static final EntityType MARKER = of("minecraft:marker", 0.0F, 0.0F, 0.0F * 0.85F, 0, 3, true, false);

    /** INTERACTION: sized(0.0F, 0.0F). */
    public static final EntityType INTERACTION = of("minecraft:interaction", 0.0F, 0.0F, 0.0F * 0.85F, 10, 3, true, false);

    /** BLOCK_DISPLAY: sized(0.0F, 0.0F). */
    public static final EntityType BLOCK_DISPLAY = of("minecraft:block_display", 0.0F, 0.0F, 0.0F * 0.85F, 10, 1, true, false);

    /** ITEM_DISPLAY: sized(0.0F, 0.0F). */
    public static final EntityType ITEM_DISPLAY = of("minecraft:item_display", 0.0F, 0.0F, 0.0F * 0.85F, 10, 1, true, false);

    /** TEXT_DISPLAY: sized(0.0F, 0.0F). */
    public static final EntityType TEXT_DISPLAY = of("minecraft:text_display", 0.0F, 0.0F, 0.0F * 0.85F, 10, 1, true, false);

    /** FALLING_BLOCK: sized(0.98F, 0.98F). */
    public static final EntityType FALLING_BLOCK = of("minecraft:falling_block", 0.98F, 0.98F, 0.98F * 0.85F, 10, 20, true, false);

    /** LIGHTNING_BOLT: noSave, sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE). */
    public static final EntityType LIGHTNING_BOLT = of("minecraft:lightning_bolt", 0.0F, 0.0F, 0.0F * 0.85F, 16, Integer.MAX_VALUE, true, false);

    /** ITEM_FRAME. */
    public static final EntityType ITEM_FRAME = of("minecraft:item_frame", 0.5F, 0.5F, 0.0F, 10, Integer.MAX_VALUE, true, false);

    /** GLOW_ITEM_FRAME. */
    public static final EntityType GLOW_ITEM_FRAME = of("minecraft:glow_item_frame", 0.5F, 0.5F, 0.0F, 10, Integer.MAX_VALUE, true, false);

    /** PAINTING. */
    public static final EntityType PAINTING = of("minecraft:painting", 0.5F, 0.5F, 0.5F * 0.85F, 10, Integer.MAX_VALUE, true, false);

    /** LEASH_KNOT. */
    public static final EntityType LEASH_KNOT = of("minecraft:leash_knot", 0.375F, 0.5F, 0.0625F, 10, Integer.MAX_VALUE, true, false);

    /** OMINOUS_ITEM_SPAWNER: sized(0.25F, 0.25F).clientTrackingRange(8). */
    public static final EntityType OMINOUS_ITEM_SPAWNER = of("minecraft:ominous_item_spawner", 0.25F, 0.25F, 0.25F * 0.85F, 8, 3, true, false);

    /** ARMOR_STAND: sized(0.5F, 1.975F).eyeHeight(1.7775). */
    public static final EntityType ARMOR_STAND = of("minecraft:armor_stand", 0.5F, 1.975F, 1.7775F, 10, 3, true, false);

    /** SHULKER_BULLET: sized(0.3125F, 0.3125F). */
    public static final EntityType SHULKER_BULLET = of("minecraft:shulker_bullet", 0.3125F, 0.3125F, 0.3125F * 0.85F, 8, 3, true, false);

    /** EVOKER_FANGS: sized(0.5F, 0.8F). */
    public static final EntityType EVOKER_FANGS = of("minecraft:evoker_fangs", 0.5F, 0.8F, 0.8F * 0.85F, 6, 2, true, false);


    /** SNOWBALL: sized(0.25F, 0.25F). */
    public static final EntityType SNOWBALL = of("minecraft:snowball", 0.25F, 0.25F, 0.25F * 0.85F, 4, 10, true, false);


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

    /** TRIDENT: sized(0.5F, 0.5F).eyeHeight(0.13). */
    public static final EntityType TRIDENT = of("minecraft:trident", 0.5F, 0.5F, 0.13F, 4, 20, true, false);

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
