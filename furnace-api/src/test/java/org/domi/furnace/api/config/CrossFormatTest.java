// 같은 ConfigSection 을 YAML/TOML/JSON 으로 쓰고 다시 읽어도 데이터가 같은지 검증
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossFormatTest {

    private static ConfigSection sample() {
        ConfigSection c = new ConfigSection();
        c.set("name", "Furnace Server");
        c.set("version", "1.21.1");
        c.set("enabled", true);
        c.set("disabled", false);
        c.set("port", 25565);
        c.set("big", 5_000_000_000L);
        c.set("neg", -42);
        c.set("ratio", 0.75);
        c.set("exp", 6.02e23);
        c.set("tiny", 1e-9);
        c.set("zero", 0);
        c.set("motd", "Line one\nLine two\n");
        c.set("quote", "He said \"hi\" and 'bye' \\ backslash");
        c.set("colon", "key: value # not a comment");
        c.set("boolish", "yes");
        c.set("numish", "123");
        c.set("empty-string", "");
        c.set("spaces", "  padded  ");
        c.set("korean", "안녕하세요 — 마인크래프트 🙂");
        c.set("unicode-escape", "tab\there \u0001 ctrl");
        c.set("long", "z".repeat(5000));
        c.set("list.ints", List.of(1, 2, 3));
        c.set("list.strings", List.of("a", "b: c", "#d", "yes", "123"));
        c.set("list.mixed", List.of(1, "two", 3.5, true));
        c.set("list.nested", List.of(List.of(1, 2), List.of("x"), List.of()));
        c.set("list.empty", List.of());
        c.set("db.host", "localhost");
        c.set("db.pool.size", 8);
        c.set("db.pool.timeout", 2.5);
        c.set("db.empty", new ConfigSection());
        c.setLiteral("dotted.key", "literal");
        c.setLiteral("key with spaces", 1);
        ConfigSection a = new ConfigSection();
        a.set("id", 1);
        a.set("tags", List.of("t1", "t2"));
        a.set("sub.flag", true);
        ConfigSection b = new ConfigSection();
        b.set("id", 2);
        c.set("items", new ArrayList<>(List.of(a, b)));
        ConfigSection perm = new ConfigSection();
        perm.setLiteral("essentials.home", true);
        perm.set("default", "op");
        c.set("permissions", perm);
        return c;
    }

    @Test
    void sameDataSurvivesEveryFormat() {
        ConfigSection original = sample();
        for (ConfigFormat f : ConfigFormat.values()) {
            String text = f.write(original);
            ConfigSection back = f.parse(text);
            assertEquals(original, back, f + " output:\n" + text);
            // TOML 은 값 -> 하위 테이블 순으로 재배치하므로 키 순서는 YAML/JSON 만 비교한다
            if (f != ConfigFormat.TOML) {
                assertEquals(List.copyOf(original.keys(true)), List.copyOf(back.keys(true)), f + " key order");
            }
            // 두 번째 왕복은 텍스트까지 동일
            assertEquals(text, f.write(back), f + " is stable");
        }
    }

    @Test
    void convertBetweenFormats() {
        ConfigSection original = sample();
        for (ConfigFormat from : ConfigFormat.values()) {
            ConfigSection viaFrom = from.parse(from.write(original));
            for (ConfigFormat to : ConfigFormat.values()) {
                assertEquals(original, to.parse(to.write(viaFrom)), from + " -> " + to);
            }
        }
    }

    @Test
    void parseFromReader() throws Exception {
        for (ConfigFormat f : ConfigFormat.values()) {
            String text = f.write(sample());
            try (java.io.Reader r = new java.io.StringReader(text)) {
                assertEquals(sample(), f.parse(r));
            }
        }
    }

    @Test
    void formatDetectionByExtension() {
        assertEquals(ConfigFormat.YAML, ConfigFormat.fromFileName("config.yml").orElseThrow());
        assertEquals(ConfigFormat.YAML, ConfigFormat.fromFileName("plugin.YAML").orElseThrow());
        assertEquals(ConfigFormat.TOML, ConfigFormat.fromFileName("furnace-plugin.toml").orElseThrow());
        assertEquals(ConfigFormat.JSON, ConfigFormat.fromFileName("a.b.JSON").orElseThrow());
        assertTrue(ConfigFormat.fromFileName("README").isEmpty());
        assertTrue(ConfigFormat.fromFileName("file.txt").isEmpty());
        assertTrue(ConfigFormat.fromFileName("trailingdot.").isEmpty());
        assertEquals(ConfigFormat.TOML, ConfigFormat.fromPath(java.nio.file.Path.of("x", "config.toml")).orElseThrow());
        assertEquals("yml", ConfigFormat.YAML.defaultExtension());
        assertEquals(List.of("yml", "yaml"), ConfigFormat.YAML.extensions());
    }

    @Test
    void sameDescriptorInAllFormats() {
        String yaml = """
                name: Demo
                version: 1.0.0
                main: org.demo.Main
                depend: [A, B]
                """;
        String toml = """
                name = "Demo"
                version = "1.0.0"
                main = "org.demo.Main"
                depend = ["A", "B"]
                """;
        String json = """
                {"name": "Demo", "version": "1.0.0", "main": "org.demo.Main", "depend": ["A", "B"]}
                """;
        ConfigSection y = ConfigFormat.YAML.parse(yaml);
        assertEquals(y, ConfigFormat.TOML.parse(toml));
        assertEquals(y, ConfigFormat.JSON.parse(json));
    }
}
