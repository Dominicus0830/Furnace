package net.minecraft.world;

/** 바닐라 난이도(Mojang 이름, T-203 — 레벨 설정이 들고 다닌다). */
public enum Difficulty {
    PEACEFUL(0, "peaceful"), EASY(1, "easy"), NORMAL(2, "normal"), HARD(3, "hard");

    private final int id;
    private final String key;

    Difficulty(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int getId() { return id; }

    public String getKey() { return key; }

    public static Difficulty byId(int id) { return values()[Math.floorMod(id, 4)]; }

    public static Difficulty byName(String name) {
        for (Difficulty d : values()) if (d.key.equals(name)) return d;
        return null;
    }
}
