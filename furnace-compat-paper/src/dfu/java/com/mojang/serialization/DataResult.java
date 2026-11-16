package com.mojang.serialization;

/** DataFixerUpper 대역(컴파일·시험 전용). 진짜는 error 결과가 오류 문구를 품는다 — 대역은 문구만 쥔다. */
public interface DataResult<R> {

    static <R> DataResult<R> success(R result) {
        return new DataResult<>() {
            @Override
            public java.util.Optional<R> result() { return java.util.Optional.of(result); }

            @Override
            public String toString() { return "DataResult.Success['" + result + "']"; }
        };
    }

    /** 성공했을 때의 값(오류 결과는 빈 값). */
    default java.util.Optional<R> result() { return java.util.Optional.empty(); }

    static <R> DataResult<R> error(java.util.function.Supplier<String> message) {
        return new DataResult<>() {
            @Override
            public String toString() { return "DataResult.Error['" + message.get() + "']"; }
        };
    }
}
