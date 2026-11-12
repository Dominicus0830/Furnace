// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.
// particle_type 레지스트리 1.21.1 의 109 종(레지스트리 순서). 상수 이름은 키를 대문자로 한 것(1.20.5 이후 Bukkit 과 같다).
// 옛 이름(REDSTONE·EXPLOSION_NORMAL …)은 Paper 처럼 클래스를 올릴 때 바꿔 준다(compat.nms.ApiRenames).
package org.bukkit;

import java.util.HashMap;
import java.util.Map;

public enum Particle implements Keyed {
    ANGRY_VILLAGER("angry_villager", Void.class),
    BLOCK("block", org.bukkit.block.data.BlockData.class),
    BLOCK_MARKER("block_marker", org.bukkit.block.data.BlockData.class),
    BUBBLE("bubble", Void.class),
    CLOUD("cloud", Void.class),
    CRIT("crit", Void.class),
    DAMAGE_INDICATOR("damage_indicator", Void.class),
    DRAGON_BREATH("dragon_breath", Void.class),
    DRIPPING_LAVA("dripping_lava", Void.class),
    FALLING_LAVA("falling_lava", Void.class),
    LANDING_LAVA("landing_lava", Void.class),
    DRIPPING_WATER("dripping_water", Void.class),
    FALLING_WATER("falling_water", Void.class),
    DUST("dust", DustOptions.class),
    DUST_COLOR_TRANSITION("dust_color_transition", DustTransition.class),
    EFFECT("effect", Void.class),
    ELDER_GUARDIAN("elder_guardian", Void.class),
    ENCHANTED_HIT("enchanted_hit", Void.class),
    ENCHANT("enchant", Void.class),
    END_ROD("end_rod", Void.class),
    ENTITY_EFFECT("entity_effect", Color.class),
    EXPLOSION_EMITTER("explosion_emitter", Void.class),
    EXPLOSION("explosion", Void.class),
    GUST("gust", Void.class),
    SMALL_GUST("small_gust", Void.class),
    GUST_EMITTER_LARGE("gust_emitter_large", Void.class),
    GUST_EMITTER_SMALL("gust_emitter_small", Void.class),
    SONIC_BOOM("sonic_boom", Void.class),
    FALLING_DUST("falling_dust", org.bukkit.block.data.BlockData.class),
    FIREWORK("firework", Void.class),
    FISHING("fishing", Void.class),
    FLAME("flame", Void.class),
    INFESTED("infested", Void.class),
    CHERRY_LEAVES("cherry_leaves", Void.class),
    SCULK_SOUL("sculk_soul", Void.class),
    SCULK_CHARGE("sculk_charge", Float.class),
    SCULK_CHARGE_POP("sculk_charge_pop", Void.class),
    SOUL_FIRE_FLAME("soul_fire_flame", Void.class),
    SOUL("soul", Void.class),
    FLASH("flash", Void.class),
    HAPPY_VILLAGER("happy_villager", Void.class),
    COMPOSTER("composter", Void.class),
    HEART("heart", Void.class),
    INSTANT_EFFECT("instant_effect", Void.class),
    ITEM("item", org.bukkit.inventory.ItemStack.class),
    VIBRATION("vibration", Vibration.class),
    ITEM_SLIME("item_slime", Void.class),
    ITEM_COBWEB("item_cobweb", Void.class),
    ITEM_SNOWBALL("item_snowball", Void.class),
    LARGE_SMOKE("large_smoke", Void.class),
    LAVA("lava", Void.class),
    MYCELIUM("mycelium", Void.class),
    NOTE("note", Void.class),
    POOF("poof", Void.class),
    PORTAL("portal", Void.class),
    RAIN("rain", Void.class),
    SMOKE("smoke", Void.class),
    WHITE_SMOKE("white_smoke", Void.class),
    SNEEZE("sneeze", Void.class),
    SPIT("spit", Void.class),
    SQUID_INK("squid_ink", Void.class),
    SWEEP_ATTACK("sweep_attack", Void.class),
    TOTEM_OF_UNDYING("totem_of_undying", Void.class),
    UNDERWATER("underwater", Void.class),
    SPLASH("splash", Void.class),
    WITCH("witch", Void.class),
    BUBBLE_POP("bubble_pop", Void.class),
    CURRENT_DOWN("current_down", Void.class),
    BUBBLE_COLUMN_UP("bubble_column_up", Void.class),
    NAUTILUS("nautilus", Void.class),
    DOLPHIN("dolphin", Void.class),
    CAMPFIRE_COSY_SMOKE("campfire_cosy_smoke", Void.class),
    CAMPFIRE_SIGNAL_SMOKE("campfire_signal_smoke", Void.class),
    DRIPPING_HONEY("dripping_honey", Void.class),
    FALLING_HONEY("falling_honey", Void.class),
    LANDING_HONEY("landing_honey", Void.class),
    FALLING_NECTAR("falling_nectar", Void.class),
    FALLING_SPORE_BLOSSOM("falling_spore_blossom", Void.class),
    ASH("ash", Void.class),
    CRIMSON_SPORE("crimson_spore", Void.class),
    WARPED_SPORE("warped_spore", Void.class),
    SPORE_BLOSSOM_AIR("spore_blossom_air", Void.class),
    DRIPPING_OBSIDIAN_TEAR("dripping_obsidian_tear", Void.class),
    FALLING_OBSIDIAN_TEAR("falling_obsidian_tear", Void.class),
    LANDING_OBSIDIAN_TEAR("landing_obsidian_tear", Void.class),
    REVERSE_PORTAL("reverse_portal", Void.class),
    WHITE_ASH("white_ash", Void.class),
    SMALL_FLAME("small_flame", Void.class),
    SNOWFLAKE("snowflake", Void.class),
    DRIPPING_DRIPSTONE_LAVA("dripping_dripstone_lava", Void.class),
    FALLING_DRIPSTONE_LAVA("falling_dripstone_lava", Void.class),
    DRIPPING_DRIPSTONE_WATER("dripping_dripstone_water", Void.class),
    FALLING_DRIPSTONE_WATER("falling_dripstone_water", Void.class),
    GLOW_SQUID_INK("glow_squid_ink", Void.class),
    GLOW("glow", Void.class),
    WAX_ON("wax_on", Void.class),
    WAX_OFF("wax_off", Void.class),
    ELECTRIC_SPARK("electric_spark", Void.class),
    SCRAPE("scrape", Void.class),
    SHRIEK("shriek", Integer.class),
    EGG_CRACK("egg_crack", Void.class),
    DUST_PLUME("dust_plume", Void.class),
    TRIAL_SPAWNER_DETECTION("trial_spawner_detection", Void.class),
    TRIAL_SPAWNER_DETECTION_OMINOUS("trial_spawner_detection_ominous", Void.class),
    VAULT_CONNECTION("vault_connection", Void.class),
    DUST_PILLAR("dust_pillar", org.bukkit.block.data.BlockData.class),
    OMINOUS_SPAWNING("ominous_spawning", Void.class),
    RAID_OMEN("raid_omen", Void.class),
    TRIAL_OMEN("trial_omen", Void.class);

    private static final Map<NamespacedKey, Particle> BY_KEY = new HashMap<>();

    static {
        for (Particle p : values()) BY_KEY.put(p.key, p);
    }

    private final NamespacedKey key;
    private final Class<?> dataType;

    Particle(String key, Class<?> dataType) {
        this.key = NamespacedKey.minecraft(key);
        this.dataType = dataType;
    }

    /** spawnParticle 의 data 로 받는 형(옵션이 없으면 Void). */
    public Class<?> getDataType() { return dataType; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static Particle byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }

    /** dust 입자의 색과 크기. */
    public static class DustOptions {
        private final Color color;
        private final float size;

        public DustOptions(Color color, float size) {
            if (color == null) throw new IllegalArgumentException("color");
            this.color = color;
            this.size = size;
        }

        public Color getColor() { return color; }

        public float getSize() { return size; }
    }

    /** dust_color_transition 입자의 시작색·끝색과 크기. */
    public static class DustTransition extends DustOptions {
        private final Color toColor;

        public DustTransition(Color fromColor, Color toColor, float size) {
            super(fromColor, size);
            if (toColor == null) throw new IllegalArgumentException("toColor");
            this.toColor = toColor;
        }

        public Color getToColor() { return toColor; }
    }
}
