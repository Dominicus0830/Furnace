// JSON 파서/작성기 검증 — RFC 8259 엄격성, 이스케이프/서로게이트, 숫자, 오류 위치, pretty 출력
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTest {

    private static ConfigSection json(String text) {
        return ConfigFormat.JSON.parse(text);
    }

    private static ConfigParseException error(String text) {
        return assertThrows(ConfigParseException.class, () -> json(text), text);
    }

    @Test
    void parsesAllValueTypes() {
        ConfigSection c = json("""
                {
                  "str": "hello",
                  "int": 42,
                  "neg": -7,
                  "zero": 0,
                  "long": 9007199254740993,
                  "huge": 123456789012345678901234567890,
                  "float": 3.14,
                  "exp": 1E3,
                  "exp2": 2.5e-2,
                  "negzero": -0.0,
                  "t": true,
                  "f": false,
                  "n": null,
                  "arr": [1, "two", 3.5, [true], {"k": "v"}, null],
                  "obj": {"a": {"b": {}}},
                  "emptyArr": [],
                  "emptyObj": {}
                }
                """);
        assertEquals("hello", c.get("str"));
        assertEquals(42, c.get("int"));
        assertInstanceOf(Integer.class, c.get("int"));
        assertEquals(-7, c.get("neg"));
        assertEquals(0, c.get("zero"));
        assertEquals(9007199254740993L, c.get("long"));
        assertEquals(1.2345678901234568E29, c.get("huge"));
        assertEquals(3.14, c.get("float"));
        assertEquals(1000.0, c.get("exp"));
        assertEquals(0.025, c.get("exp2"));
        assertEquals(-0.0, c.get("negzero"));
        assertEquals(Boolean.TRUE, c.get("t"));
        assertEquals(Boolean.FALSE, c.get("f"));
        assertTrue(c.contains("n"));
        assertNull(c.get("n"));
        List<?> arr = c.getList("arr");
        assertEquals(6, arr.size());
        assertEquals("v", ((ConfigSection) arr.get(4)).getString("k"));
        assertNull(arr.get(5));
        assertTrue(c.getSection("obj.a.b").isEmpty());
        assertEquals(List.of(), c.getList("emptyArr"));
        assertTrue(c.getSection("emptyObj").isEmpty());
        assertEquals(List.of("str", "int"), List.copyOf(c.keys(false)).subList(0, 2));
    }

    @Test
    void stringEscapes() {
        ConfigSection c = json("{\"a\": \"q\\\" b\\\\ s\\/ \\b\\f\\n\\r\\t \\u00e9 \\u0041 \\ud83d\\ude00 \\uD834\\uDD1E\", \"k\u00e9y\": \"한국어 😀\"}");
        assertEquals("q\" b\\ s/ \b\f\n\r\t \u00e9 A \uD83D\uDE00 \uD834\uDD1E", c.get("a"));
        assertEquals("한국어 😀", c.get("kéy"));
        assertEquals(1, "😀".codePointCount(0, "😀".length()));
        String s = c.getString("a");
        assertEquals(0x1F600, s.codePointAt(s.indexOf('\uD83D')));
    }

    @Test
    void whitespaceAndBom() {
        ConfigSection c = json("\uFEFF \t\r\n{ \"a\" :\n1 ,\r\n \"b\":[ 1 , 2 ] } \n\n");
        assertEquals(1, c.getInt("a"));
        assertEquals(List.of(1, 2), c.getList("b"));
    }

    @Test
    void strictnessErrors() {
        for (String bad : new String[] {"", "   ", "[1,2]", "\"str\"", "42", "null", "{", "{\"a\"}", "{\"a\":}", "{\"a\":1,}", "{\"a\":1 \"b\":2}",
                "{a: 1}", "{'a': 1}", "{\"a\": [1,]}", "{\"a\": [1 2]}", "{\"a\": 01}", "{\"a\": +1}", "{\"a\": .5}", "{\"a\": 1.}", "{\"a\": 1e}",
                "{\"a\": -}", "{\"a\": NaN}", "{\"a\": Infinity}", "{\"a\": tru}", "{\"a\": True}", "{\"a\": \"unterminated}", "{\"a\": \"bad\\q\"}",
                "{\"a\": \"\\u12\"}", "{\"a\": \"\\u12G4\"}", "{\"a\": \"tab\there\"}", "{\"a\": \"nl\nhere\"}", "{\"a\": 1} extra", "{\"a\": 1}}",
                "{\"a\": 1} // comment", "// c\n{\"a\": 1}", "{\"a\": /* c */ 1}", "{\"a\": 1, \"a\": 2}", "{\"a\": 1,, \"b\": 2}", "{,}"}) {
            error(bad);
        }
    }

    @Test
    void errorPositions() {
        ConfigParseException e = error("{\"a\": 1,}");
        assertEquals(1, e.line());
        assertEquals(9, e.column());

        e = error("{\n  \"a\": 1,\n  \"b\": tru\n}");
        assertEquals(3, e.line());
        assertEquals(8, e.column());
        assertTrue(e.getMessage().contains("line 3, column 8"), e.getMessage());

        e = error("{\"a\": 1}\n\n  x");
        assertEquals(3, e.line());
        assertEquals(3, e.column());

        e = error("{\"a\": 1, \"a\": 2}");
        assertEquals(1, e.line());
        assertEquals(10, e.column());
        assertTrue(e.detail().contains("duplicate key 'a'"));

        e = error("{\"a\": \"abc\ndef\"}");
        assertEquals(1, e.line());
        assertEquals(11, e.column());

        e = error("{\"a\": [1, 2");
        assertTrue(e.detail().contains("unterminated"));
    }

    @Test
    void deepNestingIsRejectedNotStackOverflow() {
        String deep = "{\"a\":" + "[".repeat(5000) + "]".repeat(5000) + "}";
        ConfigParseException e = error(deep);
        assertTrue(e.detail().contains("too deep"));
        String ok = "{\"a\":" + "[".repeat(100) + "]".repeat(100) + "}";
        assertNotNull(json(ok));
    }

    @Test
    void prettyOutputMatchesGsonStyle() {
        ConfigSection c = new ConfigSection();
        c.set("name", "Furnace");
        c.set("port", 25565);
        c.set("ratio", 1.5);
        c.set("on", true);
        c.set("nothing", null);
        c.set("db.host", "localhost");
        c.set("db.opts", new ConfigSection());
        c.set("tags", List.of("a", "b"));
        c.set("empty", List.of());
        c.set("nested", List.of(List.of(1, 2), Arrays.asList(1, null)));
        ConfigSection item = new ConfigSection();
        item.set("id", 1);
        c.set("items", List.of(item));
        c.set("text", "q\"\\\n\t\u0001é한");
        c.setComments("name", List.of("ignored"));
        c.setHeader(List.of("ignored"));
        String expected = """
                {
                  "name": "Furnace",
                  "port": 25565,
                  "ratio": 1.5,
                  "on": true,
                  "nothing": null,
                  "db": {
                    "host": "localhost",
                    "opts": {}
                  },
                  "tags": [
                    "a",
                    "b"
                  ],
                  "empty": [],
                  "nested": [
                    [
                      1,
                      2
                    ],
                    [
                      1,
                      null
                    ]
                  ],
                  "items": [
                    {
                      "id": 1
                    }
                  ],
                  "text": "q\\"\\\\\\n\\t\\u0001é한"
                }
                """;
        String out = ConfigFormat.JSON.write(c);
        assertEquals(expected, out);
        assertEquals(c, json(out));
    }

    @Test
    void emptyRootAndNonFiniteDoubles() {
        assertEquals("{}\n", ConfigFormat.JSON.write(new ConfigSection()));
        ConfigSection c = new ConfigSection();
        c.set("x", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> ConfigFormat.JSON.write(c));
    }

    @Test
    void numberRoundTrip() {
        ConfigSection c = new ConfigSection();
        c.set("i", Integer.MIN_VALUE);
        c.set("l", Long.MAX_VALUE);
        c.set("d", 1.0E-10);
        c.set("big", 1.0E300);
        c.set("third", 1.0 / 3);
        assertEquals(c, json(ConfigFormat.JSON.write(c)));
    }
}
