// A05-03 회귀 시험(재현: docs/audit/findings/A05-repro/A05DataVersionReproTest.java.txt 의 newerWorld_isRefusedOrKeepsUnknownBlocks).
// 바닐라 1.21.1 Main 은 판 계열(Version.Series)만 비교해 "This world was created by an incompatible version." 로 멈추고, 더 새 판 월드는 그대로 열어
// 모르는 블록을 공기로 지운다. Paper 는 새 판 청크를 만나면 서버를 멈춘다 — Furnace 는 기동 때 level.dat 판으로 같은 결과를 낸다.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.anvil.NewerDataVersionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A05NewerWorldRefusalTest {

    private static void writeLevel(Path p, Integer dataVersion, Integer versionId, String name, String series) throws Exception {
        NbtCompound data = new NbtCompound();
        if (dataVersion != null) data.putInt("DataVersion", dataVersion);
        NbtCompound version = new NbtCompound();
        if (versionId != null) version.putInt("Id", versionId);
        version.putString("Name", name);
        if (series != null) version.putString("Series", series);
        data.putCompound("Version", version);
        data.putString("LevelName", "world");
        NbtCompound file = new NbtCompound();
        file.putCompound("Data", data);
        try (var os = Files.newOutputStream(p)) { NbtIo.writeGzip(os, file, ""); }
    }

    @Test
    void newerLevelDat_isRefusedAndLeftUntouched(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 4189, 4189, "1.21.4", "main");
        byte[] before = Files.readAllBytes(dir.resolve("level.dat"));
        try (LevelStorage ls = LevelStorage.open(dir)) {
            var e = assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
            assertTrue(e.getMessage().contains("4189"), e.getMessage());
        }
        assertArrayEquals(before, Files.readAllBytes(dir.resolve("level.dat")), "level.dat 을 건드리지 않는다");
        assertFalse(Files.exists(dir.resolve("level.dat_old")), "깨진 파일로 다뤄 level.dat_old 로 되돌리지 않는다");
    }

    @Test
    void newerVersionIdWithoutDataVersion_isRefused(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), null, 4189, "1.21.4", null);
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
        }
    }

    @Test
    void newerLevelDatOld_isRefusedToo(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("level.dat"), new byte[] {1, 2, 3});   // 깨진 level.dat → level.dat_old 로 넘어간다
        writeLevel(dir.resolve("level.dat_old"), 4189, 4189, "1.21.4", "main");
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
        }
    }

    @Test
    void otherSeries_isRefusedLikeVanilla(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 3955, 3955, "1.21.1-ccpreview", "ccpreview");
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
        }
    }

    @Test
    void sameVersion_opens(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 3955, 3955, "1.21.1", "main");
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertNotNull(ls.loadLevelData());
        }
    }

    // ---- A05-04: 옛 판(픽서 없음)은 열지 않는다 ----

    @Test
    void olderLevelDat_isRefusedAndLeftUntouched(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 3700, 3700, "1.20.4", "main");
        byte[] before = Files.readAllBytes(dir.resolve("level.dat"));
        try (LevelStorage ls = LevelStorage.open(dir)) {
            var e = assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
            assertTrue(e.getMessage().contains("3700"), e.getMessage());
        }
        assertArrayEquals(before, Files.readAllBytes(dir.resolve("level.dat")), "level.dat 을 건드리지 않는다");
        assertFalse(Files.exists(dir.resolve("level.dat_old")));
    }

    @Test
    void levelDatWithoutAnyVersion_isRefusedAsOld(@TempDir Path dir) throws Exception {   // 1.9 이전
        writeLevel(dir.resolve("level.dat"), null, null, "?", null);
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertThrows(LevelStorage.IncompatibleWorldException.class, ls::loadLevelData);
        }
    }

    @Test
    void olderWorld_opensWithOverride(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 3700, 3700, "1.20.4", "main");
        System.setProperty(org.domi.furnace.server.world.anvil.OlderDataVersionException.IGNORE_PROPERTY, "true");
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertNotNull(ls.loadLevelData());
        } finally {
            System.clearProperty(org.domi.furnace.server.world.anvil.OlderDataVersionException.IGNORE_PROPERTY);
        }
    }

    @Test
    void paperOverride_opensNewerWorld(@TempDir Path dir) throws Exception {
        writeLevel(dir.resolve("level.dat"), 4189, 4189, "1.21.4", "main");
        System.setProperty(NewerDataVersionException.IGNORE_PROPERTY, "true");
        try (LevelStorage ls = LevelStorage.open(dir)) {
            assertNotNull(ls.loadLevelData());
        } finally {
            System.clearProperty(NewerDataVersionException.IGNORE_PROPERTY);
        }
    }
}
