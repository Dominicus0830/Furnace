// 메뉴 모양 — 위쪽 칸 배치. 아래쪽은 늘 플레이어 인벤토리다. 키는 마인크래프트 버전과 무관한 레지스트리 이름.
package org.domi.furnace.api.menu;

public enum MenuType {
    CHEST_1("minecraft:generic_9x1", 9), CHEST_2("minecraft:generic_9x2", 18), CHEST_3("minecraft:generic_9x3", 27),
    CHEST_4("minecraft:generic_9x4", 36), CHEST_5("minecraft:generic_9x5", 45), CHEST_6("minecraft:generic_9x6", 54),
    /** 3×3 (발사기 모양). */
    DISPENSER("minecraft:generic_3x3", 9),
    /** 가로 5칸 깔때기 모양. */
    HOPPER("minecraft:hopper", 5);

    private final String key;
    private final int size;

    MenuType(String key, int size) {
        this.key = key;
        this.size = size;
    }

    public String key() { return key; }

    /** 위쪽 칸 수. */
    public int size() { return size; }

    /** 한 줄의 칸 수(상자 9, 발사기 3, 깔때기 5). */
    public int columns() { return this == DISPENSER ? 3 : this == HOPPER ? 5 : 9; }

    public int rows() { return size / columns(); }

    /** 줄 수(1~6)로 상자 모양을 고른다. */
    public static MenuType chest(int rows) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("상자 줄 수는 1~6: " + rows);
        return values()[rows - 1];
    }
}
