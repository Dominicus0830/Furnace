package net.minecraft.nbt;

/**
 * NBT 태그 종류(Mojang 이름 shim). 바닐라 1.21.1 과 같은 이름·표시 이름·{@code isValue} 와 이진 읽기({@code load}, NMS shim 5e-3)를 둔다 —
 * 건너뛰기·방문자({@code parse}·{@code skip})는 없다(없는 멤버 — 호출 위치에서 실패). 각 태그 클래스의 {@code TYPE} 이 그 클래스 안의
 * 익명 클래스라는 점도 바닐라와 같다: ProtocolLib {@code NbtFactory} 가 {@code TagTypes.getType(번호).getClass().getEnclosingClass()} 로
 * 태그 클래스를 찾는다(2026-11-14 ProtocolLib 점검).
 */
public interface TagType<T extends Tag> {

    /** 값 태그인가(바닐라: 수·문자열·끝 태그 true, 배열·리스트·묶음 false). */
    default boolean isValue() { return false; }

    String getName();

    String getPrettyName();

    /** 바닐라: 이 종류의 몸통을 읽는다(쓴 바이트·깊이는 {@code accounter} 가 센다). */
    @SuppressWarnings("unchecked")
    default T load(java.io.DataInput input, NbtAccounter accounter) throws java.io.IOException {
        return (T) NbtBinary.load(TagTypes.idOf(this), input, accounter);
    }

    /** 모르는 번호의 종류(바닐라와 같은 이름: INVALID[n], UNKNOWN_n). */
    static TagType<EndTag> createInvalid(int id) {
        return new TagType<>() {
            @Override public String getName() { return "INVALID[" + id + "]"; }
            @Override public String getPrettyName() { return "UNKNOWN_" + id; }
            @Override public EndTag load(java.io.DataInput input, NbtAccounter accounter) throws java.io.IOException {
                throw new java.io.IOException("Invalid tag id: " + id);
            }
        };
    }
}
