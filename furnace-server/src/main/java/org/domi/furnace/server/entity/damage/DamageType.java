// 바닐라 damage_type 레지스트리 중 서버가 지금 쓰는 항목(data/minecraft/damage_type/*.json 과 damage_type 태그 그대로).
package org.domi.furnace.server.entity.damage;

import org.domi.furnace.server.net.registry.RegistrySet;

public enum DamageType {
    // key, message_id, exhaustion, fall_variants, bypasses_armor, bypasses_invulnerability, is_fall, no_impact, no_knockback, is_drowning
    GENERIC("generic", "generic", 0.0f, false, true, false, false, false, true, false),
    FALL("fall", "fall", 0.0f, true, true, false, true, false, true, false),
    OUT_OF_WORLD("out_of_world", "outOfWorld", 0.0f, false, true, true, false, false, true, false),
    GENERIC_KILL("generic_kill", "genericKill", 0.0f, false, true, true, false, false, true, false),
    STARVE("starve", "starve", 0.0f, false, true, false, false, false, true, false),
    DROWN("drown", "drown", 0.0f, false, true, false, false, true, true, true),
    OUTSIDE_BORDER("outside_border", "outsideBorder", 0.0f, false, true, false, false, false, true, false),
    IN_WALL("in_wall", "inWall", 0.0f, false, true, false, false, false, true, false),
    MAGIC("magic", "magic", 0.0f, false, true, false, false, false, false, false),
    MOB_ATTACK("mob_attack", "mob", 0.1f, false, false, false, false, false, false, false),
    PLAYER_ATTACK("player_attack", "player", 0.1f, false, false, false, false, false, false, false),
    ARROW("arrow", "arrow", 0.1f, false, false, false, false, false, false, false),
    EXPLOSION("explosion", "explosion", 0.1f, false, false, false, false, false, false, false),
    PLAYER_EXPLOSION("player_explosion", "explosion.player", 0.1f, false, false, false, false, false, false, false),
    MOB_PROJECTILE("mob_projectile", "mob", 0.1f, false, false, false, false, false, false, false),
    WITHER("wither", "wither", 0.0f, false, true, false, false, false, false, false);

    private final String key;
    private final String msgId;
    private final float exhaustion;
    private final boolean fallVariants;
    private final boolean bypassesArmor;
    private final boolean bypassesInvulnerability;
    private final boolean isFall;
    private final boolean noImpact;
    private final boolean noKnockback;
    private final boolean isDrowning;
    private volatile int registryId = -1;

    DamageType(String key, String msgId, float exhaustion, boolean fallVariants, boolean bypassesArmor,
               boolean bypassesInvulnerability, boolean isFall, boolean noImpact, boolean noKnockback, boolean isDrowning) {
        this.key = key;
        this.msgId = msgId;
        this.exhaustion = exhaustion;
        this.fallVariants = fallVariants;
        this.bypassesArmor = bypassesArmor;
        this.bypassesInvulnerability = bypassesInvulnerability;
        this.isFall = isFall;
        this.noImpact = noImpact;
        this.noKnockback = noKnockback;
        this.isDrowning = isDrowning;
    }

    private static final class Registry {
        static final RegistrySet SET = RegistrySet.load("registries/1.21.1.txt");
    }

    public String key() { return "minecraft:" + key; }
    public String msgId() { return msgId; }
    public float exhaustion() { return exhaustion; }
    public boolean fallVariants() { return fallVariants; }
    public boolean bypassesArmor() { return bypassesArmor; }
    public boolean bypassesInvulnerability() { return bypassesInvulnerability; }
    /** 태그 always_most_significant_fall(out_of_world). */
    public boolean alwaysMostSignificantFall() { return this == OUT_OF_WORLD; }
    public boolean isFall() { return isFall; }
    /** damage_type scaling when_caused_by_living_non_player: 난이도에 따라 피해가 바뀐다(몹이 때린 근접·화살 등). */
    public boolean scalesWithDifficulty() { return this == MOB_ATTACK || this == ARROW || this == MOB_PROJECTILE || this == EXPLOSION || this == PLAYER_EXPLOSION; }
    /** 난이도 배율이 때린 쪽과 무관하게 항상 붙는가(explosion 은 always). */
    public boolean scalesAlways() { return this == EXPLOSION || this == PLAYER_EXPLOSION; }
    public boolean noImpact() { return noImpact; }
    public boolean noKnockback() { return noKnockback; }
    public boolean isDrowning() { return isDrowning; }
    /** 태그 bypasses_effects(굶주림): 효과(저항)가 줄이지 못한다. */
    /** 태그 bypasses_enchantments(허공·/kill). */
    public boolean bypassesEnchantments() { return this == OUT_OF_WORLD || this == GENERIC_KILL; }
    public boolean bypassesEffects() { return this == STARVE; }
    /** 태그 bypasses_resistance(허공·/kill): 저항 효과가 줄이지 못한다. */
    public boolean bypassesResistance() { return this == OUT_OF_WORLD || this == GENERIC_KILL; }

    /** 태그 panic_causes 에 들어 있나. */
    public boolean isPanicCause() { return isInTag("minecraft:panic_causes"); }

    public boolean isInTag(String tag) {
        int[] ids = Tags.SET.ids("minecraft:damage_type", tag);
        int me = registryId();
        for (int i : ids) if (i == me) return true;
        return false;
    }

    private static final class Tags {
        static final org.domi.furnace.server.net.registry.TagSet SET = org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt");
    }

    /** 어떤 종류 키("fall", "minecraft:arrow" …)가 damage_type 태그에 들어 있나(enum 에 없는 종류도 본다). */
    public static boolean keyHasTag(String key, String tag) {
        String full = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        int me = Math.max(0, Registry.SET.indexOf("minecraft:damage_type", full));
        int[] ids = Tags.SET.ids("minecraft:damage_type", tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        if (ids == null) return false;
        for (int i : ids) if (i == me) return true;
        return false;
    }

    public static DamageType byKey(String key) {
        for (DamageType t : values()) if (t.key.equals(key)) return t;
        return GENERIC;
    }

    /** 설정 단계에서 보낸 damage_type 레지스트리 안의 순번(damage_event 패킷용). */
    public int registryId() {
        int id = registryId;
        if (id < 0) registryId = id = Math.max(0, Registry.SET.indexOf("minecraft:damage_type", key()));
        return id;
    }
}
