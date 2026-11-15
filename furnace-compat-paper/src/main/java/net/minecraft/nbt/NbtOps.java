package net.minecraft.nbt;

/**
 * NBT 용 DataFixerUpper 연산 자리(Mojang 이름 shim, N2b-6 — 상수만). 바닐라 {@code NbtOps} 는 {@code DynamicOps<Tag>} 이지만, 이 shim 은 아직 연산을
 * 하나도 갖지 않아 DynamicOps 를 구현하지 않는다 — DataFixerUpper 에 넘겨 쓰면 그 자리에서 실패한다(연산 구현은 T-191).
 * FAWE 는 어댑터를 올릴 때 이 상수를 읽어 두기만 하고, 쓰는 곳은 옛 판 데이터 고치기·아이템 구성 요소 바꾸기뿐이다.
 */
public class NbtOps {

    public static final NbtOps INSTANCE = new NbtOps();

    protected NbtOps() {}

    @Override
    public String toString() { return "NBT"; }
}
