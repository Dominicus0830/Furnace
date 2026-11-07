package org.bukkit.event;

/** 처리 순서: LOWEST 가 먼저, MONITOR 가 마지막(관찰 전용). */
public enum EventPriority {
    LOWEST(0), LOW(1), NORMAL(2), HIGH(3), HIGHEST(4), MONITOR(5);

    private final int slot;

    EventPriority(int slot) { this.slot = slot; }

    public int getSlot() { return slot; }
}
