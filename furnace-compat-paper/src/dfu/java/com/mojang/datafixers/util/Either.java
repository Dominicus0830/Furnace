package com.mojang.datafixers.util;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** DataFixerUpper 8.0.16 대역(컴파일·시험 전용 — jar 에 넣지 않는다): 이 shim 이 쓰는 멤버만, 진짜와 같은 서술자({@code App} 상속은 뺐다). */
public abstract class Either<L, R> {

    private Either() {}

    public abstract <C, D> Either<C, D> mapBoth(Function<? super L, ? extends C> f1, Function<? super R, ? extends D> f2);

    public abstract <T> T map(Function<? super L, ? extends T> l, Function<? super R, ? extends T> r);

    public abstract Either<L, R> ifLeft(Consumer<? super L> consumer);

    public abstract Either<L, R> ifRight(Consumer<? super R> consumer);

    public abstract Optional<L> left();

    public abstract Optional<R> right();

    public <T> Either<T, R> mapLeft(Function<? super L, ? extends T> l) { return map(t -> left(l.apply(t)), Either::right); }

    public <T> Either<L, T> mapRight(Function<? super R, ? extends T> r) { return map(Either::left, t -> right(r.apply(t))); }

    public static <L, R> Either<L, R> left(L value) { return new Left<>(value); }

    public static <L, R> Either<L, R> right(R value) { return new Right<>(value); }

    public L orThrow() {
        return map(l -> l, r -> {
            if (r instanceof Throwable t) throw new RuntimeException(t);
            throw new RuntimeException(r.toString());
        });
    }

    public Either<R, L> swap() { return map(Either::right, Either::left); }

    private static final class Left<L, R> extends Either<L, R> {
        private final L value;

        Left(L value) { this.value = value; }

        @Override public <C, D> Either<C, D> mapBoth(Function<? super L, ? extends C> f1, Function<? super R, ? extends D> f2) { return new Left<>(f1.apply(value)); }
        @Override public <T> T map(Function<? super L, ? extends T> l, Function<? super R, ? extends T> r) { return l.apply(value); }
        @Override public Either<L, R> ifLeft(Consumer<? super L> consumer) { consumer.accept(value); return this; }
        @Override public Either<L, R> ifRight(Consumer<? super R> consumer) { return this; }
        @Override public Optional<L> left() { return Optional.of(value); }
        @Override public Optional<R> right() { return Optional.empty(); }
        @Override public String toString() { return "Left[" + value + "]"; }
    }

    private static final class Right<L, R> extends Either<L, R> {
        private final R value;

        Right(R value) { this.value = value; }

        @Override public <C, D> Either<C, D> mapBoth(Function<? super L, ? extends C> f1, Function<? super R, ? extends D> f2) { return new Right<>(f2.apply(value)); }
        @Override public <T> T map(Function<? super L, ? extends T> l, Function<? super R, ? extends T> r) { return r.apply(value); }
        @Override public Either<L, R> ifLeft(Consumer<? super L> consumer) { return this; }
        @Override public Either<L, R> ifRight(Consumer<? super R> consumer) { consumer.accept(value); return this; }
        @Override public Optional<L> left() { return Optional.empty(); }
        @Override public Optional<R> right() { return Optional.of(value); }
        @Override public String toString() { return "Right[" + value + "]"; }
    }
}
