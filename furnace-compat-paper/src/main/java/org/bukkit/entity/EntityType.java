// 이 파일은 tools/compat/gen-bukkit-registries.py 가 만든다 — 손으로 고치지 않는다.
// 엔티티 종류 레지스트리 1.21.1. 상수 이름은 키를 대문자로 한 것이다(Bukkit 의 옛 별칭은 두지 않았다).
package org.bukkit.entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum EntityType implements Keyed {
    ALLAY(true, true),
    AREA_EFFECT_CLOUD(false, true),
    ARMADILLO(true, true),
    ARMOR_STAND(true, true),
    ARROW(false, true),
    AXOLOTL(true, true),
    BAT(true, true),
    BEE(true, true),
    BLAZE(true, true),
    BLOCK_DISPLAY(false, true),
    BOAT(false, true),
    BOGGED(true, true),
    BREEZE(true, true),
    BREEZE_WIND_CHARGE(false, true),
    CAMEL(true, true),
    CAT(true, true),
    CAVE_SPIDER(true, true),
    CHEST_BOAT(false, true),
    CHEST_MINECART(false, true),
    CHICKEN(true, true),
    COD(true, true),
    COMMAND_BLOCK_MINECART(false, true),
    COW(true, true),
    CREEPER(true, true),
    DOLPHIN(true, true),
    DONKEY(true, true),
    DRAGON_FIREBALL(false, true),
    DROWNED(true, true),
    EGG(false, true),
    ELDER_GUARDIAN(true, true),
    END_CRYSTAL(false, true),
    ENDER_DRAGON(true, true),
    ENDER_PEARL(false, true),
    ENDERMAN(true, true),
    ENDERMITE(true, true),
    EVOKER(true, true),
    EVOKER_FANGS(false, true),
    EXPERIENCE_BOTTLE(false, true),
    EXPERIENCE_ORB(false, true),
    EYE_OF_ENDER(false, true),
    FALLING_BLOCK(false, true),
    FIREBALL(false, true),
    FIREWORK_ROCKET(false, true),
    FISHING_BOBBER(false, false),
    FOX(true, true),
    FROG(true, true),
    FURNACE_MINECART(false, true),
    GHAST(true, true),
    GIANT(true, true),
    GLOW_ITEM_FRAME(false, true),
    GLOW_SQUID(true, true),
    GOAT(true, true),
    GUARDIAN(true, true),
    HOGLIN(true, true),
    HOPPER_MINECART(false, true),
    HORSE(true, true),
    HUSK(true, true),
    ILLUSIONER(true, true),
    INTERACTION(false, true),
    IRON_GOLEM(true, true),
    ITEM(false, true),
    ITEM_DISPLAY(false, true),
    ITEM_FRAME(false, true),
    LEASH_KNOT(false, true),
    LIGHTNING_BOLT(false, true),
    LLAMA(true, true),
    LLAMA_SPIT(false, true),
    MAGMA_CUBE(true, true),
    MARKER(false, true),
    MINECART(false, true),
    MOOSHROOM(true, true),
    MULE(true, true),
    OCELOT(true, true),
    OMINOUS_ITEM_SPAWNER(false, true),
    PAINTING(false, true),
    PANDA(true, true),
    PARROT(true, true),
    PHANTOM(true, true),
    PIG(true, true),
    PIGLIN(true, true),
    PIGLIN_BRUTE(true, true),
    PILLAGER(true, true),
    PLAYER(true, false),
    POLAR_BEAR(true, true),
    POTION(false, true),
    PUFFERFISH(true, true),
    RABBIT(true, true),
    RAVAGER(true, true),
    SALMON(true, true),
    SHEEP(true, true),
    SHULKER(true, true),
    SHULKER_BULLET(false, true),
    SILVERFISH(true, true),
    SKELETON(true, true),
    SKELETON_HORSE(true, true),
    SLIME(true, true),
    SMALL_FIREBALL(false, true),
    SNIFFER(true, true),
    SNOW_GOLEM(true, true),
    SNOWBALL(false, true),
    SPAWNER_MINECART(false, true),
    SPECTRAL_ARROW(false, true),
    SPIDER(true, true),
    SQUID(true, true),
    STRAY(true, true),
    STRIDER(true, true),
    TADPOLE(true, true),
    TEXT_DISPLAY(false, true),
    TNT(false, true),
    TNT_MINECART(false, true),
    TRADER_LLAMA(true, true),
    TRIDENT(false, true),
    TROPICAL_FISH(true, true),
    TURTLE(true, true),
    VEX(true, true),
    VILLAGER(true, true),
    VINDICATOR(true, true),
    WANDERING_TRADER(true, true),
    WARDEN(true, true),
    WIND_CHARGE(false, true),
    WITCH(true, true),
    WITHER(true, true),
    WITHER_SKELETON(true, true),
    WITHER_SKULL(false, true),
    WOLF(true, true),
    ZOGLIN(true, true),
    ZOMBIE(true, true),
    ZOMBIE_HORSE(true, true),
    ZOMBIE_VILLAGER(true, true),
    ZOMBIFIED_PIGLIN(true, true),
    /** 알 수 없는 종류(Bukkit 과 같은 자리). 키는 없다. */
    UNKNOWN(false, false);

    private static final Map<String, EntityType> BY_KEY = new HashMap<>();

    private final boolean living, spawnable;

    EntityType(boolean living, boolean spawnable) {
        this.living = living;
        this.spawnable = spawnable;
    }

    /** 살아 있는 엔티티(LivingEntity) 종류인가. UNKNOWN 은 false. */
    public boolean isAlive() { return living; }

    /** 서버가 따로 만들어 낼 수 있는 종류인가(Paper: 낚시찌·플레이어·UNKNOWN 만 false). */
    public boolean isSpawnable() { return spawnable; }

    static {
        for (EntityType t : values()) if (t != UNKNOWN) BY_KEY.put(t.name().toLowerCase(Locale.ROOT), t);
    }

    /** 키가 없는 UNKNOWN 은 NullPointerException 이 아니라 IllegalArgumentException(Bukkit 과 같다). */
    @Override
    public NamespacedKey getKey() {
        if (this == UNKNOWN) throw new IllegalArgumentException("UNKNOWN 은 키가 없다");
        return NamespacedKey.minecraft(name().toLowerCase(Locale.ROOT));
    }

    /** Bukkit 옛 이름(키의 경로 부분, 예 "zombie"). UNKNOWN 은 null. 패킷 라이브러리가 NMS 종류와 잇는 데 쓴다. */
    @Deprecated
    public String getName() { return this == UNKNOWN ? null : name().toLowerCase(Locale.ROOT); }

    /** 옛 이름("zombie")으로 찾는다. 없으면 null. */
    @Deprecated
    public static EntityType fromName(String name) { return name == null ? null : BY_KEY.get(name.toLowerCase(Locale.ROOT)); }

    /** "minecraft:zombie"·"zombie"·"ZOMBIE" 로 찾는다. 없으면 null. */
    public static EntityType fromKey(String key) {
        if (key == null) return null;
        String k = key.strip().toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        return BY_KEY.get(k);
    }
}
