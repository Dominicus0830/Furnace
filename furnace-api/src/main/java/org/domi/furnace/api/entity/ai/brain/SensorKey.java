// 두뇌 센서 이름표 "이름공간:이름"(예: minecraft:nearest_players, myplugin:threats). 바닐라 센서는 센서 종류 이름이다.
package org.domi.furnace.api.entity.ai.brain;

import java.util.Locale;
import java.util.regex.Pattern;

public record SensorKey(String namespace, String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9_.\\-/]+");

    public SensorKey {
        if (namespace == null || !PART.matcher(namespace).matches()) throw new IllegalArgumentException("이름공간이 잘못됐다: " + namespace);
        if (name == null || !PART.matcher(name).matches()) throw new IllegalArgumentException("이름이 잘못됐다: " + name);
    }

    public static SensorKey of(String namespace, String name) { return new SensorKey(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)); }

    public static SensorKey minecraft(String name) { return of("minecraft", name); }

    /** "ns:name" 또는 "name"(minecraft 로 본다). */
    public static SensorKey parse(String key) {
        int i = key.indexOf(':');
        return i < 0 ? minecraft(key) : of(key.substring(0, i), key.substring(i + 1));
    }

    public static final SensorKey NEAREST_LIVING_ENTITIES = minecraft("nearest_living_entities");
    public static final SensorKey NEAREST_PLAYERS = minecraft("nearest_players");
    public static final SensorKey NEAREST_ITEMS = minecraft("nearest_items");
    public static final SensorKey HURT_BY = minecraft("hurt_by");
    public static final SensorKey NEAREST_BED = minecraft("nearest_bed");
    public static final SensorKey VILLAGER_HOSTILES = minecraft("villager_hostiles");
    public static final SensorKey VILLAGER_BABIES = minecraft("villager_babies");
    public static final SensorKey SECONDARY_POIS = minecraft("secondary_pois");
    public static final SensorKey GOLEM_DETECTED = minecraft("golem_detected");
    public static final SensorKey PIGLIN_SPECIFIC = minecraft("piglin_specific_sensor");
    public static final SensorKey AXOLOTL_ATTACKABLES = minecraft("axolotl_attackables");
    public static final SensorKey AXOLOTL_TEMPTATIONS = minecraft("axolotl_temptations");
    public static final SensorKey WARDEN_ENTITY_SENSOR = minecraft("warden_entity_sensor");

    @Override
    public String toString() { return namespace + ':' + name; }
}
