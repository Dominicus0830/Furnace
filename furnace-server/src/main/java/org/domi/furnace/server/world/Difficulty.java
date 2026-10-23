// 바닐라 Difficulty. id 순서·이름이 프로토콜(Change Difficulty)과 server.properties 에 그대로 쓰인다.
package org.domi.furnace.server.world;

public enum Difficulty {
    PEACEFUL(0, "peaceful"), EASY(1, "easy"), NORMAL(2, "normal"), HARD(3, "hard");

    private static final Difficulty[] BY_ID = values();

    private final int id;
    private final String key;

    Difficulty(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() { return id; }

    public String key() { return key; }

    /** ByIdMap.continuous(..., WRAP): 범위 밖 id 는 순환. */
    public static Difficulty byId(int id) { return BY_ID[Math.floorMod(id, BY_ID.length)]; }

    /** 이름(대소문자 구분)으로 찾고 없으면 null. */
    public static Difficulty byName(String key) {
        for (Difficulty d : BY_ID) if (d.key.equals(key)) return d;
        return null;
    }
}
