// A12-04: 이동 거리 15종·jump·fall_one_cm·damage_taken·damage_absorbed/resisted·damage_dealt_absorbed/resisted 통계.
// 분기·반올림은 바닐라 ServerPlayer.checkMovementStatistics(aqv.b)·checkRidingStatistics(aqv.q)·Player.actuallyHurt(cmx.f) 바이트코드 대조 결과.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.stats.PlayerStats;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovementDamageStatsTest {

    @TempDir Path dir;
    Player player;
    PlayerStats stats;

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() {
        player = newPlayer(GameMode.SURVIVAL);
        stats = player.stats();
    }

    private Player newPlayer(GameMode mode) {
        Player p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        TotemAndProtectionTest.pastSpawnInvulnerability(p.vitals());
        p.stats(new PlayerStats(dir.resolve(p.uuid().toString()), p.uuid()));
        return p;
    }

    /** onMove(dx,dy,dz, 이전 땅, 지금 땅, 달리기, 웅크림, 몸 물, 눈 물, 오를 수 있는 블록, 발밑 블록, 경로에 물). */
    private void move(double dx, double dy, double dz, boolean onGround, boolean sprint, boolean sneak, boolean inWater, boolean eyeInWater, String climbable) {
        player.vitals().onMove(dx, dy, dz, onGround, onGround, sprint, sneak, inWater, eyeInWater, climbable, "minecraft:stone", false);
    }

    private int get(String n) { return stats.get("minecraft:" + n); }

    // ------------------------------------------------------------------ 이동 거리 분기

    @Test
    void walkSprintCrouchOnGround() {
        for (int i = 0; i < 10; i++) move(0.1, 0, 0, true, false, false, false, false, null);
        assertEquals(100, get("walk_one_cm"));
        for (int i = 0; i < 10; i++) move(0.1, 0, 0, true, true, false, false, false, null);
        assertEquals(100, get("sprint_one_cm"));
        assertEquals(100, get("walk_one_cm"), "달리기는 걷기에 안 센다");
        for (int i = 0; i < 10; i++) move(0.1, 0, 0, true, false, true, false, false, null);
        assertEquals(100, get("crouch_one_cm"));
        assertEquals(100, get("walk_one_cm"));
    }

    @Test
    void sprintBeatsCrouch() {
        move(0.5, 0, 0, true, true, true, false, false, null);
        assertEquals(50, get("sprint_one_cm"));
        assertEquals(0, get("crouch_one_cm"), "달리기 다음 웅크리기 순서");
    }

    @Test
    void groundStatCountsHorizontalOnly() {
        move(0.3, 1.0, 0.4, true, false, false, false, false, null);   // 계단 오르기: dy 는 안 센다
        assertEquals(50, get("walk_one_cm"));
    }

    @Test
    void climbCountsOnlyUpwardVerticalRounded() {
        move(0.3, 0.1176, 0, false, false, false, false, false, "minecraft:ladder");
        assertEquals(12, get("climb_one_cm"), "Math.round(dy*100.0)");
        move(0.3, -0.15, 0, false, false, false, false, false, "minecraft:ladder");
        assertEquals(12, get("climb_one_cm"), "내려갈 땐 안 오른다");
        assertEquals(0, get("walk_one_cm"));
        assertEquals(0, get("fly_one_cm"));
    }

    @Test
    void airMovementNeedsMoreThan25cmHorizontal() {
        move(0.25, 0, 0, false, false, false, false, false, null);
        assertEquals(0, get("fly_one_cm"), "25 는 안 센다(25 초과만)");
        move(0.26, 0, 0, false, false, false, false, false, null);
        assertEquals(26, get("fly_one_cm"));
        move(0.0, -5, 0.0, false, false, false, false, false, null);
        assertEquals(26, get("fly_one_cm"), "수직 이동만이면 안 센다");
    }

    @Test
    void waterBranches() {
        move(0.3, 0.2, 0.4, false, false, false, true, false, null);   // 물 안(눈은 물 밖): 수평
        assertEquals(50, get("walk_on_water_one_cm"));
        move(0.3, 0.4, 0.0, false, false, false, true, true, null);    // 눈이 물속: 3차원
        assertEquals(50, get("walk_under_water_one_cm"));
        player.swimming = true;
        move(0.0, 0.3, 0.4, false, false, false, true, true, null);    // 헤엄: 3차원
        assertEquals(50, get("swim_one_cm"));
        assertEquals(50, get("walk_under_water_one_cm"), "헤엄이 먼저");
    }

    @Test
    void elytraAviatesWithAnyDistance() {
        player.setFallFlying(true);
        move(0.0, -0.01, 0.0, false, false, false, false, false, null);
        assertEquals(1, get("aviate_one_cm"), "겉날개는 25 제한 없음, 3차원");
        move(3.0, -4.0, 0.0, false, false, false, false, false, null);
        assertEquals(501, get("aviate_one_cm"));
        assertEquals(0, get("fly_one_cm"));
    }

    @Test
    void noMoveNoStat() {
        move(0, 0, 0, true, false, false, false, false, null);
        assertEquals(0, get("walk_one_cm"));
    }

    @Test
    void passengerDoesNotCountWalking() {
        var level = new BlockPlacementTest.TestLevel();
        Boat boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        assertTrue(player.startRiding(boat, true));
        move(1.0, 0, 0, true, false, false, false, false, null);
        assertEquals(0, get("walk_one_cm"));
    }

    @Test
    void ridingStatsByVehicleKind() {
        var level = new BlockPlacementTest.TestLevel();
        Boat boat = new Boat(level, level.nextEntityId());
        boat.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(boat);
        assertTrue(player.startRiding(boat, true));
        player.vitals().checkRidingStatistics(0.3, 0.0, 0.4);
        assertEquals(50, get("boat_one_cm"));
        player.vitals().checkRidingStatistics(0, 0, 0);
        assertEquals(50, get("boat_one_cm"));
        player.stopRiding();
        player.vitals().checkRidingStatistics(1, 0, 0);
        assertEquals(50, get("boat_one_cm"), "타고 있지 않으면 안 센다");

        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(pig);
        assertTrue(player.startRiding(pig, true));
        player.vitals().checkRidingStatistics(0, 0.5, 0);
        assertEquals(50, get("pig_one_cm"));
    }

    // ------------------------------------------------------------------ 점프·낙하

    @Test
    void jumpCountsOnlyWhenLeavingGroundUpward() {
        player.vitals().onMove(0, 0.42, 0, true, false, false, false, false, false, null, "minecraft:air", false);
        assertEquals(1, get("jump"));
        player.vitals().onMove(0, -0.1, 0, true, false, false, false, false, false, null, "minecraft:air", false);   // 걸어 내려감
        player.vitals().onMove(0, 0.42, 0, false, false, false, false, false, false, null, "minecraft:air", false);  // 공중에서 위로
        assertEquals(1, get("jump"));
    }

    @Test
    void fallOneCmNeedsAtLeastTwoBlocks() {
        fall(1.9);
        assertEquals(0, get("fall_one_cm"));
        fall(5.0);
        assertEquals(500, get("fall_one_cm"), "Math.round(낙하 거리*100.0)");
    }

    @Test
    void bedLandingHalvesDistanceForStatAndDamage() {
        player.vitals().onMove(0, -10, 0, false, false, false, false, false, false, null, "minecraft:air", false);
        player.vitals().onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:red_bed", false);
        assertEquals(500, get("fall_one_cm"), "침대는 거리를 절반으로 넘긴다: 10 → 5");
        assertEquals(20, get("damage_taken"), "ceil(5 - 3) = 2");
    }

    @Test
    void spectatorOnLadderDoesNotClimb() {
        Player spec = newPlayer(GameMode.SPECTATOR);
        spec.vitals().onMove(0.0, 0.5, 0.0, false, false, false, false, false, false, "minecraft:ladder", "minecraft:stone", false);
        assertEquals(0, spec.stats().get("minecraft:climb_one_cm"));
    }

    @Test
    void flyingPlayerFallsWithoutFallStat() {
        Player creative = newPlayer(GameMode.CREATIVE);
        creative.vitals().onMove(0, -6, 0, false, false, false, false, false, false, null, "minecraft:stone", false);
        creative.vitals().onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
        assertEquals(0, creative.stats().get("minecraft:fall_one_cm"), "mayfly 면 causeFallDamage 가 통계 전에 끝난다");
    }

    private void fall(double blocks) {
        player.vitals().onMove(0, -blocks, 0, false, false, false, false, false, false, null, "minecraft:air", false);
        player.vitals().onMove(0, 0, 0, false, true, false, false, false, false, null, "minecraft:stone", false);
    }

    // ------------------------------------------------------------------ 피해

    @Test
    void damageTakenIsTenTimesRounded() {
        player.vitals().hurt(DamageType.GENERIC, 4.0F);
        assertEquals(40, get("damage_taken"));
        assertEquals(0, get("damage_absorbed"));
    }

    @Test
    void absorptionAndResistanceStats() {
        player.vitals().addEffect(new MobEffectInstance(MobEffect.ABSORPTION, 1000, 0));   // 흡수 체력 4
        player.vitals().addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 1000, 0));   // 20% 줄임
        player.vitals().hurt(DamageType.byKey("cactus"), 10.0F);
        assertEquals(20, get("damage_resisted"), "10 - 8 = 2 → 20");
        assertEquals(40, get("damage_absorbed"), "흡수 체력 4 → 40");
        assertEquals(40, get("damage_taken"), "8 - 4 = 4 → 40");
    }

    @Test
    void fullyAbsorbedHurtStillCountsAbsorbedNotTaken() {
        player.vitals().addEffect(new MobEffectInstance(MobEffect.ABSORPTION, 1000, 0));
        player.vitals().hurt(DamageType.GENERIC, 3.0F);
        assertEquals(30, get("damage_absorbed"));
        assertEquals(0, get("damage_taken"), "afterAbsorb 가 0 이면 Player.actuallyHurt 가 일찍 끝난다");
    }

    @Test
    void scoreboardCriteriaFollowsStats() {
        var sb = new org.domi.furnace.server.scores.ServerScoreboard(java.util.List::of, null);
        var walk = sb.addObjective("walk", org.domi.furnace.server.scores.ObjectiveCriteria.byName("minecraft.custom:minecraft.walk_one_cm").orElseThrow(),
                org.domi.furnace.api.text.Text.text("walk"), org.domi.furnace.server.scores.ObjectiveCriteria.RenderType.INTEGER, false, null);
        var jump = sb.addObjective("jump", org.domi.furnace.server.scores.ObjectiveCriteria.byName("minecraft.custom:minecraft.jump").orElseThrow(),
                org.domi.furnace.api.text.Text.text("jump"), org.domi.furnace.server.scores.ObjectiveCriteria.RenderType.INTEGER, false, null);
        player.scoreboard(sb);
        move(0.5, 0, 0, true, false, false, false, false, null);
        player.vitals().onMove(0, 0.42, 0, true, false, false, false, false, false, null, "minecraft:air", false);
        var holder = org.domi.furnace.server.scores.ScoreHolder.of(player);
        assertEquals(50, sb.getOrCreatePlayerScore(holder, walk).get());
        assertEquals(1, sb.getOrCreatePlayerScore(holder, jump).get());
    }

    // ------------------------------------------------------------------ 몹을 때릴 때

    @Test
    void dealtAbsorbedAndResistedGoToThePlayerAttacker() {
        var level = new BlockPlacementTest.TestLevel();
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.absorptionAmount = 4f;
        z.addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 1000, 0));
        z.hurtBy(new Entity.Damage("player_attack", false, false, false), 10f, player);
        assertEquals(20, get("damage_dealt_resisted"), "10 - 8 = 2 → 20");
        assertEquals(40, get("damage_dealt_absorbed"), "흡수 4 → 40");
        assertEquals(0, get("damage_resisted"), "때린 쪽은 damage_resisted 가 아니다");
    }

    @Test
    void mobHitByNonPlayerAwardsNothing() {
        var level = new BlockPlacementTest.TestLevel();
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.absorptionAmount = 4f;
        z.hurt(new Entity.Damage("generic", false, false, false), 10f);
        assertEquals(0, get("damage_dealt_absorbed"));
    }
}
