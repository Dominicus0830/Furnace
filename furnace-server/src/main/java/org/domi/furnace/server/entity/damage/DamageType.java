// 바닐라 damage_type 레지스트리 전체(47종). 속성은 data/minecraft/damage_type/*.json 에서 뽑은 registries/damage_types-1.21.1.txt,
// 성질(방어구 무시·넉백 없음 …)은 damage_type 태그(registries/tags-1.21.1.txt)에서 읽는다. 자주 쓰는 유형은 상수로 둔다.
package org.domi.furnace.server.entity.damage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;

public final class DamageType {

    /** 바닐라 DamageScaling. */
    public enum Scaling { NEVER, WHEN_CAUSED_BY_LIVING_NON_PLAYER, ALWAYS }

    /** 바닐라 DamageEffects(맞을 때 소리). */
    public enum Effects { HURT, THORNS, DROWNING, BURNING, POKING, FREEZING }

    /** 바닐라 DeathMessageType. */
    public enum DeathMessageType { DEFAULT, FALL_VARIANTS, INTENTIONAL_GAME_DESIGN }

    private static final class Registry {
        static final RegistrySet SET = RegistrySet.load("registries/1.21.1.txt");
    }

    private static final class Tags {
        static final TagSet SET = TagSet.load("registries/tags-1.21.1.txt");
    }

    private static final Map<String, DamageType> BY_KEY = load();

    public static final DamageType GENERIC = require("generic");
    public static final DamageType FALL = require("fall");
    public static final DamageType OUT_OF_WORLD = require("out_of_world");
    public static final DamageType GENERIC_KILL = require("generic_kill");
    public static final DamageType STARVE = require("starve");
    public static final DamageType DROWN = require("drown");
    public static final DamageType OUTSIDE_BORDER = require("outside_border");
    public static final DamageType IN_WALL = require("in_wall");
    public static final DamageType MAGIC = require("magic");
    public static final DamageType MOB_ATTACK = require("mob_attack");
    public static final DamageType PLAYER_ATTACK = require("player_attack");
    public static final DamageType ARROW = require("arrow");
    public static final DamageType EXPLOSION = require("explosion");
    public static final DamageType PLAYER_EXPLOSION = require("player_explosion");
    public static final DamageType MOB_PROJECTILE = require("mob_projectile");
    public static final DamageType WITHER = require("wither");
    public static final DamageType CAMPFIRE = require("campfire");
    /** 침대(네더·엔드)·리스폰 정박기(네더 밖) 폭발 — death_message_type intentional_game_design. */
    public static final DamageType BAD_RESPAWN_POINT = require("bad_respawn_point");

    private final String key;
    private final String msgId;
    private final Scaling scaling;
    private final float exhaustion;
    private final Effects effects;
    private final DeathMessageType deathMessageType;
    private volatile int registryId = -1;

    private DamageType(String key, String msgId, Scaling scaling, float exhaustion, Effects effects, DeathMessageType deathMessageType) {
        this.key = key;
        this.msgId = msgId;
        this.scaling = scaling;
        this.exhaustion = exhaustion;
        this.effects = effects;
        this.deathMessageType = deathMessageType;
    }

