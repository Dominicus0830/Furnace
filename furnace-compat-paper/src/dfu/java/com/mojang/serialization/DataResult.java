package com.mojang.serialization;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * DataFixerUpper 8.0.16 대역(컴파일·시험 전용). 진짜처럼 인터페이스이고 {@code Error} 는 최종 클래스(진짜는 record)라 같은 바이트코드로 링크된다.
 * 호환 계층이 쓰는 멤버만, 오류 결과는 문구와 (있으면) 일부 값을 쥔다.
 */
public interface DataResult<R> {

    static <R> DataResult<R> success(R result) { return new Success<>(result); }

    static <R> DataResult<R> error(Supplier<String> message) { return new Error<>(message, Optional.empty()); }

    static <R> DataResult<R> error(Supplier<String> message, R partial) { return new Error<>(message, Optional.of(partial)); }

    /** 성공했을 때의 값(오류 결과는 빈 값 — 일부 값이 있어도 비어 있다). */
    Optional<R> result();

    /** 오류 결과(성공이면 빈 값). */
    Optional<Error<R>> error();

    boolean isSuccess();

    default boolean isError() { return !isSuccess(); }

    /** 성공이거나 일부 값이 있는 오류일 때의 값. */
    Optional<R> resultOrPartial();

    <E extends Throwable> R getOrThrow(Function<String, E> onError) throws E;

    default R getOrThrow() { return getOrThrow(IllegalStateException::new); }

    <T> DataResult<T> map(Function<? super R, ? extends T> function);

    <R2> DataResult<R2> flatMap(Function<? super R, ? extends DataResult<R2>> function);

    DataResult<R> mapError(UnaryOperator<String> function);

    record Success<R>(R value) implements DataResult<R> {
        @Override
        public Optional<R> result() { return Optional.of(value); }

        @Override
        public Optional<Error<R>> error() { return Optional.empty(); }

        @Override
        public boolean isSuccess() { return true; }

        @Override
        public Optional<R> resultOrPartial() { return Optional.of(value); }

        @Override
        public <E extends Throwable> R getOrThrow(Function<String, E> onError) { return value; }

        @Override
        public <T> DataResult<T> map(Function<? super R, ? extends T> function) { return new Success<>(function.apply(value)); }

        @Override
        public <R2> DataResult<R2> flatMap(Function<? super R, ? extends DataResult<R2>> function) { return function.apply(value); }

        @Override
        public DataResult<R> mapError(UnaryOperator<String> function) { return this; }

        @Override
        public String toString() { return "DataResult.Success['" + value + "']"; }
    }

    final class Error<R> implements DataResult<R> {
        private final Supplier<String> message;
        private final Optional<R> partial;

        Error(Supplier<String> message, Optional<R> partial) {
            this.message = message;
            this.partial = partial;
        }

        /** 진짜 record 의 message() 와 같다. */
        public String message() { return message.get(); }

        public Optional<R> partialValue() { return partial; }

        @Override
        public Optional<R> result() { return Optional.empty(); }

        @Override
        public Optional<Error<R>> error() { return Optional.of(this); }

        @Override
        public boolean isSuccess() { return false; }

        @Override
        public Optional<R> resultOrPartial() { return partial; }

        @Override
        public <E extends Throwable> R getOrThrow(Function<String, E> onError) throws E { throw onError.apply(message()); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<T> map(Function<? super R, ? extends T> function) {
            return new Error<>(message, partial.map(v -> (T) function.apply(v)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R2> DataResult<R2> flatMap(Function<? super R, ? extends DataResult<R2>> function) { return new Error<>(message, Optional.empty()); }

        @Override
        public DataResult<R> mapError(UnaryOperator<String> function) { return new Error<>(() -> function.apply(message.get()), partial); }

        @Override
        public String toString() { return "DataResult.Error['" + message() + "']"; }
    }
}
