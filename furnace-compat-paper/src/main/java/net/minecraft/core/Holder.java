package net.minecraft.core;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * 레지스트리 값 손잡이(Mojang 이름 shim, N0). 태그({@code is(TagKey)}·{@code tags()})는 지금 걸린 태그(데이터 팩 리로드 뒤 값)를 서버에 묻는다.
 * {@code unwrap()}(Either)·{@code canSerializeIn} 은 DataFixerUpper shim 이 없어 두지 않았다. toString 꼴 {@code Reference{ResourceKey[…]=…}}·{@code Direct{…}} 은 바닐라와 같다.
 */
public interface Holder<T> {

    T value();

    boolean isBound();

    boolean is(ResourceLocation location);

    boolean is(ResourceKey<T> key);

    boolean is(Predicate<ResourceKey<T>> predicate);

    boolean is(Holder<T> other);

    boolean is(TagKey<T> tag);

    Stream<TagKey<T>> tags();

    Optional<ResourceKey<T>> unwrapKey();

    Kind kind();

    default String getRegisteredName() { return unwrapKey().map(k -> k.location().toString()).orElse("[unregistered]"); }

    static <T> Holder<T> direct(T value) { return new Direct<>(value); }

    enum Kind { REFERENCE, DIRECT }

    record Direct<T>(T value) implements Holder<T> {

        @Override
        public boolean isBound() { return true; }

        @Override
        public boolean is(ResourceLocation location) { return false; }

        @Override
        public boolean is(ResourceKey<T> key) { return false; }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) { return false; }

        @Override
        public boolean is(Holder<T> other) { return value.equals(other.value()); }

        @Override
        public boolean is(TagKey<T> tag) { return false; }

        @Override
        public Stream<TagKey<T>> tags() { return Stream.of(); }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() { return Optional.empty(); }

        @Override
        public Kind kind() { return Kind.DIRECT; }

        @Override
        public String toString() { return "Direct{" + value + "}"; }
    }

    /** 레지스트리에 이름으로 매인 손잡이. Furnace 의 내장 레지스트리는 얼어 있어 항상 묶여 있다. */
    class Reference<T> implements Holder<T> {

        private final ResourceKey<T> key;
        private final T value;

        /** 호환 모듈 전용 — 레지스트리 shim 이 만든다. */
        public Reference(ResourceKey<T> key, T value) {
            this.key = key;
            this.value = value;
        }

        public ResourceKey<T> key() { return key; }

        @Override
        public T value() { return value; }

        @Override
        public boolean isBound() { return true; }

        @Override
        public boolean is(ResourceLocation location) { return key.location().equals(location); }

        @Override
        public boolean is(ResourceKey<T> other) { return key == other; }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) { return predicate.test(key); }

        @Override
        public boolean is(Holder<T> other) { return other.is(key); }

        @Override
        public boolean is(TagKey<T> tag) { return org.domi.furnace.compat.nms.NmsTags.isTagged(key, tag); }

        @Override
        public Stream<TagKey<T>> tags() { return org.domi.furnace.compat.nms.NmsTags.tagsOf(key); }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() { return Optional.of(key); }

        @Override
        public Kind kind() { return Kind.REFERENCE; }

        @Override
        public String toString() { return "Reference{" + key + "=" + value + "}"; }
    }
}
