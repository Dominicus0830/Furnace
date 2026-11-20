package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.ListBuilder;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * 연산을 모두 안쪽 연산에 넘기는 감싸개(Mojang 이름 shim, T-200 — 바닐라 1.21.1 {@code DelegatingOps} 와 같은 메서드 목록, {@code listBuilder}·
 * {@code mapBuilder} 는 자기 자신을 감싼다). DataFixerUpper 가 있어야 올라간다.
 */
public abstract class DelegatingOps<T> implements DynamicOps<T> {

    protected final DynamicOps<T> delegate;

    protected DelegatingOps(DynamicOps<T> delegate) { this.delegate = delegate; }

    @Override
    public T empty() { return delegate.empty(); }

    @Override
    public T emptyMap() { return delegate.emptyMap(); }

    @Override
    public T emptyList() { return delegate.emptyList(); }

    @Override
    public <U> U convertTo(DynamicOps<U> out, T input) { return delegate.convertTo(out, input); }

    @Override
    public DataResult<Number> getNumberValue(T input) { return delegate.getNumberValue(input); }

    @Override
    public T createNumeric(Number i) { return delegate.createNumeric(i); }

    @Override
    public T createByte(byte v) { return delegate.createByte(v); }

    @Override
    public T createShort(short v) { return delegate.createShort(v); }

    @Override
    public T createInt(int v) { return delegate.createInt(v); }

    @Override
    public T createLong(long v) { return delegate.createLong(v); }

    @Override
    public T createFloat(float v) { return delegate.createFloat(v); }

    @Override
    public T createDouble(double v) { return delegate.createDouble(v); }

    @Override
    public DataResult<Boolean> getBooleanValue(T input) { return delegate.getBooleanValue(input); }

    @Override
    public T createBoolean(boolean v) { return delegate.createBoolean(v); }

    @Override
    public DataResult<String> getStringValue(T input) { return delegate.getStringValue(input); }

    @Override
    public T createString(String v) { return delegate.createString(v); }

    @Override
    public DataResult<T> mergeToList(T list, T value) { return delegate.mergeToList(list, value); }

    @Override
    public DataResult<T> mergeToList(T list, List<T> values) { return delegate.mergeToList(list, values); }

    @Override
    public DataResult<T> mergeToMap(T map, T key, T value) { return delegate.mergeToMap(map, key, value); }

    @Override
    public DataResult<T> mergeToMap(T map, MapLike<T> values) { return delegate.mergeToMap(map, values); }

    @Override
    public DataResult<T> mergeToMap(T map, Map<T, T> values) { return delegate.mergeToMap(map, values); }

    @Override
    public DataResult<T> mergeToPrimitive(T prefix, T value) { return delegate.mergeToPrimitive(prefix, value); }

    @Override
    public DataResult<Stream<Pair<T, T>>> getMapValues(T input) { return delegate.getMapValues(input); }

    @Override
    public DataResult<Consumer<BiConsumer<T, T>>> getMapEntries(T input) { return delegate.getMapEntries(input); }

    @Override
    public T createMap(Map<T, T> map) { return delegate.createMap(map); }

    @Override
    public T createMap(Stream<Pair<T, T>> map) { return delegate.createMap(map); }

    @Override
    public DataResult<MapLike<T>> getMap(T input) { return delegate.getMap(input); }

    @Override
    public DataResult<Stream<T>> getStream(T input) { return delegate.getStream(input); }

    @Override
    public DataResult<Consumer<Consumer<T>>> getList(T input) { return delegate.getList(input); }

    @Override
    public T createList(Stream<T> input) { return delegate.createList(input); }

    @Override
    public DataResult<ByteBuffer> getByteBuffer(T input) { return delegate.getByteBuffer(input); }

    @Override
    public T createByteList(ByteBuffer input) { return delegate.createByteList(input); }

    @Override
    public DataResult<IntStream> getIntStream(T input) { return delegate.getIntStream(input); }

    @Override
    public T createIntList(IntStream input) { return delegate.createIntList(input); }

    @Override
    public DataResult<LongStream> getLongStream(T input) { return delegate.getLongStream(input); }

    @Override
    public T createLongList(LongStream input) { return delegate.createLongList(input); }

    @Override
    public T remove(T input, String key) { return delegate.remove(input, key); }

    @Override
    public boolean compressMaps() { return delegate.compressMaps(); }

    @Override
    public ListBuilder<T> listBuilder() { return new ListBuilder.Builder<>(this); }

    @Override
    public RecordBuilder<T> mapBuilder() { return new RecordBuilder.MapBuilder<>(this); }
}
