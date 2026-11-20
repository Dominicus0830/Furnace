// T-219 기본 세 월드의 생성기·바이옴 공급자 설정 읽기, 이름 규칙, 바탕 생성기 바꾸기(만든 뒤에는 거절).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.worldgen.DefaultWorldGeneration;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultWorldGeneratorsTest {

    @Test
    void settingsReadGeneratorAndProviderKeys() {
        Properties p = new Properties();
        p.setProperty("world-generator.overworld", " Terra:default ");
        p.setProperty("world-biome-provider.the_end", "Biomes");
        p.setProperty("world-generator.the_nether", "");
        p.setProperty("world-generator.custom", "Ignored");   // 기본 세 차원만
        WorldSettings s = WorldSettings.from(p);
        assertEquals("Terra:default", s.generator("minecraft:overworld"));
        assertEquals("", s.biomeProvider("minecraft:overworld"));
        assertEquals("Biomes", s.biomeProvider("minecraft:the_end"));
        assertTrue(s.pluginGenerated("minecraft:overworld", "world"));
        assertFalse(s.pluginGenerated("minecraft:the_nether", "world_nether"), "빈 값은 바닐라");
        assertTrue(s.pluginGenerated("minecraft:the_end", "world_the_end"));
        assertEquals(2, s.generators().size() + s.biomeProviders().size());
        assertEquals(WorldSettings.DEFAULT, WorldSettings.from(new Properties()));
    }

    /** T-222: 이름별 키는 월드 이름 그대로(점·빈칸 포함, 대소문자 구분), 기본 세 월드는 차원 키가 이름별 키보다 앞선다. */
    @Test
    void namedKeysAreExactWorldNames() {
        Properties p = new Properties();
        p.setProperty("world-generator.name.my.world", " Terra:x ");
        p.setProperty("world-generator.name.Spaced Name", "A");
        p.setProperty("world-biome-provider.name.my.world", "Biomes");
        p.setProperty("world-generator.name.", "Nameless");   // 이름이 없으면 무시
        p.setProperty("world-generator.name.empty", " ");
        p.setProperty("world-generator.name.world", "ByName");
        p.setProperty("world-generator.name.world_nether", "NetherByName");
        p.setProperty("world-generator.the_nether", "NetherByDim");
        WorldSettings s = WorldSettings.from(p);
        assertEquals("Terra:x", s.generatorByName("my.world"));
        assertEquals("A", s.generatorByName("Spaced Name"));
        assertEquals("", s.generatorByName("spaced name"), "대소문자 구분");
        assertEquals("Biomes", s.biomeProviderByName("my.world"));
        assertEquals("", s.biomeProviderByName("Spaced Name"));
        assertEquals("", s.generatorByName("empty"));
        assertEquals("", s.generatorByName(""));
        // 기본 월드: 차원 키가 이기고, 없으면 그 월드의 Bukkit 식 이름으로
        assertEquals("ByName", s.generator("minecraft:overworld", "world"));
        assertEquals("NetherByDim", s.generator("minecraft:the_nether", "world_nether"));
        assertEquals("", s.generator("minecraft:the_end", "world_the_end"));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), DefaultWorldGenerators.pending(s, "world"));
        assertEquals(List.of("minecraft:the_nether"), DefaultWorldGenerators.pending(s, "survival"), "level-name 이 다르면 world 이름 키는 오버월드가 아니다");
    }

    /** T-222(검토): 기본 세 월드가 이름별 키만으로도 물음(Request)에 닿는다 — 차원 키가 있으면 그쪽. */
    @Test
    void defaultWorldRequestsReadNameKeys() {
        Properties p = new Properties();
        p.setProperty("world-generator.name.survival", "ByName");
        p.setProperty("world-biome-provider.name.survival_the_end", "EndBiomes");
        p.setProperty("world-generator.the_end", "ByDim");
        p.setProperty("world-generator.name.survival_the_end", "Shadowed");
        WorldSettings s = WorldSettings.from(p);
        var overworld = new DefaultWorldGenerators.Target(null, "minecraft:overworld", "survival", WorldEnvironment.NORMAL, 1L, -64, 320).request(s);
        assertEquals("ByName", overworld.generator());
        assertEquals("", overworld.biomeProvider());
        var end = new DefaultWorldGenerators.Target(null, "minecraft:the_end", "survival_the_end", WorldEnvironment.THE_END, 1L, 0, 256).request(s);
        assertEquals("ByDim", end.generator(), "차원 키가 이름별 키를 가린다");
        assertEquals("EndBiomes", end.biomeProvider());
    }

    @Test
    void pendingSkipsDimensionsWithFurnaceHooks() {
        Properties p = new Properties();
        p.setProperty("world-generator.overworld", "A");
        p.setProperty("world-generator.the_nether", "B");
        p.setProperty("worldgen-hooks.the_nether", "myplugin:hooks");   // Furnace 훅 키가 이긴다
        p.setProperty("world-biome-provider.the_end", "C");
        assertEquals(List.of("minecraft:overworld", "minecraft:the_end"), DefaultWorldGenerators.pending(WorldSettings.from(p), "world"));
        assertEquals(List.of(), DefaultWorldGenerators.pending(WorldSettings.DEFAULT, "world"));
    }

    @Test
    void bukkitWorldNamesAndEnvironments() {
        assertEquals("survival", DefaultWorldGenerators.worldName("survival", "minecraft:overworld"));
        assertEquals("survival_nether", DefaultWorldGenerators.worldName("survival", "minecraft:the_nether"));
        assertEquals("survival_the_end", DefaultWorldGenerators.worldName("survival", "minecraft:the_end"));
        assertEquals(WorldEnvironment.NORMAL, DefaultWorldGenerators.environmentOf("minecraft:overworld"));
        assertEquals(WorldEnvironment.NETHER, DefaultWorldGenerators.environmentOf("minecraft:the_nether"));
        assertEquals(WorldEnvironment.THE_END, DefaultWorldGenerators.environmentOf("minecraft:the_end"));
    }

    @Test
    void specSplitsOnceLikeCraftServer() {
        assertEquals("Plug", DefaultWorldGeneration.Request.pluginOf("Plug"));
        assertNull(DefaultWorldGeneration.Request.idOf("Plug"));
        assertEquals("Plug", DefaultWorldGeneration.Request.pluginOf("Plug:a:b"));
        assertEquals("a:b", DefaultWorldGeneration.Request.idOf("Plug:a:b"));
        assertEquals("", DefaultWorldGeneration.Request.idOf("Plug:"));
        var r = new DefaultWorldGeneration.Request("world", "minecraft:overworld", WorldEnvironment.NORMAL, 1L, -64, 320, null, " P ");
        assertEquals("", r.generator());
        assertEquals("P", r.biomeProvider());
    }

    @Test
    void replaceBaseOnlyBeforeFirstChunk(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        assertFalse(src.baseUsed());
        var sand = new SuperflatWorldSource(reg, List.of(new SuperflatWorldSource.Layer("minecraft:sand", 3)), 0);
        assertTrue(src.replaceBase(sand));
        assertSame(sand, src.base());
        assertEquals(reg.defaultStateId("minecraft:sand"), src.generate(0, 0).getBlock(0, -62, 0), "바꾼 바탕으로 만든다");
        assertTrue(src.baseUsed());
        assertFalse(src.replaceBase(new SuperflatWorldSource(reg, 0)), "한 청크라도 만든 뒤에는 바꾸지 않는다");
        assertSame(sand, src.base());
        store.closeAll();
    }
}
