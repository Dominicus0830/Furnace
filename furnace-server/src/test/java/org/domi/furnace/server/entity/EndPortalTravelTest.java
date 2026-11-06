// 엔드 포털: 엔티티가 모양 안에 들면 엔드 (100.5, 50, 0.5) 로 가고 흑요석 단상이 생긴다. 엔드에서는 오버월드 스폰으로 돌아온다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndPortalTravelTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Entity only(DimensionTestLevel level, Class<?> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).filter(e -> !e.isRemoved()).findFirst().orElse(null);
    }

    @Test
    void pigFallsIntoEndPortalAndArrivesOnPlatform() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        end.setBlock(new Pos(101, 49, 1), B.defaultState("minecraft:stone"), 3);   // 단상 자리 막힘 — 부서진다
        Pig pig = new Pig(over, over.nextEntityId());
        pig.moveTo(2.5, -59.8, 2.5, 10.0F, 0.0F);
        over.addFreshEntity(pig);
        over.tickEntities(5);
        assertTrue(pig.isRemoved(), "원래 돼지는 빠졌다");
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, pig.removalReason());
        Entity arrived = only(end, Pig.class);
        assertNotNull(arrived, "엔드에 도착");
        assertEquals(pig.uuid(), arrived.uuid());
        assertEquals(100.5, arrived.x(), 1e-9);
        assertEquals(0.5, arrived.z(), 1e-9);
        assertEquals(90.0F, arrived.yRot(), 1e-6, "서쪽을 본다");
        assertTrue(arrived.isOnPortalCooldown());
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            assertTrue(B.is(end.getBlockState(new Pos(100 + dx, 48, dz)), "minecraft:obsidian"));
            for (int dy = 49; dy <= 51; dy++) assertTrue(B.isAir(end.getBlockState(new Pos(100 + dx, dy, dz))));
        }
    }

    @Test
    void entityLeavesEndToOverworldSpawn() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        end.setBlock(new Pos(3, -60, 3), B.defaultState("minecraft:end_portal"), 3);
        Pig pig = new Pig(end, end.nextEntityId());
        pig.moveTo(3.5, -59.8, 3.5, 0.0F, 0.0F);
        end.addFreshEntity(pig);
        end.tickEntities(5);
        Entity arrived = only(over, Pig.class);
        assertNotNull(arrived);
        assertEquals(0.5, arrived.x(), 1e-9);
        assertEquals(0.5, arrived.z(), 1e-9);
        assertTrue(arrived.y() >= -60.0 - 1e-9, "스폰 칸 땅 위");
    }

    @Test
    void noTargetLevelMeansNothingHappens() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.moveTo(2.5, -59.8, 2.5, 0.0F, 0.0F);
        over.addFreshEntity(pig);
        over.tickEntities(5);
        assertFalse(pig.isRemoved(), "엔드가 없으면 남는다(바닐라 getLevel == null)");
        assertTrue(pig.isOnPortalCooldown(), "시간은 찼으니 대기는 걸린다");
    }

    @Test
    void portalCooldownSavesAndLoads() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.portalCooldown = 123;
        Entity back = Entities.load(over, pig.save());
        assertEquals(123, back.portalCooldown);
    }
}
