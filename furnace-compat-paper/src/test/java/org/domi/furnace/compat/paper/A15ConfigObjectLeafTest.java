package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.junit.jupiter.api.Test;

/**
 * PR #260 검토 재현. 기대값은 Paper 1.21.1 MemorySection·MemoryConfiguration·YamlConfiguration 본문(Youer 1.21.1)을 문장 단위로 따라간 결과.
 * 이름의 Rn 은 보고서 항목 번호.
 */
class A15ConfigObjectLeafTest {

    static final AtomicInteger SER = new AtomicInteger();

    public static final class Unreg implements ConfigurationSerializable {
        public int x; public String s;
        public Unreg(int x, String s) { this.x = x; this.s = s; }
        @Override public Map<String, Object> serialize() { SER.incrementAndGet(); Map<String, Object> m = new LinkedHashMap<>(); m.put("x", x); m.put("s", s); return m; }
    }

    @SerializableAs("Pr260RegT")
    public static final class Reg implements ConfigurationSerializable {
        public int x;
        public Reg(int x) { this.x = x; }
        @Override public Map<String, Object> serialize() { SER.incrementAndGet(); return new LinkedHashMap<>(Map.of("x", x)); }
        public static Reg deserialize(Map<String, Object> m) { return new Reg(((Number) m.get("x")).intValue()); }
    }

    public static final class Lazy implements ConfigurationSerializable {
        public String v;
        @Override public Map<String, Object> serialize() { if (v == null) throw new IllegalStateException("not ready"); return Map.of("v", v); }
    }

