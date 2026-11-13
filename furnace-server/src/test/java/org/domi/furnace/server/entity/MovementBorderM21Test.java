// M21: 옆걸음의 걸을 수 있는 칸 확인(MoveControl.isWalkable), 스트라이더 승객의 길찾기 비용 상속, 월드 보더·사다리를 반영한 하차 자리,
// 포털을 지나는 수레·낙하 블록(엔드가 낀 이동은 옛 낙하 블록이 마저 돌아 복제).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.EndPortalBlock;
import org.domi.furnace.server.block.types.NetherPortalBlock;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MovementBorderM21Test {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Entity only(DimensionTestLevel level, Class<?> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).filter(e -> !e.isRemoved()).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ 옆걸음 확인

    @Test
    void strafeKeepsDirectionOnWalkableGround() {
        var level = new DimensionTestLevel("minecraft:overworld");
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.97, 0, 0);   // 오른쪽(+z) 칸 가장자리
        z.setOnGround(true);
        level.addFreshEntity(z);
        z.getMoveControl().strafe(0.0F, 1.0F);
        z.getMoveControl().tick();
        assertEquals(0.0F, z.getZza(), 0.0F);
        assertEquals(1.0F, z.getXxa(), 0.0F);
    }

    @Test
    void strafeTowardNonWalkableCellFallsBackToForward() {
        var level = new DimensionTestLevel("minecraft:overworld");
        level.setBlock(new Pos(0, -60, 1), B.defaultState("minecraft:stone"), 3);   // 옆으로 가면 들어갈 칸이 막혀 있다
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.97, 0, 0);
        z.setOnGround(true);
        level.addFreshEntity(z);
        z.getMoveControl().strafe(0.0F, 1.0F);
        z.getMoveControl().tick();
        assertEquals(1.0F, z.getZza(), 0.0F, "걸을 수 없는 칸이면 앞으로 가는 쪽으로 바꾼다");
        assertEquals(0.0F, z.getXxa(), 0.0F);
    }

    // ------------------------------------------------------------------ 승객의 비용 상속

    @Test
    void passengerInheritsStriderPathfindingMalus() {
        var level = new DimensionTestLevel("minecraft:overworld");
        Strider s = new Strider(level, level.nextEntityId());
        s.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(s);
        ZombifiedPiglin rider = new ZombifiedPiglin(level, level.nextEntityId());
        rider.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(rider);
        float ownLava = rider.getPathfindingMalus(PathType.LAVA), ownWater = rider.getPathfindingMalus(PathType.WATER);
        assertNotEquals(0.0F, ownLava, "혼자일 때는 자기 비용(용암을 꺼린다)");
        assertNotEquals(-1.0F, ownWater);
        assertTrue(rider.startRiding(s));
        assertSame(s, rider.getControlledVehicle());
        assertEquals(0.0F, rider.getPathfindingMalus(PathType.LAVA), 0.0F, "스트라이더를 조종하면 그 비용표(용암 0)");
        assertEquals(-1.0F, rider.getPathfindingMalus(PathType.WATER), 0.0F, "물은 스트라이더가 막는다");
        rider.stopRiding();
        assertEquals(ownLava, rider.getPathfindingMalus(PathType.LAVA), 0.0F, "내리면 다시 자기 비용");
        assertTrue(s.shouldPassengersInheritMalus());
        assertFalse(rider.shouldPassengersInheritMalus());
    }

    // ------------------------------------------------------------------ 하차 자리: 월드 보더·사다리·엔드 포털

    @Test
    void dismountBoxOutsideWorldBorderIsRejected() {
        var level = new DimensionTestLevel("minecraft:overworld");
        level.border = new double[] {-10, -10, 10, 10};
        assertTrue(DismountHelper.canDismountTo(level, null, new AABB(0.2, -60, 0.2, 0.8, -58.2, 0.8)));
        assertFalse(DismountHelper.canDismountTo(level, null, new AABB(9.8, -60, 0.2, 10.4, -58.2, 0.8)), "상자 한쪽이 보더 밖");
        assertTrue(DismountHelper.canDismountTo(level, null, new AABB(9.3, -60, 0.2, 9.9, -58.2, 0.8)), "최대 모서리는 1e-5 안쪽까지 본다");
        assertFalse(DismountHelper.canDismountTo(level, null, new AABB(-10.4, -60, 0.2, -9.8, -58.2, 0.8)));
    }

    @Test
    void safeDismountChecksWholeBoxAgainstBorder() {
        var level = new DimensionTestLevel("minecraft:overworld");
        level.border = new double[] {0, 0, 10, 10};
        // 칸 (0,-60,5) 가운데 x=0.5 에 너비 0.6 의 몸: 0.2..0.8 → 안. 칸 가운데는 어디나 보더 안이므로 칸 하나 검사와 같다
        assertNotNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, new Pos(0, -60, 5), false));
        // 너비 1.2 의 큰 몸이면 상자(−0.1..1.1)가 보더 밖으로 나간다 — 칸 하나만 보던 예전에는 통과했다
        assertNull(DismountHelper.findSafeDismountLocation(level, 1.2F, 1.8F, new Pos(0, -60, 5), false));
    }

    @Test
    void ladderAndOpenTrapdoorAreNotFloor() {
        var level = new DimensionTestLevel("minecraft:overworld");
        Pos at = new Pos(3, -60, 3);   // 아래 칸 (3,-61,3) 이 바닥 후보
        assertNotNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, at, false), "초원 위에 서는 자리");
        level.setBlock(new Pos(3, -61, 3), B.defaultState("minecraft:ladder"), 3);
        assertNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, at, false), "사다리 위는 바닥으로 치지 않는다");
        int open = B.prop("open").with(B.prop("half").with(B.defaultState("minecraft:oak_trapdoor"), "bottom"), true);
        level.setBlock(new Pos(3, -61, 3), open, 3);
        assertNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, at, false), "열린 다락문도 마찬가지");
    }

    @Test
    void playersCannotStandInsideEndPortalButOtherBodiesCan() {
        var level = new DimensionTestLevel("minecraft:overworld");
        Pos at = new Pos(3, -60, 3);
        level.setBlock(at, B.defaultState("minecraft:end_portal"), 3);
        assertNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, false, true, at, false), "플레이어는 엔드 포털 칸 안에 못 선다");
        assertNotNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, false, false, at, false), "다른 몸은 된다(바닐라는 플레이어만 거른다)");
        // 한 칸 위가 포털인 경우도 플레이어는 거절
        var level2 = new DimensionTestLevel("minecraft:overworld");
        level2.setBlock(new Pos(3, -59, 3), B.defaultState("minecraft:end_gateway"), 3);
        assertNull(DismountHelper.findSafeDismountLocation(level2, 0.6F, 1.8F, false, true, new Pos(3, -60, 3), false));
    }

    @Test
    void dangerousCheckSkipsFireOnlyForFireImmune() {
        var level = new DimensionTestLevel("minecraft:overworld");
        Pos at = new Pos(3, -60, 3);
        level.setBlock(at, B.defaultState("minecraft:fire"), 3);
        assertNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, false, false, at, true));
        assertNotNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, true, false, at, true), "불에 면역이면 타는 블록이 위험하지 않다");
        // 선인장은 누구에게나 위험
        level.setBlock(at, B.defaultState("minecraft:cactus"), 3);
        assertNull(DismountHelper.findSafeDismountLocation(level, 0.6F, 1.8F, true, false, at, true));
    }

    // ------------------------------------------------------------------ 포털을 지나는 수레·낙하 블록

    @Test
    void minecartTicksHandlePortalAndTravels() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        AbstractMinecart cart = AbstractMinecart.create(over, 2.5, -59.8, 2.5, AbstractMinecart.Type.RIDEABLE);
        over.addFreshEntity(cart);
        over.tickEntities(6);
        assertTrue(cart.isRemoved(), "수레도 포털의 handlePortal 로 원래 쪽에서 빠진다");
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, cart.removalReason());
        assertNotNull(only(end, AbstractMinecart.class), "엔드에 도착");
    }

    private static int itemCount(DimensionTestLevel level) {
        int n = 0;
        for (Entity e : level.entities().all()) if (e instanceof ItemEntity ie && !ie.isRemoved()) n += ie.getItem().count();
        return n;
    }

    @Test
    void fallingBlockIntoEndPortalTicksOnAfterLeavingAndDuplicates() {
        var over = new DimensionTestLevel(EndPortalBlock.OVERWORLD);
        var end = new DimensionTestLevel(EndPortalBlock.THE_END);
        DimensionTestLevel.link(over, end);
        // 모래가 땅(−61 꼭대기) 위 −60 칸에 이미 앉은 채 엔드 포털 칸에 들어 있다 — 포털에 닿는 틱에 착지도 함께 일어난다
        FallingBlockEntity fb = FallingBlockEntity.fall(over, new Pos(2, -60, 2), B.defaultState("minecraft:sand"));
        over.setBlock(new Pos(2, -60, 2), B.defaultState("minecraft:end_portal"), 3);
        over.tickEntities(1);
        assertTrue(fb.isRemoved());
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, fb.removalReason(), "먼저 빠진 이유가 남는다(건너간 뒤 마저 돌아도)");
        assertNotNull(only(end, FallingBlockEntity.class), "엔드에는 복사본이 도착");
        // 엔드 포털은 대체할 수 없는 칸이라 옛 쪽은 아이템으로 떨어진다 — 건너간 복사본 + 떨어진 아이템 = 복제
        assertEquals(1, itemCount(over), "엔드가 낀 이동은 옛 낙하 블록이 이번 틱을 마저 돌아 아이템을 떨군다");
    }

    @Test
    void fallingBlockThroughNetherPortalDoesNotTickOn() {
        var over = new DimensionTestLevel(NetherPortalBlock.OVERWORLD);
        var nether = new DimensionTestLevel(NetherPortalBlock.NETHER);
        DimensionTestLevel.link(over, nether);
        int portal = B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "x");
        over.setBlock(new Pos(2, -60, 2), portal, 18);
        over.setBlock(new Pos(2, -59, 2), portal, 18);
        FallingBlockEntity fb = FallingBlockEntity.fall(over, new Pos(2, -60, 2), B.defaultState("minecraft:sand"));
        over.setBlock(new Pos(2, -60, 2), portal, 18);
        over.tickEntities(1);
        assertTrue(fb.isRemoved());
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, fb.removalReason(), "엔드가 아니면 옛 쪽은 그대로 빠진 채 끝");
        assertEquals(0, itemCount(over), "옛 낙하 블록은 더 돌지 않아 아이템이 없다");
        assertNotNull(only(nether, FallingBlockEntity.class));
    }
}
