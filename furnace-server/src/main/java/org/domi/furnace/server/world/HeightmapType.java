// 바닐라 Heightmap.Types 중 월드젠 뒤에도 남는 네 종류(keepAfterWorldgen) — /execute positioned over 가 고른다.
package org.domi.furnace.server.world;

public enum HeightmapType {
    WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES;

    /** 명령 인자 이름(소문자). */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static HeightmapType byKey(String key) {
        for (HeightmapType t : values()) if (t.key().equals(key)) return t;
        return null;
    }
}
