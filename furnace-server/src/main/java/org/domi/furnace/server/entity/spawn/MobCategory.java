// 바닐라 MobCategory: 몹 종류 묶음 — 자연 스폰 상한(max)·친화(friendly)·유지(persistent)·떠나는 거리. 즉시 사라지지 않는 거리는 모두 32.
package org.domi.furnace.server.entity.spawn;

public enum MobCategory {
    MONSTER("monster", 70, false, false, 128),
    CREATURE("creature", 10, true, true, 128),
    AMBIENT("ambient", 15, true, false, 128),
    AXOLOTLS("axolotls", 5, true, false, 128),
    UNDERGROUND_WATER_CREATURE("underground_water_creature", 5, true, false, 128),
    WATER_CREATURE("water_creature", 5, true, false, 128),
    WATER_AMBIENT("water_ambient", 20, true, false, 64),
    MISC("misc", -1, true, true, 128);

    public static final int NO_DESPAWN_DISTANCE = 32;
    private final String key;
    private final int max;
    private final boolean friendly, persistent;
    private final int despawnDistance;

    MobCategory(String key, int max, boolean friendly, boolean persistent, int despawnDistance) {
        this.key = key;
        this.max = max;
        this.friendly = friendly;
        this.persistent = persistent;
        this.despawnDistance = despawnDistance;
    }

    public String key() { return key; }

    public int max() { return max; }

    public boolean isFriendly() { return friendly; }

    public boolean isPersistent() { return persistent; }

    public int despawnDistance() { return despawnDistance; }

    public static MobCategory byKey(String key) {
        for (MobCategory c : values()) if (c.key.equals(key)) return c;
        return MISC;
    }
}
