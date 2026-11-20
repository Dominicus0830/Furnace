package net.minecraft.nbt;

/**
 * NBT 값(Mojang 이름 shim, H7b, 등급 A). 바닐라처럼 묶음·리스트·배열은 가변이고 수·문자열은 불변이다. {@code toString} 은 바닐라
 * SNBT 규칙(Furnace {@link org.domi.furnace.api.nbt.Snbt} — 바닐라 Tag.toString 1.21.1 과 같은 결과)이다. 이진 쓰기({@link #write})·읽기
 * ({@code TagType.load}, {@link NbtIo})는 바닐라 바이트 꼴이다(NMS shim 5e-3). {@code TagVisitor} 는 두지 않았다(없는 멤버 — 호출 위치에서 실패).
 * Furnace 값과는 {@link NbtBridge} 로 오간다.
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

    /** 바닐라: 이름·번호 없이 몸통만 이진으로 쓴다. */
    default void write(java.io.DataOutput output) throws java.io.IOException { NbtBinary.writePayload(this, output); }

    /** 바닐라: 이 태그가 차지하는 메모리 어림(바이트) — 묶음은 키·값을 더한다. */
    int sizeInBytes();

    /** 이 태그의 종류(바닐라는 클래스마다 TYPE 을 돌려준다 — 같은 객체). */
    default TagType<?> getType() { return TagTypes.getType(getId()); }

    Tag copy();

    /** 문자열 태그는 그 값, 나머지는 SNBT. */
    default String getAsString() { return toString(); }
}
