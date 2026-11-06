// 플레이어가 깃펜 책을 저장하거나 서명하기 직전(edit_book 패킷) — 연결(네트워크) 스레드에서 불린다. 깃펜 책이 든 칸에 대해서만 불린다.
// pages 를 바꾸면 그대로 저장되고(앞의 100쪽만), title 이 null 이 아니면 서명이다. setTitle(null) 로 서명을 저장으로 바꾸거나 제목을 주어 서명으로 바꿀 수 있다.
// 취소하면 책은 그대로다.
package org.domi.furnace.api.event.player;

import java.util.List;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerEditBookEvent extends PlayerEvent implements Cancellable {

    private final int slot;
    private final List<String> previousPages;
    private List<String> pages;
    private String title;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerEditBookEvent(Player player, int slot, List<String> previousPages, List<String> pages, String title) {
        super(player);
        this.slot = slot;
        this.previousPages = List.copyOf(previousPages);
        this.pages = List.copyOf(pages);
        this.title = title;
    }

    /** 책이 든 칸(핫바 0~8, 보조 손 40). */
    public int slot() { return slot; }

    /** 고치기 전 쪽(글자 그대로). */
    public List<String> previousPages() { return previousPages; }

    public List<String> pages() { return pages; }

    public void setPages(List<String> pages) { this.pages = List.copyOf(pages); }

    /** 서명이면 제목, 아니면 null. */
    public String title() { return title; }

    public void setTitle(String title) { this.title = title; }

    public boolean isSigning() { return title != null; }
}
