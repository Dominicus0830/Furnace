// 바닐라 GameType. id 순서·이름이 프로토콜과 server.properties·level.dat 에 그대로 쓰인다.
package org.domi.furnace.server.world;

public enum GameMode {
    SURVIVAL(0, "survival"), CREATIVE(1, "creative"), ADVENTURE(2, "adventure"), SPECTATOR(3, "spectator");

    private static final GameMode[] BY_ID = values();

    private final int id;
    private final String key;

    GameMode(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }

    public String key() { return key; }

    /** ByIdMap.continuous(..., ZERO): 범위 밖 id 는 첫 값(survival). */
    public static GameMode byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : BY_ID[0]; }

    /** 이름(대소문자 구분)으로 찾고 없으면 survival. */
    public static GameMode byName(String key) {
        for (GameMode m : BY_ID) if (m.key.equals(key)) return m;
        return SURVIVAL;
    }
}
