package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.CollisionContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 문맥 충돌 모양(T-018 R6)이 실제 이동·광선에 닿는지: 비계 위에 서기·웅크려 내려가기·안에서 오르기(플레이어 이동 검증),
 * 스트라이더가 용암 원천 위에 서기, 화살이 비계를 지나가기, 가루눈 위를 가죽 장화 신은 플레이어가 걷기.
 * 모양 값 자체는 ContextCollisionShapeVanillaTest 가 바닐라 jar 결과와 대조한다.
 */
class ContextCollisionBehaviorTest {

    private BlockPlacementTest.TestLevel level;
    private static int scaffolding, lavaSource, lavaFlowing, air;

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        scaffolding = B.prop("distance").withInt(B.defaultState("minecraft:scaffolding"), 0);
        lavaSource = B.defaultState("minecraft:lava");
        lavaFlowing = B.prop("level").withInt(lavaSource, 3);
        air = B.defaultState("minecraft:air");
    }

    @BeforeEach
    void setUp() { level = new BlockPlacementTest.TestLevel(); }

    /** 청크에 바로 놓는다(이웃 갱신·틱 없이). 평지 바닥은 y = -61 잔디(윗면 -60). */
    private void put(int x, int y, int z, int state) {
        ChunkColumn c = level.column(x >> 4, z >> 4);
        c.setBlock(x & 15, y, z & 15, state);
    }

    private static Player player(double x, double y, double z) {
        Player p = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.applyMove(x, y, z, 0f, 0f, false);
        return p;
    }

    @Test
    void playerStandsOnScaffoldingAndSneaksDown() {
        put(0, -60, 0, scaffolding);
        Player p = player(0.5, -59.0, 0.5);   // 비계 윗면에 선다
        double[] stay = PlayerMoveValidator.move(level, p, 0.0, -0.2, 0.0, false);
        assertEquals(-59.0, stay[1], 1e-12, "웅크리지 않으면 윗판에 막힌다");
        assertEquals(1.0, stay[3], "아래로 부딪힘");
        p.sneaking(true);
        double[] down = PlayerMoveValidator.move(level, p, 0.0, -0.2, 0.0, false);
        assertEquals(-59.2, down[1], 1e-12, "웅크리면 비계를 타고 내려간다(빈 모양)");
        // 문맥 없음(바닐라 CollisionContext.empty())은 웅크림과 상관없이 안정 모양
        assertFalse(level.noBlockCollision(null, new AABB(0.2, -59.2, 0.2, 0.8, -57.4, 0.8)));
        assertTrue(level.noBlockCollision(p, new AABB(0.2, -59.2, 0.2, 0.8, -57.4, 0.8)));
    }

    @Test
    void playerClimbsInsideScaffoldingWithoutRejection() {
        for (int y = -60; y <= -56; y++) put(0, y, 0, scaffolding);   // 쌓은 비계는 모두 거리 0
        Player p = player(0.5, -59.5, 0.5);   // 비계 기둥 안
        double[] up = PlayerMoveValidator.move(level, p, 0.0, 0.2, 0.0, false);
        assertEquals(-59.3, up[1], 1e-12, "안에서는 막히지 않고 오른다");
        // 클라이언트가 말한 자리(서버 계산과 같음)가 새 충돌에 들어가지 않는다 — 옛 데이터 모양(윗판)이면 되돌렸다
        AABB oldBox = PlayerMoveValidator.box(p);
        assertFalse(PlayerMoveValidator.isPlayerCollidingWithAnythingNew(level, p, oldBox, up, 0.5, -59.3, 0.5));
        // 옮긴 자리 높이가 문맥 발 높이: 비계 꼭대기 위로 올라서면 그 비계는 막는다
        AABB onTop = new AABB(0.2, -54.9, 0.2, 0.8, -53.1, 0.8);
        assertTrue(level.collisions(p, -54.9, onTop.move(0, -0.2, 0)).stream().anyMatch(s -> !s.isEmpty()), "위에 선 자리 문맥은 안정 모양");
        assertTrue(level.collisions(p, -59.3, onTop.move(0, -0.2, 0)).isEmpty(), "아래 자리 문맥은 빈 모양");
    }

    @Test
    void striderStandsOnLavaSource() {
        put(0, -61, 0, lavaSource);
        Strider s = new Strider(level, level.nextEntityId());
        s.moveTo(0.5, -60.4, 0.5, 0, 0);
        double[] m = Entity.collideMove(level, s, s.boundingBox(), false, 0.0F, 0.0, -0.3, 0.0);
        assertEquals(-0.1, m[1], 1e-9, "용암 원천 반 칸(-60.5) 위에 선다");
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60.4, 0.5, 0, 0);
        double[] c = Entity.collideMove(level, cow, cow.boundingBox(), false, 0.0F, 0.0, -0.3, 0.0);
        assertEquals(-0.3, c[1], 1e-12, "소는 용암에 빠진다");
        // 위 칸도 용암이면(같은 유체) 서지 못한다, 흐르는 용암(level != 0)도 못 선다
        put(0, -60, 0, lavaFlowing);
        assertEquals(-0.3, Entity.collideMove(level, s, s.boundingBox(), false, 0.0F, 0.0, -0.3, 0.0)[1], 1e-12);
        put(0, -60, 0, air);
        put(0, -61, 0, lavaFlowing);
        assertEquals(-0.3, Entity.collideMove(level, s, s.boundingBox(), false, 0.0F, 0.0, -0.3, 0.0)[1], 1e-12);
    }

    /**
     * 네더 포털 출구 자리(findFreePosition(entity))는 떠나는 엔티티의 문맥 스냅샷으로 블록 충돌을 본다(검토 R1): 스트라이더는 용암 원천 반 칸을 피해
     * 위로 밀리고, 문맥 없음은 그대로. 스냅샷은 가루눈 값(장화)도 떠 두어 나중에 바뀌어도 그대로다.
     */
    @Test
    void portalExitUsesTravelerContextSnapshot() {
        put(0, -61, 0, lavaSource);
        Strider s = new Strider(level, level.nextEntityId());
        s.moveTo(0.5, -60.4, 0.5, 0, 0);
        Traveler t = Traveler.of(s);
        double[] withCtx = org.domi.furnace.server.block.types.PortalShape.findCollisionFreePosition(level, t.collision(), 0.5, -60.9, 0.5, s.bbWidth(), s.bbHeight());
        assertEquals(-60.5, withCtx[1], 1e-9, "용암 원천 윗면(-60.5)으로 밀린다");
        double[] none = org.domi.furnace.server.block.types.PortalShape.findCollisionFreePosition(level, CollisionContext.EMPTY, 0.5, -60.9, 0.5, s.bbWidth(), s.bbHeight());
        assertEquals(-60.9, none[1], 1e-9);

        int snow = B.defaultState("minecraft:powder_snow");
        put(3, -60, 0, snow);
        Player p = player(3.5, -59.0, 0.5);
        p.inventory().set(8, B.stack("minecraft:leather_boots", 1));
        CollisionContext snap = Traveler.of(p).collision();
        p.inventory().set(8, org.domi.furnace.server.entity.ItemStack.EMPTY);
        AABB above = new AABB(3.2, -59.9, 0.2, 3.8, -59.5, 0.8);   // 가루눈 칸(-60..-59) 안, 아래 잔디와는 겹치지 않는다
        assertFalse(level.blockCollisionsWith(snap, above).isEmpty(), "떠 둔 문맥은 장화를 신은 채");
        assertTrue(level.blockCollisionsWith(CollisionContext.of(p).snapshot(), above).isEmpty(), "벗은 뒤 뜬 문맥은 빠진다");
    }

    @Test
    void arrowPassesThroughScaffoldingSideways() {
        put(0, -60, 0, scaffolding);
        Arrow a = new Arrow(level, level.nextEntityId());
        a.moveTo(-2.0, -59.05, 0.5, 0, 0);   // 윗판(-59.125..-59) 높이로 옆에서
        assertTrue(Clip.clipCollider(level, CollisionContext.of(a), -2.0, -59.05, 0.5, 3.0, -59.05, 0.5).miss(), "옆에서는 지나간다");
        assertFalse(Clip.clipCollider(level, CollisionContext.EMPTY, -2.0, -59.05, 0.5, 3.0, -59.05, 0.5).miss(), "문맥 없음은 안정 모양에 맞는다");
        a.moveTo(0.5, -58.5, 0.5, 0, 0);   // 위에서 떨어지면 윗판에 맞는다
        Clip.Result hit = Clip.clipCollider(level, CollisionContext.of(a), 0.5, -58.5, 0.5, 0.5, -60.5, 0.5);
        assertFalse(hit.miss());
        assertEquals(-59.0, hit.y(), 1e-9);
    }

    @Test
    void playerWithLeatherBootsWalksOnPowderSnow() {
        int snow = B.defaultState("minecraft:powder_snow");
        Pos at = new Pos(0, -60, 0);
        Player p = player(0.5, -59.0, 0.5);
        assertEquals(Shape.EMPTY, B.behavior(snow).getCollisionShape(snow, level, at, CollisionContext.of(p)), "장화 없으면 빠진다");
        p.inventory().set(8, B.stack("minecraft:leather_boots", 1));   // 발 칸
        assertEquals(Shape.BLOCK, B.behavior(snow).getCollisionShape(snow, level, at, CollisionContext.of(p)));
        p.sneaking(true);
        assertEquals(Shape.EMPTY, B.behavior(snow).getCollisionShape(snow, level, at, CollisionContext.of(p)), "웅크리면 빠진다");
        p.sneaking(false);
        p.vitals().setFallDistance(3.0F);
        assertEquals(0.9F, (float) B.behavior(snow).getCollisionShape(snow, level, at, CollisionContext.of(p)).bounds()[4], "2.5 넘게 떨어지면 0.9 높이");
    }
}
