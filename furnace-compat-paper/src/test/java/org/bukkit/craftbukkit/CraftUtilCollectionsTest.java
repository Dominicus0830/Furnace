package org.bukkit.craftbukkit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.bukkit.craftbukkit.util.ApiVersion;
import org.bukkit.craftbukkit.util.LazyHashSet;
import org.bukkit.craftbukkit.util.UnsafeList;
import org.bukkit.craftbukkit.util.Waitable;
import org.bukkit.craftbukkit.util.WeakCollection;
import org.junit.jupiter.api.Test;

/** 순수 자바 유틸 Craft 클래스(UnsafeList·LazyHashSet·WeakCollection·Waitable·ApiVersion)의 동작 시험. */
class CraftUtilCollectionsTest {

    @Test
    void unsafeListBasics() {
        UnsafeList<String> l = new UnsafeList<>();
        for (int i = 0; i < 100; i++) {
            l.add("v" + i);
        }
        assertEquals(100, l.size());
        assertEquals("v50", l.get(50));
        assertEquals("v50", l.unsafeGet(50));
        assertEquals(50, l.indexOf("v50"));
        assertEquals("v0", l.remove(0));
        assertEquals("v1", l.get(0));
        assertTrue(l.remove("v2"));
        assertFalse(l.contains("v2"));
        l.add(1, "x");
        assertEquals("x", l.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(500));
        assertEquals("x", l.set(1, "y"));
        l.clear();
        assertTrue(l.isEmpty());
        assertEquals(List.of(), new ArrayList<>(l));
    }

    @Test
    void unsafeListIteratorChecksModification() {
        UnsafeList<Integer> l = new UnsafeList<>(4);
        for (int i = 0; i < 6; i++) {
            l.add(i);
        }
        Iterator<Integer> it = l.iterator();
        assertEquals(0, it.next());
        l.add(9);
        assertThrows(ConcurrentModificationException.class, it::next);
        Iterator<Integer> it2 = l.iterator();
        while (it2.hasNext()) {
            if (it2.next() % 2 == 0) {
                it2.remove();
            }
        }
        assertEquals(List.of(1, 3, 5, 9), new ArrayList<>(l));
        @SuppressWarnings("unchecked")
        UnsafeList<Integer> copy = (UnsafeList<Integer>) l.clone();
        copy.add(7);
        assertEquals(4, l.size());
        assertEquals(5, copy.size());
    }

    @Test
    void lazyHashSetBuildsReferenceOnFirstUse() {
        int[] made = {0};
        LazyHashSet<String> s = new LazyHashSet<>() {
            @Override
            protected Set<String> makeReference() {
                made[0]++;
                return new HashSet<>(Set.of("a", "b"));
            }
        };
        assertTrue(s.isLazy());
        assertEquals(0, made[0]);
        assertTrue(s.contains("a"));
        assertFalse(s.isLazy());
        assertEquals(2, s.size());
        assertEquals(1, made[0]);
    }

    @Test
    void weakCollectionDropsCollectedValues() {
        WeakCollection<Object> c = new WeakCollection<>();
        Object keep = new Object();
        c.add(keep);
        assertEquals(1, c.size());
        assertTrue(c.contains(keep));
        assertTrue(c.remove(keep));
        assertTrue(c.isEmpty());
        assertThrows(NullPointerException.class, () -> c.add(null));
    }

    @Test
    void waitableReturnsValueAndWrapsException() throws Exception {
        Waitable<Integer> ok = new Waitable<>() {
            @Override
            protected Integer evaluate() {
                return 42;
            }
        };
        ok.run();
        assertEquals(42, ok.get());
        assertThrows(IllegalStateException.class, ok::run);
        Waitable<Integer> bad = new Waitable<>() {
            @Override
            protected Integer evaluate() {
                throw new IllegalArgumentException("boom");
            }
        };
        bad.run();
        ExecutionException e = assertThrows(ExecutionException.class, bad::get);
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    void apiVersionOrdering() {
        ApiVersion a = ApiVersion.getOrCreateVersion("1.13");
        assertSame(a, ApiVersion.FLATTENING);
        assertTrue(ApiVersion.CURRENT.isNewerThan(ApiVersion.FLATTENING));
        assertTrue(ApiVersion.NONE.isOlderThan(ApiVersion.FLATTENING));
        assertTrue(ApiVersion.FIELD_NAME_PARITY.isNewerThanOrSameAs(ApiVersion.FLATTENING));
        assertEquals("1.20.5", ApiVersion.FIELD_NAME_PARITY.getVersionString());
        assertEquals("1.13", ApiVersion.FLATTENING.getVersionString());
        assertEquals(ApiVersion.NONE, ApiVersion.getOrCreateVersion(null));
    }
}
