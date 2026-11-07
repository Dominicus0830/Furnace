// Paper/Spigot 월드 가져오기 보고서: 가져오지 않은 것(규칙·날씨·모르는 파일), 추가 Bukkit 월드 감지·환경 추론, 한 번만 쓰기.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BukkitWorldReportTest {

    private static LevelData newLevel() {
        return LevelData.create("world", 7L, true, false, GameMode.SURVIVAL, false, Difficulty.NORMAL, List.of("vanilla"), List.of());
    }

    private static void touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
    }

    private static void levelDat(Path world, NbtCompound data) throws Exception {
        Files.createDirectories(world);
        NbtCompound root = new NbtCompound();
        root.putCompound("Data", data);
        try (OutputStream out = Files.newOutputStream(world.resolve("level.dat"))) {
            NbtIo.writeGzip(out, root, "");
        }
    }

    private static NbtCompound gen(String type, String key, NbtCompound settingsOrNull, String settingsId) {
        NbtCompound g = new NbtCompound();
        g.putString("type", type);
        if (settingsOrNull != null) g.putCompound("settings", settingsOrNull);
        if (settingsId != null) g.putString("settings", settingsId);
        NbtCompound dim = new NbtCompound();
        dim.putCompound("generator", g);
        NbtCompound dims = new NbtCompound();
        dims.putCompound(key, dim);
        NbtCompound wgs = new NbtCompound();
        wgs.putCompound("dimensions", dims);
        NbtCompound data = new NbtCompound();
        data.putCompound("WorldGenSettings", wgs);
        return data;
    }

    @Test
    void reportsRulesWeatherAndUnknownFilesOfImportedDimensions(@TempDir Path universe) throws Exception {
        touch(universe.resolve("world/region/r.0.0.mca"));
        touch(universe.resolve("world_nether/DIM-1/region/r.0.0.mca"));
        touch(universe.resolve("world_nether/paper-world.yml"));
        touch(universe.resolve("world_nether/mystery.bin"));
        NbtCompound data = new NbtCompound();
        NbtCompound rules = new NbtCompound();
        rules.putString("keepInventory", "true");
        rules.putString("doDaylightCycle", "true");   // 주 월드 기본값과 같음 — 보고하지 않는다
        data.putCompound("GameRules", rules);
        data.putBoolean("raining", true);
        levelDat(universe.resolve("world_nether"), data);
        LevelData main = newLevel();

        var imported = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        var report = BukkitWorldReport.build(universe, "world", main, imported);

        String all = String.join("\n", report.notImported());
        assertTrue(all.contains("시간·날씨·스폰 지점·월드 보더는 가져오지 않는다"), all);
        assertTrue(all.contains("world_nether/level.dat: 게임 규칙 1개가 주 월드와 다르다") && all.contains("keepInventory"), all);
        assertFalse(all.contains("doDaylightCycle"), all);
        assertTrue(all.contains("날씨(비 true, 천둥 false)"), all);
        assertTrue(all.contains("world_nether/paper-world.yml"), all);
        assertTrue(all.contains("world_nether/mystery.bin: 모르는 파일"), all);
        assertTrue(report.extras().isEmpty());
    }

    @Test
    void detectsExtraBukkitWorldsAndInfersEnvironmentAndGenerator(@TempDir Path universe) throws Exception {
        touch(universe.resolve("world/region/r.0.0.mca"));
        // Multiverse 식 세 월드: 오버월드(평지), 네더, 엔드
        NbtCompound flat = new NbtCompound();
        flat.putString("biome", "minecraft:desert");
        NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound l1 = new NbtCompound();
        l1.putString("block", "minecraft:bedrock");
        l1.putInt("height", 1);
        NbtCompound l2 = new NbtCompound();
        l2.putString("block", "minecraft:sand");
        l2.putInt("height", 3);
        NbtCompound l0 = new NbtCompound();
        l0.putString("block", "minecraft:stone");
        l0.putInt("height", 0);   // 바닐라는 높이 0 층을 허용한다 — 건너뛴다
        layers.add(l1);
        layers.add(l0);
        layers.add(l2);
        flat.putList("layers", layers);
        NbtCompound d1 = gen("minecraft:flat", "minecraft:overworld", flat, null);
        d1.getCompound("WorldGenSettings").putBoolean("generate_features", false);
        touch(universe.resolve("desert/region/r.0.0.mca"));
        levelDat(universe.resolve("desert"), d1);
        touch(universe.resolve("desert_nether/DIM-1/region/r.0.0.mca"));
        levelDat(universe.resolve("desert_nether"), gen("minecraft:noise", "minecraft:the_nether", null, "minecraft:nether"));
        touch(universe.resolve("big/region/r.0.0.mca"));
        levelDat(universe.resolve("big"), gen("minecraft:noise", "minecraft:overworld", null, "minecraft:large_biomes"));
        NbtCompound badFlat = new NbtCompound();
        badFlat.putString("biome", "Not A Biome");
        touch(universe.resolve("brokenflat/region/r.0.0.mca"));
        levelDat(universe.resolve("brokenflat"), gen("minecraft:flat", "minecraft:overworld", badFlat, null));
        touch(universe.resolve("sky_the_end/DIM1/region/r.0.0.mca"));
        levelDat(universe.resolve("sky_the_end"), new NbtCompound());
        // 후보가 아닌 것: 설명서가 있는 Furnace 월드, level.dat 없음, 청크 폴더 없음, 규칙에 안 맞는 이름, 서버 폴더들
        NbtCompound own = new NbtCompound();
        own.putCompound(LevelData.FURNACE_WORLD, new NbtCompound());
        touch(universe.resolve("mine/region/r.0.0.mca"));
        levelDat(universe.resolve("mine"), own);
        touch(universe.resolve("nodat/region/r.0.0.mca"));
        levelDat(universe.resolve("nochunks"), new NbtCompound());
        touch(universe.resolve("bad name/region/r.0.0.mca"));
        levelDat(universe.resolve("bad name"), new NbtCompound());
        touch(universe.resolve("plugins/x/region/r.0.0.mca"));
        levelDat(universe.resolve("plugins"), new NbtCompound());

        var extras = BukkitWorldReport.findExtraWorlds(universe, "world");

        assertEquals(List.of("bad name", "big", "brokenflat", "desert", "desert_nether", "sky_the_end"),
                extras.stream().map(BukkitWorldReport.ExtraWorld::name).toList());
        var byName = extras.stream().collect(java.util.stream.Collectors.toMap(BukkitWorldReport.ExtraWorld::name, e -> e));
        assertNull(byName.get("bad name").creator());
        assertTrue(byName.get("bad name").problem().contains("월드 이름"));
        assertNull(byName.get("brokenflat").creator(), "평지 설정을 못 읽으면 기본 생성기로 대신하지 않고 올리지 않는다");
        assertTrue(byName.get("brokenflat").problem().contains("생성기 설정을 읽지 못해"), byName.get("brokenflat").problem());
        assertEquals(WorldGenerator.LARGE_BIOMES, byName.get("big").creator().generator());
        WorldCreator desert = byName.get("desert").creator();
        assertEquals(WorldEnvironment.NORMAL, desert.environment());
        assertEquals(WorldGenerator.FLAT, desert.generator());
        assertEquals("minecraft:desert", desert.flatBiome());
        assertEquals(2, desert.flatLayers().size());
        assertEquals("minecraft:sand", desert.flatLayers().get(1).block());
        assertFalse(desert.generateStructures());
        assertEquals(WorldEnvironment.NETHER, byName.get("desert_nether").creator().environment());
        assertEquals(WorldGenerator.DEFAULT, byName.get("desert_nether").creator().generator());
        assertEquals(WorldEnvironment.THE_END, byName.get("sky_the_end").creator().environment());
    }

    @Test
    void writesReportOnceAndRefreshesWhenADimensionIsNewlyImported(@TempDir Path universe) throws Exception {
        touch(universe.resolve("world/region/r.0.0.mca"));
        touch(universe.resolve("world_nether/DIM-1/region/r.0.0.mca"));
        touch(universe.resolve("farm/region/r.0.0.mca"));
        levelDat(universe.resolve("farm"), new NbtCompound());
        LevelData main = newLevel();
        Path file = universe.resolve(BukkitWorldReport.REPORT_FILE);

        var first = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        BukkitWorldReport.runIfNeeded(universe, universe, "world", main, first);
        String text = Files.readString(file);
        assertTrue(text.contains("네더: IMPORTED"), text);
        assertTrue(text.contains("farm: NORMAL / DEFAULT"), text);
        assertTrue(text.contains("/worlds load"), text);

        // 두 번째 기동: 이미 가져왔으니 복사도, 보고서 갱신도 없다
        Files.writeString(file, "KEEP");
        var second = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        assertEquals(BukkitWorldImport.Outcome.SKIPPED_EXISTING, second.nether());
        assertFalse(second.anyImported());
        BukkitWorldReport.runIfNeeded(universe, universe, "world", main, second);
        assertEquals("KEEP", Files.readString(file));

        // 엔드가 새로 생기면 보고서를 갱신한다
        touch(universe.resolve("world_the_end/DIM1/region/r.0.0.mca"));
        var third = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        assertTrue(third.anyImported());
        BukkitWorldReport.runIfNeeded(universe, universe, "world", main, third);
        assertTrue(Files.readString(file).contains("엔드: IMPORTED"));
    }

    @Test
    void doesNothingForAPlainFurnaceServer(@TempDir Path universe) throws Exception {
        touch(universe.resolve("world/region/r.0.0.mca"));
        touch(universe.resolve("mining/region/r.0.0.mca"));
        levelDat(universe.resolve("mining"), new NbtCompound());   // Paper 흔적(bukkit.yml·_nether 폴더)이 없으면 후보로 안 본다
        LevelData main = newLevel();
        var res = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        BukkitWorldReport.runIfNeeded(universe, universe, "world", main, res);
        assertFalse(Files.exists(universe.resolve(BukkitWorldReport.REPORT_FILE)));
    }

    @Test
    void bukkitYmlAloneCountsAsATrace(@TempDir Path universe) throws Exception {
        touch(universe.resolve("world/region/r.0.0.mca"));
        touch(universe.resolve("bukkit.yml"));
        touch(universe.resolve("mining/region/r.0.0.mca"));
        levelDat(universe.resolve("mining"), new NbtCompound());
        LevelData main = newLevel();
        var res = BukkitWorldImport.run(universe, "world", universe.resolve("world"), main);
        BukkitWorldReport.runIfNeeded(universe, universe, "world", main, res);
        String text = Files.readString(universe.resolve(BukkitWorldReport.REPORT_FILE));
        assertTrue(text.contains("mining: NORMAL / DEFAULT"), text);
    }
}
