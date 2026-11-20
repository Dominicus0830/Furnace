package org.domi.furnace.compat.nms;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 시험용 비 NBT {@code DynamicOps}: 값이 자바 객체(Map·List·String·Boolean·Number)다. JsonOps 처럼 {@code createBoolean} 이 불리언을 만들고(NbtOps 는
 * 바이트), 수는 만든 타입 그대로 둔다. 대역(src/dfu)과 진짜 DataFixerUpper 에서 같게 컴파일된다(둘 다 이 추상 메서드 집합).
 */
final class PlainOps implements DynamicOps<Object> {

    static final PlainOps INSTANCE = new PlainOps();

    private PlainOps() {}

    @Override
    public Object empty() { return Map.of(); }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, Object input) {
        if (input instanceof Map<?, ?> m) {
            return outOps.createMap(m.entrySet().stream().map(e -> Pair.of(outOps.createString((String) e.getKey()), convertTo(outOps, e.getValue()))));
        }
        if (input instanceof List<?> l) return outOps.createList(l.stream().map(o -> convertTo(outOps, o)));
        if (input instanceof String s) return outOps.createString(s);
        if (input instanceof Boolean b) return outOps.createBoolean(b);
        if (input instanceof Number n) return outOps.createNumeric(n);
        return outOps.empty();
    }

    @Override
    public DataResult<Number> getNumberValue(Object input) {
        if (input instanceof Number n) return DataResult.success(n);
        if (input instanceof Boolean b) return DataResult.success(b ? 1 : 0);
        return DataResult.error(() -> "Not a number: " + input);
    }

    @Override
    public Object createNumeric(Number i) { return i; }

    @Override
    public Object createBoolean(boolean value) { return value; }

    @Override
    public DataResult<Boolean> getBooleanValue(Object input) {
        if (input instanceof Boolean b) return DataResult.success(b);
        return getNumberValue(input).map(n -> n.byteValue() != 0);
    }

    @Override
    public DataResult<String> getStringValue(Object input) {
        return input instanceof String s ? DataResult.success(s) : DataResult.error(() -> "Not a string: " + input);
    }

    @Override
    public Object createString(String value) { return value; }

    @Override
    public DataResult<Object> mergeToList(Object list, Object value) {
        if (!(list instanceof List<?>) && !list.equals(empty())) return DataResult.error(() -> "mergeToList called with not a list: " + list);
        List<Object> out = new ArrayList<>(list instanceof List<?> l ? l : List.of());
        out.add(value);
        return DataResult.success(out);
    }

    @Override
    public DataResult<Object> mergeToMap(Object map, Object key, Object value) {
        if (!(map instanceof Map<?, ?>)) return DataResult.error(() -> "mergeToMap called with not a map: " + map);
        if (!(key instanceof String)) return DataResult.error(() -> "key is not a string: " + key);
        Map<Object, Object> out = new LinkedHashMap<>((Map<?, ?>) map);
        out.put(key, value);
        return DataResult.success(out);
    }

    @Override
    public DataResult<Stream<Pair<Object, Object>>> getMapValues(Object input) {
        if (input instanceof Map<?, ?> m) return DataResult.success(m.entrySet().stream().map(e -> Pair.<Object, Object>of(e.getKey(), e.getValue())));
        return DataResult.error(() -> "Not a map: " + input);
    }

    @Override
    public Object createMap(Stream<Pair<Object, Object>> map) {
        Map<Object, Object> out = new LinkedHashMap<>();
        map.forEach(p -> out.put(p.getFirst(), p.getSecond()));
        return out;
    }

    @Override
    public DataResult<Stream<Object>> getStream(Object input) {
        if (input instanceof List<?> l) return DataResult.success(l.stream().map(o -> (Object) o));
        return DataResult.error(() -> "Not a list: " + input);
    }

    @Override
    public Object createList(Stream<Object> input) { return input.toList(); }

    @Override
    public Object remove(Object input, String key) {
        if (!(input instanceof Map<?, ?> m)) return input;
        Map<Object, Object> out = new LinkedHashMap<>(m);
        out.remove(key);
        return out;
    }

    @Override
    public String toString() { return "PlainOps"; }
}
