// Paper/Spigot 월드 배치(<level>_nether/DIM-1, <level>_the_end/DIM1)를 바닐라 위치로 복사하고 원본을 보존하는지.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitWorldImportTest {

    private static LevelData newLevel() {
        return LevelData.create("world", 7L, true, false, GameMode.SURVIVAL, false, Difficulty.NORMAL,
                List.of("vanilla"), List.of());
    }

    private static void write(Path p, String content) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    /** Paper 배치를 흉내 낸다: 주 월드 + world_nether/DIM-1 + world_the_end/DIM1(+ 각 level.dat). */
    private static void paperLayout(Path universe, NbtCompound endDragonFight) throws Exception {
        write(universe.resolve("world/region/r.0.0.mca"), "overworld");
        write(universe.resolve("world_nether/DIM-1/region/r.0.0.mca"), "nether-region");
        write(universe.resolve("world_nether/DIM-1/entities/r.0.0.mca"), "nether-entities");
        write(universe.resolve("world_nether/DIM-1/poi/r.-1.0.mca"), "nether-poi");
        write(universe.resolve("world_nether/DIM-1/data/raids_nether.dat"), "raids");
        write(universe.resolve("world_nether/level.dat"), "not-read");
        write(universe.resolve("world_nether/session.lock"), "lock");
        write(universe.resolve("world_the_end/DIM1/region/r.0.0.mca"), "end-region");
        if (endDragonFight != null) {
            NbtCompound data = new NbtCompound();
            data.putCompound("DragonFight", endDragonFight);
            NbtCompound root = new NbtCompound();
            root.putCompound("Data", data);
            try (OutputStream out = Files.newOutputStream(universe.resolve("world_the_end/level.dat"))) {
                NbtIo.writeGzip(out, root, "");
            }
        }
    }

    @Test
    void copiesBothDimensionsAndKeepsOriginals(@TempDir Path universe) throws Exception {
        NbtCompound fight = new NbtCompound();
        fight.putBoolean("DragonKilled", true);
        fight.putBoolean("PreviouslyKilled", true);
        paperLayout(universe, fight);
        Path worldDir = universe.resolve("world");
        LevelData level = newLevel();

        var r = BukkitWorldImport.run(universe, "world", worldDir, level);

        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.nether());
        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.end());
        assertTrue(r.dragonFightImported());
        assertEquals("nether-region", Files.readString(worldDir.resolve("DIM-1/region/r.0.0.mca")));
        assertEquals("nether-entities", Files.readString(worldDir.resolve("DIM-1/entities/r.0.0.mca")));
        assertEquals("nether-poi", Files.readString(worldDir.resolve("DIM-1/poi/r.-1.0.mca")));
        assertEquals("raids", Files.readString(worldDir.resolve("DIM-1/data/raids_nether.dat")));
        assertEquals("end-region", Files.readString(worldDir.resolve("DIM1/region/r.0.0.mca")));
        assertFalse(Files.exists(worldDir.resolve("DIM-1" + BukkitWorldImport.TMP_SUFFIX)));
        assertTrue(Files.readString(worldDir.resolve("DIM-1/" + BukkitWorldImport.MARKER)).contains("world_nether"));
        assertFalse(Files.exists(universe.resolve("world_nether/DIM-1/" + BukkitWorldImport.MARKER)));
        assertFalse(Files.exists(worldDir.resolve("DIM-1/session.lock")));
        // 원본은 그대로
        assertEquals("nether-region", Files.readString(universe.resolve("world_nether/DIM-1/region/r.0.0.mca")));
        assertEquals("end-region", Files.readString(universe.resolve("world_the_end/DIM1/region/r.0.0.mca")));
        assertTrue(Files.exists(universe.resolve("world_nether/session.lock")));
        // 엔드 드래곤 전투는 엔드 월드 level.dat 에서
        NbtCompound got = level.dragonFightData();
        assertTrue(got.getBoolean("DragonKilled"));
        assertTrue(got.getBoolean("PreviouslyKilled"));
    }

    @Test
    void secondRunDoesNothing(@TempDir Path universe) throws Exception {
        paperLayout(universe, null);
        Path worldDir = universe.resolve("world");
        BukkitWorldImport.run(universe, "world", worldDir, newLevel());
        // Furnace 가 그 사이 네더에 새 청크를 썼다 — 다시 덮어쓰면 안 된다
        Files.writeString(worldDir.resolve("DIM-1/region/r.0.0.mca"), "furnace-wrote");

        var r = BukkitWorldImport.run(universe, "world", worldDir, newLevel());

        assertEquals(BukkitWorldImport.Outcome.SKIPPED_EXISTING, r.nether());
        assertEquals(BukkitWorldImport.Outcome.SKIPPED_EXISTING, r.end());
        assertFalse(r.dragonFightImported());
        assertEquals("furnace-wrote", Files.readString(worldDir.resolve("DIM-1/region/r.0.0.mca")));
    }

    @Test
    void existingVanillaDimensionWins(@TempDir Path universe) throws Exception {
        paperLayout(universe, null);
        Path worldDir = universe.resolve("world");
        write(worldDir.resolve("DIM-1/region/r.5.5.mca"), "vanilla-nether");

        var r = BukkitWorldImport.run(universe, "world", worldDir, newLevel());

        assertEquals(BukkitWorldImport.Outcome.SKIPPED_EXISTING, r.nether());
        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.end());
        assertFalse(Files.exists(worldDir.resolve("DIM-1/region/r.0.0.mca")), "기존 바닐라 네더에 섞지 않는다");
        assertEquals("vanilla-nether", Files.readString(worldDir.resolve("DIM-1/region/r.5.5.mca")));
    }

    @Test
    void emptyVanillaFolderIsReplaced(@TempDir Path universe) throws Exception {
        paperLayout(universe, null);
        Path worldDir = universe.resolve("world");
        Files.createDirectories(worldDir.resolve("DIM-1/region"));   // 파일 없는 빈 폴더만

        var r = BukkitWorldImport.run(universe, "world", worldDir, newLevel());

        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.nether());
        assertEquals("nether-region", Files.readString(worldDir.resolve("DIM-1/region/r.0.0.mca")));
    }

    @Test
    void leftoverTempFromInterruptedCopyIsRedone(@TempDir Path universe) throws Exception {
        paperLayout(universe, null);
        Path worldDir = universe.resolve("world");
        write(worldDir.resolve("DIM-1" + BukkitWorldImport.TMP_SUFFIX + "/region/r.0.0.mca"), "half");

        var r = BukkitWorldImport.run(universe, "world", worldDir, newLevel());

        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.nether());
        assertEquals("nether-region", Files.readString(worldDir.resolve("DIM-1/region/r.0.0.mca")));
        assertFalse(Files.exists(worldDir.resolve("DIM-1" + BukkitWorldImport.TMP_SUFFIX)));
    }

    @Test
    void vanillaLayoutUntouched(@TempDir Path universe) throws Exception {
        Path worldDir = universe.resolve("world");
        write(worldDir.resolve("DIM-1/region/r.0.0.mca"), "v");
        LevelData level = newLevel();

        var r = BukkitWorldImport.run(universe, "world", worldDir, level);

        assertEquals(BukkitWorldImport.Outcome.NONE, r.nether());
        assertEquals(BukkitWorldImport.Outcome.NONE, r.end());
        assertFalse(r.anyImported());
        assertNull(level.dragonFight);
        assertFalse(Files.exists(worldDir.resolve("DIM1")));
    }

    @Test
    void usesLevelNameForBukkitFolders(@TempDir Path universe) throws Exception {
        write(universe.resolve("survival_nether/DIM-1/region/r.0.0.mca"), "n");
        write(universe.resolve("world_nether/DIM-1/region/r.0.0.mca"), "other");
        Path worldDir = universe.resolve("survival");

        var r = BukkitWorldImport.run(universe, "survival", worldDir, newLevel());

        assertEquals(BukkitWorldImport.Outcome.IMPORTED, r.nether());
        assertEquals("n", Files.readString(worldDir.resolve("DIM-1/region/r.0.0.mca")));
    }
}
