// 두뇌 기억 이름표 + 값 모양. "minecraft:이름" 은 바닐라 기억(서버가 아는 모양과 같아야 한다), 다른 이름공간은 플러그인 기억이다.
// 아래 상수는 주민·피글린·아홀로틀·워든 등이 쓰는 1.21.1 기억 중 자주 쓰는 것이다. 나머지는 MemoryKey.minecraft(이름, 모양)으로 만든다
// (MobBrain.memories() 가 그 두뇌의 모든 기억 이름과 모양을 알려 준다).
package org.domi.furnace.api.entity.ai.brain;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.domi.furnace.api.world.BlockPos;

public record MemoryKey<T>(String namespace, String name, MemoryValueType<T> type) {

    private static final Pattern PART = Pattern.compile("[a-z0-9_.\\-/]+");

    public MemoryKey {
        if (namespace == null || !PART.matcher(namespace).matches()) throw new IllegalArgumentException("이름공간이 잘못됐다: " + namespace);
        if (name == null || !PART.matcher(name).matches()) throw new IllegalArgumentException("이름이 잘못됐다: " + name);
        if (type == null) throw new NullPointerException("type");
        if (namespace.equals("minecraft") && type.isPluginType()) throw new IllegalArgumentException("바닐라 기억에는 object(…) 모양을 쓸 수 없다: " + name);
    }

