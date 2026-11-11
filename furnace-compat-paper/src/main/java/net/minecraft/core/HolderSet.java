package net.minecraft.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.tags.TagKey;

/**
 * 손잡이 묶음(Mojang 이름 shim, N0). 바닐라 1.21.1 {@code jq}: 직접 묶음({@code DirectSet[…]})과 태그 묶음({@code NamedSet(TagKey[…])[…]}).
 * 태그 묶음의 내용은 지금 걸린 태그(데이터 팩 리로드 뒤 값)를 읽을 때마다 서버에서 가져온다. {@code unwrap}(Either)·{@code getRandomElement}·
 * {@code canSerializeIn} 은 DataFixerUpper·RandomSource shim 이 없어 두지 않았다.
 */
public interface HolderSet<T> extends Iterable<Holder<T>> {

    Stream<Holder<T>> stream();

    int size();

    Holder<T> get(int index);

    boolean contains(Holder<T> holder);

    Optional<TagKey<T>> unwrapKey();

    static <T> HolderSet<T> empty() { return new Direct<>(List.of()); }

    @SafeVarargs
    static <T> Direct<T> direct(Holder<T>... holders) { return new Direct<>(List.of(holders)); }

    static <T> Direct<T> direct(List<? extends Holder<T>> holders) { return new Direct<>(List.copyOf(holders)); }

    abstract class ListBacked<T> implements HolderSet<T> {

        protected abstract List<Holder<T>> contents();

        @Override
        public int size() { return contents().size(); }

        @Override
        public Iterator<Holder<T>> iterator() { return contents().iterator(); }

        @Override
        public Stream<Holder<T>> stream() { return contents().stream(); }

        @Override
        public Holder<T> get(int index) { return contents().get(index); }
    }

    final class Direct<T> extends ListBacked<T> {

        private final List<Holder<T>> contents;
        private Set<Holder<T>> contentsSet;

        Direct(List<Holder<T>> contents) { this.contents = contents; }

        @Override
        protected List<Holder<T>> contents() { return contents; }

        @Override
        public Optional<TagKey<T>> unwrapKey() { return Optional.empty(); }

        @Override
        public boolean contains(Holder<T> holder) {
            if (contentsSet == null) contentsSet = Set.copyOf(contents);
            return contentsSet.contains(holder);
        }

        @Override
        public String toString() { return "DirectSet[" + contents + "]"; }
    }

    /** 태그 묶음. {@code contains} 는 바닐라처럼 {@code holder.is(key)}. */
    class Named<T> extends ListBacked<T> {

        private final TagKey<T> key;
        private final Supplier<List<Holder<T>>> source;

        /** 호환 모듈 전용 — 레지스트리 shim 이 만든다. source 는 부를 때마다 지금 태그 내용을 준다. */
        public Named(TagKey<T> key, Supplier<List<Holder<T>>> source) {
            this.key = key;
            this.source = source;
        }

        public TagKey<T> key() { return key; }

        @Override
        protected List<Holder<T>> contents() { return source.get(); }

        @Override
        public Optional<TagKey<T>> unwrapKey() { return Optional.of(key); }

        @Override
        public boolean contains(Holder<T> holder) { return holder.is(key); }

        @Override
        public String toString() { return "NamedSet(" + key + ")[" + new ArrayList<>(contents()) + "]"; }
    }
}
