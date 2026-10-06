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
        assertEquals(25565, c.port());
        assertEquals(256, c.compressionThreshold());
        assertEquals(5, c.viewDistance());
        assertEquals(8, c.entityViewDistance());
        assertEquals(16, c.regionSize());
        assertTrue(c.regionThreads() >= 1);
        assertEquals("world", c.levelName());
        assertEquals(4, c.ioThreads());
        assertEquals(6000, c.autosaveTicks());
        assertEquals(100, c.chunkEvictionGraceTicks());
    }

    @Test
    void load_overridesFromFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\nport=25500\ncompression-threshold=-1\nview-distance=8\nentity-view-distance=12\nregion-size=8\nregion-threads=2\n"
                + "level-name=myworld\nio-threads=2\nautosave-ticks=1200\nchunk-eviction-grace-ticks=40\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
        assertEquals(25500, c.port());
        assertEquals(-1, c.compressionThreshold());
        assertEquals(8, c.viewDistance());
        assertEquals(12, c.entityViewDistance());
        assertEquals(8, c.regionSize());
        assertEquals(2, c.regionThreads());
        assertEquals("myworld", c.levelName());
        assertEquals(2, c.ioThreads());
        assertEquals(1200, c.autosaveTicks());
        assertEquals(40, c.chunkEvictionGraceTicks());
    }

    @Test
    void load_rejectsInvalidValues_withKeyName(@TempDir Path dir) throws Exception {
        for (String bad : new String[]{"region-size=0", "region-threads=0", "view-distance=1", "view-distance=99",
                "port=70000", "tick-nanos=0", "compression-threshold=-5", "io-threads=0", "autosave-ticks=0",
                "chunk-eviction-grace-ticks=0", "chunks-per-tick=0", "spawn-chunk-radius=-1", "level-name= "}) {
            Path f = dir.resolve("bad.properties");
            Files.writeString(f, bad + "\n");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> ConfigLoader.load(f), bad);
            assertTrue(e.getMessage().contains(bad.substring(0, bad.indexOf('='))), e.getMessage());
        }
    }

    @Test
    void load_rejectsNonNumeric_andOutOfIntRange(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.properties");
        Files.writeString(f, "port=abc\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(e.getMessage().contains("port"));
        Files.writeString(f, "region-size=99999999999\n");
        e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(e.getMessage().contains("region-size"));
    }

    /** 저장소의 실제 furnace.properties 가 새 검증을 통과해야 서버가 기동한다. */
    @Test
    void load_acceptsRepositoryFurnaceProperties() {
        Path real = Path.of("furnace.properties");
        if (!Files.exists(real)) return; // 테스트 작업 디렉터리에 없으면 건너뜀
        assertDoesNotThrow(() -> ConfigLoader.load(real));
    }
}
