// YAML 파서 동작 검증 — plugin.yml/config.yml 실전 예제, 스칼라 해석, 블록/플로우 문법, 오류 위치
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlParserTest {

    private static ConfigSection yaml(String text) {
        return ConfigFormat.YAML.parse(text);
    }

    private static ConfigParseException error(String text) {
        return assertThrows(ConfigParseException.class, () -> yaml(text));
    }

    // ------------------------------------------------------------ 실전 예제

    static final String PLUGIN_YML = """
            name: FurnaceEssentials
            version: 1.4.2
            main: org.example.essentials.EssentialsPlugin
            api-version: '1.21'
            description: Essential commands for Furnace servers
            author: domi
            authors: [Alice, Bob]
            website: https://example.com/essentials
            load: STARTUP
            depend: [Vault, WorldEdit]
            softdepend:
              - PlaceholderAPI
              - LuckPerms
            loadbefore:
            - Other
            commands:
              home:
                description: Teleport to your home
                usage: /<command> [name]
                aliases: [h, homes]
                permission: essentials.home
                permission-message: "You lack permission: <permission>"
              sethome:
                description: Set a home
                usage: |
                  /<command> <name>
                  Sets a home at your location
            permissions:
              essentials.*:
                description: Gives access to all essentials commands
                default: op
                children:
                  essentials.home: true
                  essentials.sethome: true
              essentials.home:
                description: Allows /home
                default: true
              essentials.admin.reload:
                default: false
            """;

    @Test
    void pluginYml() {
        ConfigSection c = yaml(PLUGIN_YML);
        assertEquals("FurnaceEssentials", c.getString("name"));
        assertEquals("1.4.2", c.getString("version"));
        assertEquals("org.example.essentials.EssentialsPlugin", c.getString("main"));
        assertEquals("1.21", c.getString("api-version"));
        assertEquals("https://example.com/essentials", c.getString("website"));
        assertEquals("STARTUP", c.getString("load"));
        assertEquals(List.of("Alice", "Bob"), c.getStringList("authors"));
        assertEquals(List.of("Vault", "WorldEdit"), c.getStringList("depend"));
        assertEquals(List.of("PlaceholderAPI", "LuckPerms"), c.getStringList("softdepend"));
        assertEquals(List.of("Other"), c.getStringList("loadbefore"));

        assertEquals(List.of("home", "sethome"), List.copyOf(c.getSection("commands").keys(false)));
        assertEquals("/<command> [name]", c.getString("commands.home.usage"));
        assertEquals(List.of("h", "homes"), c.getStringList("commands.home.aliases"));
        assertEquals("You lack permission: <permission>", c.getString("commands.home.permission-message"));
        assertEquals("/<command> <name>\nSets a home at your location\n", c.getString("commands.sethome.usage"));

        // 점이 든 literal 키(권한 노드)도 경로로 접근된다
        ConfigSection perms = c.getSection("permissions");
        assertEquals(List.of("essentials.*", "essentials.home", "essentials.admin.reload"), List.copyOf(perms.keys(false)));
        assertEquals("op", c.getString("permissions.essentials.*.default"));
        assertTrue(c.getBoolean("permissions.essentials.*.children.essentials.home"));
        assertTrue(c.getBoolean("permissions.essentials.home.default"));
        assertFalse(c.getBoolean("permissions.essentials.admin.reload.default", true));
        assertTrue(c.isSection("permissions.essentials.*.children"));
    }

    static final String CONFIG_YML = """
            # Furnace Essentials configuration
            # Edit and run /essentials reload

            # Server messages
            messages:
              # Shown on join
              welcome: "Welcome, {player}: enjoy # your stay!"
              goodbye: 'It''s over: bye # not a comment'
              motd: |
                Line one
                Line two

              footer: >-
                folded text
                continues here

            database:
              host: localhost   # inline comment
              port: 3306
              ssl: yes
              debug: off
              timeout: 2.5
              tables: [users, homes]
              nothing:
              password: ~

            limits:
              max-homes: 5
              mask: 0x1F
              names:
                - alpha
                - 'beta: gamma'
                - "delta # epsilon"
            """;

    @Test
    void configYml() {
        ConfigSection c = yaml(CONFIG_YML);
        assertEquals("Welcome, {player}: enjoy # your stay!", c.getString("messages.welcome"));
        assertEquals("It's over: bye # not a comment", c.getString("messages.goodbye"));
        assertEquals("Line one\nLine two\n", c.getString("messages.motd"));
        assertEquals("folded text continues here", c.getString("messages.footer"));
        assertEquals("localhost", c.getString("database.host"));
        assertEquals(3306, c.getInt("database.port"));
        assertEquals(Boolean.TRUE, c.get("database.ssl"));
        assertEquals(Boolean.FALSE, c.get("database.debug"));
        assertEquals(2.5, c.getDouble("database.timeout"));
        assertEquals(List.of("users", "homes"), c.getStringList("database.tables"));
        assertTrue(c.contains("database.nothing"));
        assertNull(c.get("database.nothing"));
        assertTrue(c.contains("database.password"));
        assertNull(c.get("database.password"));
        assertEquals(31, c.getInt("limits.mask"));
        assertEquals(List.of("alpha", "beta: gamma", "delta # epsilon"), c.getStringList("limits.names"));

        assertEquals(List.of("Furnace Essentials configuration", "Edit and run /essentials reload"), c.getHeader());
        assertEquals(List.of("Server messages"), c.getComments("messages"));
        assertEquals(List.of("Shown on join"), c.getComments("messages.welcome"));
        assertEquals("inline comment", c.getInlineComment("database.host"));
        assertEquals(java.util.Arrays.asList((String) null), c.getComments("database"));
    }

    // ------------------------------------------------------------ 스칼라

    @Test
    void booleansAndNulls() {
        ConfigSection c = yaml("""
                a: true
                b: False
                c: yes
                d: No
                e: ON
                f: off
                g: ~
                h: null
                i: Null
                j:
                k: ''
                l: 'yes'
                m: "true"
                n: y
                o: nope
                """);
        assertEquals(Boolean.TRUE, c.get("a"));
        assertEquals(Boolean.FALSE, c.get("b"));
        assertEquals(Boolean.TRUE, c.get("c"));
        assertEquals(Boolean.FALSE, c.get("d"));
        assertEquals(Boolean.TRUE, c.get("e"));
        assertEquals(Boolean.FALSE, c.get("f"));
        for (String k : new String[] {"g", "h", "i", "j"}) {
            assertTrue(c.contains(k), k);
            assertNull(c.get(k), k);
        }
        assertEquals("", c.get("k"));
        assertEquals("yes", c.get("l"));
        assertEquals("true", c.get("m"));
        assertEquals("y", c.get("n"));
        assertEquals("nope", c.get("o"));
    }

    @Test
    void numbers() {
        ConfigSection c = yaml("""
                dec: 42
                neg: -7
                plus: +3
                zero: 0
                hex: 0x1F
                hexneg: -0xff
                oct1: 0o17
                oct2: 017
                bin: 0b101
                under: 1_000_000
                big: 9999999999
                huge: 12345678901234567890
                float: 3.14
                negfloat: -0.5
                lead: .5
                trail: 5.
                exp: 1e3
                exp2: 1.5E-3
                exp3: 2.5e+2
                inf: .inf
                ninf: -.INF
                nan: .NaN
                notoct: 089
                version: 1.2.3
                date: 2024-01-15
                time: 12:30
                plain: -foo
                """);
        assertEquals(42, c.get("dec"));
        assertEquals(-7, c.get("neg"));
        assertEquals(3, c.get("plus"));
        assertEquals(0, c.get("zero"));
        assertEquals(31, c.get("hex"));
        assertEquals(-255, c.get("hexneg"));
        assertEquals(15, c.get("oct1"));
        assertEquals(15, c.get("oct2"));
        assertEquals(5, c.get("bin"));
        assertEquals(1_000_000, c.get("under"));
        assertEquals(9999999999L, c.get("big"));
        assertInstanceOf(Long.class, c.get("big"));
        assertEquals("12345678901234567890", c.get("huge"));
        assertEquals(3.14, c.get("float"));
        assertEquals(-0.5, c.get("negfloat"));
        assertEquals(0.5, c.get("lead"));
        assertEquals(5.0, c.get("trail"));
        assertEquals(1000.0, c.get("exp"));
        assertEquals(0.0015, c.get("exp2"));
        assertEquals(250.0, c.get("exp3"));
        assertEquals(Double.POSITIVE_INFINITY, c.get("inf"));
        assertEquals(Double.NEGATIVE_INFINITY, c.get("ninf"));
        assertTrue(Double.isNaN((Double) c.get("nan")));
        assertEquals("089", c.get("notoct"));
        assertEquals("1.2.3", c.get("version"));
        assertEquals("2024-01-15", c.get("date"));
        assertEquals("12:30", c.get("time"));
        assertEquals("-foo", c.get("plain"));
    }

    @Test
    void quotedScalars() {
        ConfigSection c = yaml("""
                dq: "tab\\there\\nnewline \\"quoted\\" back\\\\slash"
                uni: "\\u00e9\\x41\\u3042\\U0001F600"
                sq: 'it''s'
                sqmulti: 'a
                  b

                  c'
                dqmulti: "first
                  second \\
                  third"
                colon: "a: b"
                hash: 'a #b'
                keep: "  padded  "
                """);
        assertEquals("tab\there\nnewline \"quoted\" back\\slash", c.get("dq"));
        assertEquals("\u00e9A\u3042\uD83D\uDE00", c.get("uni"));
        assertEquals("it's", c.get("sq"));
        assertEquals("a b\nc", c.get("sqmulti"));
        assertEquals("first second third", c.get("dqmulti"));
        assertEquals("a: b", c.get("colon"));
        assertEquals("a #b", c.get("hash"));
        assertEquals("  padded  ", c.get("keep"));
    }

    @Test
    void plainMultiLine() {
        ConfigSection c = yaml("""
                text: this is
                  a multi line
                  plain scalar

                  with a blank
                next: x
                top: 1
                """);
        assertEquals("this is a multi line plain scalar\nwith a blank", c.get("text"));
        assertEquals("x", c.get("next"));
        assertEquals(1, c.get("top"));
    }

    @Test
    void plainScalarSpecials() {
        ConfigSection c = yaml("""
                url: http://example.com:8080/path?x=1#frag
                win: C:\\Users\\me
                colon: a:b
                percent: %player% joined
                at: @everyone
                spaced key name: v
                emoji: hi 😀
                """);
        assertEquals("http://example.com:8080/path?x=1#frag", c.get("url"));
        assertEquals("C:\\Users\\me", c.get("win"));
        assertEquals("a:b", c.get("colon"));
        assertEquals("%player% joined", c.get("percent"));
        assertEquals("@everyone", c.get("at"));
        assertEquals("v", c.get("spaced key name"));
        assertEquals("hi 😀", c.get("emoji"));
    }

    // ------------------------------------------------------------ 블록 스칼라

    @Test
    void blockScalars() {
        ConfigSection c = yaml("""
                lit: |
                  line1
                   indented
                  line3

                strip: |-
                  no newline
                keep: |+
                  kept

                folded: >
                  one
                  two

                  three
                    more indented
                  four
                foldstrip: >-
                  a
                  b
                indent: |2
                    two extra
                  base
                empty: |
                after: ok
                """);
        assertEquals("line1\n indented\nline3\n", c.get("lit"));
        assertEquals("no newline", c.get("strip"));
        assertEquals("kept\n\n", c.get("keep"));
        assertEquals("one two\nthree\n  more indented\nfour\n", c.get("folded"));
        assertEquals("a b", c.get("foldstrip"));
        assertEquals("  two extra\nbase\n", c.get("indent"));
        assertEquals("", c.get("empty"));
        assertEquals("ok", c.get("after"));
    }

    @Test
    void blockScalarInSequenceAndComment() {
        ConfigSection c = yaml("""
                items:
                  - |
                    first
                    block
                  - >-
                    folded
                    item
                  - plain
                key: | # trailing comment
                  body #notcomment
                """);
        assertEquals(List.of("first\nblock\n", "folded item", "plain"), c.getList("items"));
        assertEquals("body #notcomment\n", c.get("key"));
        assertEquals("trailing comment", c.getInlineComment("key"));
    }

    // ------------------------------------------------------------ 컬렉션

    @Test
    void sequences() {
        ConfigSection c = yaml("""
                same:
                - a
                - b
                nested:
                  -
                    - x
                    - y
                  - - p
                    - q
                maps:
                  - name: one
                    value: 1
                  - name: two
                    sub:
                      deep: true
                  -
                    name: three
                mixed:
                  - 1
                  - two
                  - 3.5
                  - true
                  - ~
                """);
        assertEquals(List.of("a", "b"), c.getList("same"));
        assertEquals(List.of(List.of("x", "y"), List.of("p", "q")), c.getList("nested"));
        List<ConfigSection> maps = c.getSectionList("maps");
        assertEquals(3, maps.size());
        assertEquals("one", maps.get(0).getString("name"));
        assertEquals(1, maps.get(0).getInt("value"));
        assertTrue(maps.get(1).getBoolean("sub.deep"));
        assertEquals("three", maps.get(2).getString("name"));
        assertEquals(java.util.Arrays.asList(1, "two", 3.5, true, null), c.getList("mixed"));
    }

    @Test
    void flowCollections() {
        ConfigSection c = yaml("""
                seq: [a, 'b c', "d,e", 1, 2.5, true, ~]
                map: {a: 1, b: [x, y], c: {d: e}}
                empty1: []
                empty2: {}
                multi: [
                  one,   # comment
                  two,
                  three,
                ]
                mm: {
                  k1: v1,
                  "k 2": v 2
                }
                json: {"a": 1, "b": [true, null]}
                nokey: {a, b: 2}
                pair: [k: v]
                nested: [[1, 2], [3, [4]]]
                url: [http://x.com/a, b:c]
                """);
        assertEquals(java.util.Arrays.asList("a", "b c", "d,e", 1, 2.5, true, null), c.getList("seq"));
        assertEquals(1, c.getInt("map.a"));
        assertEquals(List.of("x", "y"), c.getStringList("map.b"));
        assertEquals("e", c.getString("map.c.d"));
        assertEquals(List.of(), c.getList("empty1"));
        assertTrue(c.getSection("empty2").isEmpty());
        assertEquals(List.of("one", "two", "three"), c.getList("multi"));
        assertEquals("v1", c.getString("mm.k1"));
        assertEquals("v 2", c.getString("mm.k 2"));
        assertEquals(1, c.getInt("json.a"));
        assertEquals(java.util.Arrays.asList(true, null), c.getList("json.b"));
        assertNull(c.get("nokey.a"));
        assertTrue(c.contains("nokey.a"));
        assertEquals(2, c.getInt("nokey.b"));
        assertEquals("v", ((ConfigSection) c.getList("pair").get(0)).getString("k"));
        assertEquals(List.of(List.of(1, 2), List.of(3, List.of(4))), c.getList("nested"));
        assertEquals(List.of("http://x.com/a", "b:c"), c.getList("url"));
    }

    @Test
    void keys() {
        ConfigSection c = yaml("""
                "quoted key": 1
                'single': 2
                "with: colon": 3
                a.b.c: 4
                on: 5
                yes: 6
                1: 7
                """);
        assertEquals(1, c.getInt("quoted key"));
        assertEquals(2, c.getInt("single"));
        assertEquals(3, c.getInt("with: colon"));
        // 점이 든 키는 literal 키로 저장되고 점 경로로도 읽힌다
        assertTrue(c.keys(false).contains("a.b.c"));
        assertEquals(4, c.getInt("a.b.c"));
        // 키는 항상 문자열 (SnakeYAML 은 on/yes 를 Boolean 으로 바꿔 버리지만 여기서는 원문 유지)
        assertEquals(5, c.getInt("on"));
        assertEquals(6, c.getInt("yes"));
        assertEquals(7, c.getInt("1"));
    }

    @Test
    void anchorsAliasesAndMerge() {
        ConfigSection c = yaml("""
                base: &base
                  host: localhost
                  port: 25565
                  tags: [a, b]
                dev:
                  <<: *base
                  port: 1234
                prod:
                  <<: [*base, {host: ignored, extra: 1}]
                copy: *base
                scalar: &s hello
                again: *s
                list: &l
                  - 1
                  - 2
                list2: *l
                """);
        assertEquals("localhost", c.getString("dev.host"));
        assertEquals(1234, c.getInt("dev.port"));
        assertEquals(List.of("a", "b"), c.getStringList("dev.tags"));
        assertEquals("localhost", c.getString("prod.host"));
        assertEquals(1, c.getInt("prod.extra"));
        assertEquals(25565, c.getInt("prod.port"));
        assertEquals(c.getSection("base"), c.getSection("copy"));
        assertNotSame(c.getSection("base"), c.getSection("copy"));
        assertEquals("hello", c.getString("again"));
        assertEquals(List.of(1, 2), c.getList("list2"));
        assertFalse(c.contains("dev.<<"));
        assertTrue(error("a: *missing").detail().contains("undefined alias"));
    }

    @Test
    void tags() {
        ConfigSection c = yaml("""
                s: !!str 123
                i: !!int "42"
                f: !!float 3
                b: !!bool yes
                """);
        assertEquals("123", c.get("s"));
        assertEquals(42, c.get("i"));
        assertEquals(3.0, c.get("f"));
        assertEquals(Boolean.TRUE, c.get("b"));
    }

    // ------------------------------------------------------------ 문서 구조

    @Test
    void emptyAndCommentOnlyDocuments() {
        assertTrue(yaml("").isEmpty());
        assertTrue(yaml("   \n\n  \n").isEmpty());
        assertTrue(yaml("---\n").isEmpty());
        assertTrue(yaml("{}").isEmpty());
        assertTrue(yaml("~\n").isEmpty());
        ConfigSection c = yaml("# only\n# comments\n\n# more\n");
        assertTrue(c.isEmpty());
        assertEquals(java.util.Arrays.asList("only", "comments", null, "more"), c.getHeader());
    }

    @Test
    void documentMarkers() {
        ConfigSection c = yaml("%YAML 1.1\n---\na: 1\nb: 2\n...\nthis: is ignored\n");
        assertEquals(1, c.getInt("a"));
        assertEquals(2, c.getInt("b"));
        assertFalse(c.contains("this"));
        assertEquals(1, yaml("--- # start\na: 1\n").getInt("a"));
        assertThrows(ConfigParseException.class, () -> yaml("a: 1\n---\nb: 2\n"));
    }

    @Test
    void crlfAndBom() {
        ConfigSection c = yaml("\uFEFF# head\r\n\r\na: 1\r\nb:\r\n  - x\r\n  - y\r\nc: |\r\n  l1\r\n  l2\r\n");
        assertEquals(1, c.getInt("a"));
        assertEquals(List.of("x", "y"), c.getStringList("b"));
        assertEquals("l1\nl2\n", c.getString("c"));
        assertEquals(List.of("head"), c.getHeader());
    }

    @Test
    void unicodeAndLongStrings() {
        String korean = "안녕하세요 세계 — 마인크래프트 서버";
        ConfigSection c = yaml("메시지: " + korean + "\n목록:\n  - 하나\n  - \"둘: 셋\"\n");
        assertEquals(korean, c.getString("메시지"));
        assertEquals(List.of("하나", "둘: 셋"), c.getStringList("목록"));

        String big = "x".repeat(200_000);
        ConfigSection b = yaml("big: " + big + "\nquoted: \"" + big + "\"\nlit: |\n  " + big + "\n");
        assertEquals(big, b.getString("big"));
        assertEquals(big, b.getString("quoted"));
        assertEquals(big + "\n", b.getString("lit"));
    }

    @Test
    void largeDocumentIsLinear() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50_000; i++) sb.append("key").append(i).append(": value ").append(i).append('\n');
        sb.append("list:\n");
        for (int i = 0; i < 50_000; i++) sb.append("  - item").append(i).append('\n');
        long t0 = System.nanoTime();
        ConfigSection c = yaml(sb.toString());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(50_001, c.size());
        assertEquals(50_000, c.getList("list").size());
        assertTrue(ms < 5_000, "parse took " + ms + "ms");
    }

    // ------------------------------------------------------------ 오류

    @Test
    void tabIndentationIsRejectedWithPosition() {
        ConfigParseException e = error("a:\n\tb: 1\n");
        assertEquals(2, e.line());
        assertEquals(1, e.column());
        assertTrue(e.getMessage().contains("tab"), e.getMessage());
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void tabsAfterContentAreFine() {
        ConfigSection c = yaml("a:\t1\nb: 2\t# c\n");
        assertEquals(1, c.getInt("a"));
        assertEquals(2, c.getInt("b"));
    }

    @Test
    void duplicateKeysAreErrors() {
        ConfigParseException e = error("a: 1\nb: 2\na: 3\n");
        assertEquals(3, e.line());
        assertEquals(1, e.column());
        assertTrue(e.detail().contains("duplicate key 'a'"));
        assertTrue(error("x:\n  a: 1\n  a: 2\n").detail().contains("duplicate"));
        assertTrue(error("m: {a: 1, a: 2}\n").detail().contains("duplicate"));
    }

    @Test
    void syntaxErrorsHavePositions() {
        ConfigParseException e = error("a:\n  b: 1\n c: 2\n");
        assertEquals(3, e.line());
        assertTrue(e.detail().contains("indentation"));

        e = error("a: b: c\n");
        assertEquals(1, e.line());
        assertEquals(5, e.column());
        assertTrue(e.detail().contains("mapping values are not allowed"));

        e = error("a: [1, 2\nb: 3\n");
        assertTrue(e.detail().contains("expected") || e.detail().contains("unterminated"), e.detail());

        e = error("a: \"unterminated\nb: 2\n");
        assertEquals(1, e.line());
        assertEquals(4, e.column());

        e = error("just a scalar line\n");
        assertTrue(e.detail().contains("mapping"));

        e = error("a: 1\nnot a mapping entry\n");
        assertEquals(2, e.line());

        e = error("- a\n- b\n");
        assertTrue(e.detail().contains("root must be a mapping"));

        e = error("a: \"bad \\q escape\"\n");
        assertEquals(1, e.line());
        assertTrue(e.detail().contains("escape"));

        e = error("a: 'x' trailing\n");
        assertEquals(1, e.line());

        e = error("a:\n  - 1\n -2\n");
        assertEquals(3, e.line());

        e = error("a: - b\n");
        assertTrue(e.detail().contains("block sequence"));
    }

    @Test
    void errorMessageShowsSnippet() {
        ConfigParseException e = error("name: ok\nbroken: [1, 2\n");
        assertTrue(e.getMessage().contains("line "), e.getMessage());
        ConfigParseException withSource = e.withSource("config.yml");
        assertTrue(withSource.getMessage().startsWith("config.yml: "));
        assertEquals("config.yml", withSource.source());
    }

    @Test
    void hostileInputsFailCleanly() {
        StringBuilder deepBlock = new StringBuilder();
        for (int i = 0; i < 1000; i++) deepBlock.append(" ".repeat(i)).append("k:\n");
        assertTrue(error(deepBlock.toString()).detail().contains("too deeply"));

        String deepFlow = "a: " + "[".repeat(5000) + "]".repeat(5000) + "\n";
        assertTrue(error(deepFlow).detail().contains("too deeply"));

        StringBuilder bomb = new StringBuilder("l0: &l0 [x, x, x, x, x, x, x, x, x, x]\n");
        for (int i = 1; i <= 9; i++) {
            bomb.append('l').append(i).append(": &l").append(i).append(" [");
            for (int k = 0; k < 10; k++) bomb.append(k == 0 ? "" : ", ").append("*l").append(i - 1);
            bomb.append("]\n");
        }
        assertTrue(error(bomb.toString()).detail().contains("alias"));
    }

    @Test
    void toMapSnapshot() {
        ConfigSection c = yaml("a:\n  b: [1, {c: d}]\n");
        assertEquals(Map.of("a", Map.of("b", List.of(1, Map.of("c", "d")))), c.toMap());
    }
}
