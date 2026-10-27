// 농부 행동: HarvestFarmland 가 다 자란 밀을 부수고 다음 틱에 씨앗을 심는다, 생성 이유별 finalizeSpawn.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.HarvestFarmland;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerFarmerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void harvestThenReplant() {
        var level = new BlockPlacementTest.TestLevel();
        Pos crop = new Pos(0, -60, 0);
        level.setBlock(crop.below(), B.defaultState("minecraft:farmland"), 3);
        level.setBlock(crop, B.prop("age").with(B.defaultState("minecraft:wheat"), "7"), 3);
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(0.5, -60, 0.5, 0, 0);
        v.setProfession("farmer");
        v.getInventory().set(0, B.stack("minecraft:wheat_seeds", 3));
        v.getBrain().setMemory(MemoryModuleType.SECONDARY_JOB_SITE, java.util.List.of(new GlobalPos(level.dimension(), crop.below())));
        var h = new HarvestFarmland();
        long t = 100;
        assertTrue(h.tryStart(level, v, t));
        assertTrue(v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "작물로 걸어간다");
        h.tickOrStop(level, v, ++t);
        assertTrue(B.isAir(level.getBlockState(crop)), "다 자란 밀을 부순다");
        h.tickOrStop(level, v, ++t);
        assertTrue(B.is(level.getBlockState(crop), "minecraft:wheat"), "빈 경작지 위에 심는다");
        assertEquals(0, B.prop("age").intValue(level.getBlockState(crop)));
        assertEquals(2, v.getInventory().get(0).count(), "씨앗 하나를 쓴다");
        h.tickOrStop(level, v, ++t);
    }

    @Test
    void finalizeSpawnByReason() {
        var level = new BlockPlacementTest.TestLevel();
        var v = new Villager(level, level.nextEntityId());
        v.setVillagerData("plains", "farmer", 1);
        v.setFinalizeReason(SpawnReason.BREEDING);
        v.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(v.blockPosition()));
        assertEquals("none", v.profession());
        v.setFinalizeReason(SpawnReason.STRUCTURE);
        v.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(v.blockPosition()));
        assertTrue(v.assignProfessionWhenSpawned());
        var w = new Villager(level, level.nextEntityId());
        w.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(w.blockPosition()));
        assertFalse(w.assignProfessionWhenSpawned(), "자연(기본)은 표시하지 않는다");
    }
}
