package ca.spottedleaf.moonrise.common.set;

import java.util.Collection;

/**
 * Paper(moonrise) 의 작은 열거형 집합 — 비트 하나가 상수 하나(서수 64개까지). Paper 1.21.1 에서 {@code Goal#getFlags()} 의 반환형이라 그 모양만큼 둔다
 * (몹 목표 설계 M1). 공개 멤버는 Paper 와 같다.
 */
public final class OptimizedSmallEnumSet<E extends Enum<E>> {

    private final Class<E> type;
    private long bits;

    public OptimizedSmallEnumSet(Class<E> clazz) {
        if (clazz == null) throw new IllegalArgumentException("Null class");
        if (!clazz.isEnum()) throw new IllegalArgumentException("Class must be enum, not " + clazz.getCanonicalName());
        this.type = clazz;
    }

    public boolean addUnchecked(E element) {
        long before = bits;
        bits |= 1L << element.ordinal();
        return before != bits;
    }

    public boolean removeUnchecked(E element) {
        long before = bits;
        bits &= ~(1L << element.ordinal());
        return before != bits;
    }

    public void clear() { bits = 0L; }

    public int size() { return Long.bitCount(bits); }

    public void addAllUnchecked(Collection<E> enums) {
        for (E e : enums) {
            if (e == null) throw new NullPointerException("Null element");
            bits |= 1L << e.ordinal();
        }
    }

    public long getBackingSet() { return bits; }

    public boolean hasCommonElements(OptimizedSmallEnumSet<E> other) { return (other.bits & bits) != 0L; }

    public boolean hasElement(E element) { return (bits & 1L << element.ordinal()) != 0L; }
}
