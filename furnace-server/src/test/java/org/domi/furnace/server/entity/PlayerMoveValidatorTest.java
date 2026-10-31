// 플레이어 이동 서버 검증(바닐라 handleMovePlayer): moved too quickly / moved wrongly / 벽 속 위치 되돌리기 / 웅크림 가장자리 / 비행 킥.
// 기대값은 디컴파일된 1.21.1 공식 손계산(거리² − 속도² > 100 × 패킷 수, 어긋남² > 0.0625, 떠 있는 틱 > 80).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerMoveValidatorTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    /** 슈퍼플랫(바닥 윗면 y = -60). 옵션으로 비행 허용·틱 정지. */
    static final class Level extends BlockPlacementTest.TestLevel {
        boolean flightAllowed, runsNormally = true;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public boolean flightAllowed() { return flightAllowed; }
                @Override public boolean tickRunsNormally() { return runsNormally; }
            };
        }
    }

    private static Player player(double x, double y, double z, GameMode mode) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(x, y, z, 0, 0, true);
        p.applyMove(x, y, z, 0, 0, true);   // 마지막 이동 변위 0
        return p;
    }

    private static PlayerMoveValidator.Check pos(double x, double y, double z) {
        return new PlayerMoveValidator.Check(true, false, x, y, z, 0, 0);
    }

    /** 틱 머리(기준 위치 재설정). */
    private static void tick(Player p, Level l) { PlayerMoveValidator.tick(p); }

    @Test
    void ordinaryWalkIsAccepted_andUpdatesPositionAndKnownMovement() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(0.7, -60, 0.5), true));
        assertEquals(0.7, p.x(), 1e-12);
        assertEquals(0.2, p.knownMovement()[0], 1e-12);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(0.9, -60, 0.5), true));
        assertEquals(0.9, p.x(), 1e-12);
    }

    @Test
    void movedTooQuickly_thresholdIsHundredTimesPacketCount() {
        var l = new Level();
        // 한 패킷: 10.0 블록 → 100 > 100 거짓(허용), 10.5 블록 → 110.25 > 100 참(되돌림)
        var a = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(a, l);
        assertTrue(PlayerMoveValidator.handle(l, a, pos(10.5, -60, 0.5), true), "10² = 100 은 넘지 않는다");
        var b = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(b, l);
        assertFalse(PlayerMoveValidator.handle(l, b, pos(11.0, -60, 0.5), true), "10.5² = 110.25 > 100");
        assertEquals(0.5, b.x(), 1e-12, "서버 위치로 되돌아간다");
        // 한 틱에 패킷이 늘면 상한이 패킷 수 배: 2번째 12² = 144 ≤ 200, 3번째 17² = 289 ≤ 300, 4번째 21² = 441 > 400
        var c = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(c, l);
        assertTrue(PlayerMoveValidator.handle(l, c, pos(1.5, -60, 0.5), true));
        assertTrue(PlayerMoveValidator.handle(l, c, pos(12.5, -60, 0.5), true));
        assertTrue(PlayerMoveValidator.handle(l, c, pos(17.5, -60, 0.5), true));
        assertFalse(PlayerMoveValidator.handle(l, c, pos(21.5, -60, 0.5), true));
    }

    @Test
    void movedTooQuickly_packetCountIsClampedToOneWhenMoreThanFivePerTick() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        for (int i = 1; i <= 5; i++) assertTrue(PlayerMoveValidator.handle(l, p, pos(0.5 + 0.01 * i, -60, 0.5), true));
        // 6번째: q = 6 > 5 → 1 로 줄어 상한 100. 첫 좋은 위치(0.5)에서 11.0 → 121 > 100
        assertFalse(PlayerMoveValidator.handle(l, p, pos(11.5, -60, 0.5), true));
    }

    @Test
    void movedTooQuickly_elytraAllows300() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        p.setFallFlying(true);
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(17.0, -60, 0.5), false), "16.5² = 272.25 ≤ 300");
        var q = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        q.setFallFlying(true);
        tick(q, l);
        assertFalse(PlayerMoveValidator.handle(l, q, pos(18.0, -60, 0.5), false), "17.5² = 306.25 > 300");
    }

    @Test
    void frozenTickDisablesSpeedCheck() {
        var l = new Level();
        l.runsNormally = false;
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(20.5, -60, 0.5), true));
    }

    @Test
    void movedWrongly_throughWall_isRejectedAndPositionRestored() {
        var l = new Level();
        l.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:stone"), 3);
        l.setBlock(new Pos(2, -59, 0), B.defaultState("minecraft:stone"), 3);
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        // 서버 이동은 벽 앞(2 − 0.3 = 1.7)에서 막혀 x 차이 3.0 − 1.7 = 1.3 → 1.69 > 0.0625 → moved wrongly
        assertFalse(PlayerMoveValidator.handle(l, p, pos(3.0, -60, 0.5), true));
        assertEquals(0.5, p.x(), 1e-12);
        // 관전자는 충돌 없이 어디든
        var sp = player(0.5, -60, 0.5, GameMode.SPECTATOR);
        tick(sp, l);
        assertTrue(PlayerMoveValidator.handle(l, sp, pos(3.0, -60, 0.5), true));
        assertEquals(3.0, sp.x(), 1e-12);
        // 크리에이티브는 "어긋남" 검사만 건너뛴다 → 벽 속 위치 되돌리기(isPlayerCollidingWithBlocks)에서 걸린다
        var cr = player(0.5, -60, 0.5, GameMode.CREATIVE);
        tick(cr, l);
        assertFalse(PlayerMoveValidator.handle(l, cr, pos(3.0, -60, 0.5), true));
    }

    @Test
    void slightOvershootIntoWall_isRejectedByCollidingWithBlocks() {
        var l = new Level();
        l.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:stone"), 3);
        l.setBlock(new Pos(2, -59, 0), B.defaultState("minecraft:stone"), 3);
        var p = player(1.6, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        // 서버 이동은 1.7 에서 막힘(차이 0.1² = 0.01 ≤ 0.0625 → moved wrongly 아님). 클라이언트 위치 1.8 의 몸통(오른쪽 2.1)은
        // 돌(x ≥ 2)과 겹치지만 이전 몸통(오른쪽 1.9)과는 안 겹친다 → 되돌림
        assertFalse(PlayerMoveValidator.handle(l, p, pos(1.8, -60, 0.5), true));
        assertEquals(1.6, p.x(), 1e-12);
    }

    @Test
    void walkingUpSlab_isAccepted() {
        var l = new Level();
        l.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:stone_slab"), 3);   // 아래 반 블록: 윗면 −59.5
        var p = player(1.2, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(1.9, -59.5, 0.5), true));
        assertEquals(-59.5, p.y(), 1e-12);
    }

    @Test
    void rotationOnlyAndStatusOnly_keepPositionAndWrapAngles() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, new PlayerMoveValidator.Check(false, true, 0, 0, 0, 370.0F, 30.0F), true));
        assertEquals(0.5, p.x(), 1e-12);
        assertEquals(10.0F, p.yaw(), 1e-4, "wrapDegrees(370) = 10");
        assertEquals(30.0F, p.pitch(), 1e-6);
        assertTrue(PlayerMoveValidator.handle(l, p, new PlayerMoveValidator.Check(false, false, 0, 0, 0, 0, 0), false));
        assertFalse(p.onGround());
        assertEquals(10.0F, p.yaw(), 1e-4, "상태 전용은 회전을 바꾸지 않는다");
    }

    @Test
    void invalidValues() {
        assertTrue(PlayerMoveValidator.invalid(pos(Double.NaN, 0, 0)));
        assertFalse(PlayerMoveValidator.invalid(pos(Double.POSITIVE_INFINITY, 0, 0)), "무한대는 ±3e7 로 잘라 계속한다");
        assertTrue(PlayerMoveValidator.invalid(new PlayerMoveValidator.Check(true, true, 0, 0, 0, Float.NaN, 0)));
        assertTrue(PlayerMoveValidator.invalid(new PlayerMoveValidator.Check(true, true, 0, 0, 0, 0, Float.POSITIVE_INFINITY)));
    }

    @Test
    void sneakingBacksOffFromEdge_standingDoesNot() {
        var l = new Level();
        // x ≥ 2 의 바닥을 치워 가장자리를 만든다(바닥 y = −61)
        for (int x = 2; x <= 6; x++) for (int z = -2; z <= 2; z++) l.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:air"), 3);
        var sneak = player(1.5, -60, 0.5, GameMode.SURVIVAL);
        sneak.sneaking(true);
        tick(sneak, l);
        // 웅크린 서버 이동은 낭떠러지 쪽을 0.05 씩 줄여(약 0.75) 2.25 근처에 서므로 클라이언트가 말한 2.7 과 0.45² > 0.0625 → moved wrongly
        assertFalse(PlayerMoveValidator.handle(l, sneak, pos(2.7, -60, 0.5), true));
        var stand = player(1.5, -60, 0.5, GameMode.SURVIVAL);
        tick(stand, l);
        assertTrue(PlayerMoveValidator.handle(l, stand, pos(2.7, -60, 0.5), true), "서 있으면 줄이지 않는다");
    }

    @Test
    void floatingKick_after81TicksWithoutSupport() {
        var l = new Level();
        var p = player(0.5, -50, 0.5, GameMode.SURVIVAL);   // 바닥 윗면 −60 에서 10 블록 위
        tick(p, l);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(0.5, -50, 0.5), false));
        assertTrue(p.moveState.clientIsFloating());
        for (int i = 1; i <= 80; i++) tick(p, l);
        assertNull(p.kickReason(), "80 틱까지는 괜찮다: ++count > 80");
        tick(p, l);
        assertNotNull(p.kickReason());
        assertEquals(org.domi.furnace.api.text.Text.translatable("multiplayer.disconnect.flying"), p.kickReason());
    }

    @Test
    void noFloatingKick_whenAllowedMayflyOrSupported() {
        var l = new Level();
        l.flightAllowed = true;   // allow-flight
        var a = player(0.5, -50, 0.5, GameMode.SURVIVAL);
        tick(a, l);
        PlayerMoveValidator.handle(l, a, pos(0.5, -50, 0.5), false);
        assertFalse(a.moveState.clientIsFloating());
        var l2 = new Level();
        var c = player(0.5, -50, 0.5, GameMode.CREATIVE);   // mayfly
        tick(c, l2);
        PlayerMoveValidator.handle(l2, c, pos(0.5, -50, 0.5), false);
        assertFalse(c.moveState.clientIsFloating());
        var g = player(0.5, -60, 0.5, GameMode.SURVIVAL);   // 발밑 0.55 안에 바닥
        tick(g, l2);
        PlayerMoveValidator.handle(l2, g, pos(0.5, -60, 0.5), true);
        assertFalse(g.moveState.clientIsFloating());
        var e = player(0.5, -50, 0.5, GameMode.SURVIVAL);   // 겉날개 비행
        e.setFallFlying(true);
        tick(e, l2);
        PlayerMoveValidator.handle(l2, e, pos(0.5, -50, 0.5), false);
        assertFalse(e.moveState.clientIsFloating());
        var lev = player(0.5, -50, 0.5, GameMode.SURVIVAL);   // 부양
        lev.vitals().addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.LEVITATION, 100, 0));
        tick(lev, l2);
        PlayerMoveValidator.handle(l2, lev, pos(0.5, -50, 0.5), false);
        assertFalse(lev.moveState.clientIsFloating());
    }

    @Test
    void floatingCounterResetsWhenSupported() {
        var l = new Level();
        var p = player(0.5, -50, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        PlayerMoveValidator.handle(l, p, pos(0.5, -50, 0.5), false);
        for (int i = 0; i < 50; i++) tick(p, l);
        assertEquals(50, p.moveState.aboveGroundTickCount());
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        PlayerMoveValidator.handle(l, p, pos(0.5, -60, 0.5), true);
        tick(p, l);
        assertEquals(0, p.moveState.aboveGroundTickCount());
    }

    @Test
    void maximumFlyingTicksIs80ForDefaultGravity() {
        assertEquals(80, PlayerMoveValidator.maximumFlyingTicks(player(0.5, -60, 0.5, GameMode.SURVIVAL)));
    }

    @Test
    void rejectionAsksConnectionForTeleportAndHoldsUntilCleared() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        List<double[]> teleports = new ArrayList<>();
        p.teleporter(teleports::add);
        tick(p, l);
        assertFalse(PlayerMoveValidator.handle(l, p, pos(30.5, -60, 0.5), true));
        assertEquals(1, teleports.size());
        assertEquals(0.5, teleports.get(0)[0], 1e-12);
        assertEquals(-60.0, teleports.get(0)[1], 1e-12);
        assertTrue(p.moveRejectHold);
        p.clearMoves();
        assertFalse(p.moveRejectHold, "연결의 텔레포트 처리(clearMoves)가 대기를 푼다");
    }

    @Test
    void serverTeleportResetsReferencePoints() {
        var l = new Level();
        var p = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(p, l);
        p.applyMove(5.5, -60, 0.5, 0, 0, true);   // 서버 텔레포트(바닥 위 다른 곳)
        PlayerMoveValidator.teleported(p, 5.5, -60, 0.5);
        assertTrue(PlayerMoveValidator.handle(l, p, pos(5.7, -60, 0.5), true), "텔레포트 뒤 첫 걸음이 옛 기준점에서 계산되면 안 된다");
        var far = player(0.5, -60, 0.5, GameMode.SURVIVAL);
        tick(far, l);
        far.applyMove(40.5, -60, 0.5, 0, 0, true);
        PlayerMoveValidator.teleported(far, 40.5, -60, 0.5);
        assertTrue(PlayerMoveValidator.handle(l, far, pos(40.7, -60, 0.5), true));
    }
}
