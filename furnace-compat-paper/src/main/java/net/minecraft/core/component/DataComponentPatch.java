package net.minecraft.core.component;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 구성 요소 패치(Mojang 이름 shim, N2b-6 → T-200): 기본 구성 요소에서 바뀐 값과 지운 종류. Furnace 아이템({@code org.domi.furnace.api.item.ItemStack})의
 * {@code components}(키 → SNBT)·{@code removedComponents} 를 그대로 쥔다.
 *
 * <p>{@link #CODEC} 은 진짜 DataFixerUpper {@code Codec} 이다. 바닐라 {@code DataComponentPatch.CODEC} 과 같은 저장 꼴
 * ({@code {"minecraft:x": 값, "!minecraft:y": {}}})로 읽고 쓴다. 값의 변환·검사는 서버의 컴포넌트 코덱(바닐라 규칙)이 한다
 * ({@code ItemSupport#encodeComponentPatch}·{@code encodeComponentPatchValues}·{@code decodeComponentPatchPartial}).
 * <ul>
 *   <li>쓰기: {@code NbtOps}(와 그것을 감싼 {@code RegistryOps})는 NBT 꼴(참·거짓은 바이트), 그 밖의 ops(JsonOps 등)는 바닐라 JSON 꼴(참·거짓은 불리언,
 *       수는 필드 타입대로, NBT 로 저장되는 값은 NbtOps 에서 옮김)로 쓴다.</li>
 *   <li>읽기: 어떤 ops 든 NBT 로 옮겨 항목마다 읽는다. 모르는 컴포넌트·잘못된 값은 바닐라 {@code DispatchedMapCodec} 처럼
 *       {@code DataResult.error("…; … missed input: {못 읽은 항목}")} 이고, 읽은 항목은 부분 결과({@code resultOrPartial})로 준다.</li>
 * </ul>
 * 알려진 차이: 바닐라 패치는 넣은 순서를 지키지만 Furnace 아이템은 키 순으로 정렬해 들고 있어, 값이 있는 것 다음 지운 것 순서로 쓴다
 * (NBT·JSON 묶음은 순서가 의미 없어 결과가 같다).
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

    /**
     * 바닐라 entrySet(): (종류, 값 또는 지움=empty) 짝. 값 변환이 있는 종류는 NMS 값, 나머지는 {@link org.domi.furnace.compat.nms.RawComponentValue}
     * (저장 꼴 SNBT) — 값은 {@code type.streamCodec()} 으로 쓰고 읽는다. 바닐라 패치는 넣은 순서지만 여기는 값이 있는 것 다음 지운 것 순서다
     * (위 "알려진 차이").
     */
    public Set<Map.Entry<DataComponentType<?>, java.util.Optional<?>>> entrySet() {
        java.util.LinkedHashSet<Map.Entry<DataComponentType<?>, java.util.Optional<?>>> out = new java.util.LinkedHashSet<>();
        for (var e : set.entrySet()) {
            DataComponentType<?> t = DataComponents.furnaceType(e.getKey());
            Object v = t instanceof DataComponents.Type<?, ?> k ? k.fromSnbt(e.getValue()) : new org.domi.furnace.compat.nms.RawComponentValue(e.getValue());
            out.add(new java.util.AbstractMap.SimpleImmutableEntry<>(t, java.util.Optional.of(v)));
        }
        for (String key : removed) out.add(new java.util.AbstractMap.SimpleImmutableEntry<>(DataComponents.furnaceType(key), java.util.Optional.empty()));
        return java.util.Collections.unmodifiableSet(out);
    }

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

        /** NbtOps(또는 NbtOps 를 감싼 RegistryOps 등)면 참: 값을 NBT 꼴로 그대로 쓸 수 있다. 그 밖의 ops 는 참·거짓이 구분되는 JSON 꼴 값으로 쓴다. */
        private static boolean isNbt(com.mojang.serialization.DynamicOps<?> ops) {
            while (ops instanceof net.minecraft.resources.DelegatingOps<?> d) ops = d.furnaceDelegate();
            return ops instanceof net.minecraft.nbt.NbtOps;
        }

        /** 서버가 준 JSON 꼴 값(맵·리스트·문자열·참거짓·수·API NBT)을 ops 의 값으로. NBT 로 저장되는 값은 바닐라처럼 NbtOps 에서 옮긴다. */
        @SuppressWarnings("unchecked")
        private static <T> T toOps(com.mojang.serialization.DynamicOps<T> ops, Object v) {
            if (v instanceof java.util.Map<?, ?> m) {
                return ops.createMap(((java.util.Map<String, Object>) m).entrySet().stream()
                        .map(e -> com.mojang.datafixers.util.Pair.of(ops.createString(e.getKey()), toOps(ops, e.getValue()))));
            }
            if (v instanceof java.util.List<?> l) return ops.createList(l.stream().map(o -> toOps(ops, o)));
            if (v instanceof String s) return ops.createString(s);
            if (v instanceof Boolean b) return ops.createBoolean(b);
            if (v instanceof Byte n) return ops.createByte(n);
            if (v instanceof Short n) return ops.createShort(n);
            if (v instanceof Integer n) return ops.createInt(n);
            if (v instanceof Long n) return ops.createLong(n);
            if (v instanceof Float n) return ops.createFloat(n);
            if (v instanceof Double n) return ops.createDouble(n);
            return net.minecraft.nbt.NbtOps.INSTANCE.convertTo(ops, net.minecraft.nbt.NbtBridge.fromFurnace(v));
        }

        static com.mojang.serialization.Codec<DataComponentPatch> codec() {
            return new com.mojang.serialization.Codec<>() {
                @Override
                public <T> com.mojang.serialization.DataResult<T> encode(DataComponentPatch input, com.mojang.serialization.DynamicOps<T> ops, T prefix) {
                    var support = org.domi.furnace.api.item.ItemSupport.get();
                    com.mojang.serialization.RecordBuilder<T> builder = ops.mapBuilder();
                    try {
                        if (isNbt(ops)) {
                            org.domi.furnace.api.nbt.NbtCompound nbt = support.encodeComponentPatch(input.set, input.removed);
                            for (String key : nbt.keys()) {
                                builder.add(ops.createString(key), net.minecraft.nbt.NbtOps.INSTANCE.convertTo(ops, net.minecraft.nbt.NbtBridge.fromFurnace(nbt.get(key))));
                            }
                        } else {
                            for (var e : support.encodeComponentPatchValues(input.set, input.removed).entrySet()) {
                                builder.add(ops.createString(e.getKey()), toOps(ops, e.getValue()));
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        String message = e.getMessage();
                        return com.mojang.serialization.DataResult.error(() -> message);
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
                    net.minecraft.nbt.Tag tag = ops.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, input);
                    var decoded = org.domi.furnace.api.item.ItemSupport.get()
                            .decodeComponentPatchPartial((org.domi.furnace.api.nbt.NbtCompound) net.minecraft.nbt.NbtBridge.toFurnace(tag));
                    var patch = decoded.patch();
                    var pair = com.mojang.datafixers.util.Pair.of(patch.isEmpty() ? EMPTY : new DataComponentPatch(patch.set(), patch.removed()), input);
                    if (decoded.ok()) return com.mojang.serialization.DataResult.success(pair);
                    // 바닐라 DispatchedMapCodec: 오류는 "; " 로 잇고 뒤에 " missed input: " 과 못 읽은 항목(받은 ops 의 값 그대로)을 붙이며, 읽은 항목은 부분 결과로 준다
                    java.util.Set<String> failedKeys = new java.util.HashSet<>(decoded.failedKeys());
                    java.util.stream.Stream.Builder<com.mojang.datafixers.util.Pair<T, T>> failed = java.util.stream.Stream.builder();
                    map.result().get().entries().forEach(e -> {
                        String key = ops.getStringValue(e.getFirst()).result().orElse(null);
                        if (key != null && failedKeys.contains(key)) failed.add(e);
                    });
                    String message = String.join("; ", decoded.errors()) + " missed input: " + ops.createMap(failed.build());
                    return com.mojang.serialization.DataResult.error(() -> message, pair);
                }

                @Override
                public String toString() { return "DataComponentPatch.CODEC[Furnace]"; }
            };
        }
    }
}
