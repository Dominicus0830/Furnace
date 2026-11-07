// 엔티티 자세(Pose). 키 문자열은 마인크래프트 버전과 상관없이 같다.
package org.domi.furnace.api.entity;

public enum Pose {
    STANDING("standing"),
    FALL_FLYING("fall_flying"),
    SLEEPING("sleeping"),
    SWIMMING("swimming"),
    SPIN_ATTACK("spin_attack"),
    CROUCHING("crouching"),
    LONG_JUMPING("long_jumping"),
    DYING("dying"),
    CROAKING("croaking"),
    USING_TONGUE("using_tongue"),
    SITTING("sitting"),
    ROARING("roaring"),
    SNIFFING("sniffing"),
    EMERGING("emerging"),
    DIGGING("digging"),
    SLIDING("sliding"),
    SHOOTING("shooting"),
    INHALING("inhaling");

    private final String key;

    Pose(String key) { this.key = key; }

    public String key() { return key; }

    /** 바닐라 Pose 번호(데이터 동기화 값)와 같은 순서. */
    public int id() { return ordinal(); }

    public static Pose byId(int id) {
        Pose[] v = values();
        return id >= 0 && id < v.length ? v[id] : STANDING;
    }

    public static Pose byKey(String key) {
        for (Pose p : values()) if (p.key.equals(key)) return p;
        return null;
    }
}
