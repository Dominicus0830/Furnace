// 가죽끈 잔여 규칙: 배의 끈(cov 가 btk 구현), 말·낙타의 checkLeashDistance, 죽은 몹의 끈, 차원 이동 시 끈 해제.
// 기대값은 바닐라 1.21.1 디컴파일(btk.b_, cov.b 탄성, chi.a 말, cgv.a 낙타, btn/btp 죽음)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LeashRemainderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static LeashFenceKnotEntity knot(BlockPlacementTest.TestLevel level, int kx) {
        var k = new LeashFenceKnotEntity(level, level.nextEntityId(), new Pos(kx, -60, 0));
        level.addFreshEntity(k);
        return k;
    }

    private static Boat boat(BlockPlacementTest.TestLevel level) {
        var b = new Boat(level, level.nextEntityId());
        b.moveTo(0.5, -59.625, 0.5, 0, 0);
        level.addFreshEntity(b);
        return b;
    }

    @Test
    void boatIsLeashableAndElasticPullIsPointTwoOfExcess() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 8);          // 거리 8.0
        var b = boat(level);
        assertTrue(b.canHaveALeashAttachedToIt());
        b.setLeashedTo(k, false);
        assertTrue(b.isLeashed());
        b.tickLeash();
        // 방향 (1,0,0) * (8 - 6) = (2,0,0); 속도 0 이라 dot = 0 → 0.2 배 → vx = 0.4
        assertEquals(0.4, b.vx(), 1e-6);
        assertEquals(0.0, b.vy(), 1e-9);
        assertTrue(b.isLeashed());
    }

    @Test
    void boatPullIsSofterWhenAlreadyMovingTowardHolder() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 8);
        var b = boat(level);
        b.setDeltaMovement(0.1, 0.0, 0.0);
        b.setLeashedTo(k, false);
        b.tickLeash();
        // dot > 0 → 0.15 배: 0.1 + 2 * 0.15 = 0.4
        assertEquals(0.4, b.vx(), 1e-6);
    }

    @Test
    void boatLeashBreaksBeyondTenAndDropsLead() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 11);
        var b = boat(level);
        b.setLeashedTo(k, false);
        b.tickLeash();
        assertFalse(b.isLeashed());
        assertEquals(1, level.getEntitiesOfClass(ItemEntity.class, b.boundingBox().inflate(2, 2, 2), e -> true).size());
    }

    @Test
    void boatLeashDroppedWhenBoatIsDestroyed() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 3);
        var b = boat(level);
        b.setLeashedTo(k, false);
        b.discard();
        assertFalse(b.isLeashed());
        assertEquals(1, level.getEntitiesOfClass(ItemEntity.class, b.boundingBox().inflate(2, 2, 2), e -> true).size());
    }

    @Test
    void boatLeashNbtRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 3);
        var b = boat(level);
        b.setLeashedTo(k, false);
        var tag = b.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        assertTrue(tag.tags().get("leash") instanceof int[] p && p.length == 3 && p[0] == 3 && p[1] == -60 && p[2] == 0);
        var b2 = new Boat(level, level.nextEntityId());
        b2.load(tag);
        assertTrue(b2.mayBeLeashed());
        assertFalse(b2.isLeashed(), "붙잡은 쪽은 틱에서 이어진다");
    }

    @Test
    void horseStopsEatingWhenPulledPastSixButKeepsWhenClose() {
        var level = new BlockPlacementTest.TestLevel();
        var horse = new Horse(level, level.nextEntityId());
        level.addFreshEntity(horse);
        var k = knot(level, 8);
        horse.setEating(true);
        assertTrue(horse.checkLeashDistance(k, 5.0F));
        assertTrue(horse.isEating());
        assertTrue(horse.checkLeashDistance(k, 7.0F));
        assertFalse(horse.isEating());
        assertFalse(horse.hasRestriction(), "말은 PathfinderMob 의 5칸 제한을 걸지 않는다");
    }

    @Test
    void deadLeashedMobDropsLeadOnNextTick() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 3);
        var c = new Cow(level, level.nextEntityId());
        c.moveTo(0.5, -59.625, 0.5, 0, 0);
        level.addFreshEntity(c);
        c.setLeashedTo(k, false);
        c.hurtBy(new Entity.Damage("generic", false, false, false), 1000.0F, null);
        assertFalse(c.isAlive());
        c.baseTick();
        assertFalse(c.isLeashed());
        assertEquals(1, level.getEntitiesOfClass(ItemEntity.class, c.boundingBox().inflate(2, 2, 2), e -> true).size());
    }

    @Test
    void changingDimensionDropsLeashWithoutItem() {
        var level = new BlockPlacementTest.TestLevel();
        var k = knot(level, 3);
        var c = new Cow(level, level.nextEntityId());
        c.moveTo(0.5, -59.625, 0.5, 0, 0);
        level.addFreshEntity(c);
        c.setLeashedTo(k, false);
        c.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        assertFalse(c.isLeashed());
        assertTrue(level.getEntitiesOfClass(ItemEntity.class, c.boundingBox().inflate(2, 2, 2), e -> true).isEmpty(), "dropLeash(true, false): 끈 아이템 없음");
    }
}
