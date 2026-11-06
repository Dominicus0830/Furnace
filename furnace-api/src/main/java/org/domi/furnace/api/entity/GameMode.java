// 게임 모드(바닐라 GameType) — id 와 이름이 프로토콜·설정과 같다.
package org.domi.furnace.api.entity;

public enum GameMode {
    SURVIVAL(0, "survival"), CREATIVE(1, "creative"), ADVENTURE(2, "adventure"), SPECTATOR(3, "spectator");

    private final int id;
    private final String key;

    GameMode(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }

    public String key() { return key; }

    public static GameMode byId(int id) {
        for (GameMode m : values()) if (m.id == id) return m;
        return null;
    }

    public static GameMode byKey(String key) {
        for (GameMode m : values()) if (m.key.equals(key)) return m;
        return null;
    }
}
