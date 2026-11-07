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
        assertEquals(4, c.regionSectionShift());   // 기본: 구역 16×16 청크
        assertTrue(c.regionThreads() >= 1);
        assertEquals("world", c.levelName());
        assertEquals(4, c.ioThreads());
        assertEquals(6000, c.autosaveTicks());
        assertEquals(100, c.chunkEvictionGraceTicks());
    }

    @Test
    void load_overridesFromFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "server-name=My Server\ntick-nanos=25000000\nport=25500\ncompression-threshold=-1\nview-distance=8\nentity-view-distance=12\nregion-section-shift=5\nregion-threads=2\n"
                + "level-name=myworld\nio-threads=2\nautosave-ticks=1200\nchunk-eviction-grace-ticks=40\n");
        Config c = ConfigLoader.load(f);
        assertEquals("My Server", c.serverName());
        assertEquals(25_000_000L, c.tickNanos());
        assertEquals(25500, c.port());
        assertEquals(-1, c.compressionThreshold());
        assertEquals(8, c.viewDistance());
        assertEquals(12, c.entityViewDistance());
        assertEquals(5, c.regionSectionShift());
        assertEquals(2, c.regionThreads());
        assertEquals("myworld", c.levelName());
        assertEquals(2, c.ioThreads());
        assertEquals(1200, c.autosaveTicks());
        assertEquals(40, c.chunkEvictionGraceTicks());
    }

    @Test
    void load_rejectsInvalidValues_withKeyName(@TempDir Path dir) throws Exception {
        for (String bad : new String[]{"region-section-shift=0", "region-merge-radius=0", "region-threads=0", "view-distance=1", "view-distance=0", "view-distance=-1", "entity-view-distance=0",
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
    void chunksPerTick_defaultsToAuto_acceptsAutoOrNumber(@TempDir Path dir) throws Exception {
        assertEquals(Config.CHUNKS_PER_TICK_AUTO, ConfigLoader.load(dir.resolve("absent.properties")).chunksPerTick());
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "chunks-per-tick= AUTO \n");
        assertEquals(Config.CHUNKS_PER_TICK_AUTO, ConfigLoader.load(f).chunksPerTick());
        Files.writeString(f, "chunks-per-tick=16\n");
        assertEquals(16, ConfigLoader.load(f).chunksPerTick());
        Files.writeString(f, "chunks-per-tick=fast\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(e.getMessage().contains("chunks-per-tick"), e.getMessage());
    }

    @Test
    void load_rejectsNonNumeric_andOutOfIntRange(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.properties");
        Files.writeString(f, "port=abc\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(e.getMessage().contains("port"));
        Files.writeString(f, "region-section-shift=99999999999\n");
        e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(e.getMessage().contains("region-section-shift"));
    }

    /** view-distance 상한(32) 초과는 거부하지 않고 32로 조정 + WARN 1건(청크 전송은 어차피 32로 포화, 엔티티 경로는 이 값이 유일한 방어선). */
    @Test
    void load_clampsViewDistanceAbove32_withSingleWarning(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        for (String v : new String[]{"33", "64", "1000", "2147483647"}) {
            Files.writeString(f, "view-distance=" + v + "\n");
            java.util.List<String> warns = new java.util.ArrayList<>();
            Config c = ConfigLoader.load(f, warns::add);
            assertEquals(32, c.viewDistance(), v);
            assertEquals(1, warns.size(), v);
            assertTrue(warns.get(0).contains("view-distance") && warns.get(0).contains(v), warns.get(0));
        }
    }

    /** 경계: 2·32는 그대로 수락(경고 없음). 변이(>=3, <=31 등)가 살아남지 않게 고정한다. */
    @Test
    void load_acceptsViewDistanceBoundaries_withoutWarning(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        for (int v : new int[]{2, 3, 16, 31, 32}) {
            Files.writeString(f, "view-distance=" + v + "\n");
            java.util.List<String> warns = new java.util.ArrayList<>();
            assertEquals(v, ConfigLoader.load(f, warns::add).viewDistance());
            assertTrue(warns.isEmpty(), "경고 없어야 함: " + v);
        }
    }

    /** 형식 오류는 조정 대상이 아니다(거부). */
    @Test
    void load_rejectsMalformedViewDistance(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        for (String v : new String[]{"abc", "", "16.0", "2147483648"}) {
            Files.writeString(f, "view-distance=" + v + "\n");
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f), v);
            assertTrue(e.getMessage().contains("view-distance"), e.getMessage());
        }
    }

    /** 엔티티 가시 거리 = min(entity-view-distance, view-distance) — 조정된 view-distance 덕에 상한 32 이하로 묶인다. */
    @Test
    void entityRange_isBoundedByClampedViewDistance(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "view-distance=64\nentity-view-distance=2147483647\n");
        Config c = ConfigLoader.load(f, w -> { });
        assertTrue(Math.min(c.entityViewDistance(), c.viewDistance()) <= Config.MAX_VIEW_DISTANCE);
    }

    /** entity-view-distance 가 view-distance 보다 커도 거부하지 않는다(기본값 8 > 5 도 그렇다 — World 가 min 으로 자른다). */
    @Test
    void load_allowsEntityViewDistanceAboveViewDistance(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "view-distance=2\nentity-view-distance=8\n");
        assertDoesNotThrow(() -> ConfigLoader.load(f, w -> { }));
        Files.writeString(f, "view-distance=32\nentity-view-distance=2147483647\n");
        assertDoesNotThrow(() -> ConfigLoader.load(f, w -> { }));
    }

    /** 저장소 루트의 실제 furnace.properties(.gitignore 대상)가 있으면 새 검증을 통과해야 서버가 기동한다. 없으면 '통과'가 아니라 건너뜀으로 드러낸다. */
    @Test
    void load_acceptsRepositoryFurnaceProperties() {
        Path real = Path.of("furnace.properties");
        Assumptions.assumeTrue(Files.exists(real), "furnace.properties 없음(.gitignore 대상)");
        assertDoesNotThrow(() -> ConfigLoader.load(real, w -> { }));
    }

    @Test
    void bungeecord_defaultOff_parsesBoolean_andRejectsVelocityTogether(@TempDir Path dir) throws Exception {
        assertFalse(ConfigLoader.load(dir.resolve("absent.properties")).bungeecord(), "기본은 꺼짐(Spigot settings.bungeecord=false)");
        Path f = dir.resolve("furnace.properties");
        Files.writeString(f, "bungeecord=TRUE\n");
        assertTrue(ConfigLoader.load(f).bungeecord());
        Files.writeString(f, "bungeecord=yes\n");
        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(bad.getMessage().contains("bungeecord"), bad.getMessage());
        Files.writeString(f, "bungeecord=true\nvelocity-secret=s3cret\n");
        IllegalArgumentException both = assertThrows(IllegalArgumentException.class, () -> ConfigLoader.load(f));
        assertTrue(both.getMessage().contains("bungeecord") && both.getMessage().contains("velocity-secret"), both.getMessage());
        Files.writeString(f, "bungeecord=false\nvelocity-secret=s3cret\n");
        assertEquals("s3cret", ConfigLoader.load(f).velocitySecret());
    }
}
