// 바닐라 GameEvent 레지스트리(1.21.1): 이름·레지스트리 순번·알림 반경(notificationRadius). 태그는 tags-1.21.1.txt 의 game_event 쪽.
// 진동 주파수 표(VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT)와 공명 이벤트(resonate_1..15)도 여기 둔다.
package org.domi.furnace.server.world.gameevent;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.net.registry.TagSet;

public final class GameEvent {

    /** 레지스트리 순서(builtin-1.21.1.txt 와 같다). */
    private static final String[] NAMES = {
            "block_activate", "block_attach", "block_change", "block_close", "block_deactivate", "block_destroy", "block_detach",
            "block_open", "block_place", "container_close", "container_open", "drink", "eat", "elytra_glide", "entity_damage",
            "entity_die", "entity_dismount", "entity_interact", "entity_mount", "entity_place", "entity_action", "equip", "explode",
            "flap", "fluid_pickup", "fluid_place", "hit_ground", "instrument_play", "item_interact_finish", "item_interact_start",
            "jukebox_play", "jukebox_stop_play", "lightning_strike", "note_block_play", "prime_fuse", "projectile_land",
            "projectile_shoot", "sculk_sensor_tendrils_clicking", "shear", "shriek", "splash", "step", "swim", "teleport", "unequip",
            "resonate_1", "resonate_2", "resonate_3", "resonate_4", "resonate_5", "resonate_6", "resonate_7", "resonate_8",
            "resonate_9", "resonate_10", "resonate_11", "resonate_12", "resonate_13", "resonate_14", "resonate_15"};

    private static final GameEvent[] BY_ID = new GameEvent[NAMES.length];
    private static final Map<String, GameEvent> BY_NAME = new HashMap<>();

    static {
        for (int i = 0; i < NAMES.length; i++) {
            String n = NAMES[i];
            int radius = n.equals("shriek") ? 32 : n.equals("jukebox_play") || n.equals("jukebox_stop_play") ? 10 : 16;
            GameEvent e = new GameEvent(i, "minecraft:" + n, radius);
            BY_ID[i] = e;
            BY_NAME.put(e.name, e);
            BY_NAME.put(n, e);
        }
    }

    public static final GameEvent BLOCK_ACTIVATE = byName("block_activate"), BLOCK_ATTACH = byName("block_attach"),
            BLOCK_CHANGE = byName("block_change"), BLOCK_CLOSE = byName("block_close"), BLOCK_DEACTIVATE = byName("block_deactivate"),
            BLOCK_DESTROY = byName("block_destroy"), BLOCK_DETACH = byName("block_detach"), BLOCK_OPEN = byName("block_open"),
            BLOCK_PLACE = byName("block_place"), CONTAINER_CLOSE = byName("container_close"), CONTAINER_OPEN = byName("container_open"),
            DRINK = byName("drink"), EAT = byName("eat"), ELYTRA_GLIDE = byName("elytra_glide"), ENTITY_DAMAGE = byName("entity_damage"),
            ENTITY_DIE = byName("entity_die"), ENTITY_DISMOUNT = byName("entity_dismount"), ENTITY_INTERACT = byName("entity_interact"),
            ENTITY_MOUNT = byName("entity_mount"), ENTITY_PLACE = byName("entity_place"), ENTITY_ACTION = byName("entity_action"),
            EQUIP = byName("equip"), EXPLODE = byName("explode"), FLAP = byName("flap"), FLUID_PICKUP = byName("fluid_pickup"),
            FLUID_PLACE = byName("fluid_place"), HIT_GROUND = byName("hit_ground"), INSTRUMENT_PLAY = byName("instrument_play"),
            ITEM_INTERACT_FINISH = byName("item_interact_finish"), ITEM_INTERACT_START = byName("item_interact_start"),
            JUKEBOX_PLAY = byName("jukebox_play"), JUKEBOX_STOP_PLAY = byName("jukebox_stop_play"),
            LIGHTNING_STRIKE = byName("lightning_strike"), NOTE_BLOCK_PLAY = byName("note_block_play"), PRIME_FUSE = byName("prime_fuse"),
            PROJECTILE_LAND = byName("projectile_land"), PROJECTILE_SHOOT = byName("projectile_shoot"),
            SCULK_SENSOR_TENDRILS_CLICKING = byName("sculk_sensor_tendrils_clicking"), SHEAR = byName("shear"), SHRIEK = byName("shriek"),
            SPLASH = byName("splash"), STEP = byName("step"), SWIM = byName("swim"), TELEPORT = byName("teleport"),
            UNEQUIP = byName("unequip");

