// ConfigSection 경로 접근·타입 변환·기본값·주석 API 검증
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigSectionTest {

    @Test
    void setCreatesIntermediateSections() {
        ConfigSection c = new ConfigSection();
        c.set("a.b.c", 1);
        c.set("a.b.d", "x");
        c.set("a.e", true);
        assertTrue(c.isSection("a"));
        assertTrue(c.isSection("a.b"));
        assertEquals(1, c.getInt("a.b.c"));
        assertEquals("x", c.getString("a.b.d"));
        assertEquals(List.of("a", "a.b", "a.b.c", "a.b.d", "a.e"), List.copyOf(c.keys(true)));
        assertEquals(List.of("a"), List.copyOf(c.keys(false)));
        assertEquals(List.of("b", "e"), List.copyOf(c.getSection("a").keys(false)));
        assertEquals("a.b", c.getSection("a.b").getPath());
        assertEquals("b", c.getSection("a.b").getName());
        assertSame(c, c.getSection("a.b").getRoot());
        assertSame(c.getSection("a"), c.getSection("a.b").getParent());
    }

    @Test
    void insertionOrderIsPreserved() {
        ConfigSection c = new ConfigSection();
        for (String k : new String[] {"z", "y", "a", "m"}) c.set(k, 1);
        assertEquals(List.of("z", "y", "a", "m"), List.copyOf(c.keys(false)));
        c.set("y", 2);
        assertEquals(List.of("z", "y", "a", "m"), List.copyOf(c.keys(false)));
    }

    @Test
    void literalDottedKeys() {
        ConfigSection c = new ConfigSection();
        c.setLiteral("furnace.admin", 1);
        c.set("perms.furnace.admin.default", true);
        c.set("perms.furnace.admin.default", false);
        assertEquals(1, c.getInt("furnace.admin"));
        assertEquals(List.of("furnace.admin", "perms"), List.copyOf(c.keys(false)));
        assertEquals(List.of("furnace"), List.copyOf(c.getSection("perms").keys(false)));

        // 기존 literal 키 섹션 아래에 set 하면 그 키를 따라간다
        ConfigSection p = new ConfigSection();
        ConfigSection node = new ConfigSection();
        node.set("default", "op");
        p.setLiteral("my.node", node);
        p.set("my.node.children.x", true);
        assertEquals(List.of("my.node"), List.copyOf(p.keys(false)));
        assertEquals("op", p.getString("my.node.default"));
        assertTrue(p.getBoolean("my.node.children.x"));
        assertTrue(p.contains("my.node.children"));
    }

    @Test
    void setReplacesNonSectionOnPath() {
        ConfigSection c = new ConfigSection();
        c.set("a", 5);
        c.set("a.b", 6);
        assertEquals(6, c.getInt("a.b"));
        assertTrue(c.isSection("a"));
    }

    @Test
    void nullsAndRemove() {
        ConfigSection c = new ConfigSection();
        c.set("x", null);
        assertTrue(c.contains("x"));
        assertNull(c.get("x"));
        assertEquals("d", c.getString("x", "d"));
        c.set("y.z", 1);
        c.setComments("y.z", List.of("c"));
        assertEquals(1, c.remove("y.z"));
        assertFalse(c.contains("y.z"));
        assertNull(c.remove("nope"));
        assertTrue(c.getComments("y.z").isEmpty());
        c.remove("x");
        assertFalse(c.contains("x"));
    }

    @Test
    void typedGettersAndConversions() {
        ConfigSection c = new ConfigSection();
        c.set("i", 7);
        c.set("l", 1L << 40);
        c.set("small-long", 5L);
        c.set("d", 2.5);
        c.set("b", true);
        c.set("s", "str");
        c.set("numstr", " 12 ");
        c.set("boolstr", "TRUE");
        c.set("list", List.of(1, "2", 3.5, "x", true));
        c.set("when", LocalDate.of(2024, 5, 1));

        assertEquals(7, c.getInt("i"));
        assertInstanceOf(Integer.class, c.get("small-long"));
        assertInstanceOf(Long.class, c.get("l"));
        assertEquals(1L << 40, c.getLong("l"));
        assertEquals(7L, c.getLong("i"));
        assertEquals(7.0, c.getDouble("i"));
        assertEquals(2.5, c.getDouble("d"));
        assertEquals(2, c.getInt("d"));
        assertTrue(c.getBoolean("b"));
        assertTrue(c.getBoolean("boolstr"));
        assertFalse(c.getBoolean("s"));
        assertTrue(c.getBoolean("s", true));
        assertEquals(12, c.getInt("numstr"));
        assertEquals(99, c.getInt("s", 99));
        assertEquals(0, c.getInt("missing"));
        assertEquals("7", c.getString("i"));
        assertEquals("2.5", c.getString("d"));
        assertEquals("true", c.getString("b"));
        assertEquals("2024-05-01", c.getString("when"));
        assertNull(c.getString("list"));
        assertEquals("def", c.getString("missing", "def"));
        assertEquals(List.of("1", "2", "3.5", "x", "true"), c.getStringList("list"));
        assertEquals(List.of(1, 2, 3), c.getIntegerList("list"));
        assertTrue(c.getStringList("missing").isEmpty());
        assertNull(c.getList("missing"));
        assertEquals(List.of(), c.getList("missing", List.of()));
        assertTrue(c.isList("list"));
        assertTrue(c.isString("s"));
        assertTrue(c.isNumber("i"));
        assertTrue(c.isBoolean("b"));
        assertFalse(c.isSection("i"));
        assertNull(c.getSection("i"));
        assertThrows(UnsupportedOperationException.class, () -> c.getList("list").clear());
    }

    @Test
    void normalizeValueTypes() {
        ConfigSection c = new ConfigSection();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", 1L);
        m.put("nested", Map.of("x", (short) 3));
        c.set("map", m);
        c.set("arr", new Object[] {1, "a", new int[0].length});
        c.set("ch", 'c');
        c.set("f", 1.25f);
        c.set("en", Thread.State.NEW);
        c.set("sb", new StringBuilder("sb"));
        assertEquals(1, c.get("map.k"));
        assertEquals(3, c.get("map.nested.x"));
        assertTrue(c.isSection("map.nested"));
        assertEquals(List.of(1, "a", 0), c.getList("arr"));
        assertEquals("c", c.get("ch"));
        assertEquals(1.25, c.get("f"));
        assertEquals("NEW", c.get("en"));
        assertEquals("sb", c.get("sb"));
        assertThrows(IllegalArgumentException.class, () -> c.set("bad", new Object()));
        assertThrows(IllegalArgumentException.class, () -> c.set("", 1));
    }

    @Test
    void setSectionCopiesWhenAlreadyAttached() {
        ConfigSection c = new ConfigSection();
        c.set("a.x", 1);
        ConfigSection a = c.getSection("a");
        c.set("b", a);
        assertNotSame(a, c.getSection("b"));
        c.set("b.x", 2);
        assertEquals(1, c.getInt("a.x"));
        assertEquals(2, c.getInt("b.x"));
    }

    @Test
    void equalsIgnoresCommentsAndOrder() {
        ConfigSection a = new ConfigSection();
        a.set("x", 1);
        a.set("y.z", List.of(1, 2));
        ConfigSection b = new ConfigSection();
        b.set("y.z", new ArrayList<>(List.of(1, 2)));
        b.set("x", 1);
        b.setComments("x", List.of("c"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.set("x", 2);
        assertNotEquals(a, b);
    }

    @Test
    void copyIsDeepAndKeepsComments() {
        ConfigSection a = new ConfigSection();
        a.set("s.x", 1);
        a.set("l", List.of(new ConfigSection()));
        a.setComments("s", List.of("about s"));
        a.setHeader(List.of("head"));
        ConfigSection b = a.copy();
        assertEquals(a, b);
        b.set("s.x", 2);
        assertEquals(1, a.getInt("s.x"));
        assertEquals(List.of("about s"), b.getComments("s"));
        assertEquals(List.of("head"), b.getHeader());
        assertNull(b.getParent());
    }

    // ------------------------------------------------------------ 기본값

    @Test
    void defaultsFallback() {
        ConfigSection defaults = new ConfigSection();
        defaults.set("db.host", "localhost");
        defaults.set("db.port", 3306);
        defaults.set("name", "def");
        defaults.set("list", List.of("a"));
        ConfigSection c = new ConfigSection();
        c.set("db.port", 1);
        c.set("name", null);
        c.setDefaults(defaults);

        assertEquals(1, c.getInt("db.port"));
        assertEquals("localhost", c.getString("db.host"));
        assertEquals("def", c.getString("name"));
        assertEquals(List.of("a"), c.getStringList("list"));
        assertTrue(c.contains("db.host"));
        assertFalse(c.contains("db.host", true));
        assertTrue(c.contains("name", true));
        // 하위 섹션에서도 같은 경로의 기본값을 쓴다
        assertEquals("localhost", c.getSection("db").getString("host"));
        assertEquals(1, c.getSection("db").getInt("port"));
        // 기본값에만 있는 섹션은 기본값 섹션 자체가 반환된다
        defaults.set("only.here", 5);
        assertEquals(5, c.getSection("only").getInt("here"));
        assertNull(c.get("nowhere"));
    }

    @Test
    void copyDefaultsFillsMissingKeysWithComments() {
        ConfigSection defaults = ConfigFormat.YAML.parse("""
                # header
                # Server name
                name: Furnace
                db:
                  # Host
                  host: localhost # default host
                  port: 3306
                features: [a, b]
                """);
        ConfigSection c = ConfigFormat.YAML.parse("""
                name: Custom
                db:
                  port: 1234
                """);
        c.setDefaults(defaults);
        int added = c.copyDefaults();
        assertEquals(2, added); // db.host, features
        assertEquals("Custom", c.getString("name"));
        assertEquals("localhost", c.getString("db.host"));
        assertEquals(1234, c.getInt("db.port"));
        assertEquals(List.of("a", "b"), c.getStringList("features"));
        assertEquals(List.of("Host"), c.getComments("db.host"));
        assertEquals("default host", c.getInlineComment("db.host"));
        assertEquals(List.of("db", "db.port", "db.host", "name", "features").size(), c.keys(true).size());
        // 복사본이므로 원본 defaults 와 독립
        c.set("db.host", "other");
        assertEquals("localhost", defaults.getString("db.host"));
        assertEquals(0, c.copyDefaults());
    }

    @Test
    void addDefaultAndNestedCopyDefaults() {
        ConfigSection c = new ConfigSection();
        c.addDefault("a.b", 1);
        c.addDefault("a.c", 2);
        c.set("a.b", 10);
        assertEquals(10, c.getInt("a.b"));
        assertEquals(2, c.getInt("a.c"));
        ConfigSection a = c.getSection("a");
        a.addDefault("d", 3);
        assertEquals(3, c.getDefaults().getInt("a.d"));
        assertEquals(2, a.copyDefaults());
        assertEquals(Arrays.asList("b", "c", "d"), List.copyOf(a.keys(false)));
    }

    // ------------------------------------------------------------ 주석 API

    @Test
    void commentApi() {
        ConfigSection c = new ConfigSection();
        c.set("a.b", 1);
        assertTrue(c.getComments("a.b").isEmpty());
        c.setComments("a", List.of("sec"));
        c.setComments("a.b", Arrays.asList(null, "x", ""));
        c.setInlineComment("a.b", "inline");
        assertEquals(List.of("sec"), c.getComments("a"));
        assertEquals(Arrays.asList(null, "x", ""), c.getComments("a.b"));
        assertEquals("inline", c.getInlineComment("a.b"));
        assertEquals(c.getSection("a").getComments("b"), c.getComments("a.b"));
        c.setComments("a.b", null);
        c.setInlineComment("a.b", null);
        assertTrue(c.getComments("a.b").isEmpty());
        assertNull(c.getInlineComment("a.b"));
        assertThrows(IllegalArgumentException.class, () -> c.setComments("missing", List.of("x")));
        assertTrue(c.getComments("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> c.getComments("a").add("no"));

        c.setHeader(List.of("h1", "h2"));
        c.setFooter(List.of("f"));
        assertEquals(List.of("h1", "h2"), c.getHeader());
        assertEquals(List.of("f"), c.getFooter());
        c.setHeader(null);
        assertTrue(c.getHeader().isEmpty());
    }

    @Test
    void fromMapKeepsKeysLiteral() {
        ConfigSection c = ConfigSection.fromMap(Map.of("a.b", Map.of("c", 1)));
        assertEquals(1, c.getInt("a.b.c"));
        assertEquals(List.of("a.b"), List.copyOf(c.keys(false)));
    }
}
