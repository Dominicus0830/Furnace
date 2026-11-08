package net.minecraft.nbt;

import java.util.Objects;

/** 문자열 태그(불변). getAsString 은 값 그대로, toString 은 따옴표·이스케이프를 붙인 SNBT. */
public final class StringTag implements Tag {

    private static final StringTag EMPTY = new StringTag("");

    private final String data;

    private StringTag(String data) {
        Objects.requireNonNull(data, "Null string not allowed");
        this.data = data;
    }

    public static StringTag valueOf(String v) { return v.isEmpty() ? EMPTY : new StringTag(v); }

    /** 바닐라 SNBT 따옴표 규칙(내용에 덜 걸리는 따옴표). */
    public static String quoteAndEscape(String s) { return org.domi.furnace.api.nbt.Snbt.write(s); }

    @Override
    public byte getId() { return TAG_STRING; }

    @Override
    public StringTag copy() { return this; }

    @Override
    public String getAsString() { return data; }

    @Override
    public String toString() { return quoteAndEscape(data); }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof StringTag t && data.equals(t.data); }

    @Override
    public int hashCode() { return data.hashCode(); }
}
