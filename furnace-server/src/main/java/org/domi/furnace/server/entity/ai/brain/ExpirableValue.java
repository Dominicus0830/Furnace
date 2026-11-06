// 바닐라 ExpirableValue: 기억 값과 남은 유효 틱(Long.MAX_VALUE = 영구).
package org.domi.furnace.server.entity.ai.brain;

public final class ExpirableValue<T> {
    private final T value;
    private long timeToLive;

    public ExpirableValue(T value, long ttl) {
        this.value = value;
        this.timeToLive = ttl;
    }

    public static <T> ExpirableValue<T> of(T value) { return new ExpirableValue<>(value, Long.MAX_VALUE); }

    public static <T> ExpirableValue<T> of(T value, long ttl) { return new ExpirableValue<>(value, ttl); }

    public void tick() {
        if (canExpire()) timeToLive--;
    }

    public long getTimeToLive() { return timeToLive; }

    public T getValue() { return value; }

    public boolean hasExpired() { return timeToLive <= 0L; }

    public boolean canExpire() { return timeToLive != Long.MAX_VALUE; }

    @Override
    public String toString() { return value + (canExpire() ? " (ttl: " + timeToLive + ")" : ""); }
}
