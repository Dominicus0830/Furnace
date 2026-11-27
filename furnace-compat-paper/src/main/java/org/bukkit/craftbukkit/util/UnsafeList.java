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
            capacity = 128;
        }
        int rounded = Integer.highestOneBit(capacity - 1) << 1;
        if (rounded < 16) {
            rounded = 16;
        }
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
        modCount++;
        data[size++] = element;
        return true;
    }

    @Override
    public void add(int index, E element) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        growIfNeeded();
        modCount++;
        System.arraycopy(data, index, data, index + 1, size - index);
        data[index] = element;
        size++;
    }

    @Override
    public E remove(int index) {
        rangeCheck(index);
        E old = unsafeGet(index);
        modCount++;
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
            if (o == data[i] || (o != null && o.equals(data[i]))) {
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
        // 반복자 풀은 크기가 줄었을 때 다시 쓰도록 비운다
        modCount++;
        for (int i = 0; i < size; i++) {
            data[i] = null;
        }
        size = 0;
        if (data.length > initialCapacity << 3) {
            data = new Object[initialCapacity];
        }
        for (int i = 0; i < iterPool.length; i++) {
            if (iterPool[i] != null) {
                ((Itr) iterPool[i]).valid = false;
            }
        }
        poolCounter = 0;
    }

    public void trimToSize() {
        if (size < data.length) {
            data = java.util.Arrays.copyOf(data, size);
        }
    }

    @Override
    public Object clone() {
        try {
            UnsafeList<E> copy = (UnsafeList<E>) super.clone();
            copy.data = java.util.Arrays.copyOf(data, size);
            copy.iterPool = new Iterator[1];
            copy.iterPool[0] = copy.new Itr();
            copy.poolCounter = 0;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
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
        // 풀에서 쓸 수 있는 반복자를 꺼내거나 새로 만든다
        for (int i = 0; i < iterPool.length; i++) {
            Iterator<?> it = iterPool[i];
            if (it != null) {
                Itr itr = (Itr) it;
                if (!itr.valid) {
                    itr.reset();
                    return itr;
                }
            }
        }
        Itr fresh = new Itr();
        if (poolCounter < maxPool) {
            if (poolCounter >= iterPool.length) {
                iterPool = java.util.Arrays.copyOf(iterPool, iterPool.length + 1);
            }
            iterPool[poolCounter++] = fresh;
        }
        return fresh;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(size);
        for (int i = 0; i < size; i++) {
            out.writeObject(data[i]);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int n = in.readInt();
        data = new Object[Math.max(n, 16)];
        for (int i = 0; i < n; i++) {
            data[i] = in.readObject();
        }
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
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            int i = index;
            if (i >= size) {
                throw new NoSuchElementException();
            }
            index = i + 1;
            lastRet = i;
            return unsafeGet(i);
        }

        @Override
        public void remove() {
            if (lastRet < 0) {
                throw new IllegalStateException();
            }
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
            UnsafeList.this.remove(lastRet);
            index = lastRet;
            lastRet = -1;
            expectedModCount = modCount;
        }
    }
}
