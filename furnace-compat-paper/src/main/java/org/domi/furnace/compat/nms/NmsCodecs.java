package org.domi.furnace.compat.nms;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/**
 * NMS shim 5e-2: 바닐라 코덱 몇 개를 진짜 DataFixerUpper {@code Codec} 으로 — {@code Registry.byNameCodec}·{@code holderByNameCodec},
 * {@code BlockState.CODEC}, {@code PalettedContainer.codecRW}·{@code codecRO}(디스크 청크 섹션의 {@code block_states}·{@code biomes}).
 *
 * <p>바닐라는 DFU 조합기({@code dispatch}·{@code RecordCodecBuilder}·{@code listOf}·{@code lenientOptionalFieldOf})로 짓는다. 호환 계층은 DFU 를 jar 에 싣지 않고
 * 컴파일 대역만 있어 조합기를 쓸 수 없으므로, 그 조합기들이 내는 결과(값·부분 결과·오류 문구·쓰는 키 순서)를 손으로 같게 냈다. 근거는 DFU 8.0.16 의
 * {@code KeyDispatchCodec}·{@code ListCodec}·{@code FieldDecoder}·{@code OptionalFieldCodec}·{@code DataResult.Error.flatMap} 과 Paper 1.21.1 의
 * {@code PalettedContainer.codec}·{@code unpack}·{@code pack}. 맵을 줄이는 ops({@code compressMaps})는 바닐라 저장·패킷에 쓰이지 않아 아직 없다(UnsupportedNmsCall).
 *
 * <p>DFU 타입을 쓰므로 {@link NmsDfu#visible()} 가 참일 때만 로드한다.
 */
public final class NmsCodecs {

    private NmsCodecs() {}

    // ------------------------------------------------------------------ 레지스트리 이름 코덱

