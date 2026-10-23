// 파일 기반 Config 검증 — UTF-8 로드, 원자적 저장, reload, 포맷 감지, 오류에 파일 이름 포함
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileTest {

    @TempDir
    Path dir;

    @Test
    void loadSaveReloadYaml() throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "# 설정\nmessage: 안녕하세요\nport: 25565 # 포트\n", StandardCharsets.UTF_8);
        Config cfg = Config.load(file);
        assertEquals(ConfigFormat.YAML, cfg.format());
        assertEquals("안녕하세요", cfg.root().getString("message"));
        assertEquals(List.of("설정"), cfg.root().getComments("message"));

        cfg.root().set("port", 25566);
        cfg.root().set("extra.list", List.of("a", "b"));
        cfg.save();
        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("# 설정"));
        assertTrue(saved.contains("port: 25566 # 포트"));
        assertTrue(saved.contains("안녕하세요"));

        // 외부 수정 후 reload
        Files.writeString(file, "message: changed\nport: 1\n", StandardCharsets.UTF_8);
        cfg.reload();
        assertEquals("changed", cfg.root().getString("message"));
        assertEquals(1, cfg.root().getInt("port"));
        assertFalse(cfg.root().contains("extra"));
    }

    @Test
    void saveLeavesNoTempFilesAndCreatesParents() throws IOException {
        Path file = dir.resolve("nested/deeper/plugin.toml");
        Config cfg = Config.empty(file);
        assertEquals(ConfigFormat.TOML, cfg.format());
        cfg.root().set("name", "x");
        cfg.save();
        assertEquals("name = \"x\"\n", Files.readString(file));
        cfg.root().set("name", "y");
        cfg.save();
        assertEquals("name = \"y\"\n", Files.readString(file));
        try (Stream<Path> s = Files.list(file.getParent())) {
            assertEquals(List.of("plugin.toml"), s.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void jsonFileAndExplicitFormat() throws IOException {
        Path file = dir.resolve("data.json");
        Files.writeString(file, "{\"a\": [1, 2]}");
        Config cfg = Config.load(file);
        assertEquals(ConfigFormat.JSON, cfg.format());
        assertEquals(List.of(1, 2), cfg.root().getList("a"));

        Path odd = dir.resolve("settings.conf");
        Files.writeString(odd, "k = 1\n");
        assertEquals(ConfigFormat.YAML, Config.empty(odd).format());
        Config t = Config.load(odd, ConfigFormat.TOML);
        assertEquals(1, t.root().getInt("k"));
    }

    @Test
    void missingFile() throws IOException {
        Path file = dir.resolve("missing.yml");
        assertThrows(NoSuchFileException.class, () -> Config.load(file));
        Config cfg = Config.loadOrEmpty(file);
        assertTrue(cfg.root().isEmpty());
        assertFalse(Files.exists(file));
        cfg.root().set("a", 1);
        cfg.save();
        assertEquals("a: 1\n", Files.readString(file));
    }

    @Test
    void parseErrorContainsFileNameAndPosition() throws IOException {
        Path file = dir.resolve("broken.yml");
        Files.writeString(file, "ok: 1\nbad: [1, 2\n");
        ConfigParseException e = assertThrows(ConfigParseException.class, () -> Config.load(file));
        assertEquals("broken.yml", e.source());
        assertTrue(e.getMessage().startsWith("broken.yml: "), e.getMessage());
        assertTrue(e.line() >= 2);
    }

    @Test
    void reloadKeepsDefaultsAndFailedReloadKeepsState() throws IOException {
        Path file = dir.resolve("c.yml");
        Files.writeString(file, "a: 1\n");
        Config cfg = Config.load(file);
        ConfigSection defaults = new ConfigSection();
        defaults.set("a", 0);
        defaults.set("b", 2);
        cfg.setDefaults(defaults);
        assertEquals(2, cfg.root().getInt("b"));
        assertEquals(1, cfg.copyDefaults());
        cfg.save();
        assertEquals("a: 1\nb: 2\n", Files.readString(file));

        Files.writeString(file, "a: 5\n");
        cfg.reload();
        assertEquals(5, cfg.root().getInt("a"));
        assertEquals(2, cfg.root().getInt("b")); // defaults 유지

        Files.writeString(file, "a: [broken\n");
        assertThrows(ConfigParseException.class, cfg::reload);
        assertEquals(5, cfg.root().getInt("a"));
    }

    @Test
    void utf8WithBomIsAccepted() throws IOException {
        Path file = dir.resolve("bom.yml");
        Files.write(file, new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ':', ' ', '1', '\n'});
        assertEquals(1, Config.load(file).root().getInt("a"));
    }
}
