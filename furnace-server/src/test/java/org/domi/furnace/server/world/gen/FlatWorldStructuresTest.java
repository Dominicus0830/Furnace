// A06-04(뒤쪽): 평지(FlatLevelSource)의 structure_overrides 구조물 — 기본 프리셋은 요새·마을, 설정에 없으면 레지스트리 전부.
// 높이는 FlatLevelSource.getBaseHeight(층), 지그소 경계는 차원 높이(-64..320) 기준이라 평지 지표(-60) 마을이 선다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.domi.furnace.server.world.gen.structure.TerrainAccess;
import org.junit.jupiter.api.Test;

class FlatWorldStructuresTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGS = RegistrySet.load("registries/1.21.1.txt");

    private static NoiseWorldSource flat(List<String> overrides) {
        return NoiseWorldSource.flat(1234L, REG, REGS,
                new NoiseWorldSource.FlatSpec(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false, overrides));
    }

    private static final List<String> DEFAULT = List.of("minecraft:strongholds", "minecraft:villages");

    /** 기본 평지: 가장 가까운 마을 둘레에 층 위 블록(집·길)이 있다. 지형 높이 질의는 층 꼭대기. */
    @Test
    void defaultFlatGeneratesVillage() {
        NoiseWorldSource gen = flat(DEFAULT);
        assertEquals(-61, gen.firstOccupiedHeight(TerrainAccess.HM_WORLD_SURFACE_WG, 123, -456), "잔디 층 y");
        assertEquals(-61, gen.firstOccupiedHeight(TerrainAccess.HM_OCEAN_FLOOR_WG, 0, 0));
        assertEquals(REG.defaultStateId("minecraft:grass_block"), gen.baseBlock(5, -61, 5));
        assertTrue(ChunkSection.isAir(gen.baseBlock(5, -60, 5)));
        var vil = gen.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, -60, 0, 100);
        assertNotNull(vil, "평지에서 마을을 못 찾는다");
        int cx = vil.x() >> 4, cz = vil.z() >> 4, above = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ChunkColumn col = gen.generateWithFeatures(cx + dx, cz + dz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -60; y < -36; y++) {
                if (!ChunkSection.isAir(col.getBlock(x, y, z))) above++;
            }
        }
        assertTrue(above > 100, "마을 " + vil + " 둘레 층 위 블록 " + above);
    }

    /** structure_overrides 가 정한 세트만: 기본 프리셋에는 약탈자 전초기지가 없고, 설정에 없으면(레지스트리 전부) 있다. 요새는 기본에 있다. */
    @Test
    void overridesSelectStructureSets() {
        var outpost = List.of("minecraft:pillager_outpost");
        assertNull(flat(DEFAULT).structureLocator().findNearest(outpost, 0, -60, 0, 100));
        assertNotNull(flat(null).structureLocator().findNearest(outpost, 0, -60, 0, 100));
        assertNotNull(flat(DEFAULT).structureLocator().findNearest(List.of("minecraft:stronghold"), 0, -60, 0, 100));
        assertNull(flat(List.of()).structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, -60, 0, 100));
    }

    /** server.properties generator-settings: layers 가 없으면 코덱 실패 → 기본 프리셋 전체, 있으면 structure_overrides 가 없을 때 필드도 없다. */
    @Test
    void generatorSettingsFollowVanillaCodec() {
        var noLayers = com.google.gson.JsonParser.parseString("{\"biome\":\"minecraft:desert\",\"features\":true}").getAsJsonObject();
        var def = SuperflatWorldSource.flatSpecOf(SuperflatWorldSource.settingsNbt(noLayers));
        assertEquals("minecraft:plains", def.biome());
        assertFalse(def.features());
        assertEquals(DEFAULT, def.structureOverrides());
        var withLayers = com.google.gson.JsonParser.parseString(
                "{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":3}],\"biome\":\"minecraft:desert\"}").getAsJsonObject();
        var all = SuperflatWorldSource.flatSpecOf(SuperflatWorldSource.settingsNbt(withLayers));
        assertEquals("minecraft:desert", all.biome());
        assertNull(all.structureOverrides(), "structure_overrides 가 없으면 레지스트리 전부");
        var tooTall = com.google.gson.JsonParser.parseString("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":4065}]}").getAsJsonObject();
        // 층 하나가 intRange(0,4064) 밖이면 그 층만 빠진다(resultOrPartial). 높이 합 > 4064 는 부분 결과 그대로.
        assertEquals(List.of(), SuperflatWorldSource.flatSpecOf(SuperflatWorldSource.settingsNbt(tooTall)).layers());
    }
}
