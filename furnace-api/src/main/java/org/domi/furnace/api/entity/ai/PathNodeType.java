// 길찾기가 칸에 붙이는 종류(버전 중립 문자열 키). 종류마다 몹별 비용이 있고, 비용이 음수면 그 칸으로 가지 않는다.
// 아래 상수는 1.21.1 의 종류다. 서버가 모르는 키를 쓰면 IllegalArgumentException.
package org.domi.furnace.api.entity.ai;

import java.util.Locale;

public record PathNodeType(String key) {

    public PathNodeType {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("빈 길 종류");
        key = key.toLowerCase(Locale.ROOT);
    }

    public static PathNodeType of(String key) { return new PathNodeType(key); }

    public static final PathNodeType BLOCKED = of("blocked");
    public static final PathNodeType OPEN = of("open");
    public static final PathNodeType WALKABLE = of("walkable");
    public static final PathNodeType WALKABLE_DOOR = of("walkable_door");
    public static final PathNodeType TRAPDOOR = of("trapdoor");
    public static final PathNodeType POWDER_SNOW = of("powder_snow");
    public static final PathNodeType DANGER_POWDER_SNOW = of("danger_powder_snow");
    public static final PathNodeType FENCE = of("fence");
    public static final PathNodeType LAVA = of("lava");
    public static final PathNodeType WATER = of("water");
    public static final PathNodeType WATER_BORDER = of("water_border");
    public static final PathNodeType RAIL = of("rail");
    public static final PathNodeType UNPASSABLE_RAIL = of("unpassable_rail");
    public static final PathNodeType DANGER_FIRE = of("danger_fire");
    public static final PathNodeType DAMAGE_FIRE = of("damage_fire");
    public static final PathNodeType DANGER_OTHER = of("danger_other");
    public static final PathNodeType DAMAGE_OTHER = of("damage_other");
    public static final PathNodeType DOOR_OPEN = of("door_open");
    public static final PathNodeType DOOR_WOOD_CLOSED = of("door_wood_closed");
    public static final PathNodeType DOOR_IRON_CLOSED = of("door_iron_closed");
    public static final PathNodeType BREACH = of("breach");
    public static final PathNodeType LEAVES = of("leaves");
    public static final PathNodeType STICKY_HONEY = of("sticky_honey");
    public static final PathNodeType COCOA = of("cocoa");
    public static final PathNodeType DAMAGE_CAUTIOUS = of("damage_cautious");
    public static final PathNodeType DANGER_TRAPDOOR = of("danger_trapdoor");

    @Override
    public String toString() { return key; }
}
