package org.bukkit;

public enum GameMode {
    CREATIVE(1), SURVIVAL(0), ADVENTURE(2), SPECTATOR(3);

    private final int value;

    GameMode(int value) { this.value = value; }

    /** 바닐라 게임 모드 번호. */
    public int getValue() { return value; }

    public static GameMode getByValue(int value) {
        for (GameMode m : values()) if (m.value == value) return m;
        return null;
    }
}
