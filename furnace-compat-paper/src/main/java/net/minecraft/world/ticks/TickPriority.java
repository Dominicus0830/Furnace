package net.minecraft.world.ticks;

/** 예약 틱 우선순위(Mojang 이름 shim, T-204). 값·순서는 바닐라 1.21.1 과 같다(작을수록 먼저). */
public enum TickPriority {
    EXTREMELY_HIGH(-3),
    VERY_HIGH(-2),
    HIGH(-1),
    NORMAL(0),
    LOW(1),
    VERY_LOW(2),
    EXTREMELY_LOW(3);

    private final int value;

    TickPriority(int value) { this.value = value; }

    /** 바닐라: 범위 밖이면 끝 값으로. */
    public static TickPriority byValue(int value) {
        for (TickPriority p : values()) if (p.value == value) return p;
        return value < EXTREMELY_HIGH.value ? EXTREMELY_HIGH : EXTREMELY_LOW;
    }

    public int getValue() { return value; }
}
