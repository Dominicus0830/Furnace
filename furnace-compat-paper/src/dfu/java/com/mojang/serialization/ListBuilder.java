package com.mojang.serialization;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용): 진짜의 추상 메서드와 같은 서술자. */
public interface ListBuilder<T> {
    DynamicOps<T> ops();

    DataResult<T> build(T prefix);

    ListBuilder<T> add(T value);

    ListBuilder<T> add(DataResult<T> value);

    ListBuilder<T> withErrorsFrom(DataResult<?> result);

    ListBuilder<T> mapError(UnaryOperator<String> onError);

    final class Builder<T> implements ListBuilder<T> {
        private final DynamicOps<T> ops;
        private final List<T> values = new ArrayList<>();
        private String error;

        public Builder(DynamicOps<T> ops) { this.ops = ops; }

        @Override
        public DynamicOps<T> ops() { return ops; }

        @Override
        public ListBuilder<T> add(T value) {
            values.add(value);
            return this;
        }

        @Override
        public ListBuilder<T> add(DataResult<T> value) {
            if (value.isSuccess()) values.add(value.result().get());
            else withErrorsFrom(value);
            return this;
        }

        @Override
        public ListBuilder<T> withErrorsFrom(DataResult<?> result) {
            if (result.isError()) {
                String m = result.error().get().message();
                error = error == null ? m : error + "; " + m;
            }
            return this;
        }

        @Override
        public ListBuilder<T> mapError(UnaryOperator<String> onError) {
            if (error != null) error = onError.apply(error);
            return this;
        }

        @Override
        public DataResult<T> build(T prefix) {
            DataResult<T> merged = ops.mergeToList(prefix, values);
            if (error == null) return merged;
            String m = error;
            return DataResult.error(() -> m, merged.resultOrPartial().orElse(prefix));
        }
    }
}
