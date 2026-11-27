package org.bukkit.craftbukkit.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/**
 * 범위 검사 없이 읽는 {@link #unsafeGet(int)} 를 가진 배열 리스트. Paper 의 {@code UnsafeList} 와 같은 모양(2의 거듭제곱 용량, 반복자 풀)이다.
 * 틱 스레드 안에서만 쓰는 용도라 동기화하지 않는다.
 */
public class UnsafeList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, Serializable {
    private static final long serialVersionUID = 8683452581112892191L;

    private transient Object[] data;
    private int size;
    private int initialCapacity;

    private Iterator[] iterPool = new Iterator[1];
    private int poolCounter;
    private int maxPool;

    public UnsafeList(int capacity) {
        this(capacity, 5);
    }

    public UnsafeList(int capacity, int maxIterPool) {
        super();
        if (capacity < 0) {
            capacity = 32;
        }
        int rounded = Integer.highestOneBit(capacity - 1) << 1;
        data = new Object[rounded];
        initialCapacity = rounded;
        this.maxPool = maxIterPool;
        iterPool[0] = new Itr();
    }

    public UnsafeList() {
        this(32);
    }

    @Override
    public E get(int index) {
        rangeCheck(index);
        return unsafeGet(index);
    }

    @SuppressWarnings("unchecked")
    public E unsafeGet(int index) {
        return (E) data[index];
    }

    @Override
    public E set(int index, E element) {
        rangeCheck(index);
        E old = unsafeGet(index);
        data[index] = element;
        return old;
    }

    @Override
    public boolean add(E element) {
        growIfNeeded();
        data[size++] = element;
        return true;
    }

    @Override
    public void add(int index, E element) {
        growIfNeeded();
        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = element;
        size++;
    }

    @Override
    public E remove(int index) {
        rangeCheck(index);
        E old = unsafeGet(index);
        int moved = size - index - 1;
        if (moved > 0) {
            System.arraycopy(data, index + 1, data, index, moved);
        }
        data[--size] = null;
        return old;
    }

    @Override
    public boolean remove(Object o) {
        int index = indexOf(o);
        if (index < 0) {
            return false;
        }
        remove(index);
        return true;
    }

    @Override
    public int indexOf(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == data[i] || o.equals(data[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        size = 0;
        if (data.length > initialCapacity << 3) {
            data = new Object[initialCapacity];
        } else {
            for (int i = 0; i < data.length; i++) {
                data[i] = null;
            }
        }
    }

    public void trimToSize() {
        int old = data.length;
        int rounded = Integer.highestOneBit(size - 1) << 1;
        if (rounded < old) {
            data = java.util.Arrays.copyOf(data, rounded);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object clone() throws CloneNotSupportedException {
        UnsafeList<E> copy = (UnsafeList<E>) super.clone();
        copy.data = java.util.Arrays.copyOf(data, size);
        copy.size = size;
        copy.initialCapacity = initialCapacity;
        copy.iterPool = new Iterator[1];
        copy.iterPool[0] = new Itr();
        copy.maxPool = maxPool;
        copy.poolCounter = 0;
        return copy;
    }

    private void rangeCheck(int index) {
        if (index >= size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    private void growIfNeeded() {
        if (size == data.length) {
            data = java.util.Arrays.copyOf(data, size << 1);
        }
    }

    @Override
    public Iterator<E> iterator() {
        Iterator[] pool = iterPool;
        int length = pool.length;
        for (int i = 0; i < length; i++) {
            Itr itr = (Itr) pool[i];
            if (!itr.valid) {
                itr.reset();
                return itr;
            }
        }
        if (iterPool.length < maxPool) {
            Iterator[] bigger = new Iterator[iterPool.length + 1];
            System.arraycopy(iterPool, 0, bigger, 0, iterPool.length);
            iterPool = bigger;
            iterPool[iterPool.length - 1] = new Itr();
            return iterPool[iterPool.length - 1];
        }
        poolCounter = ++poolCounter % iterPool.length;
        iterPool[poolCounter] = new Itr();
        return iterPool[poolCounter];
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(size);
        out.writeInt(initialCapacity);
        for (int i = 0; i < size; i++) {
            out.writeObject(data[i]);
        }
        out.writeInt(maxPool);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        size = in.readInt();
        initialCapacity = in.readInt();
        data = new Object[Integer.highestOneBit(size - 1) << 1];
        for (int i = 0; i < size; i++) {
            data[i] = in.readObject();
        }
        maxPool = in.readInt();
        iterPool = new Iterator[1];
        iterPool[0] = new Itr();
    }

    public class Itr implements Iterator<E> {
        int index;
        int lastRet = -1;
        int expectedModCount = modCount;
        public boolean valid = true;

        public void reset() {
            index = 0;
            lastRet = -1;
            expectedModCount = modCount;
            valid = true;
        }

        @Override
        public boolean hasNext() {
            valid = index != size;
            return valid;
        }

        @Override
        public E next() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            int i = index;
            if (i >= size) {
                throw new NoSuchElementException();
            }
            if (i >= data.length) {
                throw new ConcurrentModificationException();
            }
            index = i + 1;
            return (E) data[lastRet = i];
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException("");
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            try {
                UnsafeList.this.remove(lastRet);
                index = lastRet;
                lastRet = -1;
                expectedModCount = modCount;
            } catch (IndexOutOfBoundsException e) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
