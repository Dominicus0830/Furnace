// 구조물 찾기: 찾은 청크에 실제로 시작점이 생기고, 더 안쪽 격자 테두리에는 없다(findNearestMapStructure 와 같은 나선 순서).
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class StructureLocatorTest {

    @Test
    void nearestVillage_isGeneratedAndNoCloserRingHasOne() {
        NoiseWorldSource gen = NoiseWorldSource.overworld(0L, BlockRegistry.load("blocks/1.21.1.txt"), RegistrySet.load("registries/1.21.1.txt"));
        List<String> ids = StructureRegistry.structureTag("minecraft:village");
        assertNotNull(ids);
        assertTrue(ids.contains("minecraft:village_plains"));
        StructureLocator.Found f = gen.structureLocator().findNearest(ids, 0, 70, 0, 100);
        assertNotNull(f);
        assertEquals(0, f.y());
        assertEquals(0, f.x() & 15);
        assertEquals(0, f.z() & 15);
        int cx = f.x() >> 4, cz = f.z() >> 4;
        // 생성 경로(createStarts)도 그 청크에 같은 구조물 시작점을 만든다
        assertTrue(gen.structureStartsFresh(cx, cz).stream().anyMatch(s -> s.structureId().equals(f.structureId())));
        // 같은 배치의 더 안쪽 격자 테두리에는 마을이 없다
        StructureEngine e = gen.structureEngine();
        StructureRegistry.SetDef villages = e.placementsFor("minecraft:village_plains").get(0);
        RandomSpreadStructurePlacement p = villages.spread();
        int ring = Math.max(Math.abs(Math.floorDiv(cx, p.spacing())), Math.abs(Math.floorDiv(cz, p.spacing())));
        for (int r = 0; r < ring; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                ChunkPos c = p.getPotentialStructureChunk(e.seed(), p.spacing() * dx, p.spacing() * dz);
                assertNull(e.startOf(villages, c.x(), c.z(), gen), "r=" + r + " " + c);
            }
        }
        // 같은 질의는 같은 답
        assertEquals(f, gen.structureLocator().findNearest(ids, 0, 70, 0, 100));
        // 이 월드에서 만들 수 없는 구조물(엔드 도시)은 배치가 없어 null
        assertNull(gen.structureLocator().findNearest(List.of("minecraft:end_city"), 0, 70, 0, 100));
    }

    @Test
    void buriedTreasure_usesLocateOffset() {
        NoiseWorldSource gen = NoiseWorldSource.overworld(0L, BlockRegistry.load("blocks/1.21.1.txt"), RegistrySet.load("registries/1.21.1.txt"));
        StructureLocator.Found f = gen.structureLocator().findNearest(List.of("minecraft:buried_treasure"), 0, 64, 0, 100);
        assertNotNull(f);
        assertEquals(9, f.x() & 15);   // locate_offset [9, 0, 9]
        assertEquals(9, f.z() & 15);
    }
}