    private static Map<String, DamageType> load() {
        Map<String, DamageType> m = new LinkedHashMap<>();
        try (InputStream in = DamageType.class.getClassLoader().getResourceAsStream("registries/damage_types-1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("registries/damage_types-1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split(" ");
                String id = f[0].substring(f[0].indexOf(':') + 1);
                m.put(id, new DamageType(id, f[1], Scaling.valueOf(f[2].toUpperCase(Locale.ROOT)), Float.parseFloat(f[3]),
                        Effects.valueOf(f[4].toUpperCase(Locale.ROOT)), DeathMessageType.valueOf(f[5].toUpperCase(Locale.ROOT))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableMap(m);
    }

    private static DamageType require(String key) {
        DamageType t = BY_KEY.get(key);
        if (t == null) throw new IllegalStateException("damage_type 없음: " + key);
        return t;
    }

    /** 모든 유형(파일 이름 순 = 레지스트리 순). */
    public static Collection<DamageType> all() { return BY_KEY.values(); }

    private static String path(String key) {
        if (key.startsWith("minecraft:")) return key.substring(10);
        return key;
    }

    /** id("fall", "minecraft:fall")로 찾는다. 없으면 null. */
    public static DamageType find(String key) {
        return key == null ? null : BY_KEY.get(path(key));
    }

    /** id 로 찾는다. 레지스트리에 없는 id 는 generic(이전 동작과 같다). */
    public static DamageType byKey(String key) {
        DamageType t = find(key);
        return t != null ? t : GENERIC;
    }

    public String key() { return "minecraft:" + key; }
    /** "minecraft:" 없는 id(Entity.Damage.type 과 같은 꼴). */
    public String path() { return key; }
    public String msgId() { return msgId; }
    public Scaling scaling() { return scaling; }
    public float exhaustion() { return exhaustion; }
    public Effects effects() { return effects; }
    public DeathMessageType deathMessageType() { return deathMessageType; }

    public boolean fallVariants() { return deathMessageType == DeathMessageType.FALL_VARIANTS; }
    /** death_message_type intentional_game_design(bad_respawn_point). */
    public boolean intentionalGameDesign() { return deathMessageType == DeathMessageType.INTENTIONAL_GAME_DESIGN; }

    /**
     * 바닐라 DamageSource.scalesWithDifficulty: never → 거짓, always → 참,
     * when_caused_by_living_non_player → 원인 엔티티가 플레이어가 아닌 LivingEntity 일 때.
     */
    public boolean scalesWithDifficulty(boolean causedByLivingNonPlayer) {
        return switch (scaling) {
            case NEVER -> false;
            case ALWAYS -> true;
            case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> causedByLivingNonPlayer;
        };
    }

    /** 원인에 따라 난이도 배율이 붙을 수 있나(never 가 아님). */
    public boolean scalesWithDifficulty() { return scaling != Scaling.NEVER; }
    /** 난이도 배율이 때린 쪽과 무관하게 항상 붙는가(scaling always). */
    public boolean scalesAlways() { return scaling == Scaling.ALWAYS; }

    public boolean bypassesArmor() { return isInTag("minecraft:bypasses_armor"); }
    public boolean bypassesInvulnerability() { return isInTag("minecraft:bypasses_invulnerability"); }
    /** 태그 always_most_significant_fall(out_of_world). */
    public boolean alwaysMostSignificantFall() { return isInTag("minecraft:always_most_significant_fall"); }
    public boolean isFall() { return isInTag("minecraft:is_fall"); }
    public boolean noImpact() { return isInTag("minecraft:no_impact"); }
    public boolean noKnockback() { return isInTag("minecraft:no_knockback"); }
    public boolean isDrowning() { return isInTag("minecraft:is_drowning"); }
    public boolean isProjectile() { return isInTag("minecraft:is_projectile"); }
    public boolean isExplosion() { return isInTag("minecraft:is_explosion"); }
    /** 태그 bypasses_enchantments(허공·/kill·sonic_boom). */
    public boolean bypassesEnchantments() { return isInTag("minecraft:bypasses_enchantments"); }
    /** 태그 bypasses_effects(굶주림): 효과(저항)가 줄이지 못한다. */
    public boolean bypassesEffects() { return isInTag("minecraft:bypasses_effects"); }
    /** 태그 bypasses_resistance(허공·/kill): 저항 효과가 줄이지 못한다. */
    public boolean bypassesResistance() { return isInTag("minecraft:bypasses_resistance"); }
    /** 태그 is_fire(불·용암·모닥불 …): 화염 저항이 막는다. */
    public boolean isFire() { return isInTag("minecraft:is_fire"); }
    public boolean isFreezing() { return isInTag("minecraft:is_freezing"); }
    /** 태그 panic_causes 에 들어 있나. */
    public boolean isPanicCause() { return isInTag("minecraft:panic_causes"); }

    public boolean isInTag(String tag) {
        int[] ids = Tags.SET.ids("minecraft:damage_type", tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        if (ids == null) return false;
        int me = registryId();
        for (int i : ids) if (i == me) return true;
        return false;
    }

    /** 어떤 종류 키("fall", "minecraft:arrow" …)가 damage_type 태그에 들어 있나. 레지스트리에 없는 키는 거짓. */
    public static boolean keyHasTag(String key, String tag) {
        String full = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        int me = Registry.SET.indexOf("minecraft:damage_type", full);
        if (me < 0) return false;
        int[] ids = Tags.SET.ids("minecraft:damage_type", tag.indexOf(':') < 0 ? "minecraft:" + tag : tag);
        if (ids == null) return false;
        for (int i : ids) if (i == me) return true;
        return false;
    }

    /** 설정 단계에서 보낸 damage_type 레지스트리 안의 순번(damage_event 패킷용). */
    public int registryId() {
        int id = registryId;
        if (id < 0) registryId = id = Math.max(0, Registry.SET.indexOf("minecraft:damage_type", key()));
        return id;
    }

    @Override
    public String toString() { return "DamageType(" + key() + ")"; }
}
