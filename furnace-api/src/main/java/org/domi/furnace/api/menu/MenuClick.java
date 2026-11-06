// 메뉴가 열린 동안 일어난 클릭 한 번. 메뉴의 클릭 처리기에 들어온다.
// 기본은 클릭이 취소된다(아이템을 꺼내거나 넣을 수 없다). 바닐라처럼 움직이게 하려면 allow() 를 부른다.
// 처리기는 연결 스레드에서 불리므로 짧게 끝낸다 — 오래 걸리는 일은 스케줄러·비동기 실행기로 넘긴다.
package org.domi.furnace.api.menu;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;

public final class MenuClick {

    /** 창 바깥을 눌렀을 때의 windowSlot. */
    public static final int OUTSIDE = -999;

    private final Player player;
    private final Menu menu;
    private final int windowSlot;
    private final ClickKind kind;
    private final int hotbarButton;
    private final ItemStack clicked;
    private final ItemStack cursor;
    private boolean allowed;

    public MenuClick(Player player, Menu menu, int windowSlot, ClickKind kind, int hotbarButton, ItemStack clicked, ItemStack cursor) {
        this.player = Objects.requireNonNull(player);
        this.menu = Objects.requireNonNull(menu);
        this.windowSlot = windowSlot;
        this.kind = Objects.requireNonNull(kind);
        this.hotbarButton = hotbarButton;
        this.clicked = Objects.requireNonNull(clicked);
        this.cursor = Objects.requireNonNull(cursor);
    }

    public Player player() { return player; }

    public Menu menu() { return menu; }

    /** 창 번호: 0..size-1 은 메뉴 칸, 그 뒤는 플레이어 인벤토리, 창 바깥은 OUTSIDE. */
    public int windowSlot() { return windowSlot; }

    /** 메뉴 위쪽 칸을 눌렀는가. */
    public boolean inMenu() { return windowSlot >= 0 && windowSlot < menu.size(); }

    /** 메뉴 칸 번호(0..size-1), 메뉴 칸이 아니면 -1. */
    public int slot() { return inMenu() ? windowSlot : -1; }

    /** 창 바깥을 눌렀는가. */
    public boolean isOutside() { return windowSlot == OUTSIDE; }

    public ClickKind kind() { return kind; }

    /** NUMBER_KEY 일 때 핫바 칸 0..8, 아니면 -1. */
    public int hotbarButton() { return hotbarButton; }

    /** 눌린 칸의 아이템(클릭 전, 창 바깥이면 비어 있다). */
    public ItemStack clicked() { return clicked; }

    /** 마우스 커서가 들고 있는 아이템(클릭 전). */
    public ItemStack cursor() { return cursor; }

    public boolean isShift() { return kind == ClickKind.SHIFT_LEFT || kind == ClickKind.SHIFT_RIGHT; }

    /** 클릭을 바닐라 규칙대로 처리하게 한다(기본은 취소). */
    public void allow() { allowed = true; }

    /** 클릭을 취소한다(기본). */
    public void deny() { allowed = false; }

    public boolean isAllowed() { return allowed; }

    /** 이 클릭이 끝난 뒤 이 플레이어의 메뉴를 닫는다. */
    public void close() { player.closeMenu(); }
}
