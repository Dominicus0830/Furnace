// 바이옴 몹 스폰 목록의 종류(MobCategory 의 이름). 문자열 키는 마인크래프트 버전과 상관없이 같다.
package org.domi.furnace.api.worldgen;

public enum SpawnCategory {
    MONSTER("monster"),
    CREATURE("creature"),
    AMBIENT("ambient"),
    AXOLOTLS("axolotls"),
    UNDERGROUND_WATER_CREATURE("underground_water_creature"),
    WATER_CREATURE("water_creature"),
    WATER_AMBIENT("water_ambient"),
    MISC("misc");

    private final String key;

    SpawnCategory(String key) { this.key = key; }

    /** 바닐라 바이옴 JSON 의 spawners 키. */
    public String key() { return key; }
}
