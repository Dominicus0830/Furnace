// TOML v1.0.0 파서/작성기 검증 — 공식 README 예제, 문자열/숫자/날짜, 테이블 규칙, 중복 오류, 주석 왕복
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TomlTest {

    private static ConfigSection toml(String text) {
        return ConfigFormat.TOML.parse(text);
    }

    private static ConfigParseException error(String text) {
        return assertThrows(ConfigParseException.class, () -> toml(text), text);
    }

    static final String README = """
            # This is a TOML document

            title = "TOML Example"

            [owner]
            name = "Tom Preston-Werner"
            dob = 1979-05-27T07:32:00-08:00

            [database]
            enabled = true
            ports = [ 8000, 8001, 8002 ]
            data = [ ["delta", "phi"], [3.14] ]
            temp_targets = { cpu = 79.5, case = 72.0 }

            [servers]

            [servers.alpha]
            ip = "10.0.0.1"
            role = "frontend"

            [servers.beta]
            ip = "10.0.0.2"
            role = "backend"
            """;

    @Test
    void officialReadmeExample() {
        ConfigSection c = toml(README);
        assertEquals("TOML Example", c.getString("title"));
        assertEquals("Tom Preston-Werner", c.getString("owner.name"));
        assertEquals(OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 0, ZoneOffset.ofHours(-8)), c.get("owner.dob"));
        assertTrue(c.getBoolean("database.enabled"));
        assertEquals(List.of(8000, 8001, 8002), c.getList("database.ports"));
        assertEquals(List.of(List.of("delta", "phi"), List.of(3.14)), c.getList("database.data"));
        assertEquals(79.5, c.getDouble("database.temp_targets.cpu"));
        assertEquals(72.0, c.getDouble("database.temp_targets.case"));
        assertTrue(c.isSection("servers"));
        assertEquals("10.0.0.1", c.getString("servers.alpha.ip"));
        assertEquals("backend", c.getString("servers.beta.role"));
        assertEquals(List.of("title", "owner", "database", "servers"), List.copyOf(c.keys(false)));
        assertEquals(List.of("This is a TOML document"), c.getHeader());

        // 왕복
        String out = ConfigFormat.TOML.write(c);
        assertEquals(c, toml(out), out);
        assertEquals(out, ConfigFormat.TOML.write(toml(out)));
    }

    @Test
    void keys() {
        ConfigSection c = toml("""
                bare_key = 1
                bare-key = 2
                1234 = 3
                "quoted key" = 4
                'literal key' = 5
                "" = 6
                a . b . c = 7
                site."google.com" = 8
                fruit.apple.color = "red"
                fruit.apple.taste.sweet = true
                """);
        assertEquals(1, c.getInt("bare_key"));
        assertEquals(2, c.getInt("bare-key"));
        assertEquals(3, c.getInt("1234"));
        assertEquals(4, c.getInt("quoted key"));
        assertEquals(5, c.getInt("literal key"));
        assertEquals(6, c.toMap().get(""));
        assertEquals(7, c.getInt("a.b.c"));
        assertEquals(8, c.getInt("site.google.com")); // literal 키 "google.com" 도 경로로 접근된다
        assertEquals("red", c.getString("fruit.apple.color"));
        assertTrue(c.getBoolean("fruit.apple.taste.sweet"));
        assertEquals(c, toml(ConfigFormat.TOML.write(c)));
    }

    @Test
    void strings() {
        ConfigSection c = toml("""
                basic = "I'm a string. \\"You can quote me\\". Name\\tJos\\u00E9\\nLocation\\tSF."
                u8 = "\\U0001F600 \\u3042"
                literal = 'C:\\Users\\nodejs\\templates'
                lit2 = 'Tom "Dubs" Preston-Werner'
                ml1 = \"\"\"
                Roses are red
                Violets are blue\"\"\"
                ml2 = \"\"\"
                The quick brown \\


                  fox jumps over \\
                    the lazy dog.\"\"\"
                ml3 = \"\"\"Here are two quotation marks: "". Simple enough.\"\"\"
                ml4 = \"\"\"Here are three quotation marks: ""\\".\"\"\"
                ml5 = \"\"\"This,\\
                       however, is a "quoted\"\"\"\"
                mll = '''
                I [dw]on't need \\d{2} apples'''
                mll2 = '''Here are fifteen quotation marks: \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"'''
                mll3 = ''''That,' she said, 'is still pointless.''''
                empty = ""
                korean = "한국어 🙂"
                """);
        assertEquals("I'm a string. \"You can quote me\". Name\tJos\u00e9\nLocation\tSF.", c.get("basic"));
        assertEquals("\uD83D\uDE00 \u3042", c.get("u8"));
        assertEquals("C:\\Users\\nodejs\\templates", c.get("literal"));
        assertEquals("Tom \"Dubs\" Preston-Werner", c.get("lit2"));
        assertEquals("Roses are red\nViolets are blue", c.get("ml1"));
        assertEquals("The quick brown fox jumps over the lazy dog.", c.get("ml2"));
        assertEquals("Here are two quotation marks: \"\". Simple enough.", c.get("ml3"));
        assertEquals("Here are three quotation marks: \"\"\".", c.get("ml4"));
        assertEquals("This,however, is a \"quoted\"", c.get("ml5"));
        assertEquals("I [dw]on't need \\d{2} apples", c.get("mll"));
        assertEquals("Here are fifteen quotation marks: \"\"\"\"\"\"\"\"\"\"\"\"\"\"\"", c.get("mll2"));
        assertEquals("'That,' she said, 'is still pointless.'", c.get("mll3"));
        assertEquals("", c.get("empty"));
        assertEquals("한국어 🙂", c.get("korean"));
        assertEquals(c, toml(ConfigFormat.TOML.write(c)));
    }

    @Test
    void integersAndFloats() {
        ConfigSection c = toml("""
                int1 = +99
                int2 = 42
                int3 = 0
                int4 = -17
                int5 = 1_000
                int6 = 5_349_221
                hex1 = 0xDEADBEEF
                hex2 = 0xdead_beef
                oct1 = 0o01234567
                oct2 = 0o755
                bin1 = 0b11010110
                big = 9223372036854775807
                min = -9223372036854775808
                flt1 = +1.0
                flt2 = 3.1415
                flt3 = -0.01
                flt4 = 5e+22
                flt5 = 1e06
                flt6 = -2E-2
                flt7 = 6.626e-34
                flt8 = 224_617.445_991_228
                sf1 = inf
                sf2 = +inf
                sf3 = -inf
                sf4 = nan
                sf5 = +nan
                sf6 = -nan
                z = -0
                fz = -0.0
                """);
        assertEquals(99, c.get("int1"));
        assertEquals(42, c.get("int2"));
        assertEquals(0, c.get("int3"));
        assertEquals(-17, c.get("int4"));
        assertEquals(1000, c.get("int5"));
        assertEquals(5349221, c.get("int6"));
        assertEquals(0xDEADBEEFL, c.get("hex1"));
        assertInstanceOf(Long.class, c.get("hex1"));
        assertEquals(0xDEADBEEFL, c.get("hex2"));
        assertEquals(342391, c.get("oct1"));
        assertEquals(493, c.get("oct2"));
        assertEquals(214, c.get("bin1"));
        assertEquals(Long.MAX_VALUE, c.get("big"));
        assertEquals(Long.MIN_VALUE, c.get("min"));
        assertEquals(1.0, c.get("flt1"));
        assertEquals(3.1415, c.get("flt2"));
        assertEquals(-0.01, c.get("flt3"));
        assertEquals(5e22, c.get("flt4"));
        assertEquals(1e6, c.get("flt5"));
        assertEquals(-0.02, c.get("flt6"));
        assertEquals(6.626e-34, c.get("flt7"));
        assertEquals(224617.445991228, c.get("flt8"));
        assertEquals(Double.POSITIVE_INFINITY, c.get("sf1"));
        assertEquals(Double.POSITIVE_INFINITY, c.get("sf2"));
        assertEquals(Double.NEGATIVE_INFINITY, c.get("sf3"));
        assertTrue(Double.isNaN((Double) c.get("sf4")));
        assertTrue(Double.isNaN((Double) c.get("sf5")));
        assertTrue(Double.isNaN((Double) c.get("sf6")));
        assertEquals(0, c.get("z"));
        assertEquals(-0.0, c.get("fz"));
        ConfigSection back = toml(ConfigFormat.TOML.write(c));
        assertEquals(c, back);
    }

    @Test
    void numberErrors() {
        for (String bad : new String[] {"a = 01", "a = 1__0", "a = _1", "a = 1_", "a = 1.", "a = .5", "a = 1.e5", "a = 0x", "a = 0xG", "a = +0x1",
                "a = 1e", "a = 99999999999999999999", "a = 0xFFFFFFFFFFFFFFFF", "a = 1.5.2", "a = inff", "a = 1 2", "a = tru", "a = True"}) {
            error(bad + "\n");
        }
    }

    @Test
    void dateTimes() {
        ConfigSection c = toml("""
                odt1 = 1979-05-27T07:32:00Z
                odt2 = 1979-05-27T00:32:00-07:00
                odt3 = 1979-05-27T00:32:00.999999-07:00
                odt4 = 1979-05-27 07:32:00Z
                odt5 = 1979-05-27t07:32:00z
                ldt1 = 1979-05-27T07:32:00
                ldt2 = 1979-05-27T00:32:00.999999
                ld1 = 1979-05-27
                lt1 = 07:32:00
                lt2 = 00:32:00.999999
                lt3 = 07:32:00.5
                arr = [1979-05-27, 07:32:00]
                """);
        assertEquals(OffsetDateTime.of(1979, 5, 27, 7, 32, 0, 0, ZoneOffset.UTC), c.get("odt1"));
        assertEquals(OffsetDateTime.of(1979, 5, 27, 0, 32, 0, 0, ZoneOffset.ofHours(-7)), c.get("odt2"));
        assertEquals(OffsetDateTime.of(1979, 5, 27, 0, 32, 0, 999_999_000, ZoneOffset.ofHours(-7)), c.get("odt3"));
        assertEquals(c.get("odt1"), c.get("odt4"));
        assertEquals(c.get("odt1"), c.get("odt5"));
        assertEquals(LocalDateTime.of(1979, 5, 27, 7, 32, 0), c.get("ldt1"));
        assertEquals(LocalDateTime.of(1979, 5, 27, 0, 32, 0, 999_999_000), c.get("ldt2"));
        assertEquals(LocalDate.of(1979, 5, 27), c.get("ld1"));
        assertEquals(LocalTime.of(7, 32, 0), c.get("lt1"));
        assertEquals(LocalTime.of(0, 32, 0, 999_999_000), c.get("lt2"));
        assertEquals(LocalTime.of(7, 32, 0, 500_000_000), c.get("lt3"));
        assertEquals(List.of(LocalDate.of(1979, 5, 27), LocalTime.of(7, 32)), c.getList("arr"));
        assertEquals("1979-05-27", c.getString("ld1"));
        assertEquals(c, toml(ConfigFormat.TOML.write(c)));

        for (String bad : new String[] {"a = 1979-13-01", "a = 1979-02-30", "a = 25:00:00", "a = 07:32", "a = 1979-05-27T07:32:00+0800", "a = 1979-05-27T07:32:00+24:00"}) {
            error(bad + "\n");
        }
    }

    @Test
    void arraysAndInlineTables() {
        ConfigSection c = toml("""
                a1 = [ 1, 2, 3 ]
                a2 = [ "red", "yellow", "green" ]
                a3 = [ [ 1, 2 ], ["a", "b", "c"] ]
                a4 = [ 1, 2.0 ]
                a5 = [
                  "multi",   # comment
                  # whole line comment
                  "line",
                ]
                a6 = []
                a7 = [ { x = 1 }, { x = 2, y = "z" } ]
                t1 = { first = "Tom", last = "Preston-Werner" }
                t2 = { x = 1, y = 2, nested = { z = [1, 2] } }
                t3 = {}
                t4 = { a.b = 1, a.c = 2 }
                """);
        assertEquals(List.of(1, 2, 3), c.getList("a1"));
        assertEquals(List.of("red", "yellow", "green"), c.getList("a2"));
        assertEquals(List.of(List.of(1, 2), List.of("a", "b", "c")), c.getList("a3"));
        assertEquals(List.of(1, 2.0), c.getList("a4"));
        assertEquals(List.of("multi", "line"), c.getList("a5"));
        assertEquals(List.of(), c.getList("a6"));
        assertEquals(2, c.getSectionList("a7").size());
        assertEquals("z", c.getSectionList("a7").get(1).getString("y"));
        assertEquals("Tom", c.getString("t1.first"));
        assertEquals(List.of(1, 2), c.getList("t2.nested.z"));
        assertTrue(c.getSection("t3").isEmpty());
        assertEquals(1, c.getInt("t4.a.b"));
        assertEquals(2, c.getInt("t4.a.c"));
        assertEquals(c, toml(ConfigFormat.TOML.write(c)));
    }

    @Test
    void arrayOfTables() {
        ConfigSection c = toml("""
                [[products]]
                name = "Hammer"
                sku = 738594937

                [[products]]  # empty table within the array

                [[products]]
                name = "Nail"
                sku = 284758393
                color = "gray"

                [[fruits]]
                name = "apple"

                [fruits.physical]  # subtable
                color = "red"
                shape = "round"

                [[fruits.varieties]]  # nested array of tables
                name = "red delicious"

                [[fruits.varieties]]
                name = "granny smith"

                [[fruits]]
                name = "banana"

                [[fruits.varieties]]
                name = "plantain"
                """);
        List<ConfigSection> products = c.getSectionList("products");
        assertEquals(3, products.size());
        assertEquals("Hammer", products.get(0).getString("name"));
        assertEquals(738594937, products.get(0).getInt("sku"));
        assertTrue(products.get(1).isEmpty());
        assertEquals("gray", products.get(2).getString("color"));

        List<ConfigSection> fruits = c.getSectionList("fruits");
        assertEquals(2, fruits.size());
        assertEquals("apple", fruits.get(0).getString("name"));
        assertEquals("red", fruits.get(0).getString("physical.color"));
        assertEquals(2, fruits.get(0).getSectionList("varieties").size());
        assertEquals("granny smith", fruits.get(0).getSectionList("varieties").get(1).getString("name"));
        assertEquals(1, fruits.get(1).getSectionList("varieties").size());
        assertFalse(fruits.get(1).contains("physical"));

        String out = ConfigFormat.TOML.write(c);
        assertEquals(c, toml(out), out);
        assertTrue(out.contains("[[products]]"));
        assertTrue(out.contains("[[fruits.varieties]]"));
    }

    @Test
    void tableRules() {
        // 상위 테이블이 나중에 정의되는 것은 허용
        ConfigSection c = toml("""
                [x.y.z.w]
                a = 1
                [x]
                b = 2
                [x.y]
                c = 3
                """);
        assertEquals(1, c.getInt("x.y.z.w.a"));
        assertEquals(2, c.getInt("x.b"));
        assertEquals(3, c.getInt("x.y.c"));

        // 점 키로 만든 테이블에 하위 테이블 추가는 허용
        ConfigSection d = toml("""
                [fruit]
                apple.color = "red"
                apple.taste.sweet = true

                [fruit.apple.texture]
                smooth = true
                """);
        assertTrue(d.getBoolean("fruit.apple.texture.smooth"));
        assertTrue(d.getBoolean("fruit.apple.taste.sweet"));

        // 빈 키 테이블/최상위 이후 테이블
        ConfigSection e = toml("a = 1\n[b]\nc = 2\n[b.d]\n");
        assertTrue(e.getSection("b.d").isEmpty());
    }

    @Test
    void duplicateAndRedefinitionErrors() {
        assertTrue(error("a = 1\na = 2\n").detail().contains("duplicate key"));
        assertTrue(error("a = 1\nb.c = 1\nb.c = 2\n").detail().contains("duplicate key"));
        assertTrue(error("[a]\nb = 1\n[a]\nc = 2\n").detail().contains("already defined"));
        error("a = 1\n[a]\n");
        error("[a]\n[a.b]\n[a.b]\n");
        error("[fruit]\napple.color = \"red\"\n[fruit.apple]\n");
        error("[fruit]\napple.color = \"red\"\n[fruit.apple.taste]\napple = 1\n[fruit.apple]\n");
        error("a.b = 1\n[a]\n");
        error("a = { b = 1 }\n[a]\n");
        error("a = { b = 1 }\n[a.c]\n");
        error("a = { b = 1 }\na.c = 2\n");
        error("a = [1, 2]\n[[a]]\n");
        error("a = [{ x = 1 }]\n[[a]]\n");
        error("[[a]]\n[a]\n");
        error("[a]\n[[a]]\n");
        error("a = 1\n[a.b]\n");
        error("a = 1\na.b = 2\n");
        error("[a.b]\nc = 1\n[a]\nb.d = 1\n");
        ConfigParseException e = error("[a]\nx = 1\n\n[a]\n");
        assertEquals(4, e.line());
        assertEquals(1, e.column());
    }

    @Test
    void syntaxErrorsHavePositions() {
        ConfigParseException e = error("a = 1\nb = \n");
        assertEquals(2, e.line());
        e = error("a = 1 2\n");
        assertEquals(1, e.line());
        assertEquals(7, e.column());
        e = error("a = \"unterminated\n");
        assertEquals(1, e.line());
        assertEquals(18, e.column()); // 문자열이 줄바꿈을 만난 위치
        e = error("a = \"bad \\q\"\n");
        assertTrue(e.detail().contains("escape"));
        e = error("a = 'x'\nb c = 1\n");
        assertEquals(2, e.line());
        e = error("[a\nb = 1\n");
        assertEquals(1, e.line());
        e = error("a = [1, 2\n");
        assertTrue(e.detail().contains("unterminated"));
        e = error("a = { x = 1,\n y = 2 }\n");
        assertEquals(1, e.line());
        e = error("a = { x = 1, }\n");
        assertTrue(e.detail().contains("trailing comma"));
        error("a = 1 # ok\nb = \"tab\tok\"\nc = \"\u0001\"\n");
        error("\"\"\"multi\"\"\" = 1\n");
        error("a = \"\"\"never closed\n");
        error("a = '''x'''''''\n");
        error("= 1\n");
        error("a b = 1\n");
        error("[]\n");
        error("[a]b = 1\n");
        error("[[a]\n");
        e = error("# c\n\nkey = 'v'\n\u0000bad = 1\n");
        assertEquals(4, e.line());
    }

    @Test
    void crlfLineEndings() {
        ConfigSection c = toml("# h\r\n\r\na = 1\r\n[t]\r\nb = \"\"\"x\r\ny\"\"\"\r\nc = [1,\r\n 2]\r\n");
        assertEquals(1, c.getInt("a"));
        assertEquals("x\ny", c.getString("t.b"));
        assertEquals(List.of(1, 2), c.getList("t.c"));
    }

    @Test
    void emptyAndCommentOnly() {
        assertTrue(toml("").isEmpty());
        assertTrue(toml("   \n\n").isEmpty());
        ConfigSection c = toml("# just\n# comments\n");
        assertTrue(c.isEmpty());
        assertEquals(List.of("just", "comments"), c.getHeader());
    }

    // ------------------------------------------------------------ 작성기

    @Test
    void writerLayout() {
        ConfigSection c = new ConfigSection();
        c.set("title", "Hello \"world\"\n");
        c.set("count", 3);
        c.set("list", List.of(1, 2, 3));
        c.set("server.host", "localhost");
        c.set("server.ports", List.of(80, 443));
        c.set("server.tls.enabled", true);
        ConfigSection a = new ConfigSection();
        a.set("name", "a");
        ConfigSection b = new ConfigSection();
        b.set("name", "b");
        b.set("opts.fast", true);
        c.set("items", List.of(a, b));
        c.set("mixed", java.util.Arrays.asList(1, "x", a));
        c.setLiteral("dotted.key", 1);
        c.setLiteral("sp ace", 2);
        c.set("none", null);
        assertEquals("""
                title = "Hello \\"world\\"\\n"
                count = 3
                list = [1, 2, 3]
                mixed = [1, "x", { name = "a" }]
                "dotted.key" = 1
                "sp ace" = 2

                [server]
                host = "localhost"
                ports = [80, 443]

                [server.tls]
                enabled = true

                [[items]]
                name = "a"

                [[items]]
                name = "b"

                [items.opts]
                fast = true
                """, ConfigFormat.TOML.write(c));
    }

    @Test
    void commentsRoundTrip() {
        String text = """
                # Header

                # Title comment
                title = "x" # inline

                # Server section
                [server]
                # port comment
                port = 25565

                # list of items
                [[items]]
                id = 1
                """;
        ConfigSection c = toml(text);
        assertEquals(List.of("Header"), c.getHeader());
        assertEquals(List.of("Title comment"), c.getComments("title"));
        assertEquals("inline", c.getInlineComment("title"));
        assertEquals(java.util.Arrays.asList((String) null, "Server section"), c.getComments("server"));
        assertEquals(List.of("port comment"), c.getComments("server.port"));
        assertEquals(List.of("list of items"), c.getSectionList("items").get(0).getHeader().stream().filter(s -> s != null).toList());

        String out = ConfigFormat.TOML.write(c);
        assertEquals(text, out);
        assertEquals(c, toml(out));
    }

    @Test
    void nullsAreOmittedByWriter() {
        ConfigSection c = new ConfigSection();
        c.set("a", 1);
        c.set("b", null);
        c.set("l", java.util.Arrays.asList(1, null, 2));
        ConfigSection back = toml(ConfigFormat.TOML.write(c));
        assertFalse(back.contains("b"));
        assertEquals(List.of(1, 2), back.getList("l"));
    }

    @Test
    void containerTablesWithoutValuesGetNoHeader() {
        ConfigSection c = new ConfigSection();
        c.set("a.b.c", 1);
        String out = ConfigFormat.TOML.write(c);
        assertEquals("[a.b]\nc = 1\n", out.strip() + "\n");
        assertEquals(c, toml(out));
    }
}
