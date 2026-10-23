// 바닐라 ClickType — 순서가 container_click 패킷의 clickType 번호다.
package org.domi.furnace.server.menu;

public enum ClickType {
    PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL;

    private static final ClickType[] VALUES = values();

    /** 패킷 번호 → 종류. 범위 밖이면 null(바닐라는 디코드 실패로 연결을 끊는다). */
    public static ClickType byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }
}