    /** 바닐라 {@code Registry.holderByNameCodec}: 문자열 이름 ↔ 등록된 손잡이. */
    public static <E> Codec<Holder<E>> holderByName(Registry<E> registry) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
                return lookup(registry, ops, input).map(ref -> Pair.of((Holder<E>) ref, ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
                if (!(input instanceof Holder.Reference<E> ref)) return DataResult.error(() -> "Unregistered holder in " + registry.key() + ": " + input);
                return ops.mergeToPrimitive(prefix, ops.createString(ref.key().location().toString()));
            }

            @Override
            public String toString() { return "holderByNameCodec[" + registry.key() + "]"; }
        };
    }

    /** 바닐라 {@code Registry.byNameCodec}: 문자열 이름 ↔ 값. */
    public static <E> Codec<E> byName(Registry<E> registry) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {
                return lookup(registry, ops, input).map(ref -> Pair.of(ref.value(), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
                Holder<E> h = registry.wrapAsHolder(input);
                if (!(h instanceof Holder.Reference<E> ref)) return DataResult.error(() -> "Unregistered holder in " + registry.key() + ": " + h);
                return ops.mergeToPrimitive(prefix, ops.createString(ref.key().location().toString()));
            }

            @Override
            public String toString() { return "byNameCodec[" + registry.key() + "]"; }
        };
    }

    /** {@code ResourceLocation.CODEC} 로 읽고(바닐라 {@code ResourceLocation.read} 문구) 레지스트리에서 찾는다(바닐라 {@code referenceHolderWithLifecycle}). */
    private static <E, T> DataResult<Holder.Reference<E>> lookup(Registry<E> registry, DynamicOps<T> ops, T input) {
        DataResult<String> s = ops.getStringValue(input);
        if (s.isError()) {
            String m = s.error().get().message();
            return DataResult.error(() -> m);
        }
        String id = s.result().get();
        ResourceLocation loc;
        try {
            loc = ResourceLocation.parse(id);
        } catch (ResourceLocationException e) {
            String m = e.getMessage();
            return DataResult.error(() -> "Not a valid resource location: " + id + " " + m);
        }
        Optional<Holder.Reference<E>> h = registry.getHolder(loc);
        if (h.isEmpty()) return DataResult.error(() -> "Unknown registry key in " + registry.key() + ": " + loc);
        return DataResult.success(h.get());
    }

    // ------------------------------------------------------------------ BlockState.CODEC

    /**
     * 바닐라 {@code BlockState.CODEC = codec(BLOCK.byNameCodec(), Block::defaultBlockState)} — {@code Name} 으로 블록을 고르고, 속성이 있는 블록은
     * {@code Properties} 를 너그럽게(없거나 맵이 아니면 기본 상태, 속성마다 틀린 값은 그 속성의 기본값) 읽는다. 쓸 때는 {@code Properties} 다음 {@code Name}.
     */
    public static Codec<BlockState> blockState() {
        return new Codec<>() {
            /** 블록 레지스트리는 처음 쓸 때 — BlockState 초기화 중에 BuiltInRegistries 를 초기화하면 두 스레드가 서로의 클래스 초기화를 기다릴 수 있다. */
            private volatile Codec<Block> name;

            private Codec<Block> name() {
                Codec<Block> n = name;
                if (n == null) name = n = byName(BuiltInRegistries.BLOCK);
                return n;
            }

            @Override
            public <T> DataResult<Pair<BlockState, T>> decode(DynamicOps<T> ops, T input) {
                noCompression(ops, "BlockState.CODEC");
                DataResult<MapLike<T>> map = ops.getMap(input);
                if (map.isError()) return relay(map);
                MapLike<T> m = map.result().get();
                T elementName = m.get("Name");
                if (elementName == null) return DataResult.error(() -> "Input does not contain a key [Name]: " + m);
                DataResult<Pair<Block, T>> block = name().decode(ops, elementName);
                if (block.isError()) return relay(block);
                BlockState def = block.result().get().getFirst().defaultBlockState();
                if (def.getValues().isEmpty()) return DataResult.success(Pair.of(def, input));   // MapCodec.unit
                T props = m.get("Properties");
                if (props == null) return DataResult.success(Pair.of(def, input));
                DataResult<MapLike<T>> pm = ops.getMap(props);
                if (pm.isError()) return DataResult.success(Pair.of(def, input));   // lenientOptionalFieldOf: 못 읽으면 없는 것
                DataResult<BlockState> state = NmsStateCodec.<BlockState>direct(def).decode(ops, pm.result().get());
                return DataResult.success(Pair.of(state.result().orElse(def), input));
            }

            @Override
            public <T> DataResult<T> encode(BlockState input, DynamicOps<T> ops, T prefix) {
                noCompression(ops, "BlockState.CODEC");
                RecordBuilder<T> b = ops.mapBuilder();
                if (!input.getValues().isEmpty()) {
                    DataResult<T> props = NmsStateCodec.<BlockState>direct(input).encode(input, ops, ops.mapBuilder()).build(ops.empty());
                    b = b.add(ops.createString("Properties"), props);
                }
                b = b.add(ops.createString("Name"), name().encode(input.getBlock(), ops, ops.empty()));
                return b.build(prefix);
            }

            @Override
            public String toString() { return "BlockState.CODEC[Furnace]"; }
        };
    }

    // ------------------------------------------------------------------ PalettedContainer.codecRW·codecRO

    /**
     * Paper {@code PalettedContainer.codec}: {@code {palette: [항목…], data: long 배열}}. 항목 하나를 못 읽으면 그 자리는 기본값으로 두고(부분 결과)
     * 오류 문구를 "(원문 -> using default)" 로 감싸 뒤 항목부터 "; " 로 잇는다({@code orElsePartial}·{@code ListCodec}, 바닐라 1.21.1 jar·DFU 8.0.16 으로 확인). {@code data} 는 없거나 못 읽으면 없는 것({@code lenientOptionalFieldOf}).
     * 팔레트 크기에 비트가 필요한데 {@code data} 가 없으면 "Missing values for non-zero storage", 길이가 틀리면 "Failed to read PalettedContainer: …".
     */
    @SuppressWarnings("unchecked")
    public static <T, C extends PalettedContainerRO<T>> Codec<C> paletted(IdMap<T> registry, Codec<T> entry, PalettedContainer.Strategy strategy, T defaultValue,
                                                                          String what) {
        return new Codec<>() {
            @Override
            public <O> DataResult<Pair<C, O>> decode(DynamicOps<O> ops, O input) {
                noCompression(ops, what);
                DataResult<MapLike<O>> map = ops.getMap(input);
                if (map.isError()) return relay(map);
                MapLike<O> m = map.result().get();
                // palette: fieldOf("palette") + listOf(entry.mapResult(orElsePartial(defaultValue)))
                O rawPalette = m.get("palette");
                if (rawPalette == null) return DataResult.error(() -> "No key palette in " + m);
                DataResult<Consumer<Consumer<O>>> list = ops.getList(rawPalette);
                if (list.isError()) return relay(list);
                List<T> entries = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                list.result().get().accept(v -> {
                    DataResult<Pair<T, O>> r = entry.decode(ops, v);
                    if (r.isError()) {
                        // ExtraCodecs.orElsePartial: 부분 결과가 없으면 문구를 "(… -> using default)" 로 감싸고 기본값을 부분 결과로, 있으면 그대로
                        Optional<T> partial = r.resultOrPartial().map(Pair::getFirst);
                        String message = r.error().get().message();
                        errors.add(partial.isPresent() ? message : "(" + message + " -> using default)");
                        entries.add(partial.orElse(defaultValue));
                    } else {
                        entries.add(r.result().get().getFirst());
                    }
                });
                // data: lenientOptionalFieldOf("data") — Codec.LONG_STREAM
                Optional<LongStream> data = Optional.empty();
                O rawData = m.get("data");
                if (rawData != null) data = ops.getLongStream(rawData).result();
                Object made = PalettedContainer.furnaceUnpack(registry, strategy, entries, data);
                DataResult<C> unpacked = made instanceof String message ? DataResult.error(() -> message) : DataResult.success((C) made);
                if (errors.isEmpty()) return unpacked.map(c -> Pair.of(c, input));
                // ListCodec 은 항목 오류 문구를 뒤 항목부터 잇는다. 앞 단계가 부분 결과가 있는 오류면 DFU 는 부분 결과로 다음 단계를 돌리고
                // 문구를 잇는다(DataResult.Error.flatMap)
                String head = String.join("; ", errors.reversed());
                if (unpacked.isError()) {
                    String tail = unpacked.error().get().message();
                    return DataResult.error(() -> head + "; " + tail);
                }
                C c = unpacked.result().get();
                return DataResult.error(() -> head, Pair.of(c, input));
            }

            @Override
            public <O> DataResult<O> encode(C input, DynamicOps<O> ops, O prefix) {
                noCompression(ops, what);
                PalettedContainerRO.PackedData<T> packed = input.pack(registry, strategy);
                ListBuilder<O> lb = ops.listBuilder();
                for (T e : packed.paletteEntries()) lb.add(entry.encodeStart(ops, e));
                RecordBuilder<O> b = ops.mapBuilder();
                b = b.add(ops.createString("palette"), lb.build(ops.empty()));
                if (packed.storage().isPresent()) b = b.add(ops.createString("data"), DataResult.success(ops.createLongList(packed.storage().get())));
                return b.build(prefix);
            }

            @Override
            public String toString() { return what + "[Furnace]"; }
        };
    }

    // ------------------------------------------------------------------ 공용

    @SuppressWarnings("unchecked")
    private static <A, B> DataResult<B> relay(DataResult<A> error) {
        String m = error.error().get().message();
        return DataResult.error(() -> m);
    }

    private static void noCompression(DynamicOps<?> ops, String what) {
        if (ops.compressMaps()) {
            throw new UnsupportedNmsCall(what + " with compressed maps", "compressed map ops are not used by vanilla saves or packets and are not supported yet");
        }
    }
}