    public static <T> MemoryKey<T> of(String namespace, String name, MemoryValueType<T> type) {
        return new MemoryKey<>(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT), type);
    }

    public static <T> MemoryKey<T> minecraft(String name, MemoryValueType<T> type) { return of("minecraft", name, type); }

    /** "ns:name" 또는 "name"(minecraft 로 본다). */
    public static <T> MemoryKey<T> parse(String key, MemoryValueType<T> type) {
        int i = key.indexOf(':');
        return i < 0 ? minecraft(key, type) : of(key.substring(0, i), key.substring(i + 1), type);
    }

    /** 플러그인 기억(저장되지 않는다). */
    public static <T> MemoryKey<T> plugin(String namespace, String name, Class<T> type) { return of(namespace, name, MemoryValueType.object(type)); }

    public boolean isVanilla() { return namespace.equals("minecraft"); }

    /** "ns:name". */
    public String key() { return namespace + ':' + name; }

    // ---- 여럿이 쓰는 것
    public static final MemoryKey<UUID> ATTACK_TARGET = minecraft("attack_target", MemoryValueType.ENTITY);
    public static final MemoryKey<WalkTarget> WALK_TARGET = minecraft("walk_target", MemoryValueType.WALK_TARGET);
    public static final MemoryKey<BrainTarget> LOOK_TARGET = minecraft("look_target", MemoryValueType.TARGET);
    public static final MemoryKey<UUID> INTERACTION_TARGET = minecraft("interaction_target", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> BREED_TARGET = minecraft("breed_target", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> HURT_BY_ENTITY = minecraft("hurt_by_entity", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> AVOID_TARGET = minecraft("avoid_target", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> NEAREST_VISIBLE_PLAYER = minecraft("nearest_visible_player", MemoryValueType.ENTITY);
    public static final MemoryKey<List<UUID>> NEAREST_PLAYERS = minecraft("nearest_players", MemoryValueType.ENTITY_LIST);
    public static final MemoryKey<List<UUID>> NEAREST_LIVING_ENTITIES = minecraft("mobs", MemoryValueType.ENTITY_LIST);
    public static final MemoryKey<List<UUID>> NEAREST_VISIBLE_LIVING_ENTITIES = minecraft("visible_mobs", MemoryValueType.ENTITY_LIST);
    public static final MemoryKey<UUID> TEMPTING_PLAYER = minecraft("tempting_player", MemoryValueType.ENTITY);
    public static final MemoryKey<Integer> TEMPTATION_COOLDOWN_TICKS = minecraft("temptation_cooldown_ticks", MemoryValueType.INTEGER);
    public static final MemoryKey<Boolean> IS_TEMPTED = minecraft("is_tempted", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> IS_PANICKING = minecraft("is_panicking", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Long> CANT_REACH_WALK_TARGET_SINCE = minecraft("cant_reach_walk_target_since", MemoryValueType.LONG);

    // ---- 주민
    public static final MemoryKey<GlobalBlockPos> HOME = minecraft("home", MemoryValueType.GLOBAL_POS);
    public static final MemoryKey<GlobalBlockPos> JOB_SITE = minecraft("job_site", MemoryValueType.GLOBAL_POS);
    public static final MemoryKey<GlobalBlockPos> POTENTIAL_JOB_SITE = minecraft("potential_job_site", MemoryValueType.GLOBAL_POS);
    public static final MemoryKey<GlobalBlockPos> MEETING_POINT = minecraft("meeting_point", MemoryValueType.GLOBAL_POS);
    public static final MemoryKey<List<GlobalBlockPos>> SECONDARY_JOB_SITE = minecraft("secondary_job_site", MemoryValueType.GLOBAL_POS_LIST);
    public static final MemoryKey<GlobalBlockPos> HIDING_PLACE = minecraft("hiding_place", MemoryValueType.GLOBAL_POS);
    public static final MemoryKey<BlockPos> NEAREST_BED = minecraft("nearest_bed", MemoryValueType.BLOCK_POS);
    public static final MemoryKey<UUID> NEAREST_HOSTILE = minecraft("nearest_hostile", MemoryValueType.ENTITY);
    public static final MemoryKey<Long> HEARD_BELL_TIME = minecraft("heard_bell_time", MemoryValueType.LONG);
    public static final MemoryKey<Boolean> GOLEM_DETECTED_RECENTLY = minecraft("golem_detected_recently", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Long> LAST_SLEPT = minecraft("last_slept", MemoryValueType.LONG);
    public static final MemoryKey<Long> LAST_WOKEN = minecraft("last_woken", MemoryValueType.LONG);
    public static final MemoryKey<Long> LAST_WORKED_AT_POI = minecraft("last_worked_at_poi", MemoryValueType.LONG);

    // ---- 피글린
    public static final MemoryKey<UUID> ANGRY_AT = minecraft("angry_at", MemoryValueType.UUID_VALUE);
    public static final MemoryKey<Boolean> UNIVERSAL_ANGER = minecraft("universal_anger", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> ADMIRING_ITEM = minecraft("admiring_item", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> ADMIRING_DISABLED = minecraft("admiring_disabled", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> HUNTED_RECENTLY = minecraft("hunted_recently", MemoryValueType.BOOLEAN);
    public static final MemoryKey<BlockPos> CELEBRATE_LOCATION = minecraft("celebrate_location", MemoryValueType.BLOCK_POS);
    public static final MemoryKey<Boolean> DANCING = minecraft("dancing", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> ATE_RECENTLY = minecraft("ate_recently", MemoryValueType.BOOLEAN);
    public static final MemoryKey<BlockPos> NEAREST_REPELLENT = minecraft("nearest_repellent", MemoryValueType.BLOCK_POS);
    public static final MemoryKey<Boolean> PACIFIED = minecraft("pacified", MemoryValueType.BOOLEAN);
    public static final MemoryKey<UUID> NEAREST_VISIBLE_NEMESIS = minecraft("nearest_visible_nemesis", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> NEAREST_VISIBLE_WANTED_ITEM = minecraft("nearest_visible_wanted_item", MemoryValueType.ENTITY);
    public static final MemoryKey<UUID> NEAREST_PLAYER_HOLDING_WANTED_ITEM = minecraft("nearest_player_holding_wanted_item", MemoryValueType.ENTITY);

    // ---- 아홀로틀
    public static final MemoryKey<Integer> PLAY_DEAD_TICKS = minecraft("play_dead_ticks", MemoryValueType.INTEGER);
    public static final MemoryKey<UUID> NEAREST_ATTACKABLE = minecraft("nearest_attackable", MemoryValueType.ENTITY);
    public static final MemoryKey<Boolean> HAS_HUNTING_COOLDOWN = minecraft("has_hunting_cooldown", MemoryValueType.BOOLEAN);
    public static final MemoryKey<Boolean> IS_IN_WATER = minecraft("is_in_water", MemoryValueType.FLAG);

    // ---- 워든
    public static final MemoryKey<UUID> ROAR_TARGET = minecraft("roar_target", MemoryValueType.ENTITY);
    public static final MemoryKey<BlockPos> DISTURBANCE_LOCATION = minecraft("disturbance_location", MemoryValueType.BLOCK_POS);
    public static final MemoryKey<Boolean> RECENT_PROJECTILE = minecraft("recent_projectile", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> IS_SNIFFING = minecraft("is_sniffing", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> IS_EMERGING = minecraft("is_emerging", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> DIG_COOLDOWN = minecraft("dig_cooldown", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> SNIFF_COOLDOWN = minecraft("sniff_cooldown", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> TOUCH_COOLDOWN = minecraft("touch_cooldown", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> VIBRATION_COOLDOWN = minecraft("vibration_cooldown", MemoryValueType.FLAG);
    public static final MemoryKey<Boolean> SONIC_BOOM_COOLDOWN = minecraft("sonic_boom_cooldown", MemoryValueType.FLAG);

    @Override
    public String toString() { return key() + " (" + type + ")"; }
}