    // R1 객체 아래 createSection — Paper 는 객체를 구역으로 바꾼다. PR 은 객체를 남기고 만든 구역은 저장 때 사라진다.
    @Test
    void r1_createSectionUnderObjectReplacesIt() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("o", new Unreg(1, "a"));
        ConfigurationSection sub = c.createSection("o.sub");
        sub.set("k", 1);
        assertInstanceOf(ConfigurationSection.class, c.get("o"));
        assertEquals(1, c.get("o.sub.k"));
        assertEquals("o:\n  sub:\n    k: 1\n", c.saveToString());
    }

    // R2 비동기 저장이 트리를 고친다 — 다른 스레드의 getKeys(true) 가 ConcurrentModificationException. Paper 저장은 읽기만 한다.
    @Test
    void r2_saveDoesNotMutateTheTree() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        for (int i = 0; i < 50; i++) c.set("k" + i, new Unreg(i, "s"));
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger cme = new AtomicInteger();
        Thread t = new Thread(() -> { while (!stop.get()) c.saveToString(); });
        t.start();
        long end = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < end) {
            try { c.getKeys(true); c.getValues(true); } catch (ConcurrentModificationException e) { cme.incrementAndGet(); }
        }
        stop.set(true);
        t.join();
        assertEquals(0, cme.get());
    }

    // R3 copyDefaults 저장: 기본값 구역(o.k)이 자기 객체(o)의 저장 꼴에 섞여 들어간다. Paper 는 객체만 쓴다.
    @Test
    void r3_copyDefaultsDoesNotMergeDefaultsIntoObject() {
        YamlConfiguration c = new YamlConfiguration();
        c.addDefault("o.k", 1);
        c.set("o", new Unreg(1, "a"));
        c.options().copyDefaults(true);
        assertEquals("o:\n  '==': " + Unreg.class.getName() + "\n  x: 1\n  s: a\n", c.saveToString());
    }

    // R4 객체는 잎이다 — getKeys(true)/getValues(true) 가 객체 안 키로 내려가지 않는다.
    @Test
    void r4_objectIsALeafForDeepKeys() {
        YamlConfiguration c = new YamlConfiguration();
        Unreg o = new Unreg(1, "a");
        c.set("a.o", o);
        c.set("a.n", 3);
        assertEquals(List.of("a", "a.o", "a.n"), new ArrayList<>(c.getKeys(true)));
        assertEquals(List.of("a", "a.o", "a.n"), new ArrayList<>(c.getValues(true).keySet()));
    }

    // R4 의 결과: getKeys(true) 로 다른 설정에 옮겨 담으면 객체가 사라진다(등록 안 한 클래스는 MemorySection 이 됨).
    @Test
    void r4_copyByDeepKeysKeepsObject() {
        YamlConfiguration src = new YamlConfiguration();
        Unreg o = new Unreg(1, "a");
        src.set("a.o", o);
        YamlConfiguration dst = new YamlConfiguration();
        for (String k : src.getKeys(true)) dst.set(k, src.get(k));
        assertSame(o, dst.get("a.o"));
    }

    // R5 객체 아래 경로 읽기 — Paper 는 객체를 구역으로 보지 않으므로 없다.
    @Test
    void r5_pathsBelowObjectAreAbsent() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("o", new Unreg(1, "a"));
        assertNull(c.get("o.x"));
        assertFalse(c.contains("o.x"));
        assertEquals(0, c.getInt("o.x"));
        assertNull(c.get("o.=="));
        c.setComments("o.x", List.of("inner"));
        assertEquals(List.of(), c.getComments("o.x"));
    }

    // R6 serialize() 는 저장할 때만 — set 때 부르지 않는다(아직 준비 안 된 객체도 set 된다).
    @Test
    void r6_serializeIsNotCalledOnSet() {
        YamlConfiguration c = new YamlConfiguration();
        SER.set(0);
        c.set("o", new Reg(1));
        assertEquals(0, SER.get());
        Lazy l = new Lazy();
        assertDoesNotThrow(() -> c.set("l", l));
        l.v = "ready";
        assertSame(l, c.get("l"));
        assertTrue(c.saveToString().contains("v: ready"));
    }

    // R7 addDefault·addDefaults(Configuration) 도 참조를 보관한다(Paper 기본값은 MemoryConfiguration.set).
    @Test
    void r7_defaultsKeepReference() {
        YamlConfiguration c = new YamlConfiguration();
        Unreg o = new Unreg(1, "a");
        c.addDefault("d", o);
        assertSame(o, c.get("d"));
        assertSame(o, c.getObject("d", Unreg.class));
        YamlConfiguration src = new YamlConfiguration();
        Unreg p = new Unreg(2, "b");
        src.set("p", p);
        YamlConfiguration c2 = new YamlConfiguration();
        c2.addDefaults(src);
        assertSame(p, c2.get("p"));
    }

    // R8 copyDefaults 저장 때 기본값 쪽 객체도 쓰는 순간의 serialize().
    @Test
    void r8_copyDefaultsSavesCurrentDefaultObject() {
        YamlConfiguration d = new YamlConfiguration();
        Unreg o = new Unreg(1, "a");
        d.set("o", o);
        YamlConfiguration c = new YamlConfiguration();
        c.setDefaults(d);
        o.x = 4;
        c.options().copyDefaults(true);
        assertTrue(c.saveToString().contains("x: 4"), c.saveToString());
    }

    // R9 객체가 든 구역을 다른 경로에 set — copy() 가 붙인 객체를 버린다(Paper 는 같은 구역 참조라 같은 객체).
    @Test
    void r9_sectionCopyKeepsObject() {
        YamlConfiguration c = new YamlConfiguration();
        Unreg o = new Unreg(1, "a");
        c.set("a.o", o);
        c.set("b", c.getConfigurationSection("a"));
        assertSame(o, c.get("b.o"));
    }

    // R10 읽어 온 객체는 한 번 되살려 보관 — 두 번 get 해도 같은 참조(Paper 는 읽을 때 한 번 deserialize).
    @Test
    void r10_loadedObjectIsStable() throws Exception {
        ConfigurationSerialization.registerClass(Reg.class);
        try {
            YamlConfiguration c = new YamlConfiguration();
            c.loadFromString("o:\n  ==: Pr260RegT\n  x: 3\n");
            assertSame(c.get("o"), c.get("o"));
        } finally {
            ConfigurationSerialization.unregisterClass(Reg.class);
        }
    }
}
