// 축(바닐라 Direction.Axis) — axis 속성 값 x/y/z.
package org.domi.furnace.api.world;

public enum Axis {
    X, Y, Z;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static Axis byId(String id) { return valueOf(id.toUpperCase(java.util.Locale.ROOT)); }
}
