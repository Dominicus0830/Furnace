// O05 C1: 엔티티 충돌 모양(배·셜커 = canBeCollidedWith, 배·수레는 밀 수 있는 것도)과 월드 보더 모양이 Entity.collide·noCollision·getCollisions 에
// 들어가는지(바닐라 1.21.1 Entity.collide/collectColliders, CollisionGetter, EntityGetter.getEntityCollisions).
// 모양 연산 자체(보더 모양·엔티티 상자 collide·겹침·오르기 후보)는 바닐라 서버 클래스를 직접 불러 무작위 90만 건을 비트 단위로 대조했다
// (context-notes/2026-11-12-entity-border-collision.md) — 아래 VanillaValues 의 기대값은 그 하네스가 낸 바닐라 값이다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityCollisionShapeTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 평지(서는 높이 -60). 보더 {minX, minZ, maxX, maxZ} 와 플레이어 목록을 시험에서 정한다. */
    static final class CollLevel extends BlockPlacementTest.TestLevel {
        double[] border = {-2.9999984E7, -2.9999984E7, 2.9999984E7, 2.9999984E7};
        final java.util.List<Player> players = new java.util.ArrayList<>();

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public double[] borderBounds() { return border.clone(); }
                @Override public void forEachPlayer(Consumer<Player> c) { players.forEach(c); }
            };
        }
    }

    private static final double GROUND = -60.0;

    private static Boat boatAt(CollLevel l, double x, double z) {
        Boat b = new Boat(l, l.nextEntityId());
        b.moveTo(x, GROUND, z, 0, 0);
        l.addFreshEntity(b);
        return b;
    }

    private static Zombie zombieAt(CollLevel l, double x, double y, double z) {
        Zombie zb = new Zombie(l, l.nextEntityId());
        zb.moveTo(x, y, z, 0, 0);
        l.addFreshEntity(zb);
        return zb;
    }

    private static Shulker shulkerAt(CollLevel l, double x, double z) {
        Shulker s = new Shulker(l, l.nextEntityId());
        s.moveTo(x, GROUND, z, 0, 0);
        l.addFreshEntity(s);
        return s;
    }

    private static Player playerAt(CollLevel l, double x, double y, double z) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 0, 0, false);
        p.applyMove(x, y, z, 0, 0, false);
        PlayerMoveValidator.tick(p);
        return p;
    }

    // ---------------------------------------------------------------- 엔티티 충돌 모양

    /** 배 위로 떨어지는 좀비는 배 윗면(-60 + 0.5625)에 서서 땅 위가 된다(바닐라: 몹·아이템이 배 위에 선다). */
    @Test
    void mobLandsOnBoat() {
        var l = new CollLevel();
        Boat boat = boatAt(l, 0.5, 0.5);
        assertEquals(GROUND + 0.5625, boat.boundingBox().maxY(), 1.0E-9);
        Zombie zb = zombieAt(l, 0.5, -58.0, 0.5);
        zb.move(Entity.MOVER_SELF, 0.0, -3.0, 0.0);
        assertEquals(GROUND + 0.5625, zb.y(), 1.0E-9, "배 윗면에 멈춘다");
        assertTrue(zb.onGround());
    }

    /** 셜커(1×1×1)는 옆으로 막는다: 0.6 폭 좀비의 오른쪽 면이 셜커 왼쪽 면(x = 2)에 닿는 x = 1.7 에서 선다. 오를 수 없는 높이(1 > 0.6). */
    @Test
    void shulkerBlocksSidewaysAndCannotBeStepped() {
        var l = new CollLevel();
        Shulker s = shulkerAt(l, 2.5, 0.5);
        assertEquals(2.0, s.boundingBox().minX(), 1.0E-9);
        Zombie zb = zombieAt(l, 0.5, GROUND, 0.5);
        zb.setOnGround(true);
        zb.move(Entity.MOVER_SELF, 2.0, 0.0, 0.0);
        assertEquals(1.7, zb.x(), 1.0E-6);   // 폭은 float 0.6F
        assertEquals(GROUND, zb.y(), 1.0E-9);
        assertTrue(zb.horizontalCollision);
    }

    /** 죽은(없어진) 셜커는 canBeCollidedWith = isAlive 가 거짓이라 막지 않는다. */
    @Test
    void removedShulkerDoesNotCollide() {
        var l = new CollLevel();
        Shulker s = shulkerAt(l, 2.5, 0.5);
        Zombie zb = zombieAt(l, 0.5, GROUND, 0.5);
        AABB box = new AABB(2.1, GROUND, 0.1, 2.9, GROUND + 0.9, 0.9);
        assertFalse(l.noCollision(zb, box));
        s.discard();
        assertTrue(l.noCollision(zb, box));
    }

    /** 배 높이(0.5625)는 step_height 0.6 이하라 땅 위 좀비가 걸어서 배 위로 오른다(후보 높이 = 배 모양의 Y 좌표). */
    @Test
    void mobStepsUpOntoBoat() {
        var l = new CollLevel();
        boatAt(l, 0.5, 0.5);   // x ∈ [-0.1875, 1.1875]
        Zombie zb = zombieAt(l, -0.6, GROUND, 0.5);
        zb.setOnGround(true);
        zb.move(Entity.MOVER_SELF, 0.3, 0.0, 0.0);
        assertEquals(-0.3, zb.x(), 1.0E-9, "막히지 않고 끝까지 간다");
        assertEquals(GROUND + 0.5625, zb.y(), 1.0E-6, "배 윗면으로 올랐다");
    }

    /** 몹끼리는 부딪히지 않는다(좀비의 canCollideWith = 상대 canBeCollidedWith — 좀비는 거짓). */
    @Test
    void mobsDoNotCollideWithEachOther() {
        var l = new CollLevel();
        zombieAt(l, 2.0, GROUND, 0.5);
        Zombie zb = zombieAt(l, 0.5, GROUND, 0.5);
        zb.setOnGround(true);
        zb.move(Entity.MOVER_SELF, 2.0, 0.0, 0.0);
        assertEquals(2.5, zb.x(), 1.0E-9);
    }

    /** 배는 밀 수 있는 몹에 막힌다(Boat.canVehicleCollide). 그 몹이 이 배의 승객이면 막히지 않는다. */
    @Test
    void boatIsBlockedByPushableMobUnlessPassenger() {
        var l = new CollLevel();
        Zombie zb = zombieAt(l, 3.0, GROUND, 0.5);   // x ∈ [2.7, 3.3]
        Boat boat = boatAt(l, 0.5, 0.5);             // 오른쪽 면 1.1875
        boat.move(Entity.MOVER_SELF, 3.0, 0.0, 0.0);
        assertEquals(2.7 - 0.6875, boat.x(), 1.0E-6, "좀비 왼쪽 면에서 멈춘다");

        var l2 = new CollLevel();
        Boat b2 = boatAt(l2, 0.5, 0.5);
        Zombie rider = zombieAt(l2, 0.5, GROUND, 0.5);
        assertTrue(rider.startRiding(b2, true));
        rider.setPos(2.0, GROUND, 0.5);   // 승객 자리를 옮겨 길목에 둔다(같은 탈것 무리라 제외돼야 한다)
        b2.move(Entity.MOVER_SELF, 1.0, 0.0, 0.0);
        assertEquals(1.5, b2.x(), 1.0E-9);
    }

    /** 배는 밀 수 있는 플레이어에도 막힌다(플레이어는 Furnace 에서 따로 들고 있다). 관전자는 막지 않는다. */
    @Test
    void boatIsBlockedByPlayer() {
        var l = new CollLevel();
        Player p = playerAt(l, 3.0, GROUND, 0.5);
        l.players.add(p);
        Boat boat = boatAt(l, 0.5, 0.5);
        boat.move(Entity.MOVER_SELF, 3.0, 0.0, 0.0);
        assertEquals(2.7 - 0.6875, boat.x(), 1.0E-6);

        p.vitals().loadGameTypes(null, GameMode.SPECTATOR, false);
        boat.move(Entity.MOVER_SELF, 1.0, 0.0, 0.0);
        assertEquals(2.7 - 0.6875 + 1.0, boat.x(), 1.0E-6, "관전자는 충돌 후보가 아니다");
    }

    /** 플레이어 이동 검증의 move 도 배 윗면에 선다(클라이언트와 같은 결과라야 moved wrongly 오탐이 없다). */
    @Test
    void playerMoveLandsOnBoat() {
        var l = new CollLevel();
        boatAt(l, 0.5, 0.5);
        Player p = playerAt(l, 0.5, -59.0, 0.5);
        double[] r = PlayerMoveValidator.move(l, p, 0.0, -1.0, 0.0, false);
        assertEquals(GROUND + 0.5625, r[1], 1.0E-9);
        assertEquals(1.0, r[3], "아래로 부딪혔다");
    }

    /** noCollision 의 세 갈래: self 없음 = 단단한 엔티티(배), self 몹 = 자기 기준(배는 막고 좀비는 아니다), self 배 = 밀 수 있는 몹도. noBlockCollision 은 엔티티를 안 본다. */
    @Test
    void noCollisionVariants() {
        var l = new CollLevel();
        boatAt(l, 0.5, 0.5);
        zombieAt(l, 5.5, GROUND, 0.5);
        AABB onBoat = new AABB(0.2, GROUND + 0.1, 0.2, 0.8, GROUND + 0.4, 0.8);
        AABB onZombie = new AABB(5.2, GROUND + 0.1, 0.2, 5.8, GROUND + 1.0, 0.8);
        assertFalse(l.noCollision(onBoat));
        assertTrue(l.noBlockCollision(null, onBoat));
        assertTrue(l.noCollision(onZombie), "self 없음: 좀비는 canBeCollidedWith 가 거짓");
        Zombie probe = new Zombie(l, l.nextEntityId());
        probe.moveTo(5.5, GROUND, 0.5, 0, 0);
        assertTrue(l.noCollision(probe, onZombie));
        assertFalse(l.noCollision(probe, onBoat));
        Boat newBoat = new Boat(l, l.nextEntityId());
        newBoat.moveTo(5.5, GROUND, 0.5, 0, 0);
        assertFalse(l.noCollision(newBoat, onZombie), "배를 놓으려는 자리에 좀비가 있으면 막힌다(BoatItem)");
    }

    /** getCollisions(null, 칸) 을 쓰는 알 스폰 높이(EntityType.getYOffset): 배가 있으면 배 윗면에 놓인다. */
    @Test
    void spawnEggSnapsOntoBoat() {
        var l = new CollLevel();
        boatAt(l, 0.5, 0.5);
        Zombie zb = new Zombie(l, l.nextEntityId());
        double d = l.snapOffset(zb, new org.domi.furnace.server.block.Pos(0, -60, 0), false);
        assertEquals(0.5625, d, 1.0E-9);
    }

    /** 엔티티 상자가 1e-7 넓힌 조회 상자에 걸리면 후보다(바닐라 getEntities(self, box.inflate(1e-7))). 크기 평균 1e-7 미만 상자는 빈 목록. */
    @Test
    void entityCollisionQueryInflatesAndSkipsTinyBoxes() {
        var l = new CollLevel();
        Boat b = boatAt(l, 0.5, 0.5);
        double right = b.boundingBox().maxX();
        AABB touching = new AABB(right + 0.5e-7, GROUND, 0.0, right + 1.0, GROUND + 1.0, 1.0);
        assertEquals(1, l.entityCollisions(null, touching).size());
        assertTrue(l.entityCollisions(null, new AABB(0.5, -59.8, 0.5, 0.5, -59.8, 0.5)).isEmpty());
    }

    // ---------------------------------------------------------------- 월드 보더

    /**
     * 보더 [-5.5, 5.5] 의 충돌 모양은 정수로 넓힌 [-6, 6] 기둥 밖이다. 보더 가까이(거리 < 2d, d = max(이동 상자 폭, 1)) 있는 좀비는 x = 6 - 0.3 에서
     * 멈춘다. 보더를 d 넘게 벗어난 엔티티는 보더 모양을 모으지 않고, 모으더라도 이미 모양 안이면 막히지 않는다.
     */
    @Test
    void borderStopsMobsCloseToIt() {
        var l = new CollLevel();
        l.border = new double[] {-5.5, -5.5, 5.5, 5.5};
        Zombie near = zombieAt(l, 5.0, GROUND, 0.5);
        near.setOnGround(true);
        near.move(Entity.MOVER_SELF, 2.0, 0.0, 0.0);
        assertEquals(5.7, near.x(), 1.0E-6);
        assertTrue(near.horizontalCollision);

        Zombie outside = zombieAt(l, 10.0, GROUND, 0.5);
        outside.setOnGround(true);
        outside.move(Entity.MOVER_SELF, -1.0, 0.0, 0.0);
        assertEquals(9.0, outside.x(), 1.0E-9, "보더에서 4.5 밖(d = 1.6 넘게)이라 보더 모양이 없다");
        outside.move(Entity.MOVER_SELF, -3.0, 0.0, 0.0);
        assertEquals(6.0, outside.x(), 1.0E-9, "d = 3.6 이라 보더 모양을 모으지만 이미 그 안에 있어 막히지 않는다(VoxelShape.collide 는 겹친 칸을 건너뛴다)");
    }

    /** noCollision: self 가 있으면 보더 밖과 겹치는 상자는 충돌, self 가 없으면 보더를 보지 않는다. 길찾기 판정도 보더를 본다. */
    @Test
    void noCollisionSeesBorderOnlyWithSelf() {
        var l = new CollLevel();
        l.border = new double[] {-5.5, -5.5, 5.5, 5.5};
        Zombie zb = zombieAt(l, 5.0, GROUND, 0.5);
        AABB across = new AABB(5.8, GROUND, 0.2, 6.4, GROUND + 1.9, 0.8);
        AABB inside = new AABB(5.3, GROUND, 0.2, 5.9, GROUND + 1.9, 0.8);
        assertFalse(l.noCollision(zb, across));
        assertTrue(l.noCollision(zb, inside), "실제 보더(5.5)를 넘어도 정수 보더(6) 안이면 괜찮다");
        assertTrue(l.noCollision(across));
        assertFalse(l.noCollisionIgnoringEntities(zb, across));
        assertTrue(l.noCollision(zb, 0.5, 0.5, across), "보더 가까움은 넘겨준 위치로 본다");
    }

    /** 보더 모양은 오르기 후보를 만들지 않는다(Y 격자가 ±무한) — 모양 Y 좌표 확인. */
    @Test
    void borderShapeHasInfiniteYCoords() {
        Shape s = Shape.outsideBorder(-6, -6, 6, 6);
        assertArrayEquals(new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}, s.exactYCoords());
    }

    // ---------------------------------------------------------------- 바닐라 값

    /** 하네스가 바닐라 Shapes/VoxelShape 로 낸 값(그대로 옮김): 보더 모양·엔티티 상자 모양의 collide. */
    @Test
    void vanillaValues() {
        // 보더 [-28,-34]..[-0.0,-6]: Math.ceil 이 -0.0 을 내고, 바닐라는 옮기지 않은 모양의 좌표를 그대로 빼 -0.0 을 돌려준다
        Shape border = Shape.outsideBorder(-28.0, -34.0, Math.ceil(-0.3), -6.0);
        double r = border.collide(0, new AABB(0.0, 0.0, 0.375, 0.25, 0.375, 1.0), -0.8982155525526667);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(r));
        // 1/8 격자 상자는 바닐라 큐브 격자(Y 좌표 0, 1/8, …, 1)를 갖는다 → 오르기 후보에 중간 좌표가 들어간다
        Shape cube = Shape.box(0.75, 0.375, 0.625, 1.0, 0.625, 0.875);
        assertArrayEquals(new double[] {0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0}, cube.exactYCoords());
        float[] c = Entity.stepHeightCandidates(new AABB(0, 0.5187257146919335, 0, 1, 1.5, 1), List.of(cube), 0.6F, 0.0F);
        assertArrayEquals(new float[] {0.106274284F, 0.23127429F, 0.35627428F, 0.48127428F}, c);   // 바닐라 값(격자 0.625·0.75·0.875·1 - minY)
        assertEquals(1, Shape.box(0, 0, 0, 1, 1, 1).boxCount());
        assertSame(Shape.BLOCK, Shape.box(0, 0, 0, 1, 1, 1));
    }

    /** 크기 0 인 상자는 Shapes.empty(): 막지도 겹치지도 않지만 Y 격자 {0} 이 오르기 후보(0 − minY)를 낸다(바닐라 버릇). */
    @Test
    void emptyEntityBoxKeepsVanillaZeroCoord() {
        Shape e = Shape.box(0.5, 0.0, 0.0, 0.5, 1.0, 1.0);
        assertTrue(e.isEmpty());
        assertArrayEquals(new double[] {0.0}, e.exactYCoords());
        assertEquals(0.7, e.collide(1, new AABB(0, 0.5, 0, 1, 1.5, 1), 0.7));
        assertFalse(e.overlaps(new AABB(0, 0, 0, 1, 1, 1), 0, 0, 0));
        assertArrayEquals(new float[] {0.25F}, Entity.stepHeightCandidates(new AABB(0, -0.25, 0, 1, 1.5, 1), List.of(e), 0.6F, 0.0F));
        assertArrayEquals(new float[0], Entity.stepHeightCandidates(new AABB(0, 0.25, 0, 1, 1.5, 1), List.of(e), 0.6F, 0.0F));
    }

    // ---------------------------------------------------------------- 블록만 보는 판정·도착 자리 보더

    /** 내릴·리스폰 자리(findSafeDismountLocation, canDismountTo)는 블록만 본다(getBlockCollisions) — 배가 있어도 그 자리를 버리지 않는다. */
    @Test
    void dismountLocationIgnoresHardEntities() {
        var l = new CollLevel();
        boatAt(l, 0.5, 0.5);
        var pos = new org.domi.furnace.server.block.Pos(0, -60, 0);
        assertNotNull(DismountHelper.findSafeDismountLocation(l, DismountHelper.PLAYER_WIDTH, 1.8F, pos, false));
        assertTrue(DismountHelper.canDismountTo(l, null, new AABB(0.2, GROUND, 0.2, 0.8, GROUND + 1.8, 0.8)));
    }

    /** 후렴과 순간이동(randomTeleport → teleportTo 뒤 noCollision(this))은 도착 자리 기준으로 보더 밖과 겹치면 실패한다. */
    @Test
    void chorusTeleportChecksBorderAtTarget() {
        var l = new CollLevel();
        l.border = new double[] {-5.5, -5.5, 5.5, 5.5};
        Player p = playerAt(l, 0.5, GROUND, 0.5);
        assertNull(ItemEffects.randomTeleportTarget(p, l, 5.9, GROUND, 0.5), "몸이 정수 보더 6 을 넘는다");
        assertNotNull(ItemEffects.randomTeleportTarget(p, l, 5.5, GROUND, 0.5));
    }
}
