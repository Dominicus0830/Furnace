package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

/**
 * 월드젠의 구조물 몹(T-221, 바닐라 WorldGenRegion): 몹은 그 장식의 레벨 난수로 finalizeSpawn(STRUCTURE) 하고, 더하는 순간 저장한 NBT 가 그 청크에 남는다.
 * 난이도는 (서버 난이도, 하루 시각, 머문 시간 0, 달 밝기).
 */
class StructureMobsGenerationTest {

    private static FeatureWorldView view(ChunkColumn col) {
        return new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
    }

    /** NBT 나무에서 id 가 그것인 첫 묶음. */
    private static NbtCompound find(Object node, String id) {
        if (node instanceof NbtCompound c) {
            if (id.equals(c.tags().get("id"))) return c;
            for (Object v : c.tags().values()) {
                NbtCompound f = find(v, id);
                if (f != null) return f;
            }
        } else if (node instanceof NbtList l) {
            for (Object v : l.items()) {
                NbtCompound f = find(v, id);
                if (f != null) return f;
            }
        }
        return null;
    }

    @Test
    void witchIsFinalizedWithTheRegionRandomAndSavedToTheChunk() {
        ChunkColumn col = new ChunkColumn(0);
        FeatureWorldView v = view(col);
        XoroshiroRandomSource region = new XoroshiroRandomSource(42L);
        XoroshiroRandomSource expect = new XoroshiroRandomSource(42L);
        v.setMobEnv(StructureMobs.generation(v, region, List.of(), LevelServices.DEFAULT, null));
        v.addStructureMob("minecraft:witch", 8.5, 70, 8.5, Mob::setPersistenceRequired, "CHUNK_GEN");

        List<NbtCompound> saved = col.generatedEntities();
        assertEquals(1, saved.size());
        NbtCompound witch = saved.get(0);
        assertEquals("minecraft:witch", witch.getString("id"));
        assertTrue(witch.getBoolean("PersistenceRequired"));
        // Raider·PatrollingMonster 는 STRUCTURE 에서 뽑지 않는다 → Mob.finalizeSpawn 이 첫 뽑기
        double bonus = 0.0 + 0.11485000000000001 * (expect.nextDouble() - expect.nextDouble());
        NbtCompound mod = find(witch, "minecraft:random_spawn_bonus");
        assertNotNull(mod, witch.tags().toString());
        assertEquals(bonus, mod.getDouble("amount"), 0.0);
        assertEquals(expect.nextFloat() < 0.05F, witch.getBoolean("LeftHanded"));
        // 다음 뽑기가 같은 수열을 잇는다(레벨 난수를 구조물·지형지물이 이어 쓴다)
        assertEquals(expect.nextInt(), region.nextInt());
    }

    @Test
    void mobsOutsideTheCenterChunkAreNotSaved() {
        ChunkColumn col = new ChunkColumn(0);
        FeatureWorldView v = view(col);
        v.setMobEnv(StructureMobs.generation(v, new XoroshiroRandomSource(1L), List.of(), LevelServices.DEFAULT, null));
        v.addStructureMob("minecraft:witch", 40.5, 70, 8.5, null, "DEFAULT");
        assertTrue(col.generatedEntities().isEmpty());
    }
}
