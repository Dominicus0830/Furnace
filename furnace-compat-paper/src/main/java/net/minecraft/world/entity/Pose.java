package net.minecraft.world.entity;

/** 자세(Mojang 이름 shim). 상수 순서·번호는 바닐라 1.21.1 과 같다. */
public enum Pose {
    STANDING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK, CROUCHING, LONG_JUMPING, DYING, CROAKING, USING_TONGUE, SITTING, ROARING, SNIFFING, EMERGING, DIGGING, SLIDING, SHOOTING, INHALING;

    public int id() { return ordinal(); }

    /** 바닐라 BY_ID: 범위 밖 번호는 STANDING(ZERO 전략). */
    public static final java.util.function.IntFunction<Pose> BY_ID = id -> id >= 0 && id < values().length ? values()[id] : STANDING;

    public static Pose byId(int id) { return BY_ID.apply(id); }
}
