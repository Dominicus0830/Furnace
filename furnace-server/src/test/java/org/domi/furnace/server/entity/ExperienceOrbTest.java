// 경험치 구슬: 값 단계 나누기, 같은 값 구슬 합치기(count), 저장/불러오기, 틱하면 떨어진다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

class ExperienceOrbTest {

    @org.junit.jupiter.api.BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static List<ExperienceOrb> orbs(BlockPlacementTest.TestLevel level) {
        return level.getEntitiesOfClass(ExperienceOrb.class, new AABB(-50, -80, -50, 50, 80, 50), o -> true);
    }

    @Test
    void awardSplitsIntoSizeTiers() {
        var level = new BlockPlacementTest.TestLevel();
        ExperienceOrb.award(level, 0.5, -55, 0.5, 1000);
        var list = orbs(level);
        assertEquals(List.of(3, 73, 307, 617), list.stream().map(ExperienceOrb::value).sorted().toList());
        assertEquals(1000, list.stream().mapToInt(o -> o.value() * o.count()).sum());
    }

    @Test
    void sameValueOrbsMergeInsteadOfStackingEntities() {
        var level = new BlockPlacementTest.TestLevel();
        for (int i = 0; i < 6; i++) ExperienceOrb.award(level, 0.5, -55, 0.5, 1);
        int entities = orbs(level).size();
        int total = orbs(level).stream().mapToInt(o -> o.value() * o.count()).sum();
        assertEquals(6, total);
        assertTrue(entities <= 6);
    }

    @Test
    void getExperienceValueTiers() {
        assertEquals(1, ExperienceOrb.getExperienceValue(2));
        assertEquals(3, ExperienceOrb.getExperienceValue(6));
        assertEquals(7, ExperienceOrb.getExperienceValue(16));
        assertEquals(17, ExperienceOrb.getExperienceValue(36));
        assertEquals(2477, ExperienceOrb.getExperienceValue(100000));
    }

    @Test
    void savesAndLoads() {
        var level = new BlockPlacementTest.TestLevel();
        var orb = new ExperienceOrb(level, level.nextEntityId(), 1, -55, 1, 37);
        NbtCompound tag = orb.saveWithoutId(new NbtCompound());
        var back = new ExperienceOrb(level, level.nextEntityId());
        back.load(tag);
        assertEquals(37, back.value());
        assertEquals(1, back.count());
    }

    @Test
    void fallsWithGravityWhenTicked() {
        var level = new BlockPlacementTest.TestLevel();
        var orb = new ExperienceOrb(level, level.nextEntityId(), 0.5, -40, 0.5, 5);
        level.addFreshEntity(orb);
        double y0 = orb.y();
        for (int i = 0; i < 60; i++) {
            orb.setOldPosAndRot();
            orb.tickCount++;
            orb.tick();
        }
        assertTrue(orb.y() < y0, "중력으로 떨어진다 y0=" + y0 + " y=" + orb.y() + " vy=" + orb.vy() + " removed=" + orb.isRemoved() + " " + orb.removalReason());
    }
}
