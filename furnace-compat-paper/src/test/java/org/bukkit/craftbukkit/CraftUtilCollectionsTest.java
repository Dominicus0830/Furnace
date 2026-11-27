package org.bukkit.craftbukkit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
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
    void unsafeListDoesNotTrackModCountLikePaper() {
        UnsafeList<Integer> l = new UnsafeList<>(4);
        for (int i = 0; i < 6; i++) {
            l.add(i);
        }
        Iterator<Integer> it = l.iterator();
        assertEquals(0, it.next());
        l.add(9); // Paper 는 modCount 를 올리지 않아 반복 중 추가해도 던지지 않는다
        assertEquals(1, it.next());
        Iterator<Integer> it2 = l.iterator();
        assertNotSame(it, it2);
        while (it2.hasNext()) {
            if (it2.next() % 2 == 0) {
                it2.remove();
            }
        }
        assertEquals(List.of(1, 3, 5, 9), new ArrayList<>(l));
        l.clear();
        assertTrue(l.isEmpty());
        assertThrows(NoSuchElementException.class, l.iterator()::next);
    }

    @Test
    void unsafeListCloneIsIndependent() throws Exception {
        UnsafeList<Integer> l = new UnsafeList<>();
        l.add(1);
        @SuppressWarnings("unchecked")
        UnsafeList<Integer> copy = (UnsafeList<Integer>) l.clone();
        copy.add(7);
        assertEquals(1, l.size());
        assertEquals(2, copy.size());
        assertThrows(NullPointerException.class, () -> l.indexOf(null));
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
    void apiVersionMatchesPaperParsing() {
        assertSame(ApiVersion.getOrCreateVersion("1.13"), ApiVersion.getOrCreateVersion("1.13.0"));
        assertSame(ApiVersion.FLATTENING, ApiVersion.getOrCreateVersion("1.13"));
        assertEquals("1.13.0", ApiVersion.FLATTENING.getVersionString());
        assertEquals("1.20.5", ApiVersion.FIELD_NAME_PARITY.getVersionString());
        assertEquals("none", ApiVersion.NONE.getVersionString());
        assertSame(ApiVersion.NONE, ApiVersion.getOrCreateVersion(null));
        assertSame(ApiVersion.NONE, ApiVersion.getOrCreateVersion("  "));
        assertSame(ApiVersion.NONE, ApiVersion.getOrCreateVersion("None"));
        assertTrue(ApiVersion.CURRENT.isNewerThan(ApiVersion.FLATTENING));
        assertTrue(ApiVersion.NONE.isOlderThan(ApiVersion.FLATTENING));
        assertTrue(ApiVersion.FIELD_NAME_PARITY.isNewerThanOrSameAs(ApiVersion.FLATTENING));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.getOrCreateVersion("1"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.getOrCreateVersion("1.2.3.4"));
        assertThrows(NumberFormatException.class, () -> ApiVersion.getOrCreateVersion("1.x"));
    }
}
