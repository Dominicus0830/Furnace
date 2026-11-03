// 워든 시험: 분노 관리 값(증가·상한·감소·정렬·제거 사유), 저장 왕복(분노·진동), 진동 수신 → 분노·소란 위치,
// 땅에서 나오는 동안 무적, 경고 추적기, 소환(SpawnUtil) 난수 순서 고정 시드 대조.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.domi.furnace.server.world.gameevent.VibrationSystem;
import org.junit.jupiter.api.Test;

class WardenTest {

    static class Lvl extends BlockPlacementTest.TestLevel {
        long time = 100;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return time; }
        };
        @Override protected LevelServices services() { return svc; }
    }

    static Warden warden(Lvl l, double x, double y, double z) {
        Warden w = new Warden(l, l.nextEntityId());
        w.moveTo(x, y, z, 0, 0);
        assertTrue(l.addFreshEntity(w));
        return w;
    }

    static Cow cow(Lvl l, double x, double y, double z) {
        Cow c = new Cow(l, l.nextEntityId());
        c.moveTo(x, y, z, 0, 0);
        assertTrue(l.addFreshEntity(c));
        return c;
    }

    @Test
    void angerIncreasesClampsDecaysAndSorts() {
        Lvl l = new Lvl();
        Warden w = warden(l, 0.5, -60, 0.5);
        Cow a = cow(l, 3.5, -60, 0.5), b = cow(l, 5.5, -60, 0.5);
        var am = w.angerManagement();
        assertEquals(35, am.increaseAnger(a, 35));
        assertEquals(90, am.increaseAnger(b, 90));
        assertEquals(150, am.increaseAnger(b, 100), "상한 150");
        assertSame(b, am.suspects().get(0), "화난(80 이상) 쪽이 먼저");
        assertEquals(150, am.getActiveAnger(null), "대상이 없으면 가장 큰 분노");
        am.tick(l, t -> true);
        assertEquals(34, am.getActiveAnger(a));
        assertEquals(149, am.getActiveAnger(b));
        // 죽은(KILLED) 용의자는 버리고, 내려간(UNLOADED_TO_CHUNK) 용의자는 UUID 로 남긴다
        a.remove(Entity.RemovalReason.KILLED);
        b.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        am.tick(l, t -> true);
        assertTrue(am.suspects().isEmpty());
        var saved = am.save().getList("suspects");
        assertEquals(1, saved.size());
        assertEquals(148, ((org.domi.furnace.server.net.io.nbt.NbtCompound) saved.get(0)).getInt("anger"));
        assertEquals(AngerManagement.AngerLevel.CALM, AngerManagement.AngerLevel.byAnger(39));
        assertEquals(AngerManagement.AngerLevel.AGITATED, AngerManagement.AngerLevel.byAnger(40));
        assertTrue(AngerManagement.AngerLevel.byAnger(80).isAngry());
    }

    @Test
    void saveRoundTripKeepsAngerAndListener() {
        Lvl l = new Lvl();
        Warden w = warden(l, 0.5, -60, 0.5);
        Cow c = cow(l, 3.5, -60, 0.5);
        w.increaseAngerAt(c, 50, false);
        assertEquals(50, w.angerManagement().getActiveAnger(c));
        var tag = w.saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        assertTrue(tag.contains("anger"));
        assertTrue(tag.contains("listener"));
        Warden back = new Warden(l, l.nextEntityId());
        back.load(tag);
        // UUID 로 불러와 다음 변환 틱에 엔티티로 돌아온다(변환 지연 0..2)
        for (int i = 0; i < 3; i++) back.angerManagement().tick(l, t -> true);
        assertEquals(47, back.angerManagement().getActiveAnger(c));
    }

    @Test
    void vibrationFromMobAngersWardenAndSetsDisturbance() {
        Lvl l = new Lvl();
        Warden w = warden(l, 0.5, -60, 0.5);
        Cow c = cow(l, 6.5, -60, 0.5);
        l.gameEvent(GameEvent.STEP, new Vec3(c.x(), c.y(), c.z()), GameEventContext.of(c));
        var data = w.getVibrationData();
        for (int i = 0; i < 10 && w.angerManagement().getActiveAnger(c) == 0; i++) {
            l.time++;
            VibrationSystem.Ticker.tick(l, data, w.getVibrationUser());
        }
        assertEquals(35, w.angerManagement().getActiveAnger(c), "진동 한 번 = 분노 35");
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.VIBRATION_COOLDOWN));
        assertEquals(new Pos(6, -60, 0), w.getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION).orElseThrow());
        // 진동 대기 중에는 받지 않는다
        assertFalse(w.getVibrationUser().canReceiveVibration(l, new Pos(3, -60, 0), GameEvent.STEP, GameEventContext.of(c)));
    }

    @Test
    void wardenDampensAndIsInvulnerableWhileEmerging() {
        Lvl l = new Lvl();
        Warden w = warden(l, 0.5, -60, 0.5);
        assertTrue(w.dampensVibrations());
        w.finalizeTriggeredSpawn(l);
        assertEquals(Entity.POSE_EMERGING, w.getPose());
        assertEquals(1.0F, w.bbHeight());
        assertTrue(w.isInvulnerableTo(new Entity.Damage("mob_attack", false, false, false)));
        assertFalse(w.isInvulnerableTo(Entity.Damage.OUT_OF_WORLD));
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.IS_EMERGING));
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN));
    }

    @Test
    void spawnTrackerDecaysAndSaves() {
        var t = new WardenSpawnTracker(11999, 3, 5);
        t.tick();
        assertEquals(3, t.getWarningLevel());
        t.tick();
        assertEquals(2, t.getWarningLevel(), "12000틱마다 한 단계");
        var back = WardenSpawnTracker.load(t.save());
        assertEquals(2, back.getWarningLevel());
        assertEquals(t.save().getInt("cooldown_ticks"), back.save().getInt("cooldown_ticks"));
        t.setWarningLevel(9);
        assertEquals(4, t.getWarningLevel());
    }

    @Test
    void triggeredSpawnConsumesRandomInVanillaOrder() {
        Lvl l = new Lvl();
        ((LegacyRandomSource) l.random()).setSeed(12345L);
        RandomSource ref = RandomSource.create(12345L);
        int dx = ref.nextInt(11) - 5, dz = ref.nextInt(11) - 5;   // 첫 시도: 평지라 바로 성공
        float yaw = ref.nextFloat() * 360.0F;
        assertTrue(Warden.trySpawnTriggered(l, new Pos(0, -60, 0)));
        Warden w = l.getEntitiesOfClass(Warden.class, new AABB(-10, -70, -10, 10, -40, 10), e -> true).get(0);
        assertEquals(dx + 0.5, w.x());
        assertEquals(-60.0, w.y(), "잔디(-61) 위");
        assertEquals(dz + 0.5, w.z());
        assertEquals(org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(yaw), w.yRot());
        assertEquals(Entity.POSE_EMERGING, w.getPose());
    }

    /** 사용자 버그 C2: /summon(COMMAND) 의 finalizeSpawn 은 DIG_COOLDOWN 1200 만 두고 기본 자세 — 틱을 돌려도 파고들지 않는다. */
    @Test
    void summonedWardenDoesNotDigImmediately() {
        Lvl l = new Lvl();
        Warden w = new Warden(l, l.nextEntityId());
        w.moveTo(0.5, -60, 0.5, 0, 0);
        w.finalizeSpawn(l.random(), l.getCurrentDifficultyAt(w.blockPosition()));
        assertTrue(l.addFreshEntity(w));
        assertEquals(Entity.POSE_STANDING, w.getPose(), "소환 직후 기본 자세(EMERGING 아님)");
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN));
        assertFalse(w.getBrain().hasMemoryValue(MemoryModuleType.IS_EMERGING));
        for (int t = 0; t < 200; t++) {
            l.time++;
            w.tick();
            assertNotEquals(Entity.POSE_DIGGING, w.getPose(), "틱 " + t + " 에 파고들기 시작");
        }
        assertFalse(w.isRemoved());
    }

    /** 가까운 철 골렘을 노리는 워든을 n 틱 돌리며 음파(골렘 피해 10)와 근접 타격(피해 30) 틱을 모은다. */
    static List<String> fightNear(int ticks, java.util.function.IntPredicate advanceGameTime) {
        Lvl l = new Lvl();
        Warden w = warden(l, 0.5, -60, 0.5);
        IronGolem g = new IronGolem(l, l.nextEntityId());
        g.moveTo(2.5, -60, 0.5, 0, 0);
        assertTrue(l.addFreshEntity(g));
        w.increaseAngerAt(g, 150, false);
        w.setAttackTarget(g);
        w.getBrain().eraseMemory(MemoryModuleType.SONIC_BOOM_COOLDOWN);   // setAttackTarget 의 200틱 대기를 건너뛰고 바로 시작
        w.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, org.domi.furnace.server.entity.ai.brain.Unit.INSTANCE, 100000L);
        List<String> events = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            if (advanceGameTime.test(t)) l.time++;
            w.tickCount++;
            w.tick();
            float lost = g.maxHealth() - g.health();
            if (lost >= 30.0F) events.add("melee@" + t);
            else if (lost >= 10.0F) events.add("boom@" + t);
            g.setHealth(g.maxHealth());
            g.hurtTime = 0;
            g.invulnerableTime = 0;
            g.moveTo(2.5, -60, 0.5, 0, 0);
        }
        return events;
    }

    /**
     * 바닐라 출처(1.21.1 디컴파일 SonicBoom=bzh): 지속 ceil(60)=60, 발사 지연 ceil(34)=34, 소리 대기 60-34=26, 종료 쿨다운 40,
     * 시작 시 ATTACK_COOLING_DOWN 60. WardenAi FIGHT 목록은 SonicBoom 다음 MeleeAttack.create(18), Warden.doHurtTarget 은 음파 쿨다운 40.
     * 시작 틱 1 → 소리 대기 34 가 지난 틱 36 에 발사. 근접은 ATTACK_COOLING_DOWN 18 이 풀리는 데 19 브레인 틱(기억은 TTL 0 다음 틱에 지워짐).
     * 사용자 버그 T6: 가까운 대상 — 음파 한 번(시작 35틱 뒤) 뒤에는 근접 공격이 19틱마다 이어지고(타격마다 음파 대기 40) 음파는 다시 안 나온다. */
    @Test
    void sonicBoomOnceThenMeleeWhenTargetIsNear() {
        List<String> ev = fightNear(400, t -> true);
        assertEquals("boom@36", ev.get(0), ev.toString());
        assertEquals(1, ev.stream().filter(s -> s.startsWith("boom")).count(), ev.toString());
        List<Integer> melee = ev.stream().filter(s -> s.startsWith("melee")).map(s -> Integer.parseInt(s.substring(6))).toList();
        assertTrue(melee.size() >= 15, ev.toString());
        assertTrue(melee.get(0) > 61, "음파가 도는 60틱(ATTACK_COOLING_DOWN) 동안은 근접 없음: " + ev);
        for (int i = 1; i < melee.size(); i++) assertEquals(19, melee.get(i) - melee.get(i - 1), ev.toString());
    }

    /** 사용자 버그 T6: region 틱이 전역 gameTime 보다 앞서도(충전 중 gameTime 이 2틱 멈춤) 음파 한 번의 충전에 음파는 한 번이다. */
    @Test
    void sonicBoomFiresOncePerChargeEvenIfGameTimeLags() {
        List<String> ev = fightNear(120, t -> t != 10 && t != 20);
        assertEquals(1, ev.stream().filter(s -> s.startsWith("boom")).count(), ev.toString());
    }

    /** 사용자 버그 C2: 소환 가능한 비명체를 같은 플레이어가 대기(200틱) 간격으로 울리면 경고 단계 1→2→3→4, 4단계에 워든이 나온다. */
    @Test
    void shriekerWarningLevelsRiseAndSummonAtFour() {
        Player p = new Player(1, java.util.UUID.randomUUID(), "A", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.applyMove(4.5, -60, 4.5, 0, 0, true);
        Lvl l = new Lvl() {
            final LevelServices withPlayer = new LevelServices.Delegating(svc) {
                @Override public void forEachPlayer(java.util.function.Consumer<Player> c) { c.accept(p); }
            };
            @Override protected LevelServices services() { return withPlayer; }
        };
        Pos sp = new Pos(0, -60, 0);
        var canSummon = org.domi.furnace.server.block.B.prop("can_summon");
        var shrieking = org.domi.furnace.server.block.B.prop("shrieking");
        l.setBlock(sp, canSummon.with(org.domi.furnace.server.block.B.defaultState(org.domi.furnace.server.block.B.id("sculk_shrieker")), true), 3);
        var be = assertInstanceOf(org.domi.furnace.server.block.entity.SculkShriekerBlockEntity.class, l.getBlockEntity(sp));
        for (int level = 1; level <= 4; level++) {
            be.tryShriek(l, p);
            assertEquals(level, be.warningLevel(), "경고 단계");
            assertEquals(level, p.wardenSpawnTracker().getWarningLevel());
            assertTrue(shrieking.is(l.getBlockState(sp)), "비명 중");
            for (int t = 0; t < 200; t++) {   // 비명 90틱 뒤 응답, 추적기 대기 200틱
                l.time++;
                l.runScheduledTicks();
                p.wardenSpawnTracker().tick();
            }
            assertFalse(shrieking.is(l.getBlockState(sp)));
            int wardens = l.getEntitiesOfClass(Warden.class, new AABB(-30, -90, -30, 30, -30, 30), e -> true).size();
            assertEquals(level == 4 ? 1 : 0, wardens, "단계 " + level + " 의 워든 수");
        }
    }
}
