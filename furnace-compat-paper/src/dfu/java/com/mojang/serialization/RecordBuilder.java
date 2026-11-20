package com.mojang.serialization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜의 추상 메서드와 같은 서술자, {@link MapBuilder} 는 진짜처럼 mergeToMap 으로 만든다. */
public interface RecordBuilder<T> {
    DynamicOps<T> ops();

    RecordBuilder<T> add(T key, T value);

    RecordBuilder<T> add(T key, DataResult<T> value);

    RecordBuilder<T> add(DataResult<T> key, DataResult<T> value);

    RecordBuilder<T> withErrorsFrom(DataResult<?> result);

    RecordBuilder<T> setLifecycle(Lifecycle lifecycle);

    RecordBuilder<T> mapError(UnaryOperator<String> onError);

    DataResult<T> build(T prefix);

    default RecordBuilder<T> add(String key, T value) { return add(ops().createString(key), value); }

    final class MapBuilder<T> implements RecordBuilder<T> {
        private final DynamicOps<T> ops;
        private final Map<T, T> entries = new LinkedHashMap<>();
        private String error;

        public MapBuilder(DynamicOps<T> ops) { this.ops = ops; }

        @Override
        public DynamicOps<T> ops() { return ops; }

        @Override
        public RecordBuilder<T> add(T key, T value) {
            entries.put(key, value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(T key, DataResult<T> value) { return add(DataResult.success(key), value); }

        @Override
        public RecordBuilder<T> add(DataResult<T> key, DataResult<T> value) {
            var k = key.result();
            var v = value.result();
            if (k.isPresent() && v.isPresent()) entries.put(k.get(), v.get());
            else withErrorsFrom(key).withErrorsFrom(value);
            return this;
        }

        @Override
        public RecordBuilder<T> withErrorsFrom(DataResult<?> result) {
            if (result.isError()) {
                String m = result.error().get().message();
                error = error == null ? m : error + "; " + m;
            }
            return this;
        }

        @Override
        public RecordBuilder<T> setLifecycle(Lifecycle lifecycle) { return this; }

        @Override
        public RecordBuilder<T> mapError(UnaryOperator<String> onError) {
            if (error != null) error = onError.apply(error);
            return this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            DataResult<T> merged = ops.mergeToMap(prefix, entries);
            if (error == null) return merged;
            String m = error;
            return DataResult.error(() -> m, merged.resultOrPartial().orElse(prefix));
        }
    }
}
