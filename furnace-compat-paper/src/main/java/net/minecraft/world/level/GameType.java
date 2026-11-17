package net.minecraft.world.level;

/** 바닐라 게임 모드(Mojang 이름, T-203 — 레벨 설정이 들고 다닌다). */
public enum GameType {
    SURVIVAL(0, "survival"), CREATIVE(1, "creative"), ADVENTURE(2, "adventure"), SPECTATOR(3, "spectator");

    public static final GameType DEFAULT_MODE = SURVIVAL;

    private final int id;
    private final String name;

    GameType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    public boolean isCreative() { return this == CREATIVE; }

    public boolean isSurvival() { return this == SURVIVAL || this == ADVENTURE; }

    public static GameType byId(int id) {
        for (GameType t : values()) if (t.id == id) return t;
        return DEFAULT_MODE;
    }

    public static GameType byName(String name) {
        for (GameType t : values()) if (t.name.equals(name)) return t;
        return DEFAULT_MODE;
    }
}
