// 엔더 드래곤: 단계 전이, 파트 피해 배율, 죽음 경험치(12000/500), 저장 왕복, 고정 시드 재현, 전투(처치 → 출구 포털·알, 수정 회복).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnderDragonTest {

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

    private static int orbTotal(DimensionTestLevel level) {
        int sum = 0;
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof ExperienceOrb o && !o.isRemoved()) sum += o.value() * o.count();
        return sum;
    }

    @Test
    void startsHoveringAndPhaseIsSynced() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        assertEquals(DragonPhase.HOVERING, d.getPhaseManager().getCurrentPhase().id());
        assertTrue(d.getPhaseManager().getCurrentPhase().isSitting());
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        assertEquals(DragonPhase.HOLDING_PATTERN, (Integer) d.entityData().get(EnderDragon.DATA_PHASE));
        assertEquals(200.0F, d.health());
        assertEquals(d.id() + 1, d.head.id());
        assertSame(d.tail3, d.partById(d.id() + 6));
    }

    @Test
    void partDamageMultiplierAndPlayerOnly() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, -60, 0.5);
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        assertTrue(d.hurt(d.head, PLAYER_HIT, 10.0F, p));
        assertEquals(190.0F, d.health(), 1e-4, "머리는 그대로");
        d.invulnerableTime = 0;
        assertTrue(d.hurt(d.body, PLAYER_HIT, 10.0F, p));
        assertEquals(190.0F - 3.5F, d.health(), 1e-4, "그 밖은 10/4 + min(10, 1)");
        d.invulnerableTime = 0;
        assertTrue(d.hurt(d.wing1, new Entity.Damage("mob_attack", false, false, false), 10.0F, null), "맞았다고는 한다");
        assertEquals(186.5F, d.health(), 1e-4, "플레이어가 아니면 깎이지 않는다");
        d.invulnerableTime = 0;
        assertTrue(d.hurt(d.wing1, new Entity.Damage("explosion", false, true, false), 0.8F, null));
        assertEquals(186.5F - (0.2F + 0.8F), d.health(), 1e-4, "폭발은 언제나 깎인다");
    }

    @Test
    void lethalHitInFlightGoesDyingAndSittingDamageTakesOff() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, -60, 0.5);
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.SITTING_SCANNING);
        d.hurt(d.head, PLAYER_HIT, 49.0F, p);
        assertEquals(DragonPhase.SITTING_SCANNING, d.getPhaseManager().getCurrentPhase().id(), "25% 이하면 그대로");
        d.invulnerableTime = 0;
        d.hurt(d.head, PLAYER_HIT, 2.0F, p);
        assertEquals(DragonPhase.TAKEOFF, d.getPhaseManager().getCurrentPhase().id(), "앉아서 50 넘게 맞으면 이륙");
        assertEquals(0.0F, d.sittingDamageReceived());
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        d.invulnerableTime = 0;
        d.hurt(d.head, PLAYER_HIT, 1000.0F, p);
        assertEquals(DragonPhase.DYING, d.getPhaseManager().getCurrentPhase().id());
        assertEquals(1.0F, d.health());
        assertFalse(d.hurt(d.head, PLAYER_HIT, 10.0F, p), "죽는 중엔 안 맞는다");
    }

    @Test
    void arrowsIgnitedWhileSitting() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.SITTING_ATTACKING);
        Arrow arrow = new Arrow(level, level.nextEntityId());
        assertEquals(0.0F, d.getPhaseManager().getCurrentPhase().onHurt(PLAYER_HIT, arrow, 9.0F));
        assertTrue(arrow.remainingFireTicks() > 0);
    }

    @Test
    void dyingPhaseNearPodiumThenDeathAnimationAwardsFirstKillXp() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var fight = new EndDragonFight(level, 0L, new Pos(0, 0, 0));
        fight.skipArenaLoadedCheck();
        level.setDragonFight(fight);
        EnderDragon d = fight.createNewDragon();
        d.moveTo(0.5, -55, 0.5, 0, 0);   // 받침대(지면) 10칸 안
        d.getPhaseManager().setPhase(DragonPhase.DYING);
        level.tickEntities(1);
        assertTrue(d.isDeadOrDying(), "가까우면 체력 0");
        level.tickEntities(199);
        assertEquals(1, d.dragonDeathTime > 0 ? 1 : 0);
        assertFalse(d.isRemoved(), "200틱째 전");
        level.tickEntities(5);
        assertTrue(d.isRemoved());
        assertEquals(12000, orbTotal(level));
        assertTrue(fight.isDragonKilled());
        assertTrue(fight.hasPreviouslyKilledDragon());
        Pos portal = fight.portalLocation();
        assertNotNull(portal);
        assertTrue(B.is(level.getBlockState(new Pos(portal.x() + 1, portal.y(), portal.z())), "minecraft:end_portal"), "출구 포털이 열렸다");
        assertTrue(B.is(level.getBlockState(new Pos(0, level.getMotionBlockingHeight(0, 0) - 1, 0)), "minecraft:dragon_egg"));
        assertEquals(19, fight.gateways().size(), "관문 하나");
    }

    @Test
    void fourCrystalsOnExitPortalRespawnDragonAndRebuildPillars() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, -60, 0.5);
        p.vitals().loadGameTypes(null, GameMode.CREATIVE, false);
        var fight = new EndDragonFight(level, 0L, new Pos(0, 0, 0));
        fight.skipArenaLoadedCheck();
        level.setDragonFight(fight);
        EnderDragon first = fight.createNewDragon();
        fight.setDragonKilled(first);
        first.discard();
        assertFalse(fight.barVisible(), "처치 뒤 바 숨김");
        Pos portal = fight.portalLocation();
        int[][] off = {{0, -2}, {2, 0}, {0, 2}, {-2, 0}};
        for (int[] o : off) {
            EndCrystal c = new EndCrystal(level, level.nextEntityId());
            c.moveTo(portal.x() + o[0] + 0.5, portal.y() + 1, portal.z() + o[1] + 0.5, 0, 0);
            level.addFreshEntity(c);
        }
        fight.tryRespawn();
        assertEquals(EndDragonFight.RespawnStage.START, fight.respawnStage());
        EnderDragon second = null;
        for (int t = 0; t < 800 && second == null; t++) {
            fight.tick();
            level.tickEntities(1);
            for (Entity e : level.entities().tickSnapshot()) if (e instanceof EnderDragon d && d != first && !d.isRemoved()) second = d;
        }
        assertNotNull(second, "부활했다");
        assertFalse(fight.isDragonKilled());
        var spike = org.domi.furnace.server.world.gen.feature.EndSpikeFeature.spikesForSeed(level.worldSeed()).get(0);
        assertTrue(B.is(level.getBlockState(new Pos(spike.centerX(), spike.height() - 1, spike.centerZ())), "minecraft:obsidian"), "기둥 재생성");
        fight.updateCrystalCount();
        assertEquals(10, fight.getCrystalsAlive());
        assertTrue(fight.barPlayers().contains(p));
    }

    @Test
    void laterKillsAward500() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        assertEquals(500, d.experienceTotal());
        d.setHealth(0.0F);
        for (int i = 0; i < 200; i++) d.tickDeath();
        assertTrue(d.isRemoved());
        assertEquals(500, orbTotal(level));
    }

    @Test
    void crystalHealsEveryTenTicks() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        d.setNoAi(false);
        EndCrystal c = new EndCrystal(level, level.nextEntityId());
        c.moveTo(5.5, 70, 0.5, 0, 0);
        level.addFreshEntity(c);
        d.setHealth(100.0F);
        level.tickEntities(200);
        assertSame(c, d.nearestCrystal);
        assertTrue(d.health() > 100.0F);
        assertTrue(c.hurtBy(PLAYER_HIT, 1.0F, null));
        assertTrue(c.isRemoved());
        assertFalse(new EndCrystal(level, level.nextEntityId()).hurtBy(PLAYER_HIT, 1.0F, d), "드래곤에게는 안 깨진다");
    }

    @Test
    void saveRoundTrip() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 70, 0.5);
        d.getPhaseManager().setPhase(DragonPhase.CHARGING_PLAYER);
        d.dragonDeathTime = 17;
        Entity back = Entities.load(level, d.save());
        assertInstanceOf(EnderDragon.class, back);
        EnderDragon b = (EnderDragon) back;
        assertEquals(DragonPhase.CHARGING_PLAYER, b.getPhaseManager().getCurrentPhase().id());
        assertEquals(17, b.dragonDeathTime);
        assertEquals(d.uuid(), b.uuid());

        var fight = new EndDragonFight(level, 42L, new Pos(0, 0, 0));
        fight.spawnExitPortal(false);
        var f2 = new EndDragonFight(level, 0L, new Pos(0, 0, 0));
        f2.load(fight.save());
        assertEquals(fight.portalLocation(), f2.portalLocation());
        assertEquals(fight.gateways(), f2.gateways());
    }

    @Test
    void nodeGraphPathAndFixedSeedReplay() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level, 0.5, 100, 0.5);
        int start = d.findClosestNode();
        assertTrue(start >= 12, "전투가 없으면 안쪽 고리만");
        Path path = d.findPath(12, 16, null);
        assertNotNull(path);
        assertEquals(12, indexOf(d, path.getNode(0)));

        double[] a = flight(77L), b = flight(77L);
        assertArrayEquals(a, b, "같은 시드면 같은 궤적");
        assertFalse(java.util.Arrays.equals(a, flight(78L)), "시드가 다르면 다르다");
    }

    private static int indexOf(EnderDragon d, org.domi.furnace.server.entity.ai.navigation.Node n) {
        return d.findClosestNode(n.x, n.y, n.z);
    }

    private static double[] flight(long seed) {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = new EnderDragon(level, 1000);
        d.moveTo(0.5, 100, 0.5, 0, 0);
        level.addFreshEntity(d);
        ((LegacyRandomSource) d.random()).setSeed(seed);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        level.tickEntities(300);
        return new double[] {d.x(), d.y(), d.z(), d.yRot()};
    }
}
