package com.destroystokyo.paper.event.server;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 명령 자동완성 후보를 비동기로 계산하게 하는 이벤트(Paper). 후보를 채우면(또는 handled 로 표시하면) 표준 과정 — 명령의 tabComplete 나 접속자 이름 — 은
 * 불리지 않고, 취소하면 후보도 표준 과정도 없다. 플레이어가 Tab 을 눌렀을 때와 콘솔 입력줄에서 불린다.
 * <p>
 * 이 호환 계층에 Adventure 가 없어 Paper 의 {@code Completion}(설명 글 Component 를 가진 후보)·{@code completions()}·{@code completions(List)} 는 없다 —
 * 부르면 NoSuchMethodError. 문자열 후보({@link #getCompletions()}·{@link #setCompletions(List)})는 같다.
 */
public class AsyncTabCompleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final CommandSender sender;
    private final String buffer;
    private final boolean isCommand;
    private final Location location;
    private final List<String> completions = new ArrayList<>();
    private boolean handled;
    private boolean cancelled;

    public AsyncTabCompleteEvent(CommandSender sender, String buffer, boolean isCommand, Location loc) {
        super(true);
        this.sender = sender;
        this.buffer = buffer;
        this.isCommand = isCommand;
        this.location = loc;
    }

    @Deprecated
    public AsyncTabCompleteEvent(CommandSender sender, List<String> completions, String buffer, boolean isCommand, Location loc) {
        super(true);
        this.sender = sender;
        this.completions.addAll(completions);
        this.buffer = buffer;
        this.isCommand = isCommand;
        this.location = loc;
    }

    /** 자동완성을 요청한 쪽. */
    public CommandSender getSender() { return sender; }

    /** 보낼 후보(바꿀 수 있는 목록, 순서대로). 이 목록이 비어 있지 않으면 표준 과정은 불리지 않는다. */
    public List<String> getCompletions() { return completions; }

    /** 후보를 통째로 바꾼다 — 넘긴 목록은 복사한다. 이 이벤트의 {@link #getCompletions()} 목록 자신이면 아무 일도 없다. */
    public void setCompletions(List<String> completions) {
        if (completions == null) throw new IllegalArgumentException("Completions list cannot be null");
        if (completions == this.completions) return;
        List<String> copy = new ArrayList<>(completions);
        this.completions.clear();
        this.completions.addAll(copy);
    }

    /** 입력 칸 전체 글(플레이어는 앞의 '/' 를 포함한다). */
    public String getBuffer() { return buffer; }

    /** 명령 자동완성이면 true, 채팅 자동완성이면 false. */
    public boolean isCommand() { return isCommand; }

    /** 요청자가 보고 있는 위치(없으면 null — 서버가 보내는 요청에는 없다). */
    public Location getLocation() { return location != null ? location.clone() : null; }

    /** true 면 표준 과정(명령 tabComplete·접속자 이름)은 불리지 않는다. 후보가 하나라도 있으면 늘 true. */
    public boolean isHandled() { return !completions.isEmpty() || handled; }

    public void setHandled(boolean handled) { this.handled = handled; }

    /** 후보와 따로 정한 handled 표시 — 호환 계층이 Furnace 이벤트와 맞출 때 쓴다(플러그인용이 아니다). */
    public final boolean handledFlag() { return handled; }

    @Override
    public boolean isCancelled() { return cancelled; }

    /** 취소하면 후보도 없고 표준 과정도 불리지 않는다. */
    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLER_LIST; }

    public static HandlerList getHandlerList() { return HANDLER_LIST; }
}
