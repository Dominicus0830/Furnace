// 게으른 불변 목록 — 이벤트 시점에 원소 원본 참조 배열만 복사해 두고, API 형태로 바꾸는 일은 칸마다 처음 읽을 때 한 번 한다(T-033, O09-01 (b)).
//
// List.copyOf 로 만든 목록과 관측 결과가 같도록 만든다:
//   - 범위 안 get(i)·size()·isEmpty() 만 게으르다(그 칸만 변환).
//   - 그 밖의 모든 것(반복·equals·hashCode·toString·contains·subList·stream·바꾸기 시도·범위 밖 get 의 예외 …)은
//     칸을 모두 변환한 List.of(…) 사본에 맡긴다 — 예외 종류·메시지까지 List.copyOf 결과와 같다.
//   - get(i) 로 받은 객체와 반복으로 받은 객체는 같은 객체다(칸마다 처음 고정한 값을 사본도 쓴다).
// 원소 원본은 불변이어야 한다(서버 ItemStack 처럼). 배열은 받을 때 복사하므로 부른 쪽이 그 뒤에 배열을 고쳐도 상관없다.
// 변환 결과가 null 이면 NullPointerException(List.copyOf 가 null 원소를 거부하는 것과 같은 계약 — 다만 읽을 때 난다).
package org.domi.furnace.api.event;

import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * 원소를 처음 읽을 때 변환하는 불변 목록. {@link List#copyOf} 결과와 관측 결과가 같다(위 주석).
 *
 * @param <T> 원소 타입
 */
public final class LazyList<T> implements List<T>, RandomAccess, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle FULL;

    static {
        try {
            FULL = MethodHandles.lookup().findVarHandle(LazyList.class, "full", List.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final transient Object[] sources;
    private final transient Function<Object, ? extends T> convert;
    /** 칸마다 변환한 값(null = 아직). */
    private final transient Object[] done;
    @SuppressWarnings("unused")   // FULL 로만 만진다. null = 아직
    private transient volatile List<T> full;

    @SuppressWarnings("unchecked")
    private LazyList(Object[] sources, Function<?, ? extends T> convert) {
        this.sources = sources;
        this.convert = (Function<Object, ? extends T>) convert;
        this.done = new Object[sources.length];
    }

    /**
     * 원본 배열(복사함)과 칸 변환 함수. 변환은 그 칸을 처음 읽을 때 한다.
     *
     * @param sources 원소 원본 — 불변 값이어야 한다
     * @param convert 원본 → 원소. 원본만 보고 같은 결과를 내야 하며 null 을 돌려주면 안 된다
     */
    public static <S, T> LazyList<T> of(S[] sources, Function<? super S, ? extends T> convert) {
        return new LazyList<>(sources.clone(), Objects.requireNonNull(convert, "convert"));
    }

    /** 칸 i 의 값 — 처음이면 변환하고 고정한다. */
    @SuppressWarnings("unchecked")
    private T slot(int i) {
        Object v = SLOT.getVolatile(done, i);
        if (v == null) {
            T made = Objects.requireNonNull(convert.apply(sources[i]), "LazyList 변환 결과가 null");
            Object witness = SLOT.compareAndExchange(done, i, null, made);
            v = witness == null ? made : witness;
        }
        return (T) v;
    }

    /** 모든 칸을 변환한 List.of 사본(한 번 만들고 고정). */
    @SuppressWarnings("unchecked")
    private List<T> full() {
        List<T> f = (List<T>) FULL.getVolatile(this);
        if (f == null) {
            Object[] all = new Object[sources.length];
            for (int i = 0; i < all.length; i++) all[i] = slot(i);
            List<T> made = (List<T>) List.of(all);
            List<T> witness = (List<T>) FULL.compareAndExchange(this, null, made);
            f = witness == null ? made : witness;
        }
        return f;
    }

    /** 변환한 칸 수(시험·진단용). */
    public int resolvedCount() {
        int n = 0;
        for (int i = 0; i < done.length; i++) if (SLOT.getVolatile(done, i) != null) n++;
        return n;
    }

    // ------------------------------------------------------------------ 게으른 길

    @Override
    public int size() { return sources.length; }

    @Override
    public boolean isEmpty() { return sources.length == 0; }

    @Override
    public T get(int index) {
        if (index >= 0 && index < sources.length) return slot(index);
        return full().get(index);   // 범위 밖 — List.copyOf 결과와 같은 예외
    }

    // ------------------------------------------------------------------ 나머지는 사본에 맡긴다

    @Override public boolean contains(Object o) { return full().contains(o); }
    @Override public Iterator<T> iterator() { return full().iterator(); }
    @Override public Object[] toArray() { return full().toArray(); }
    @Override public <A> A[] toArray(A[] a) { return full().toArray(a); }
    @Override public <A> A[] toArray(IntFunction<A[]> generator) { return full().toArray(generator); }
    @Override public boolean add(T t) { return full().add(t); }
    @Override public boolean remove(Object o) { return full().remove(o); }
    @Override public boolean containsAll(Collection<?> c) { return full().containsAll(c); }
    @Override public boolean addAll(Collection<? extends T> c) { return full().addAll(c); }
    @Override public boolean addAll(int index, Collection<? extends T> c) { return full().addAll(index, c); }
    @Override public boolean removeAll(Collection<?> c) { return full().removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { return full().retainAll(c); }
    @Override public boolean removeIf(Predicate<? super T> filter) { return full().removeIf(filter); }
    @Override public void replaceAll(UnaryOperator<T> operator) { full().replaceAll(operator); }
    @Override public void sort(Comparator<? super T> c) { full().sort(c); }
    @Override public void clear() { full().clear(); }
    @Override public T set(int index, T element) { return full().set(index, element); }
    @Override public void add(int index, T element) { full().add(index, element); }
    @Override public T remove(int index) { return full().remove(index); }
    @Override public int indexOf(Object o) { return full().indexOf(o); }
    @Override public int lastIndexOf(Object o) { return full().lastIndexOf(o); }
    @Override public ListIterator<T> listIterator() { return full().listIterator(); }
    @Override public ListIterator<T> listIterator(int index) { return full().listIterator(index); }
    @Override public List<T> subList(int fromIndex, int toIndex) { return full().subList(fromIndex, toIndex); }
    @Override public Spliterator<T> spliterator() { return full().spliterator(); }
    @Override public Stream<T> stream() { return full().stream(); }
    @Override public Stream<T> parallelStream() { return full().parallelStream(); }
    @Override public void forEach(Consumer<? super T> action) { full().forEach(action); }
    @Override public void addFirst(T t) { full().addFirst(t); }
    @Override public void addLast(T t) { full().addLast(t); }
    @Override public T getFirst() { return full().getFirst(); }
    @Override public T getLast() { return full().getLast(); }
    @Override public T removeFirst() { return full().removeFirst(); }
    @Override public T removeLast() { return full().removeLast(); }
    @Override public List<T> reversed() { return full().reversed(); }

    @Override public boolean equals(Object o) { return o == this || full().equals(o); }
    @Override public int hashCode() { return full().hashCode(); }
    @Override public String toString() { return full().toString(); }

    /** 직렬화하면 다 변환한 List.of 사본이 된다(List.copyOf 결과를 직렬화한 것과 같다). */
    @Serial
    private Object writeReplace() { return full(); }
}
