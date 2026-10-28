// 드래곤 재대조(2026-10-28)에서 고친 항목별 회귀 시험과 앉은 단계 전이 시나리오.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonParityRecheckTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage PLAYER_HIT = new Entity.Damage("player_attack", false, false, false);

    private static Player player(DimensionTestLevel level, double x, double y, double z) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 0, 0, true);
        level.player = p;
        return p;
    }

    private static EnderDragon dragon(DimensionTestLevel level, double x, double y, double z) {
        EnderDragon d = new EnderDragon(level, level.nextEntityIds(9));
        d.moveTo(x, y, z, 0.0F, 0.0F);
        level.addFreshEntity(d);
        return d;
    }

    @Test
    void invalidSavedPhaseFallsBackToHoldingPattern() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        for (int bad : new int[] {99, -3}) {
            NbtCompound tag = d.save();
            tag.putInt("DragonPhase", bad);
            EnderDragon b = (EnderDragon) Entities.load(level, tag);
            assertEquals(DragonPhase.HOLDING_PATTERN, b.getPhaseManager().getCurrentPhase().id(), "getById 범위 밖 → HOLDING_PATTERN: " + bad);
        }
    }

    @Test
    void windChargeIgnitedAndIgnoredWhileSitting() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.SITTING_SCANNING);
        WindCharge w = new WindCharge(level, level.nextEntityId());
        assertEquals(0.0F, d.getPhaseManager().getCurrentPhase().onHurt(PLAYER_HIT, w, 9.0F));
        assertTrue(w.remainingFireTicks() > 0);
    }

    @Test
    void invulnerableDragonStillRunsPhaseOnHurt() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 10.5);
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        d.setInvulnerable(true);
        d.getPhaseManager().setPhase(DragonPhase.SITTING_ATTACKING);
        Arrow arrow = new Arrow(level, level.nextEntityId());
        assertFalse(d.hurtByVia(PLAYER_HIT, 9.0F, p, arrow), "피해 0 이라 false — 그래도 무적 검사 없이 단계까지 간다");
        assertTrue(arrow.remainingFireTicks() > 0, "앉은 단계가 화살에 불을 붙인다");
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        d.hurtBy(PLAYER_HIT, 50.0F, p);
        assertEquals(200.0F, d.health(), "깎는 것은 LivingEntity.hurt 의 무적 검사가 막는다");
    }

    @Test
    void partBlocksFireButNotInvulnerableFlag() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 10.5);
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        assertFalse(d.head.hurt(new Entity.Damage("in_fire", true, false, false), 10.0F, p), "파트도 드래곤 종류라 불에 면역");
        d.setInvulnerable(true);
        assertTrue(d.head.hurt(PLAYER_HIT, 10.0F, p), "파트에는 무적 표시가 없다 — 본체에 넘긴다");
        assertEquals(200.0F, d.health());
    }

    @Test
    void crystalDestroyedWithoutDestroyerPicksNearestPlayerWithoutRange() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 120.5, 100, 0.5);   // 드래곤에서 64 넘게 떨어짐
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        EndCrystal c = new EndCrystal(level, level.nextEntityId());
        c.moveTo(110.5, 100, 0.5, 0, 0);
        level.addFreshEntity(c);
        d.onCrystalDestroyed(c, c.blockPosition(), Entity.Damage.EXPLOSION, null);
        assertEquals(DragonPhase.STRAFE_PLAYER, d.getPhaseManager().getCurrentPhase().id(), "출처 없는 조건은 거리 제한이 없다");
        assertSame(p, ((DragonPhase.StrafePlayer) d.getPhaseManager().getCurrentPhase()).target());
    }

    @Test
    void dragonFireballCloudMovesToNearbyPlayer() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 2.5, 100, 0.5);
        DragonFireball fb = new DragonFireball(level, level.nextEntityId());
        fb.moveTo(0.5, 100, 0.5, 0, 0);
        level.addFreshEntity(fb);
        fb.onHitForTest();
        AreaEffectCloud cloud = null;
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof AreaEffectCloud a && !a.isRemoved()) cloud = a;
        assertNotNull(cloud);
        assertEquals(p.x(), cloud.x(), 1e-9, "4칸 안 플레이어 자리로");
        assertEquals(p.z(), cloud.z(), 1e-9);
    }

    @Test
    void fightScansPlayersOnFirstTickAndLoadsIsRespawning() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 0.5);
        var fight = new EndDragonFight(level, 0L, new Pos(0, 0, 0));
        fight.skipArenaLoadedCheck();
        level.setDragonFight(fight);
        fight.tick();
        assertTrue(fight.barPlayers().contains(p), "초기값 21 → 첫 틱에 훑는다");

        NbtCompound t = fight.save();
        assertTrue(t.contains("IsRespawning"));
        t.putBoolean("IsRespawning", true);
        var f2 = new EndDragonFight(level, 0L, new Pos(0, 0, 0));
        f2.load(t);
        assertEquals(EndDragonFight.RespawnStage.START, f2.respawnStage());
    }

    /** 앉은 단계: 둘러보기 → (플레이어, 26틱) 공격 → (41틱) 숨결 → (200틱) 둘러보기 … 숨결 4번째 끝 → 이륙 → 받침대에서 멀면 맴돌기. */
    @Test
    void sittingCycleScenario() {
        var level = new DimensionTestLevel("minecraft:the_end");
        player(level, 6.5, 100, 0.5);
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        var pm = d.getPhaseManager();
        pm.setPhase(DragonPhase.LANDING);
        pm.getPhase(DragonPhase.SITTING_FLAMING);
        ((DragonPhase.SittingFlaming) pm.getPhase(DragonPhase.SITTING_FLAMING)).resetFlameCount();
        pm.setPhase(DragonPhase.SITTING_SCANNING);
        for (int round = 1; round <= 4; round++) {
            assertEquals(DragonPhase.SITTING_SCANNING, pm.getCurrentPhase().id());
            for (int i = 0; i < 25; i++) pm.getCurrentPhase().doServerTick();
            assertEquals(DragonPhase.SITTING_SCANNING, pm.getCurrentPhase().id(), "25틱까지는 돌아보기만");
            pm.getCurrentPhase().doServerTick();
            assertEquals(DragonPhase.SITTING_ATTACKING, pm.getCurrentPhase().id());
            for (int i = 0; i < 40; i++) pm.getCurrentPhase().doServerTick();
            assertEquals(DragonPhase.SITTING_ATTACKING, pm.getCurrentPhase().id());
            pm.getCurrentPhase().doServerTick();
            assertEquals(DragonPhase.SITTING_FLAMING, pm.getCurrentPhase().id());
            assertEquals(round, ((DragonPhase.SittingFlaming) pm.getCurrentPhase()).flameCount());
            for (int i = 0; i < 10; i++) pm.getCurrentPhase().doServerTick();
            assertEquals(1, countClouds(level), "10틱째 숨결 구름");
            for (int i = 0; i < 190; i++) pm.getCurrentPhase().doServerTick();
            assertEquals(0, countClouds(level), "단계가 끝나면 구름을 지운다");
        }
        assertEquals(DragonPhase.TAKEOFF, pm.getCurrentPhase().id(), "4번째 숨결 뒤 이륙");
        pm.getCurrentPhase().doServerTick();
        assertEquals(DragonPhase.TAKEOFF, pm.getCurrentPhase().id(), "첫 틱은 경로 찾기");
        pm.getCurrentPhase().doServerTick();
        assertEquals(DragonPhase.HOLDING_PATTERN, pm.getCurrentPhase().id(), "받침대에서 10칸 넘게 떨어져 있으면 맴돌기");
    }

    @Test
    void scanningWithoutPlayerTakesOffAfter100Ticks() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        var pm = d.getPhaseManager();
        pm.setPhase(DragonPhase.SITTING_SCANNING);
        for (int i = 0; i < 99; i++) pm.getCurrentPhase().doServerTick();
        assertEquals(DragonPhase.SITTING_SCANNING, pm.getCurrentPhase().id());
        pm.getCurrentPhase().doServerTick();
        assertEquals(DragonPhase.TAKEOFF, pm.getCurrentPhase().id(), "150칸 안에 아무도 없으면 이륙만");
    }

    private static int countClouds(DimensionTestLevel level) {
        int n = 0;
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof AreaEffectCloud a && !a.isRemoved()) n++;
        return n;
    }
}
