// 이 파일은 tools/compat/gen-bukkit-registries.py 가 만든다 — 손으로 고치지 않는다.
// 엔티티 종류 레지스트리 1.21.1. 상수 이름은 키를 대문자로 한 것이다(Bukkit 의 옛 별칭은 두지 않았다).
package org.bukkit.entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum EntityType implements Keyed {
    ALLAY,
    AREA_EFFECT_CLOUD,
    ARMADILLO,
    ARMOR_STAND,
    ARROW,
    AXOLOTL,
    BAT,
    BEE,
    BLAZE,
    BLOCK_DISPLAY,
    BOAT,
    BOGGED,
    BREEZE,
    BREEZE_WIND_CHARGE,
    CAMEL,
    CAT,
    CAVE_SPIDER,
    CHEST_BOAT,
    CHEST_MINECART,
    CHICKEN,
    COD,
    COMMAND_BLOCK_MINECART,
    COW,
    CREEPER,
    DOLPHIN,
    DONKEY,
    DRAGON_FIREBALL,
    DROWNED,
    EGG,
    ELDER_GUARDIAN,
    END_CRYSTAL,
    ENDER_DRAGON,
    ENDER_PEARL,
    ENDERMAN,
    ENDERMITE,
    EVOKER,
    EVOKER_FANGS,
    EXPERIENCE_BOTTLE,
    EXPERIENCE_ORB,
    EYE_OF_ENDER,
    FALLING_BLOCK,
    FIREBALL,
    FIREWORK_ROCKET,
    FISHING_BOBBER,
    FOX,
    FROG,
    FURNACE_MINECART,
    GHAST,
    GIANT,
    GLOW_ITEM_FRAME,
    GLOW_SQUID,
    GOAT,
    GUARDIAN,
    HOGLIN,
    HOPPER_MINECART,
    HORSE,
    HUSK,
    ILLUSIONER,
    INTERACTION,
    IRON_GOLEM,
    ITEM,
    ITEM_DISPLAY,
    ITEM_FRAME,
    LEASH_KNOT,
    LIGHTNING_BOLT,
    LLAMA,
    LLAMA_SPIT,
    MAGMA_CUBE,
    MARKER,
    MINECART,
    MOOSHROOM,
    MULE,
    OCELOT,
    OMINOUS_ITEM_SPAWNER,
    PAINTING,
    PANDA,
    PARROT,
    PHANTOM,
    PIG,
    PIGLIN,
    PIGLIN_BRUTE,
    PILLAGER,
    PLAYER,
    POLAR_BEAR,
    POTION,
    PUFFERFISH,
    RABBIT,
    RAVAGER,
    SALMON,
    SHEEP,
    SHULKER,
    SHULKER_BULLET,
    SILVERFISH,
    SKELETON,
    SKELETON_HORSE,
    SLIME,
    SMALL_FIREBALL,
    SNIFFER,
    SNOW_GOLEM,
    SNOWBALL,
    SPAWNER_MINECART,
    SPECTRAL_ARROW,
    SPIDER,
    SQUID,
    STRAY,
    STRIDER,
    TADPOLE,
    TEXT_DISPLAY,
    TNT,
    TNT_MINECART,
    TRADER_LLAMA,
    TRIDENT,
    TROPICAL_FISH,
    TURTLE,
    VEX,
    VILLAGER,
    VINDICATOR,
    WANDERING_TRADER,
    WARDEN,
    WIND_CHARGE,
    WITCH,
    WITHER,
    WITHER_SKELETON,
    WITHER_SKULL,
    WOLF,
    ZOGLIN,
    ZOMBIE,
    ZOMBIE_HORSE,
    ZOMBIE_VILLAGER,
    ZOMBIFIED_PIGLIN,
    /** 알 수 없는 종류(Bukkit 과 같은 자리). 키는 없다. */
    UNKNOWN;

    private static final Map<String, EntityType> BY_KEY = new HashMap<>();

    static {
        for (EntityType t : values()) if (t != UNKNOWN) BY_KEY.put(t.name().toLowerCase(Locale.ROOT), t);
    }

    /** 키가 없는 UNKNOWN 은 NullPointerException 이 아니라 IllegalArgumentException(Bukkit 과 같다). */
    @Override
    public NamespacedKey getKey() {
        if (this == UNKNOWN) throw new IllegalArgumentException("UNKNOWN 은 키가 없다");
        return NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT));
    }

    /** "minecraft:zombie"·"zombie"·"ZOMBIE" 로 찾는다. 없으면 null. */
    public static EntityType fromKey(String key) {
        if (key == null) return null;
        String k = key.strip().toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        return BY_KEY.get(k);
    }
}
