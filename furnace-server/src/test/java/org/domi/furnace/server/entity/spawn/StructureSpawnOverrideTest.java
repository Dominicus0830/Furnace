// 구조물 스폰 덮어쓰기: 늪 오두막 조각 안에서만 마녀·고양이, 요새 바닥(네더 벽돌)은 요새 적.
package org.domi.furnace.server.entity.spawn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.ChunkStructures;
import org.domi.furnace.server.world.gen.structure.Direction;
import org.domi.furnace.server.world.gen.structure.StructurePiece;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.junit.jupiter.api.Test;

class StructureSpawnOverrideTest {

    static final class Box extends StructurePiece {
        Box(int x0, int y0, int z0, int x1, int y1, int z1) { super(new BoundingBox(x0, y0, z0, x1, y1, z1), Direction.NORTH); }
    }

    private static ChunkLevel levelWith(ChunkColumn col) {
        return new ChunkLevel(1000) {
            @Override protected ChunkColumn chunk(int x, int z) { return x == 0 && z == 0 ? col : null; }
            @Override protected void markDirty(int x, int z) {}
            @Override protected void broadcastToChunk(int x, int z, Packet p) {}
            @Override protected LevelServices services() { return LevelServices.DEFAULT; }
        };
    }

    private static ChunkColumn columnWith(StructureStart s) {
        ChunkColumn col = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0).generate(0, 0);
        col.structures(ChunkStructures.generated(0, 0, List.of(s), List.of(s)));
        return col;
    }

    @Test
    void swampHut_witchOnlyInsidePiece() {
        // 오두막 조각 두 개(사이에 빈 칸) — bounding_box: piece
        StructureStart hut = new StructureStart("minecraft:swamp_hut", 0, 0,
            List.of(new Box(2, -60, 2, 6, -55, 6), new Box(10, -60, 2, 12, -55, 4)));
        ChunkLevel level = levelWith(columnWith(hut));
        String biome = "minecraft:swamp";
        List<BiomeSpawns.SpawnerData> witch = NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(4, -60, 4), biome);
        assertEquals(1, witch.size());
        assertEquals("minecraft:witch", witch.get(0).type());
        assertEquals("minecraft:witch", NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(6, -55, 6), biome).get(0).type());
        assertEquals("minecraft:cat", NaturalSpawner.mobsAt(level, MobCategory.CREATURE, new Pos(11, -58, 3), biome).get(0).type());
        // 조각 밖(전체 상자 안이라도)·위쪽·옆 청크는 바이옴 목록
        for (Pos p : new Pos[]{new Pos(8, -58, 3), new Pos(4, -54, 4), new Pos(1, -60, 4), new Pos(20, -60, 4)}) {
            assertEquals(BiomeSpawns.mobs(biome, MobCategory.MONSTER), NaturalSpawner.mobsAt(level, MobCategory.MONSTER, p, biome), p.toString());
        }
        // 덮어쓰기가 없는 종류는 그대로
        assertEquals(BiomeSpawns.mobs(biome, MobCategory.AMBIENT), NaturalSpawner.mobsAt(level, MobCategory.AMBIENT, new Pos(4, -60, 4), biome));
        // 오두막 안 무작위 뽑기는 언제나 마녀
        LegacyRandomSource rnd = new LegacyRandomSource(42);
        for (int i = 0; i < 20; i++) {
            assertEquals("minecraft:witch", BiomeSpawns.getRandom(NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(3, -59, 3), biome), rnd).type());
        }
    }

    @Test
    void fortress_netherBricksFloorUsesFortressEnemies() {
        // 조각은 y 70.. 인데 전체 상자는 x 0..15 — 네더 벽돌 위·전체 상자 안이면 조각 밖이어도 요새 적
        StructureStart fort = new StructureStart("minecraft:fortress", 0, 0,
            List.of(new Box(0, -60, 0, 3, -50, 3), new Box(12, -60, 12, 15, -50, 15)));
        ChunkColumn col = columnWith(fort);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        col.setBlock(8, -61, 8, reg.defaultStateId("minecraft:nether_bricks"));
        ChunkLevel level = levelWith(col);
        String biome = "minecraft:nether_wastes";
        assertSame(NaturalSpawner.FORTRESS_ENEMIES, NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(8, -60, 8), biome));
        // 벽돌이 아니면(조각 밖) 바이옴 목록
        assertEquals(BiomeSpawns.mobs(biome, MobCategory.MONSTER), NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(9, -60, 8), biome));
        // 조각 안이면 데이터의 덮어쓰기(내용은 요새 적과 같다)
        assertEquals(NaturalSpawner.FORTRESS_ENEMIES, NaturalSpawner.mobsAt(level, MobCategory.MONSTER, new Pos(1, -60, 1), biome));
        assertTrue(NaturalSpawner.isInNetherFortressBounds(level, MobCategory.MONSTER, new Pos(8, -60, 8)));
        assertFalse(NaturalSpawner.isInNetherFortressBounds(level, MobCategory.CREATURE, new Pos(8, -60, 8)));
    }
}
