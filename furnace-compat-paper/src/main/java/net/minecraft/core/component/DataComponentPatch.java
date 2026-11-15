package net.minecraft.core.component;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 구성 요소 패치(Mojang 이름 shim, N2b-6): 기본 구성 요소에서 바뀐 값과 지운 종류. Furnace 아이템({@code org.domi.furnace.api.item.ItemStack})의
 * {@code components}(키 → SNBT)·{@code removedComponents} 를 그대로 쥔다.
 *
 * <p>{@link #CODEC} 은 진짜 DataFixerUpper {@code Codec} 객체라 {@code optionalFieldOf(..)} 같은 조합은 되지만, 인코딩·디코딩 자체는 아직
 * 오류 결과({@code DataResult.error})를 준다 — NBT 와 Furnace 구성 요소 사이의 바꾸기(NbtOps·직렬화 문맥)가 아직 없다(TASK). FAWE 는
 * 어댑터를 올릴 때 이 상수로 코덱을 만들어 두기만 하고, 쓰는 곳은 아이템 변환(완드·브러시 아이템 주기)뿐이다.
 */
public final class DataComponentPatch {

    public static final DataComponentPatch EMPTY = new DataComponentPatch(Map.of(), Set.of());

    /** 서버 제공 라이브러리 묶음 datafixerupper 가 있어야 만들어진다(그 묶음을 쓰는 플러그인이 있을 때만 이 클래스가 초기화된다). */
    public static final com.mojang.serialization.Codec<DataComponentPatch> CODEC = Lib.codec();

    private final Map<String, String> set;
    private final Set<String> removed;

    private DataComponentPatch(Map<String, String> set, Set<String> removed) {
        this.set = Map.copyOf(set);
        this.removed = Set.copyOf(removed);
    }

    /** 호환 모듈 전용: Furnace 아이템의 구성 요소 표에서. */
    public static DataComponentPatch furnaceOf(Map<String, String> set, Set<String> removed) {
        return set.isEmpty() && removed.isEmpty() ? EMPTY : new DataComponentPatch(set, removed);
    }

    /** 호환 모듈 전용: 바뀐 값(키 → SNBT). */
    public Map<String, String> furnaceSet() { return set; }

    /** 호환 모듈 전용: 지운 종류. */
    public Set<String> furnaceRemoved() { return removed; }

    public boolean isEmpty() { return set.isEmpty() && removed.isEmpty(); }

    public int size() { return set.size() + removed.size(); }

    @Override
    public boolean equals(Object o) { return o instanceof DataComponentPatch p && set.equals(p.set) && removed.equals(p.removed); }

    @Override
    public int hashCode() { return set.hashCode() * 31 + removed.hashCode(); }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("{");
        set.forEach((k, v) -> b.append(b.length() > 1 ? ", " : "").append(k).append("=>").append(v));
        removed.forEach(k -> b.append(b.length() > 1 ? ", " : "").append('!').append(k));
        return b.append('}').toString();
    }

    /** DataFixerUpper 를 쓰는 본문(CompatLinkTest 가 라이브러리 없이 링크를 볼 때 이 이름만 건너뛴다). */
    static final class Lib {
        private Lib() {}

        static com.mojang.serialization.Codec<DataComponentPatch> codec() {
            return new com.mojang.serialization.Codec<>() {
                @Override
                public <T> com.mojang.serialization.DataResult<T> encode(DataComponentPatch input, com.mojang.serialization.DynamicOps<T> ops, T prefix) {
                    return com.mojang.serialization.DataResult.error(() -> "Furnace: 아이템 구성 요소 패치 코덱은 아직 없다(인코딩 " + input + ")");
                }

                @Override
                public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<DataComponentPatch, T>> decode(
                        com.mojang.serialization.DynamicOps<T> ops, T input) {
                    return com.mojang.serialization.DataResult.error(() -> "Furnace: 아이템 구성 요소 패치 코덱은 아직 없다(디코딩)");
                }

                @Override
                public String toString() { return "DataComponentPatch.CODEC[Furnace]"; }
            };
        }
    }
}
