// level.dat·session.lock 이 바닐라와 같은 구조로 읽히고 쓰이며, 모르는 필드를 보존하는지.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LevelStorageTest {

    @Test
    void readsVanillaLevelDat(@TempDir Path dir) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/storage/vanilla-1.21.1-level.dat")) {
            Files.copy(in, dir.resolve("level.dat"));
        }
        try (LevelStorage s = LevelStorage.open(dir)) {
            LevelData d = s.loadLevelData();
            assertEquals(1234L, d.seed);
            assertFalse(d.generateFeatures);
            assertEquals(-400, d.spawnX);
            assertEquals(65, d.spawnY);
            assertEquals(-62, d.spawnZ);
            assertEquals(GameMode.SURVIVAL, d.gameType);
            assertEquals(Difficulty.EASY, d.difficulty);
            assertEquals(1299L, d.gameTime);
            assertEquals("world", d.levelName);
            assertEquals(List.of("vanilla"), d.enabledPacks);
            assertEquals(List.of("bundle", "trade_rebalance"), d.disabledPacks);
            assertEquals(LevelData.DEFAULT_GAME_RULES, d.gameRules);
            // 바닐라가 쓴 DragonFight 관문 순서 = 우리 계산(LegacyRandom(seed) 셔플)
            NbtCompound out = d.toNbt(0L);
            NbtCompound fresh = LevelData.defaultDragonFight(1234L);
            assertArrayEquals((int[]) fresh.tags().get("Gateways"), (int[]) out.getCompound("DragonFight").tags().get("Gateways"));
        }
    }

    @Test
    void saveLoadRoundTripKeepsUnknownFields(@TempDir Path dir) throws Exception {
        try (LevelStorage s = LevelStorage.open(dir)) {
            assertFalse(s.hasWorldData());
            LevelData d = LevelData.create("world", 42L, true, false, GameMode.CREATIVE, false, Difficulty.HARD,
                    List.of("vanilla"), List.of("bundle", "trade_rebalance"));
            d.spawnX = 10; d.spawnY = 70; d.spawnZ = -5; d.gameTime = 500; d.dayTime = 13000; d.initialized = true;
            s.saveLevelData(d);
            assertFalse(Files.exists(dir.resolve("level.dat_old")));
            s.saveLevelData(d);
            assertTrue(Files.exists(dir.resolve("level.dat_old")), "두 번째 저장부터 이전 파일을 _old 로 보관");

            // 다른 서버가 넣은 필드를 흉내 낸다
            NbtCompound root;
            try (InputStream in = Files.newInputStream(dir.resolve("level.dat"))) {
                root = NbtIo.readGzip(in);
            }
            root.getCompound("Data").putString("Bukkit.Version", "x");
            try (var out = Files.newOutputStream(dir.resolve("level.dat"))) {
                NbtIo.writeGzip(out, root, "");
            }
            LevelData back = s.loadLevelData();
            assertEquals(42L, back.seed);
            assertEquals(GameMode.CREATIVE, back.gameType);
            assertEquals(Difficulty.HARD, back.difficulty);
            assertEquals(500L, back.gameTime);
            assertEquals(13000L, back.dayTime);
            assertEquals(10, back.spawnX);
            assertTrue(back.initialized);
            NbtCompound data = back.toNbt(1L);
            assertEquals("x", data.getString("Bukkit.Version"));
            assertEquals(3955, data.getInt("DataVersion"));
            assertEquals(19133, data.getInt("version"));
            assertEquals("1.21.1", data.getCompound("Version").getString("Name"));
            assertTrue(data.getCompound("WorldGenSettings").getCompound("dimensions").contains("minecraft:the_end"));
        }
    }

    @Test
    void corruptLevelDatFallsBackToOld(@TempDir Path dir) throws Exception {
        try (LevelStorage s = LevelStorage.open(dir)) {
            LevelData d = LevelData.create("world", 7L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY,
                    List.of("vanilla"), List.of());
            s.saveLevelData(d);
            s.saveLevelData(d);
            Files.writeString(dir.resolve("level.dat"), "garbage");
            assertEquals(7L, s.loadLevelData().seed);
        }
    }

    @Test
    void sessionLockIsExclusive(@TempDir Path dir) throws Exception {
        try (LevelStorage s = LevelStorage.open(dir)) {
            assertEquals("☃", Files.readString(dir.resolve("session.lock")));
            assertThrows(DirectoryLock.LockException.class, () -> LevelStorage.open(dir));
        }
        LevelStorage.open(dir).close();
    }
}
