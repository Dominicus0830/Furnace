package net.minecraft.nbt;

/**
 * NBT 값(Mojang 이름 shim, H7b, 등급 A). 바닐라처럼 묶음·리스트·배열은 가변이고 수·문자열은 불변이다. {@code toString} 은 바닐라
 * SNBT 규칙(Furnace {@link org.domi.furnace.api.nbt.Snbt} — 바닐라 Tag.toString 1.21.1 과 같은 결과)이다. 바이트 읽기·쓰기(write·
 * {@code TagType}·{@code TagVisitor})는 두지 않았다(없는 멤버 — 호출 위치에서 실패). Furnace 값과는 {@link NbtBridge} 로 오간다.
 */
public interface Tag {
    int OBJECT_HEADER = 8;
    int ARRAY_HEADER = 12;
    int OBJECT_REFERENCE = 4;
    int STRING_SIZE = 28;
    byte TAG_END = 0;
    byte TAG_BYTE = 1;
    byte TAG_SHORT = 2;
    byte TAG_INT = 3;
    byte TAG_LONG = 4;
    byte TAG_FLOAT = 5;
    byte TAG_DOUBLE = 6;
    byte TAG_BYTE_ARRAY = 7;
    byte TAG_STRING = 8;
    byte TAG_LIST = 9;
    byte TAG_COMPOUND = 10;
    byte TAG_INT_ARRAY = 11;
    byte TAG_LONG_ARRAY = 12;
    int TAG_ANY_NUMERIC = 99;
    int MAX_DEPTH = 512;

    byte getId();

    Tag copy();

    /** 문자열 태그는 그 값, 나머지는 SNBT. */
    default String getAsString() { return toString(); }
}
