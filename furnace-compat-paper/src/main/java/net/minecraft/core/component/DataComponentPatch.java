package net.minecraft.core.component;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 구성 요소 패치(Mojang 이름 shim, N2b-6): 기본 구성 요소에서 바뀐 값과 지운 종류. Furnace 아이템({@code org.domi.furnace.api.item.ItemStack})의
 * {@code components}(키 → SNBT)·{@code removedComponents} 를 그대로 쥔다.
 *
 * <p>{@link #CODEC} 은 진짜 DataFixerUpper {@code Codec} 객체라 {@code optionalFieldOf(..)} 같은 조합은 되지만, 인코딩·디코딩을 <b>부르면</b>
 * {@link org.domi.furnace.compat.nms.UnsupportedNmsCall} 을 던진다 — NBT({@code NbtOps} 는 아직 {@code DynamicOps} 가 아니다)와 Furnace 구성 요소(키 → SNBT)
 * 사이를 바닐라와 같은 결과로 바꾸는 길이 아직 없다(T-191·T-200). 오류 결과({@code DataResult.error})로 조용히 넘기지 않아 부른 자리에서 이유와 대체
 * (Furnace 아이템 API)가 바로 보인다. 코덱을 만들어 두기만 하는 어댑터는 영향이 없다.
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
                    throw unsupported("encode");
                }

                @Override
                public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<DataComponentPatch, T>> decode(
                        com.mojang.serialization.DynamicOps<T> ops, T input) {
                    throw unsupported("decode");
                }

                @Override
                public String toString() { return "DataComponentPatch.CODEC[Furnace]"; }
            };
        }

        private static org.domi.furnace.compat.nms.UnsupportedNmsCall unsupported(String what) {
            return new org.domi.furnace.compat.nms.UnsupportedNmsCall("DataComponentPatch.CODEC#" + what,
                    "converting item component patches to and from NBT or JSON is not available yet (it needs the NbtOps DynamicOps). Use the Furnace item API (ItemStack components) instead");
        }
    }
}
