// level.dat 의 Border* 키 9개: 새 월드 기본값, 저장→로드 왕복(진행 중인 보간 포함), 모르는 필드 보존, 키 이름·태그 타입.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.WorldBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LevelDataBorderTest {

    private static LevelData fresh() {
        return LevelData.create("world", 1L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of("vanilla"), List.of());
    }

    @Test
    void newWorldWritesVanillaDefaults() {
        NbtCompound d = fresh().toNbt(0L);
        assertEquals(0.0, d.tags().get("BorderCenterX"));
        assertEquals(0.0, d.tags().get("BorderCenterZ"));
        assertEquals(5.9999968E7, d.tags().get("BorderSize"));
        assertEquals(5.0, d.tags().get("BorderSafeZone"));
        assertEquals(5.0, d.tags().get("BorderWarningBlocks"));
        assertEquals(15.0, d.tags().get("BorderWarningTime"));
        assertEquals(5.9999968E7, d.tags().get("BorderSizeLerpTarget"));
        assertEquals(0L, d.tags().get("BorderSizeLerpTime"));
        assertEquals(0.2, d.tags().get("BorderDamagePerBlock"));
    }

    @Test
    void fieldsRoundTripThroughNbtAndKeepUnknownFields() {
        LevelData a = fresh();
        a.worldBorder = new WorldBorder.Settings(12.5, -7.0, 0.75, 3.0, 8, 20, 300.0, 45_000L, 500.0);
        NbtCompound nbt = a.toNbt(0L);
        nbt.putString("Bukkit.Version", "x");
        LevelData b = LevelData.fromNbt(nbt);
        assertEquals(a.worldBorder, b.worldBorder);
        NbtCompound out = b.toNbt(1L);
        assertEquals(12.5, out.tags().get("BorderCenterX"));
        assertEquals(-7.0, out.tags().get("BorderCenterZ"));
        assertEquals(300.0, out.tags().get("BorderSize"));
        assertEquals(45_000L, out.tags().get("BorderSizeLerpTime"));
        assertEquals(500.0, out.tags().get("BorderSizeLerpTarget"));
        assertEquals(3.0, out.tags().get("BorderSafeZone"));
        assertEquals(0.75, out.tags().get("BorderDamagePerBlock"));
        assertEquals(8.0, out.tags().get("BorderWarningBlocks"));
        assertEquals(20.0, out.tags().get("BorderWarningTime"));
        assertEquals("x", out.getString("Bukkit.Version"));
    }

    @Test
    void storageRoundTripOfRunningBorder(@TempDir Path dir) throws Exception {
        try (LevelStorage s = LevelStorage.open(dir)) {
            WorldBorder border = new WorldBorder();
            border.setCenter(100.0, 200.0);
            border.setWarningTime(1);
            border.lerpSizeBetween(1000.0, 100.0, 3_600_000L);
            LevelData d = fresh();
            d.worldBorder = border.createSettings();
            s.saveLevelData(d);
            LevelData back = s.loadLevelData();
            assertEquals(100.0, back.worldBorder.centerX());
            assertEquals(200.0, back.worldBorder.centerZ());
            assertEquals(100.0, back.worldBorder.sizeLerpTarget());
            assertEquals(1, back.worldBorder.warningTime());
            assertTrue(back.worldBorder.sizeLerpTime() > 3_000_000L && back.worldBorder.sizeLerpTime() <= 3_600_000L);
            WorldBorder restored = new WorldBorder();
            restored.applySettings(back.worldBorder);
            assertEquals(WorldBorder.Status.SHRINKING, restored.getStatus());
            assertEquals(100.0, restored.getLerpTarget());
        }
    }

    @Test
    void missingKeysFallBackToVanillaDefaultSettings() {
        NbtCompound d = fresh().toNbt(0L);
        for (String k : List.of("BorderCenterX", "BorderCenterZ", "BorderSize", "BorderSafeZone", "BorderWarningBlocks",
                "BorderWarningTime", "BorderSizeLerpTarget", "BorderSizeLerpTime", "BorderDamagePerBlock")) {
            d.tags().remove(k);
        }
        assertEquals(WorldBorder.DEFAULT_SETTINGS, LevelData.fromNbt(d).worldBorder);
    }
}
