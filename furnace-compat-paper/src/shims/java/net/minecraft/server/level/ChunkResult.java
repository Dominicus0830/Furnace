package net.minecraft.server.level;

/** 청크 얻기 결과(Mojang 이름 shim, N2b-6) — 바닐라 그대로: 성공이면 값, 실패면 오류 문구. */
interface ChunkResult {

    static <T> ChunkResult<T> of(T value) { return new Success<>(value); }

    static <T> ChunkResult<T> error(String message) { return error(() -> message); }

    static <T> ChunkResult<T> error(java.util.function.Supplier<String> message) { return new Fail<>(message); }

    boolean isSuccess();

    T orElse(T other);

    static <R> R orElse(ChunkResult<? extends R> result, R other) {
        R r = result.orElse(null);
        return r != null ? r : other;
    }

    String getError();

    ChunkResult<T> ifSuccess(java.util.function.Consumer<T> action);

    <R> ChunkResult<R> map(java.util.function.Function<T, R> f);

    <E extends Throwable> T orElseThrow(java.util.function.Supplier<E> error) throws E;

    record Success<T>(T value) implements ChunkResult<T> {
        @Override public boolean isSuccess() { return true; }
        @Override public T orElse(T other) { return value; }
        @Override public String getError() { return null; }
        @Override public ChunkResult<T> ifSuccess(java.util.function.Consumer<T> action) { action.accept(value); return this; }
        @Override public <R> ChunkResult<R> map(java.util.function.Function<T, R> f) { return new Success<>(f.apply(value)); }
        @Override public <E extends Throwable> T orElseThrow(java.util.function.Supplier<E> error) { return value; }
    }

    record Fail<T>(java.util.function.Supplier<String> error) implements ChunkResult<T> {
        @Override public boolean isSuccess() { return false; }
        @Override public T orElse(T other) { return other; }
        @Override public String getError() { return error.get(); }
        @Override public ChunkResult<T> ifSuccess(java.util.function.Consumer<T> action) { return this; }
        @Override public <R> ChunkResult<R> map(java.util.function.Function<T, R> f) { return new Fail<>(error); }
        @Override public <E extends Throwable> T orElseThrow(java.util.function.Supplier<E> error) throws E { throw error.get(); }
    }
}
