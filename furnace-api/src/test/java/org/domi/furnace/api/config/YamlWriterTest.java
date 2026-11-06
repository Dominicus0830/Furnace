// YAML 작성기 검증 — 따옴표 규칙, 블록 스타일 출력, 주석 보존, 로드→수정→저장→로드 왕복
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlWriterTest {

    private static String write(ConfigSection s) {
        return ConfigFormat.YAML.write(s);
    }

    private static ConfigSection parse(String s) {
        return ConfigFormat.YAML.parse(s);
    }

    @Test
    void cleanBlockOutput() {
        ConfigSection c = new ConfigSection();
        c.set("name", "Furnace");
        c.set("port", 25565);
        c.set("ratio", 1.5);
        c.set("on", true);
        c.set("nothing", null);
        c.set("db.host", "localhost");
        c.set("db.pool.size", 8);
        c.set("tags", List.of("a", "b"));
        c.set("empty-list", List.of());
        c.set("empty-map", new ConfigSection());
        assertEquals("""
                name: Furnace
                port: 25565
                ratio: 1.5
                'on': true
                nothing: null
                db:
                  host: localhost
                  pool:
                    size: 8
                tags:
                  - a
                  - b
                empty-list: []
                empty-map: {}
                """, write(c));
    }

    @Test
    void emptyDocument() {
        assertEquals("{}\n", write(new ConfigSection()));
        assertTrue(parse(write(new ConfigSection())).isEmpty());
    }

    @Test
    void listOfSections() {
        ConfigSection c = new ConfigSection();
        ConfigSection a = new ConfigSection();
        a.set("name", "one");
        a.set("opts.fast", true);
        ConfigSection b = new ConfigSection();
        b.set("name", "two");
        c.set("items", Arrays.asList(a, b, new ConfigSection(), List.of(1, 2), List.of(), "x"));
        String text = write(c);
        assertEquals("""
                items:
                  - name: one
                    opts:
                      fast: true
                  - name: two
                  - {}
                  -
                    - 1
                    - 2
                  - []
                  - x
                """, text);
        assertEquals(c, parse(text));
    }

    private static final String[] TRICKY = {
            "", " ", "  lead", "trail  ", "yes", "No", "on", "OFF", "true", "False", "null", "NULL", "~", "123", "-5", "1.5", "0x1F",
            "017", "1e3", ".inf", "-.inf", ".nan", "1_000", "-", "- a", "-a", "? b", ": c", ":d", "a: b", "a:", "a :b", "a #b", "#c", "a#b",
            "!tag", "&anchor", "*alias", "|", ">", "'q'", "\"dq\"", "%x", "@x", "`x", "[a]", "{a}", "a,b", ",a", "a]b", "=", "<<", "---",
            "...", "--- x", "multi\nline", "ends\n", "ends\n\n\n", "\nstarts", "a\n\nb", "tab\there", "\ttab", "back\\slash", "a'b", "'", "\"",
            "\u0001ctrl", "\u0085", "\u2028", "line1\n\nline3\n\n\n", "  indented\nline", "x\n   \ny", "a\r\nb", "a\rb", "한국어 텍스트", "문자열: 값",
            "emoji 😀", "http://example.com:80/a?b=c#d", "C:\\dir\\file", "1.2.3", "2024-01-15", "12:30", "key: value # c", "text\nwith: colon\nand #hash",
            "ends with colon:", "\uFEFFbom", "a  b", "#", "'", "x\u007fy"
    };

    @Test
    void trickyStringsRoundTripAsValuesKeysAndListItems() {
        for (String s : TRICKY) {
            ConfigSection c = new ConfigSection();
            c.setLiteral("k", s);
            c.setLiteral(s, "v");
            c.set("list", new ArrayList<>(List.of(s, "z")));
            ConfigSection sec = new ConfigSection();
            sec.setLiteral("inner", s);
            c.set("seq", List.of(sec));
            String text = write(c);
            ConfigSection back;
            try {
                back = parse(text);
            } catch (ConfigParseException e) {
                fail("cannot re-parse for value " + repr(s) + ":\n" + text + "\n" + e.getMessage());
                return;
            }
            assertEquals(c, back, "value " + repr(s) + " via:\n" + text);
        }
    }

    private static String repr(String s) {
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    @Test
    void quotingStyle() {
        ConfigSection c = new ConfigSection();
        c.set("a", "yes");
        c.set("b", "123");
        c.set("c", "plain text");
        c.set("d", "has: colon");
        c.set("e", "it's");
        c.set("f", "");
        c.set("g", "bell\u0007");
        c.set("h", "line1\nline2\n");
        c.set("i", "line1\nline2");
        c.set("j", "a\n b");
        c.set("k", "한국어");
        assertEquals("""
                a: 'yes'
                b: '123'
                c: plain text
                d: 'has: colon'
                e: it's
                f: ''
                g: "bell\\u0007"
                h: |
                  line1
                  line2
                i: |-
                  line1
                  line2
                j: |-
                  a
                   b
                k: 한국어
                """, write(c));
    }

    @Test
    void numbersAndSpecialDoubles() {
        ConfigSection c = new ConfigSection();
        c.set("i", 7);
        c.set("l", 5_000_000_000L);
        c.set("d", 0.1);
        c.set("big", 1.0E20);
        c.set("small", 1.0E-7);
        c.set("inf", Double.POSITIVE_INFINITY);
        c.set("ninf", Double.NEGATIVE_INFINITY);
        c.set("nan", Double.NaN);
        c.set("neg0", -0.0);
        c.set("f", 1.5f);
        String text = write(c);
        ConfigSection back = parse(text);
        assertEquals(c, back, text);
        assertTrue(text.contains("inf: .inf"));
        assertTrue(text.contains("nan: .nan"));
    }

    // ------------------------------------------------------------ 주석 / 왕복

    static final String COMMENTED = """
            # Header line 1
            # Header line 2

            # About server
            server:
              # The port
              port: 25565 # default port
              motd: "Hello: #world"

              # Spacer comment above debug
              debug: no
            # About list
            names:
              - a
              - b
            multi: |
              keep
              this
            after: 1
            # trailing footer
            """;

    @Test
    void commentsAreParsedAndWrittenBack() {
        ConfigSection c = parse(COMMENTED);
        assertEquals(List.of("Header line 1", "Header line 2"), c.getHeader());
        assertEquals(List.of("About server"), c.getComments("server"));
        assertEquals(List.of("The port"), c.getComments("server.port"));
        assertEquals("default port", c.getInlineComment("server.port"));
        assertEquals(Arrays.asList(null, "Spacer comment above debug"), c.getComments("server.debug"));
        assertEquals(List.of("About list"), c.getComments("names"));
        assertEquals(List.of("trailing footer"), c.getFooter());

        String out = write(c);
        assertEquals("""
                # Header line 1
                # Header line 2

                # About server
                server:
                  # The port
                  port: 25565 # default port
                  motd: 'Hello: #world'

                  # Spacer comment above debug
                  debug: false
                # About list
                names:
                  - a
                  - b
                multi: |
                  keep
                  this
                after: 1
                # trailing footer
                """, out);
        // 한 번 더 왕복해도 동일 (고정점)
        assertEquals(out, write(parse(out)));
    }

    @Test
    void loadModifySaveLoad() {
        ConfigSection c = parse(CONFIG_FOR_ROUNDTRIP);
        c.set("database.port", 3307);
        c.set("database.name", "furnace");
        c.set("features.fly", true);
        c.setComments("features", List.of("New feature flags"));
        c.setComments("features.fly", List.of("Allow flying", "(ops only)"));
        c.setInlineComment("database.port", "changed");
        c.set("limits.names", List.of("x", "y: z", "#w"));
        c.remove("messages.goodbye");

        String saved = write(c);
        ConfigSection reloaded = parse(saved);
        assertEquals(c, reloaded);
        assertEquals(c.keys(true), reloaded.keys(true));
        assertEquals(List.copyOf(c.keys(true)), List.copyOf(reloaded.keys(true)));

        assertEquals(c.getHeader(), reloaded.getHeader());
        assertEquals(List.of("Shown on join"), reloaded.getComments("messages.welcome"));
        assertEquals(List.of("New feature flags"), reloaded.getComments("features"));
        assertEquals(List.of("Allow flying", "(ops only)"), reloaded.getComments("features.fly"));
        assertEquals("changed", reloaded.getInlineComment("database.port"));
        assertEquals("inline comment", reloaded.getInlineComment("database.host"));
        assertEquals(3307, reloaded.getInt("database.port"));
        assertFalse(reloaded.contains("messages.goodbye"));
        assertEquals("Line one\nLine two\n", reloaded.getString("messages.motd"));
        assertTrue(saved.contains("# Shown on join"));
        assertTrue(saved.contains("# Allow flying"));
    }

    static final String CONFIG_FOR_ROUNDTRIP = """
            # Furnace config
            # second header line

            # messages
            messages:
              # Shown on join
              welcome: "Welcome, {player}: enjoy # your stay!"
              goodbye: 'It''s over'
              motd: |
                Line one
                Line two
            database:
              host: localhost # inline comment
              port: 3306
              ssl: yes
              tables: [users, homes]
            limits:
              max: 5
            """;

    @Test
    void writeIsDeterministicAndStable() {
        ConfigSection c = parse(CONFIG_FOR_ROUNDTRIP);
        String once = write(c);
        String twice = write(parse(once));
        assertEquals(once, twice);
        assertEquals(c, parse(once));
    }

    @Test
    void sequenceItemCommentsOfFirstKey() {
        ConfigSection c = new ConfigSection();
        ConfigSection item = new ConfigSection();
        item.set("id", 1);
        item.set("name", "a");
        item.setComments("id", List.of("first item"));
        c.set("items", List.of(item));
        String text = write(c);
        assertEquals("""
                items:
                  # first item
                  - id: 1
                    name: a
                """, text);
        assertEquals(c, parse(text));
    }
}
