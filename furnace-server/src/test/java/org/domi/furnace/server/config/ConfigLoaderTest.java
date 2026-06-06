// 설정 로더의 기본값/오버라이드 동작 테스트
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void load_returnsDefaults_whenFileMissing(@TempDir Path dir) {
        Config c = ConfigLoader.load(dir.resolve("absent.properties"));
        assertEquals("Furnace Server", c.serverName());
        assertEquals(50_000_000L, c.tickNanos());
    }

    @Test
    void load_overridesFromFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
    }
}
