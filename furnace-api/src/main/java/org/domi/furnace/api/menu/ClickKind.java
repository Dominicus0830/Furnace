// 메뉴 클릭의 종류 — 클라이언트 입력을 버전과 무관한 이름으로.
package org.domi.furnace.api.menu;

public enum ClickKind {
    LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, MIDDLE,
    /** 숫자 키로 핫바 칸과 바꾸기(MenuClick.hotbarButton). */
    NUMBER_KEY,
    /** F 키로 왼손과 바꾸기. */
    SWAP_OFFHAND,
    /** Q 키. */
    DROP,
    /** Ctrl+Q. */
    CONTROL_DROP,
    /** 같은 아이템을 두 번 눌러 모으기. */
    DOUBLE_CLICK,
    /** 눌러 끌어 나누기(단계마다 한 번씩 온다). */
    DRAG,
    UNKNOWN
}
