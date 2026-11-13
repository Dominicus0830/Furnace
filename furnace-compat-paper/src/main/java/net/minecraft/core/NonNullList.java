package net.minecraft.core;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** null 을 받지 않는 목록(Mojang 이름). 컨테이너 내용 패킷·인벤토리가 쓴다. 기본값이 있으면 크기가 고정이다. */
public class NonNullList<E> extends AbstractList<E> {

    private final List<E> list;
    private final E defaultValue;

    protected NonNullList(List<E> list, E defaultValue) {
        this.list = list;
        this.defaultValue = defaultValue;
    }

    public static <E> NonNullList<E> create() { return new NonNullList<>(new ArrayList<>(), null); }

    public static <E> NonNullList<E> createWithCapacity(int capacity) { return new NonNullList<>(new ArrayList<>(capacity), null); }

    public static <E> NonNullList<E> withSize(int size, E defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        Object[] a = new Object[size];
        Arrays.fill(a, defaultValue);
        @SuppressWarnings("unchecked") List<E> l = (List<E>) Arrays.asList(a);
        return new NonNullList<>(l, defaultValue);
    }

    @SafeVarargs
    public static <E> NonNullList<E> of(E defaultValue, E... values) { return new NonNullList<>(Arrays.asList(values), defaultValue); }

    @Override
    public E get(int index) { return list.get(index); }

    @Override
    public E set(int index, E value) { return list.set(index, Objects.requireNonNull(value, "value")); }

    @Override
    public void add(int index, E value) { list.add(index, Objects.requireNonNull(value, "value")); }

    @Override
    public E remove(int index) { return list.remove(index); }

    @Override
    public int size() { return list.size(); }

    /** 기본값이 있으면 모두 기본값으로, 없으면 비운다(바닐라). */
    @Override
    public void clear() {
        if (defaultValue == null) super.clear();
        else for (int i = 0; i < size(); i++) set(i, defaultValue);
    }
}
