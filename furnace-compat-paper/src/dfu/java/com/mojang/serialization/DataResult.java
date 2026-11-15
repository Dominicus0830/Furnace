package com.mojang.serialization;

/** DataFixerUpper 대역(컴파일·시험 전용). 진짜는 error 결과가 오류 문구를 품는다 — 대역은 문구만 쥔다. */
public interface DataResult<R> {

    static <R> DataResult<R> error(java.util.function.Supplier<String> message) {
        return new DataResult<>() {
            @Override
            public String toString() { return "DataResult.Error['" + message.get() + "']"; }
        };
    }
}
