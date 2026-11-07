package org.bukkit.event;

/**
 * 이벤트 바탕 클래스. 하위 클래스는 정적 {@code getHandlerList()} 와 {@link #getHandlers()} 를 가져야 한다(Bukkit 규약).
 * Furnace 에는 "메인 스레드"가 없다 — 동기 이벤트는 그 이벤트가 일어난 region(또는 전역) 스레드에서 불린다.
 */
public abstract class Event {

    private String name;
    private final boolean async;

    public Event() { this(false); }

    public Event(boolean isAsync) { this.async = isAsync; }

    public String getEventName() {
        if (name == null) name = getClass().getSimpleName();
        return name;
    }

    public abstract HandlerList getHandlers();

    public final boolean isAsynchronous() { return async; }

    public enum Result {
        DENY, DEFAULT, ALLOW
    }
}
