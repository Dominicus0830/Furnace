// 공통 상태 비트(EntityDataKeys.Entity.SHARED_FLAGS, 바닐라 Entity.FLAG_*) — 이름으로 비트를 다룬다.
// 예) byte f = EntityFlags.set(old, EntityFlags.GLOWING, true);
package org.domi.furnace.api.entity.data;

public final class EntityFlags {

    private EntityFlags() {}

    public static final int ON_FIRE = 0x01, CROUCHING = 0x02, SPRINTING = 0x08, SWIMMING = 0x10, INVISIBLE = 0x20, GLOWING = 0x40, FALL_FLYING = 0x80;

    /** 여러 비트를 합친다. */
    public static byte of(int... bits) {
        int f = 0;
        for (int b : bits) f |= b;
        return (byte) f;
    }

    public static byte set(byte flags, int bit, boolean on) { return (byte) (on ? flags | bit : flags & ~bit); }

    public static boolean has(byte flags, int bit) { return (flags & bit) != 0; }
}
