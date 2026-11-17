package org.bukkit;

import java.util.HashMap;
import java.util.Map;

/** @deprecated 옛 나무 종류(데이터 값 시절). Bukkit 1.21.1 에도 남아 있다. */
@Deprecated
public enum TreeSpecies {
    GENERIC(0x0), REDWOOD(0x1), BIRCH(0x2), JUNGLE(0x3), ACACIA(0x4), DARK_OAK(0x5);

    private static final Map<Byte, TreeSpecies> BY_DATA = new HashMap<>();

    static {
        for (TreeSpecies t : values()) BY_DATA.put(t.data, t);
    }

    private final byte data;

    TreeSpecies(int data) { this.data = (byte) data; }

    @Deprecated
    public byte getData() { return data; }

    /** 데이터 값으로 찾기(없으면 null). */
    @Deprecated
    public static TreeSpecies getByData(byte data) { return BY_DATA.get(data); }
}