    /** 태그 이름(짧은 꼴). */
    public static final String VIBRATIONS = "minecraft:vibrations", WARDEN_CAN_LISTEN = "minecraft:warden_can_listen",
            SHRIEKER_CAN_LISTEN = "minecraft:shrieker_can_listen", IGNORE_VIBRATIONS_SNEAKING = "minecraft:ignore_vibrations_sneaking",
            ALLAY_CAN_LISTEN = "minecraft:allay_can_listen";

    private static final class Tags {
        static final TagSet SET = TagSet.load("registries/tags-1.21.1.txt");
    }

    /** VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT(없으면 0). resonate_n 은 n. */
    private static final int[] FREQUENCY = new int[NAMES.length];

    static {
        Object[][] table = {
                {"step", 1}, {"swim", 1}, {"flap", 1}, {"projectile_land", 2}, {"hit_ground", 2}, {"splash", 2},
                {"item_interact_finish", 3}, {"projectile_shoot", 3}, {"instrument_play", 3}, {"entity_action", 4},
                {"elytra_glide", 4}, {"unequip", 4}, {"entity_dismount", 5}, {"equip", 5}, {"entity_interact", 6}, {"shear", 6},
                {"entity_mount", 6}, {"entity_damage", 7}, {"drink", 8}, {"eat", 8}, {"container_close", 9}, {"block_close", 9},
                {"block_deactivate", 9}, {"block_detach", 9}, {"container_open", 10}, {"block_open", 10}, {"block_activate", 10},
                {"block_attach", 10}, {"prime_fuse", 10}, {"note_block_play", 10}, {"block_change", 11}, {"block_destroy", 12},
                {"fluid_pickup", 12}, {"block_place", 13}, {"fluid_place", 13}, {"entity_place", 14}, {"lightning_strike", 14},
                {"teleport", 14}, {"entity_die", 15}, {"explode", 15}};
        for (Object[] row : table) FREQUENCY[byName((String) row[0]).id] = (Integer) row[1];
        for (int i = 1; i <= 15; i++) FREQUENCY[resonance(i).id] = i;
    }

    public final int id;
    public final String name;
    public final int notificationRadius;

    private GameEvent(int id, String name, int notificationRadius) {
        this.id = id;
        this.name = name;
        this.notificationRadius = notificationRadius;
    }

    /** 이름(minecraft: 있어도 없어도)으로 찾는다. 모르면 null. */
    public static GameEvent byName(String name) { return BY_NAME.get(name); }

    public static GameEvent byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : null; }

    public static int count() { return BY_ID.length; }

    /** Holder.is(태그). */
    public boolean is(String tag) {
        int[] ids = Tags.SET.ids("minecraft:game_event", tag);
        if (ids == null) return false;
        for (int i : ids) if (i == id) return true;
        return false;
    }

    /** VibrationSystem.getGameEventFrequency. */
    public int frequency() { return FREQUENCY[id]; }

    /** VibrationSystem.getResonanceEventByFrequency(1..15). */
    public static GameEvent resonance(int frequency) { return BY_NAME.get("resonate_" + frequency); }

    @Override
    public String toString() { return name; }
}
