package com.mojang.serialization;

import com.mojang.datafixers.util.Pair;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜 인터페이스의 공개 메서드를 모두 같은 서술자로 가졌다(DelegatingOps·NbtOps 가 덮어쓰는 것들).
 * 기본 메서드의 본문은 이 호환 계층 시험이 부르는 만큼만 진짜와 같게 썼다 — 나머지 조합 도구(withEncoder 등)는 진짜 라이브러리에만 있다.
 */
public interface DynamicOps<T> {
    T empty();

    default T emptyMap() { return createMap(Stream.empty()); }

    default T emptyList() { return createList(Stream.empty()); }

    <U> U convertTo(DynamicOps<U> outOps, T input);

    DataResult<Number> getNumberValue(T input);

    default Number getNumberValue(T input, Number defaultValue) { return getNumberValue(input).result().orElse(defaultValue); }

    T createNumeric(Number i);

    default T createByte(byte value) { return createNumeric(value); }

    default T createShort(short value) { return createNumeric(value); }

    default T createInt(int value) { return createNumeric(value); }

    default T createLong(long value) { return createNumeric(value); }

    default T createFloat(float value) { return createNumeric(value); }

    default T createDouble(double value) { return createNumeric(value); }

    default DataResult<Boolean> getBooleanValue(T input) { return getNumberValue(input).map(number -> number.byteValue() != 0); }

    default T createBoolean(boolean value) { return createByte((byte) (value ? 1 : 0)); }

    DataResult<String> getStringValue(T input);

    T createString(String value);

    DataResult<T> mergeToList(T list, T value);

    default DataResult<T> mergeToList(T list, List<T> values) {
        DataResult<T> result = DataResult.success(list);
        for (T value : values) result = result.flatMap(r -> mergeToList(r, value));
        return result;
    }

    DataResult<T> mergeToMap(T map, T key, T value);

    default DataResult<T> mergeToMap(T map, Map<T, T> values) {
        return mergeToMap(map, MapLike.forMap(values, this));
    }

    default DataResult<T> mergeToMap(T map, MapLike<T> values) {
        return values.entries().reduce(DataResult.success(map), (r, entry) -> r.flatMap(m -> mergeToMap(m, entry.getFirst(), entry.getSecond())), (a, b) -> a);
    }

    default DataResult<T> mergeToPrimitive(T prefix, T value) {
        return prefix.equals(empty()) ? DataResult.success(value) : DataResult.error(() -> "Do not know how to append a primitive value " + value + " to " + prefix, value);
    }

    DataResult<Stream<Pair<T, T>>> getMapValues(T input);

    default DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T input) {
        return getMapValues(input).map(s -> c -> s.forEach(p -> c.accept(p.getFirst(), p.getSecond())));
    }

    T createMap(Stream<Pair<T, T>> map);

    default DataResult<MapLike<T>> getMap(T input) {
        return getMapValues(input).flatMap(s -> {
            Map<T, T> m = new java.util.LinkedHashMap<>();
            s.forEach(p -> m.put(p.getFirst(), p.getSecond()));
            return DataResult.success(MapLike.forMap(m, this));
        });
    }

    default T createMap(Map<T, T> map) {
        return createMap(map.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())));
    }

    DataResult<Stream<T>> getStream(T input);

    default DataResult<Consumer<Consumer<T>>> getList(T input) { return getStream(input).map(s -> s::forEach); }

    T createList(Stream<T> input);

    default DataResult<ByteBuffer> getByteBuffer(T input) { return DataResult.error(() -> "Not a byte list: " + input); }

    default T createByteList(ByteBuffer input) {
        return createList(java.util.stream.IntStream.range(0, input.capacity()).mapToObj(i -> createByte(input.get(i))));
    }

    default DataResult<IntStream> getIntStream(T input) { return DataResult.error(() -> "Not an int list: " + input); }

    default T createIntList(IntStream input) { return createList(input.mapToObj(this::createInt)); }

    default DataResult<LongStream> getLongStream(T input) { return DataResult.error(() -> "Not a long list: " + input); }

    default T createLongList(LongStream input) { return createList(input.mapToObj(this::createLong)); }

    T remove(T input, String key);

    default boolean compressMaps() { return false; }

    default DataResult<T> get(T input, String key) {
        return getGeneric(input, createString(key));
    }

    default DataResult<T> getGeneric(T input, T key) {
        return getMap(input).flatMap(m -> {
            T v = m.get(key);
            return v == null ? DataResult.error(() -> "No element " + key + " in the map " + input) : DataResult.success(v);
        });
    }

    default T set(T input, String key, T value) { return mergeToMap(input, createString(key), value).result().orElse(input); }

    default T update(T input, String key, java.util.function.Function<T, T> function) {
        return get(input, key).map(v -> set(input, key, function.apply(v))).result().orElse(input);
    }

    default T updateGeneric(T input, T key, java.util.function.Function<T, T> function) {
        return getGeneric(input, key).flatMap(v -> mergeToMap(input, key, function.apply(v))).result().orElse(input);
    }

    default ListBuilder<T> listBuilder() { return new ListBuilder.Builder<>(this); }

    default RecordBuilder<T> mapBuilder() { return new RecordBuilder.MapBuilder<>(this); }

    default <U> U convertList(DynamicOps<U> outOps, T input) {
        return outOps.createList(getStream(input).result().orElse(Stream.empty()).map(e -> convertTo(outOps, e)));
    }

    default <U> U convertMap(DynamicOps<U> outOps, T input) {
        return outOps.createMap(getMapValues(input).result().orElse(Stream.empty()).map(e -> Pair.of(convertTo(outOps, e.getFirst()), convertTo(outOps, e.getSecond()))));
    }
}
