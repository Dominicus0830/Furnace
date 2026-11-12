package net.minecraft.world.entity;

/** 자세(Mojang 이름 shim). 상수 순서·번호는 바닐라 1.21.1 과 같다. */
public enum Pose {
    STANDING, FALL_FLYING, SLEEPING, SWIMMING, SPIN_ATTACK, CROUCHING, LONG_JUMPING, DYING, CROAKING, USING_TONGUE, SITTING, ROARING, SNIFFING, EMERGING, DIGGING, SLIDING, SHOOTING, INHALING;

    public int id() { return ordinal(); }

    public static Pose byId(int id) { return values()[Math.floorMod(id, values().length)]; }
}
