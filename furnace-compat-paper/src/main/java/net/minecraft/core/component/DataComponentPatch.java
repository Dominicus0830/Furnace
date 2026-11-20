package net.minecraft.core.component;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 구성 요소 패치(Mojang 이름 shim, N2b-6 → T-200): 기본 구성 요소에서 바뀐 값과 지운 종류. Furnace 아이템({@code org.domi.furnace.api.item.ItemStack})의
 * {@code components}(키 → SNBT)·{@code removedComponents} 를 그대로 쥔다.
 *
 * <p>{@link #CODEC} 은 진짜 DataFixerUpper {@code Codec} 이다. 바닐라 {@code DataComponentPatch.CODEC} 과 같은 저장 꼴
 * ({@code {"minecraft:x": 값, "!minecraft:y": {}}})로 어떤 {@code DynamicOps} 로든 읽고 쓴다. 값의 변환·검사는 서버의 컴포넌트 코덱(바닐라 규칙)이 하고
 * ({@code ItemSupport#encodeComponentPatch}·{@code decodeComponentPatch}), 이 shim 은 그 NBT 꼴을 받은 ops 의 값으로 옮긴다. 모르는 컴포넌트·잘못된 값은
 * 바닐라처럼 {@code DataResult.error} 로 돌려준다. 알려진 차이: 바닐라 패치는 넣은 순서를 지키지만 Furnace 아이템은 키 순으로 정렬해 들고 있어, 값이 있는 것
 * 다음 지운 것 순서로 쓴다(NBT 묶음은 순서가 없어 결과가 같다).
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
                    org.domi.furnace.api.nbt.NbtCompound nbt;
                    try {
                        nbt = org.domi.furnace.api.item.ItemSupport.get().encodeComponentPatch(input.set, input.removed);
                    } catch (IllegalArgumentException e) {
                        String message = e.getMessage();
                        return com.mojang.serialization.DataResult.error(() -> message);
                    }
                    com.mojang.serialization.RecordBuilder<T> builder = ops.mapBuilder();
                    for (String key : nbt.keys()) {
                        builder.add(ops.createString(key), net.minecraft.nbt.NbtOps.INSTANCE.convertTo(ops, net.minecraft.nbt.NbtBridge.fromFurnace(nbt.get(key))));
                    }
                    return builder.build(prefix);
                }

                @Override
                public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<DataComponentPatch, T>> decode(
                        com.mojang.serialization.DynamicOps<T> ops, T input) {
                    var map = ops.getMap(input);
                    if (map.isError()) {
                        String message = map.error().get().message();
                        return com.mojang.serialization.DataResult.error(() -> message);
                    }
                    try {
                        net.minecraft.nbt.Tag tag = ops.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, input);
                        var nbt = (org.domi.furnace.api.nbt.NbtCompound) net.minecraft.nbt.NbtBridge.toFurnace(tag);
                        var patch = org.domi.furnace.api.item.ItemSupport.get().decodeComponentPatch(nbt);
                        return com.mojang.serialization.DataResult.success(
                                com.mojang.datafixers.util.Pair.of(patch.isEmpty() ? EMPTY : new DataComponentPatch(patch.set(), patch.removed()), input));
                    } catch (IllegalArgumentException e) {
                        String message = e.getMessage();
                        return com.mojang.serialization.DataResult.error(() -> message);
                    }
                }

                @Override
                public String toString() { return "DataComponentPatch.CODEC[Furnace]"; }
            };
        }
    }
}
