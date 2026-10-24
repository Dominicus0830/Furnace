// 바닐라 MemoryModuleType: 브레인이 기억하는 값의 종류(이름·저장 방식). 저장하지 않는 기억은 codec 이 없다.
package org.domi.furnace.server.entity.ai.brain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AgeableMob;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class MemoryModuleType<U> {
    /** 값 ↔ NBT 변환. */
    public interface Codec<U> {
        Object encode(U value);

        U decode(Object nbt);
    }

    private static final Map<String, MemoryModuleType<?>> REGISTRY = new LinkedHashMap<>();

    private final String name;
    private final Codec<U> codec;

    private MemoryModuleType(String name, Codec<U> codec) {
        this.name = name;
        this.codec = codec;
    }

    public String name() { return name; }

    public Codec<U> codec() { return codec; }

    public static MemoryModuleType<?> byName(String name) { return REGISTRY.get(name); }

    @Override public String toString() { return "minecraft:" + name; }

    private static <U> MemoryModuleType<U> of(String name) { return of(name, null); }

    private static <U> MemoryModuleType<U> of(String name, Codec<U> codec) {
        MemoryModuleType<U> t = new MemoryModuleType<>(name, codec);
        REGISTRY.put(name, t);
        return t;
    }

    // ---- 코덱
    static final Codec<Boolean> BOOL = new Codec<>() {
        public Object encode(Boolean v) { return (byte) (v ? 1 : 0); }

        public Boolean decode(Object o) { return o instanceof Number n ? n.byteValue() != 0 : null; }
    };
    static final Codec<Integer> INT = new Codec<>() {
        public Object encode(Integer v) { return v; }

        public Integer decode(Object o) { return o instanceof Number n ? n.intValue() : null; }
    };
    static final Codec<Long> LONG = new Codec<>() {
        public Object encode(Long v) { return v; }

        public Long decode(Object o) { return o instanceof Number n ? n.longValue() : null; }
    };
    static final Codec<UUID> UUID_CODEC = new Codec<>() {
        public Object encode(UUID u) {
            long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
            return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
        }

        public UUID decode(Object o) {
            return o instanceof int[] a && a.length == 4 ? new UUID((long) a[0] << 32 | a[1] & 0xFFFFFFFFL, (long) a[2] << 32 | a[3] & 0xFFFFFFFFL) : null;
        }
    };
    static final Codec<Unit> UNIT = new Codec<>() {
        public Object encode(Unit u) { return new NbtCompound(); }

        public Unit decode(Object o) { return Unit.INSTANCE; }
    };
    static final Codec<GlobalPos> GLOBAL_POS = new Codec<>() {
        public Object encode(GlobalPos p) { return p.toNbt(); }

        public GlobalPos decode(Object o) { return GlobalPos.fromNbt(o); }
    };
    static final Codec<Pos> BLOCK_POS = new Codec<>() {
        public Object encode(Pos p) { return new int[] {p.x(), p.y(), p.z()}; }

        public Pos decode(Object o) { return o instanceof int[] a && a.length == 3 ? new Pos(a[0], a[1], a[2]) : null; }
    };
    static final Codec<List<GlobalPos>> GLOBAL_POS_LIST = new Codec<>() {
        public Object encode(List<GlobalPos> l) {
            NbtList out = new NbtList((byte) 10);
            for (GlobalPos p : l) out.add(p.toNbt());
            return out;
        }

        public List<GlobalPos> decode(Object o) {
            if (!(o instanceof NbtList l)) return null;
            List<GlobalPos> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                GlobalPos p = GlobalPos.fromNbt(l.get(i));
                if (p != null) out.add(p);
            }
            return out;
        }
    };

    // ---- 종류(바닐라 이름)
    public static final MemoryModuleType<Void> DUMMY = of("dummy");
    public static final MemoryModuleType<GlobalPos> HOME = of("home", GLOBAL_POS);
    public static final MemoryModuleType<GlobalPos> JOB_SITE = of("job_site", GLOBAL_POS);
    public static final MemoryModuleType<GlobalPos> POTENTIAL_JOB_SITE = of("potential_job_site", GLOBAL_POS);
    public static final MemoryModuleType<GlobalPos> MEETING_POINT = of("meeting_point", GLOBAL_POS);
    public static final MemoryModuleType<List<GlobalPos>> SECONDARY_JOB_SITE = of("secondary_job_site");
    public static final MemoryModuleType<List<Targetable>> NEAREST_LIVING_ENTITIES = of("mobs");
    public static final MemoryModuleType<NearestVisibleLivingEntities> NEAREST_VISIBLE_LIVING_ENTITIES = of("visible_mobs");
    public static final MemoryModuleType<List<Targetable>> VISIBLE_VILLAGER_BABIES = of("visible_villager_babies");
    public static final MemoryModuleType<List<Player>> NEAREST_PLAYERS = of("nearest_players");
    public static final MemoryModuleType<Player> NEAREST_VISIBLE_PLAYER = of("nearest_visible_player");
    public static final MemoryModuleType<Player> NEAREST_VISIBLE_ATTACKABLE_PLAYER = of("nearest_visible_targetable_player");
    public static final MemoryModuleType<WalkTarget> WALK_TARGET = of("walk_target");
    public static final MemoryModuleType<PositionTracker> LOOK_TARGET = of("look_target");
    public static final MemoryModuleType<Targetable> ATTACK_TARGET = of("attack_target");
    public static final MemoryModuleType<Boolean> ATTACK_COOLING_DOWN = of("attack_cooling_down");
    public static final MemoryModuleType<Targetable> INTERACTION_TARGET = of("interaction_target");
    public static final MemoryModuleType<AgeableMob> BREED_TARGET = of("breed_target");
    public static final MemoryModuleType<Entity> RIDE_TARGET = of("ride_target");
    public static final MemoryModuleType<Path> PATH = of("path");
    public static final MemoryModuleType<List<GlobalPos>> INTERACTABLE_DOORS = of("interactable_doors");
    public static final MemoryModuleType<Set<GlobalPos>> DOORS_TO_CLOSE = of("doors_to_close");
    public static final MemoryModuleType<Pos> NEAREST_BED = of("nearest_bed");
    public static final MemoryModuleType<Entity.Damage> HURT_BY = of("hurt_by");
    public static final MemoryModuleType<Targetable> HURT_BY_ENTITY = of("hurt_by_entity");
    public static final MemoryModuleType<Targetable> AVOID_TARGET = of("avoid_target");
    public static final MemoryModuleType<Targetable> NEAREST_HOSTILE = of("nearest_hostile");
    public static final MemoryModuleType<Targetable> NEAREST_ATTACKABLE = of("nearest_attackable");
    public static final MemoryModuleType<GlobalPos> HIDING_PLACE = of("hiding_place");
    public static final MemoryModuleType<Long> HEARD_BELL_TIME = of("heard_bell_time");
    public static final MemoryModuleType<Long> CANT_REACH_WALK_TARGET_SINCE = of("cant_reach_walk_target_since");
    public static final MemoryModuleType<Boolean> GOLEM_DETECTED_RECENTLY = of("golem_detected_recently", BOOL);
    public static final MemoryModuleType<Boolean> DANGER_DETECTED_RECENTLY = of("danger_detected_recently", BOOL);
    public static final MemoryModuleType<Long> LAST_SLEPT = of("last_slept", LONG);
    public static final MemoryModuleType<Long> LAST_WOKEN = of("last_woken", LONG);
    public static final MemoryModuleType<Long> LAST_WORKED_AT_POI = of("last_worked_at_poi", LONG);
    public static final MemoryModuleType<AgeableMob> NEAREST_VISIBLE_ADULT = of("nearest_visible_adult");
    public static final MemoryModuleType<ItemEntity> NEAREST_VISIBLE_WANTED_ITEM = of("nearest_visible_wanted_item");
    public static final MemoryModuleType<Mob> NEAREST_VISIBLE_NEMESIS = of("nearest_visible_nemesis");
    public static final MemoryModuleType<Integer> PLAY_DEAD_TICKS = of("play_dead_ticks", INT);
    public static final MemoryModuleType<Player> TEMPTING_PLAYER = of("tempting_player");
    public static final MemoryModuleType<Integer> TEMPTATION_COOLDOWN_TICKS = of("temptation_cooldown_ticks", INT);
    public static final MemoryModuleType<Integer> GAZE_COOLDOWN_TICKS = of("gaze_cooldown_ticks", INT);
    public static final MemoryModuleType<Boolean> IS_TEMPTED = of("is_tempted", BOOL);
    public static final MemoryModuleType<Integer> LONG_JUMP_COOLDOWN_TICKS = of("long_jump_cooling_down", INT);
    public static final MemoryModuleType<Boolean> LONG_JUMP_MID_JUMP = of("long_jump_mid_jump");
    public static final MemoryModuleType<Boolean> HAS_HUNTING_COOLDOWN = of("has_hunting_cooldown", BOOL);
    public static final MemoryModuleType<Integer> RAM_COOLDOWN_TICKS = of("ram_cooldown_ticks", INT);
    public static final MemoryModuleType<double[]> RAM_TARGET = of("ram_target");
    public static final MemoryModuleType<Unit> IS_IN_WATER = of("is_in_water", UNIT);
    public static final MemoryModuleType<Unit> IS_PREGNANT = of("is_pregnant", UNIT);
    public static final MemoryModuleType<Boolean> IS_PANICKING = of("is_panicking", BOOL);
    public static final MemoryModuleType<List<UUID>> UNREACHABLE_TONGUE_TARGETS = of("unreachable_tongue_targets");
    public static final MemoryModuleType<UUID> ANGRY_AT = of("angry_at", UUID_CODEC);
    public static final MemoryModuleType<Boolean> UNIVERSAL_ANGER = of("universal_anger", BOOL);
    public static final MemoryModuleType<Boolean> ADMIRING_ITEM = of("admiring_item", BOOL);
    public static final MemoryModuleType<Integer> TIME_TRYING_TO_REACH_ADMIRE_ITEM = of("time_trying_to_reach_admire_item");
    public static final MemoryModuleType<Boolean> DISABLE_WALK_TO_ADMIRE_ITEM = of("disable_walk_to_admire_item");
    public static final MemoryModuleType<Boolean> ADMIRING_DISABLED = of("admiring_disabled", BOOL);
    public static final MemoryModuleType<Boolean> HUNTED_RECENTLY = of("hunted_recently", BOOL);
    public static final MemoryModuleType<Pos> CELEBRATE_LOCATION = of("celebrate_location");
    public static final MemoryModuleType<Boolean> DANCING = of("dancing");
    public static final MemoryModuleType<Mob> NEAREST_VISIBLE_HUNTABLE_HOGLIN = of("nearest_visible_huntable_hoglin");
    public static final MemoryModuleType<Mob> NEAREST_VISIBLE_BABY_HOGLIN = of("nearest_visible_baby_hoglin");
    public static final MemoryModuleType<Player> NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD = of("nearest_targetable_player_not_wearing_gold");
    public static final MemoryModuleType<List<Mob>> NEARBY_ADULT_PIGLINS = of("nearby_adult_piglins");
    public static final MemoryModuleType<List<Mob>> NEAREST_VISIBLE_ADULT_PIGLINS = of("nearest_visible_adult_piglins");
    public static final MemoryModuleType<List<Mob>> NEAREST_VISIBLE_ADULT_HOGLINS = of("nearest_visible_adult_hoglins");
    public static final MemoryModuleType<Mob> NEAREST_VISIBLE_ADULT_PIGLIN = of("nearest_visible_adult_piglin");
    public static final MemoryModuleType<Targetable> NEAREST_VISIBLE_ZOMBIFIED = of("nearest_visible_zombified");
    public static final MemoryModuleType<Integer> VISIBLE_ADULT_PIGLIN_COUNT = of("visible_adult_piglin_count");
    public static final MemoryModuleType<Integer> VISIBLE_ADULT_HOGLIN_COUNT = of("visible_adult_hoglin_count");
    public static final MemoryModuleType<Player> NEAREST_PLAYER_HOLDING_WANTED_ITEM = of("nearest_player_holding_wanted_item");
    public static final MemoryModuleType<Boolean> ATE_RECENTLY = of("ate_recently");
    public static final MemoryModuleType<Pos> NEAREST_REPELLENT = of("nearest_repellent");
    public static final MemoryModuleType<Boolean> PACIFIED = of("pacified");
    public static final MemoryModuleType<Targetable> ROAR_TARGET = of("roar_target");
    public static final MemoryModuleType<Pos> DISTURBANCE_LOCATION = of("disturbance_location");
    public static final MemoryModuleType<Unit> RECENT_PROJECTILE = of("recent_projectile", UNIT);
    public static final MemoryModuleType<Unit> IS_SNIFFING = of("is_sniffing", UNIT);
    public static final MemoryModuleType<Unit> IS_EMERGING = of("is_emerging", UNIT);
    public static final MemoryModuleType<Unit> ROAR_SOUND_DELAY = of("roar_sound_delay", UNIT);
    public static final MemoryModuleType<Unit> DIG_COOLDOWN = of("dig_cooldown", UNIT);
    public static final MemoryModuleType<Unit> ROAR_SOUND_COOLDOWN = of("roar_sound_cooldown", UNIT);
    public static final MemoryModuleType<Unit> SNIFF_COOLDOWN = of("sniff_cooldown", UNIT);
    public static final MemoryModuleType<Unit> TOUCH_COOLDOWN = of("touch_cooldown", UNIT);
    public static final MemoryModuleType<Unit> VIBRATION_COOLDOWN = of("vibration_cooldown", UNIT);
    public static final MemoryModuleType<Unit> SONIC_BOOM_COOLDOWN = of("sonic_boom_cooldown", UNIT);
    public static final MemoryModuleType<Unit> SONIC_BOOM_SOUND_COOLDOWN = of("sonic_boom_sound_cooldown", UNIT);
    public static final MemoryModuleType<Unit> SONIC_BOOM_SOUND_DELAY = of("sonic_boom_sound_delay", UNIT);
    public static final MemoryModuleType<UUID> LIKED_PLAYER = of("liked_player", UUID_CODEC);
    public static final MemoryModuleType<GlobalPos> LIKED_NOTEBLOCK_POSITION = of("liked_noteblock", GLOBAL_POS);
    public static final MemoryModuleType<Integer> LIKED_NOTEBLOCK_COOLDOWN_TICKS = of("liked_noteblock_cooldown_ticks", INT);
    public static final MemoryModuleType<Integer> ITEM_PICKUP_COOLDOWN_TICKS = of("item_pickup_cooldown_ticks", INT);
    public static final MemoryModuleType<List<GlobalPos>> SNIFFER_EXPLORED_POSITIONS = of("sniffer_explored_positions", GLOBAL_POS_LIST);
    public static final MemoryModuleType<Pos> SNIFFER_SNIFFING_TARGET = of("sniffer_sniffing_target");
    public static final MemoryModuleType<Boolean> SNIFFER_DIGGING = of("sniffer_digging");
    public static final MemoryModuleType<Boolean> SNIFFER_HAPPY = of("sniffer_happy");
    public static final MemoryModuleType<Unit> BREEZE_JUMP_COOLDOWN = of("breeze_jump_cooldown", UNIT);
    public static final MemoryModuleType<Unit> BREEZE_SHOOT = of("breeze_shoot", UNIT);
    public static final MemoryModuleType<Unit> BREEZE_SHOOT_CHARGING = of("breeze_shoot_charging", UNIT);
    public static final MemoryModuleType<Unit> BREEZE_SHOOT_RECOVER = of("breeze_shoot_recover", UNIT);
    public static final MemoryModuleType<Unit> BREEZE_SHOOT_COOLDOWN = of("breeze_shoot_cooldown", UNIT);
    public static final MemoryModuleType<Unit> BREEZE_JUMP_INHALING = of("breeze_jump_inhaling", UNIT);
    public static final MemoryModuleType<Pos> BREEZE_JUMP_TARGET = of("breeze_jump_target", BLOCK_POS);
    public static final MemoryModuleType<Unit> BREEZE_LEAVING_WATER = of("breeze_leaving_water", UNIT);
}
