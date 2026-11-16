// 이 파일은 tools/compat/gen-bukkit-tag.py 가 만든다 — 손으로 고치지 않는다.
// Bukkit GameEvent(1.21.1): 게임 이벤트 레지스트리 항목마다 상수 하나(이름 = 키 대문자).
package org.bukkit;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameEvent implements Keyed {

    private static final Map<NamespacedKey, GameEvent> BY_KEY = new LinkedHashMap<>();

    public static final GameEvent BLOCK_ACTIVATE = register("block_activate", 16, 10);

    public static final GameEvent BLOCK_ATTACH = register("block_attach", 16, 10);

    public static final GameEvent BLOCK_CHANGE = register("block_change", 16, 11);

    public static final GameEvent BLOCK_CLOSE = register("block_close", 16, 9);

    public static final GameEvent BLOCK_DEACTIVATE = register("block_deactivate", 16, 9);

    public static final GameEvent BLOCK_DESTROY = register("block_destroy", 16, 12);

    public static final GameEvent BLOCK_DETACH = register("block_detach", 16, 9);

    public static final GameEvent BLOCK_OPEN = register("block_open", 16, 10);

    public static final GameEvent BLOCK_PLACE = register("block_place", 16, 13);

    public static final GameEvent CONTAINER_CLOSE = register("container_close", 16, 9);

    public static final GameEvent CONTAINER_OPEN = register("container_open", 16, 10);

    public static final GameEvent DRINK = register("drink", 16, 8);

    public static final GameEvent EAT = register("eat", 16, 8);

    public static final GameEvent ELYTRA_GLIDE = register("elytra_glide", 16, 4);

    public static final GameEvent ENTITY_DAMAGE = register("entity_damage", 16, 7);

    public static final GameEvent ENTITY_DIE = register("entity_die", 16, 15);

    public static final GameEvent ENTITY_DISMOUNT = register("entity_dismount", 16, 5);

    public static final GameEvent ENTITY_INTERACT = register("entity_interact", 16, 6);

    public static final GameEvent ENTITY_MOUNT = register("entity_mount", 16, 6);

    public static final GameEvent ENTITY_PLACE = register("entity_place", 16, 14);

    public static final GameEvent ENTITY_ACTION = register("entity_action", 16, 4);

    public static final GameEvent EQUIP = register("equip", 16, 5);

    public static final GameEvent EXPLODE = register("explode", 16, 15);

    public static final GameEvent FLAP = register("flap", 16, 1);

    public static final GameEvent FLUID_PICKUP = register("fluid_pickup", 16, 12);

    public static final GameEvent FLUID_PLACE = register("fluid_place", 16, 13);

    public static final GameEvent HIT_GROUND = register("hit_ground", 16, 2);

    public static final GameEvent INSTRUMENT_PLAY = register("instrument_play", 16, 3);

    public static final GameEvent ITEM_INTERACT_FINISH = register("item_interact_finish", 16, 3);

    public static final GameEvent ITEM_INTERACT_START = register("item_interact_start", 16, 0);

    public static final GameEvent JUKEBOX_PLAY = register("jukebox_play", 10, 0);

    public static final GameEvent JUKEBOX_STOP_PLAY = register("jukebox_stop_play", 10, 0);

    public static final GameEvent LIGHTNING_STRIKE = register("lightning_strike", 16, 14);

    public static final GameEvent NOTE_BLOCK_PLAY = register("note_block_play", 16, 10);

    public static final GameEvent PRIME_FUSE = register("prime_fuse", 16, 10);

    public static final GameEvent PROJECTILE_LAND = register("projectile_land", 16, 2);

    public static final GameEvent PROJECTILE_SHOOT = register("projectile_shoot", 16, 3);

    public static final GameEvent SCULK_SENSOR_TENDRILS_CLICKING = register("sculk_sensor_tendrils_clicking", 16, 0);

    public static final GameEvent SHEAR = register("shear", 16, 6);

    public static final GameEvent SHRIEK = register("shriek", 32, 0);

    public static final GameEvent SPLASH = register("splash", 16, 2);

    public static final GameEvent STEP = register("step", 16, 1);

    public static final GameEvent SWIM = register("swim", 16, 1);

    public static final GameEvent TELEPORT = register("teleport", 16, 14);

    public static final GameEvent UNEQUIP = register("unequip", 16, 4);

    public static final GameEvent RESONATE_1 = register("resonate_1", 16, 1);

    public static final GameEvent RESONATE_2 = register("resonate_2", 16, 2);

    public static final GameEvent RESONATE_3 = register("resonate_3", 16, 3);

    public static final GameEvent RESONATE_4 = register("resonate_4", 16, 4);

    public static final GameEvent RESONATE_5 = register("resonate_5", 16, 5);

    public static final GameEvent RESONATE_6 = register("resonate_6", 16, 6);

    public static final GameEvent RESONATE_7 = register("resonate_7", 16, 7);

    public static final GameEvent RESONATE_8 = register("resonate_8", 16, 8);

    public static final GameEvent RESONATE_9 = register("resonate_9", 16, 9);

    public static final GameEvent RESONATE_10 = register("resonate_10", 16, 10);

    public static final GameEvent RESONATE_11 = register("resonate_11", 16, 11);

    public static final GameEvent RESONATE_12 = register("resonate_12", 16, 12);

    public static final GameEvent RESONATE_13 = register("resonate_13", 16, 13);

    public static final GameEvent RESONATE_14 = register("resonate_14", 16, 14);

    public static final GameEvent RESONATE_15 = register("resonate_15", 16, 15);

    /** 옛 이름 — BLOCK_ACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent BLOCK_PRESS = BLOCK_ACTIVATE;

    /** 옛 이름 — BLOCK_ACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent BLOCK_SWITCH = BLOCK_ACTIVATE;

    /** 옛 이름 — BLOCK_DEACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent BLOCK_UNPRESS = BLOCK_DEACTIVATE;

    /** 옛 이름 — BLOCK_DEACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent BLOCK_UNSWITCH = BLOCK_DEACTIVATE;

    /** 옛 이름 — BLOCK_ACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent DISPENSE_FAIL = BLOCK_ACTIVATE;

    /** 옛 이름 — BLOCK_ACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent PISTON_EXTEND = BLOCK_ACTIVATE;

    /** 옛 이름 — BLOCK_DEACTIVATE 과 같다. */
    @Deprecated
    public static final GameEvent PISTON_CONTRACT = BLOCK_DEACTIVATE;

    /** 옛 이름 — DRINK 과 같다. */
    @Deprecated
    public static final GameEvent DRINKING_FINISH = DRINK;

    /** 옛 이름 — ELYTRA_GLIDE 과 같다. */
    @Deprecated
    public static final GameEvent ELYTRA_FREE_FALL = ELYTRA_GLIDE;

    /** 옛 이름 — ENTITY_DAMAGE 과 같다. */
    @Deprecated
    public static final GameEvent ENTITY_DAMAGED = ENTITY_DAMAGE;

    /** 옛 이름 — ENTITY_DIE 과 같다. */
    @Deprecated
    public static final GameEvent ENTITY_DYING = ENTITY_DIE;

    /** 옛 이름 — ENTITY_DIE 과 같다. */
    @Deprecated
    public static final GameEvent ENTITY_KILLED = ENTITY_DIE;

    /** 옛 이름 — ENTITY_ACTION 과 같다. */
    @Deprecated
    public static final GameEvent ENTITY_ROAR = ENTITY_ACTION;

    /** 옛 이름 — ENTITY_ACTION 과 같다. */
    @Deprecated
    public static final GameEvent ENTITY_SHAKE = ENTITY_ACTION;

    /** 옛 이름 — ENTITY_ACTION 과 같다. */
    @Deprecated
    public static final GameEvent RAVAGER_ROAR = ENTITY_ACTION;

    /** 옛 이름 — ENTITY_ACTION 과 같다. */
    @Deprecated
    public static final GameEvent WOLF_SHAKING = ENTITY_ACTION;

    /** 옛 이름 — ENTITY_INTERACT 과 같다. */
    @Deprecated
    public static final GameEvent MOB_INTERACT = ENTITY_INTERACT;

    /** 옛 이름 — BLOCK_CHANGE 과 같다. */
    @Deprecated
    public static final GameEvent RING_BELL = BLOCK_CHANGE;

    /** 옛 이름 — CONTAINER_CLOSE 과 같다. */
    @Deprecated
    public static final GameEvent SHULKER_CLOSE = CONTAINER_CLOSE;

    /** 옛 이름 — CONTAINER_OPEN 과 같다. */
    @Deprecated
    public static final GameEvent SHULKER_OPEN = CONTAINER_OPEN;

    private final NamespacedKey key;
    private final int range;
    private final int vibrationLevel;

    private GameEvent(NamespacedKey key, int range, int vibrationLevel) {
        this.key = key;
        this.range = range;
        this.vibrationLevel = vibrationLevel;
    }

    private static GameEvent register(String path, int range, int vibrationLevel) {
        GameEvent e = new GameEvent(NamespacedKey.minecraft(path), range, vibrationLevel);
        BY_KEY.put(e.key, e);
        return e;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 알림 반경(바닐라 notificationRadius, 블록). */
    public int getRange() { return range; }

    /** 진동 세기 1..15(VibrationSystem 의 빈도, 진동 표에 없으면 0). */
    public int getVibrationLevel() { return vibrationLevel; }

    /** 키의 게임 이벤트(없으면 null). */
    public static GameEvent getByKey(NamespacedKey key) { return BY_KEY.get(key); }

    /** 전부(바꿀 수 없다). */
    public static Collection<GameEvent> values() { return Collections.unmodifiableCollection(BY_KEY.values()); }

    @Override
    public String toString() { return "GameEvent{key=" + key + "}"; }
}
