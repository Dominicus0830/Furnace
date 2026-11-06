// 아이템 희귀도(minecraft:rarity) — 이름 색을 정한다.
package org.domi.furnace.api.item;

public enum Rarity {
    COMMON, UNCOMMON, RARE, EPIC;

    /** 저장 이름("common" 등). */
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Rarity byId(String id) {
        for (Rarity r : values()) if (r.id().equals(id)) return r;
        throw new IllegalArgumentException("알 수 없는 희귀도: " + id);
    }
}
