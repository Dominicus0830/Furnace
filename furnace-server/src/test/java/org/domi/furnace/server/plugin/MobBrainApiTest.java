// 두뇌 API(E2): 기억 읽기·쓰기(버전 중립 값 모양·엔티티 UUID·만료·플러그인 기억), 센서 목록·빼기·플러그인 센서, 활동 바꾸기·붙잡기·놓기,
// 플러그인 행동(조건·시작·이어 가기·멈춤·틱 도중 변경·꺼진 플러그인/예외 정리), 두뇌를 새로 만들 때 옮겨 가기, 소유 스레드 검사.
// 주민·피글린·아홀로틀·워든으로 본다. 레벨은 스레드 없는 시험 레벨(늘 소유 스레드).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ai.MobAi;
import org.domi.furnace.api.entity.ai.brain.ActivityKey;
import org.domi.furnace.api.entity.ai.brain.BehaviorInfo;
import org.domi.furnace.api.entity.ai.brain.BrainBehavior;
import org.domi.furnace.api.entity.ai.brain.BrainSensor;
import org.domi.furnace.api.entity.ai.brain.BrainTarget;
import org.domi.furnace.api.entity.ai.brain.GlobalBlockPos;
import org.domi.furnace.api.entity.ai.brain.MemoryCondition;
import org.domi.furnace.api.entity.ai.brain.MemoryInfo;
import org.domi.furnace.api.entity.ai.brain.MemoryKey;
import org.domi.furnace.api.entity.ai.brain.MemoryValueType;
import org.domi.furnace.api.entity.ai.brain.MobBrain;
import org.domi.furnace.api.entity.ai.brain.SensorKey;
import org.domi.furnace.api.entity.ai.brain.WalkTarget;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Axolotl;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.Warden;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobBrainApiTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 시각을 시험이 올리는 레벨(낮). */
    static final class Clocked extends BlockPlacementTest.TestLevel {
        long game = 1000, day = 6000;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return day; }

                @Override public long gameTime() { return game; }
            };
        }

        void step(Entity... es) {
            game++;
            day++;
            for (Entity e : es) {
                if (e.isRemoved()) continue;
                e.setOldPosAndRot();
                e.tickCount++;
                e.tick();
            }
        }

        void run(int n, Entity... es) { for (int i = 0; i < n; i++) step(es); }
    }

    private static final TestPlugins.Gamma ON = new TestPlugins.Gamma();

    static {
        ON.setEnabled(true);
    }

    private static <T extends Entity> T put(BlockPlacementTest.TestLevel level, T e, double x, double z) {
        e.moveTo(x, -60, z, 0, 0);
        e.setOnGround(true);
        level.addFreshEntity(e);
        return e;
    }

    /** 시험 플러그인 끄기(로더 없이 켠 플러그인은 작업 정리에서 NPE 가 나지만 꺼짐 표시는 먼저 바뀐다). */
    private static void disable(TestPlugins.Gamma p) {
        try {
            p.setEnabled(false);
        } catch (NullPointerException ignored) {
        }
        assertFalse(p.isEnabled());
    }

    private static MobAi ai(Mob m) { return new MobAiImpl(null, m); }

    private static MobBrain brain(Mob m) { return ai(m).brain().orElseThrow(); }

    // ------------------------------------------------------------------ 기억

    @Test
    void nonBrainMobHasNoBrainHandle() {
        var level = new Clocked();
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 0.5, 0.5);
        assertEquals(Optional.empty(), ai(cow).brain());
    }

    @Test
    void villagerMemoriesReadAndWriteAsVersionNeutralValues() {
        var level = new Clocked();
        Villager v = put(level, new Villager(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(v);
        GlobalBlockPos job = GlobalBlockPos.of(level.dimension(), 5, -60, 7);
        assertTrue(b.hasMemory(MemoryKey.JOB_SITE));
        assertTrue(b.setMemory(MemoryKey.JOB_SITE, job));
        assertEquals(Optional.of(new GlobalPos(level.dimension(), new Pos(5, -60, 7))), v.getBrain().getMemory(MemoryModuleType.JOB_SITE), "서버 값으로 들어간다");
        assertEquals(Optional.of(job), b.memory(MemoryKey.JOB_SITE));
        assertEquals(java.util.OptionalLong.empty(), b.memoryExpiry(MemoryKey.JOB_SITE), "영구 값");

        // 기억 목록: 이름·모양, 내부 값(길)은 모양 없음
        List<MemoryInfo> all = b.memories();
        MemoryInfo jobInfo = all.stream().filter(m -> m.key().equals("minecraft:job_site")).findFirst().orElseThrow();
        assertEquals(MemoryValueType.GLOBAL_POS, jobInfo.type());
        assertTrue(jobInfo.present());
        assertEquals(job, jobInfo.value());
        MemoryInfo path = all.stream().filter(m -> m.key().equals("minecraft:path")).findFirst().orElseThrow();
        assertNull(path.type(), "길은 API 로 다루지 않는다");

        // 모양이 다르면 거부, 이 몹이 쓰지 않는 기억은 바닐라처럼 무시
        assertThrows(IllegalArgumentException.class, () -> b.memory(MemoryKey.minecraft("job_site", MemoryValueType.BLOCK_POS)));
        assertThrows(IllegalArgumentException.class, () -> b.setMemory(MemoryKey.minecraft("path", MemoryValueType.BOOLEAN), true));
        assertFalse(b.hasMemory(MemoryKey.ANGRY_AT));
        assertFalse(b.setMemory(MemoryKey.ANGRY_AT, UUID.randomUUID()), "등록 안 된 기억");
        b.registerMemory(MemoryKey.ANGRY_AT);
        UUID who = UUID.randomUUID();
        assertTrue(b.setMemory(MemoryKey.ANGRY_AT, who));
        assertEquals(Optional.of(who), b.memory(MemoryKey.ANGRY_AT));
        assertThrows(IllegalArgumentException.class, () -> b.registerMemory(MemoryKey.minecraft("no_such_memory", MemoryValueType.BOOLEAN)));

        // 지우기, 빈 목록은 지우기(바닐라 setMemory)
        assertTrue(b.eraseMemory(MemoryKey.JOB_SITE));
        assertEquals(Optional.empty(), b.memory(MemoryKey.JOB_SITE));
        assertTrue(b.setMemory(MemoryKey.SECONDARY_JOB_SITE, List.of(job)));
        assertEquals(Optional.of(List.of(job)), b.memory(MemoryKey.SECONDARY_JOB_SITE));
        assertTrue(b.setMemory(MemoryKey.SECONDARY_JOB_SITE, List.of()));
        assertFalse(v.getBrain().hasMemoryValue(MemoryModuleType.SECONDARY_JOB_SITE));

        // 저장은 바닐라 그대로(저장 방식 있는 기억만)
        b.setMemory(MemoryKey.HOME, job);
        NbtCompound saved = v.getBrain().save();
        assertTrue(((NbtCompound) saved.tags().get("memories")).tags().containsKey("minecraft:home"));
    }

    @Test
    void entityMemoriesUseUuidsAndCheckTheKind() {
        var level = new Clocked();
        Piglin p = put(level, new Piglin(level, level.nextEntityId()), 0.5, 0.5);
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 3.5, 0.5);
        MobBrain b = brain(p);
        assertTrue(b.setMemory(MemoryKey.ATTACK_TARGET, cow.uuid()));
        assertSame(cow, p.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow(), "같은 region 의 엔티티로 바뀐다");
        assertEquals(Optional.of(cow.uuid()), b.memory(MemoryKey.ATTACK_TARGET));
        assertEquals(Optional.of(cow.uuid()), ai(p).target(), "목표 API 와 같은 값");
        assertFalse(b.setMemory(MemoryKey.ATTACK_TARGET, UUID.randomUUID()), "없는 엔티티");
        assertFalse(b.setMemory(MemoryKey.NEAREST_VISIBLE_PLAYER, cow.uuid()), "플레이어만 받는 기억에 소");

        // 걸어갈 목표·바라볼 목표
        WalkTarget walk = new WalkTarget(BrainTarget.entity(cow.uuid()), 1.0f, 2);
        assertTrue(b.setMemory(MemoryKey.WALK_TARGET, walk));
        assertEquals(Optional.of(walk), b.memory(MemoryKey.WALK_TARGET));
        assertTrue(b.setMemory(MemoryKey.LOOK_TARGET, BrainTarget.block(4, -60, 4)));
        assertEquals(Optional.of(BrainTarget.block(4, -60, 4)), b.memory(MemoryKey.LOOK_TARGET));
        assertTrue(b.setMemory(MemoryKey.LOOK_TARGET, BrainTarget.entity(cow.uuid())));
        assertInstanceOf(EntityTracker.class, p.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow());

        // 보이는 생물 목록(내부 질의 객체) ↔ UUID 목록
        assertTrue(b.setMemory(MemoryKey.NEAREST_VISIBLE_LIVING_ENTITIES, List.of(cow.uuid())));
        assertTrue(p.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseThrow().contains(cow));
    }

    @Test
    void timedMemoriesExpireLikeVanillaAndPluginMemoriesAreNotSaved() {
        var level = new Clocked();
        Axolotl a = put(level, new Axolotl(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(a);
        assertTrue(b.setMemory(MemoryKey.HAS_HUNTING_COOLDOWN, true, 3));
        assertEquals(3, b.memoryExpiry(MemoryKey.HAS_HUNTING_COOLDOWN).getAsLong());
        assertThrows(IllegalArgumentException.class, () -> b.setMemory(MemoryKey.HAS_HUNTING_COOLDOWN, true, 0));

        MemoryKey<String> note = MemoryKey.plugin("test", "note", String.class);
        assertFalse(b.setMemory(note, "x"), "등록 전");
        b.registerMemory(note);
        assertTrue(b.setMemory(note, "hello", 3));
        assertEquals(Optional.of("hello"), b.memory(note));
        assertTrue(b.memories().stream().anyMatch(m -> m.plugin() && m.key().equals("test:note") && "hello".equals(m.value())));
        assertThrows(IllegalArgumentException.class, () -> b.setMemory((MemoryKey) note, 5), "모양이 다른 값");

        level.run(4, a);
        assertEquals(Optional.empty(), b.memory(MemoryKey.HAS_HUNTING_COOLDOWN), "3틱 뒤 지워진다");
        assertEquals(Optional.empty(), b.memory(note), "플러그인 기억도 같은 규칙");
        assertTrue(b.hasMemory(note), "등록은 남는다");

        b.setMemory(note, "kept");
        NbtCompound mem = (NbtCompound) a.getBrain().save().tags().get("memories");
        assertFalse(mem.tags().keySet().stream().anyMatch(k -> k.startsWith("test:")), "플러그인 기억은 저장하지 않는다(바닐라 결과 그대로)");
    }

    @Test
    void flagMemoriesReadAsTrueAndFalseErases() {
        var level = new Clocked();
        Warden w = put(level, new Warden(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(w);
        assertTrue(b.setMemory(MemoryKey.DIG_COOLDOWN, true, 1200));
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN));
        assertEquals(Optional.of(true), b.memory(MemoryKey.DIG_COOLDOWN));
        assertTrue(b.setMemory(MemoryKey.DIG_COOLDOWN, false));
        assertFalse(w.getBrain().hasMemoryValue(MemoryModuleType.DIG_COOLDOWN));
        assertTrue(b.setMemory(MemoryKey.DISTURBANCE_LOCATION, new BlockPos(1, -60, 2)));
        assertEquals(Optional.of(new Pos(1, -60, 2)), w.getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION));
    }

    @Test
    void everyVanillaMemoryHasAnApiShapeExceptInternalOnes() {
        Set<String> opaque = new java.util.TreeSet<>();
        for (var e : BrainValues.all().entrySet()) if (e.getValue().api() == null) opaque.add(e.getKey().name());
        assertEquals(Set.of("dummy", "path", "hurt_by", "ram_target"), opaque, "새 기억이 생기면 모양을 정할 것");
        for (var f : MemoryKey.class.getFields()) {
            if (f.getType() != MemoryKey.class) continue;
            try {
                MemoryKey<?> k = (MemoryKey<?>) f.get(null);
                MemoryModuleType<?> t = MemoryModuleType.byName(k.name());
                assertNotNull(t, "상수 " + k.key() + " 는 이 판의 기억");
                assertEquals(BrainValues.shape(t).api(), k.type(), "상수 " + k.key() + " 의 모양");
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> MemoryKey.minecraft("x", MemoryValueType.object(String.class)));
    }

    // ------------------------------------------------------------------ 활동

    @Test
    void piglinActivitiesCanBeReadTriedForcedAndReleased() {
        var level = new Clocked();
        Piglin p = put(level, new Piglin(level, level.nextEntityId()), 0.5, 0.5);
        level.run(2, p);
        MobBrain b = brain(p);
        assertTrue(b.activities().containsAll(Set.of(ActivityKey.CORE, ActivityKey.IDLE, ActivityKey.ADMIRE_ITEM, ActivityKey.FIGHT, ActivityKey.AVOID, ActivityKey.CELEBRATE)),
                b.activities().toString());
        assertEquals(Set.of(ActivityKey.CORE), b.coreActivities());
        assertEquals(ActivityKey.IDLE, b.defaultActivity());
        assertEquals(Set.of(ActivityKey.CORE, ActivityKey.IDLE), b.activeActivities());

        assertFalse(b.trySetActivity(ActivityKey.FIGHT), "공격 대상 기억이 없으면 싸움 활동 조건이 안 맞는다");
        assertEquals(Set.of(ActivityKey.CORE, ActivityKey.IDLE), b.activeActivities());

        // 붙잡지 않으면 몹이 다음 틱 스스로 되돌린다
        p.getBrain().setMemory(MemoryModuleType.CELEBRATE_LOCATION, new Pos(0, -60, 0));
        assertTrue(b.trySetActivity(ActivityKey.CELEBRATE));
        p.getBrain().eraseMemory(MemoryModuleType.CELEBRATE_LOCATION);
        level.step(p);
        assertFalse(b.activeActivities().contains(ActivityKey.CELEBRATE), "조건이 사라지면 피글린이 스스로 다시 고른다");

        // 붙잡으면 머문다(조건 없이도)
        b.forceActivity(ActivityKey.AVOID);
        assertEquals(Optional.of(ActivityKey.AVOID), b.forcedActivity());
        level.run(40, p);
        assertEquals(Set.of(ActivityKey.CORE, ActivityKey.AVOID), b.activeActivities(), "40틱 뒤에도 피하기");
        assertTrue(b.releaseActivity());
        assertFalse(b.releaseActivity());
        assertEquals(Set.of(ActivityKey.CORE, ActivityKey.IDLE), b.activeActivities(), "놓으면 기본 활동");
        level.run(5, p);
        assertEquals(Set.of(ActivityKey.CORE, ActivityKey.IDLE), b.activeActivities());
        assertThrows(IllegalArgumentException.class, () -> b.forceActivity(ActivityKey.minecraft("no_such_activity")));
    }

    @Test
    void forcingActivityErasesMemoriesOfTheActivityItStopsLikeVanilla() {
        var level = new Clocked();
        Warden w = put(level, new Warden(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(w);
        b.forceActivity(ActivityKey.ROAR);
        Cow cow = put(level, new Cow(level, level.nextEntityId()), 3.5, 0.5);
        assertTrue(b.setMemory(MemoryKey.ROAR_TARGET, cow.uuid()));
        b.forceActivity(ActivityKey.IDLE);
        assertFalse(w.getBrain().hasMemoryValue(MemoryModuleType.ROAR_TARGET), "포효 활동이 꺼지면서 포효 대상 기억을 지운다(바닐라 규칙)");
    }

    // ------------------------------------------------------------------ 행동

    /** 부른 횟수를 세는 행동. */
    static class Probe implements BrainBehavior {
        final String name;
        int starts, ticks, stops, keepFor, current;
        Map<MemoryKey<?>, MemoryCondition> req = Map.of();
        Probe(String name, int keepFor) {
            this.name = name;
            this.keepFor = keepFor;
        }
        @Override public String name() { return name; }
        @Override public Map<MemoryKey<?>, MemoryCondition> requiredMemories() { return req; }
        @Override public boolean shouldContinue(MobAi mob, MobBrain brain) { return current < keepFor; }
        @Override public void start(MobAi mob, MobBrain brain) { starts++; current = 0; }
        @Override public void tick(MobAi mob, MobBrain brain) { ticks++; current++; }
        @Override public void stop(MobAi mob, MobBrain brain) { stops++; }
    }

    @Test
    void pluginBehaviorRunsInItsActivityAndPluginActivityCanBeForced() {
        var level = new Clocked();
        Villager v = put(level, new Villager(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(v);
        ActivityKey guard = ActivityKey.of("test", "guard");
        Probe p = new Probe("test:guard_post", 5);
        b.addBehavior(ON, guard, 0, p);
        b.addBehavior(ON, guard, 0, p);
        assertEquals(1, b.behaviors().stream().filter(BehaviorInfo::plugin).count(), "같은 행동 객체는 한 번만");
        assertTrue(b.activities().contains(guard));
        level.run(10, v);
        assertEquals(0, p.starts, "켜지지 않은 활동의 행동은 시작하지 않는다");

        b.forceActivity(guard);
        assertEquals(Set.of(ActivityKey.CORE, guard), b.activeActivities());
        level.run(1, v);
        assertEquals(1, p.starts);
        assertEquals(1, p.ticks, "시작한 틱에 바로 한 번 돈다(바닐라: 시작 → 돌던 행동 틱)");
        assertTrue(b.behaviors().stream().anyMatch(i -> i.plugin() && i.running() && i.name().equals("test:guard_post") && i.activity().equals(guard)));
        level.run(4, v);
        assertEquals(5, p.ticks);
        assertEquals(0, p.stops);
        level.run(1, v);
        assertEquals(1, p.stops, "shouldContinue 가 false 면 멈춘다");
        level.run(1, v);
        assertEquals(2, p.starts, "멈춘 다음 틱 다시 시작(바닐라 행동 규칙)");
        level.run(200, v);
        assertEquals(Set.of(ActivityKey.CORE, guard), b.activeActivities(), "주민 시간표가 지나가도 붙잡은 활동에 머문다");

        assertTrue(b.removeBehavior(p));
        assertFalse(b.removeBehavior(p));
        assertEquals(p.starts, p.stops, "돌던 행동은 멈춘 뒤 빠진다");
    }

    @Test
    void behaviorConditionsUseVanillaAndPluginMemories() {
        var level = new Clocked();
        Axolotl a = put(level, new Axolotl(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(a);
        MemoryKey<Boolean> alarm = MemoryKey.plugin("test", "alarm", Boolean.class);
        Probe p = new Probe("alarm", 0);
        p.req = Map.of(alarm, MemoryCondition.PRESENT, MemoryKey.PLAY_DEAD_TICKS, MemoryCondition.ABSENT);
        b.addBehavior(ON, ActivityKey.CORE, 99, p);
        assertTrue(b.hasMemory(alarm), "조건 기억은 넣을 때 등록된다");
        level.run(5, a);
        assertEquals(0, p.starts);
        b.setMemory(alarm, true);
        level.run(1, a);
        assertEquals(1, p.starts);
        b.setMemory(MemoryKey.PLAY_DEAD_TICKS, 50);
        level.run(5, a);
        assertEquals(1, p.starts, "죽은 척 기억이 있으면 시작하지 않는다");
    }

    @Test
    void changingBehaviorsInsideCallbacksIsAppliedAfterTheTick() {
        var level = new Clocked();
        Piglin pg = put(level, new Piglin(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(pg);
        Probe late = new Probe("late", 0);
        List<String> log = new ArrayList<>();
        BrainBehavior swapper = new BrainBehavior() {
            @Override public String name() { return "swapper"; }
            @Override public void start(MobAi mob, MobBrain brain) {
                log.add("start");
                brain.removeBehavior(this);
                brain.addBehavior(ON, ActivityKey.CORE, 0, late);
                log.add("cleared " + (brain.clearBehaviors(ActivityKey.CELEBRATE) > 0));
            }
        };
        b.addBehavior(ON, ActivityKey.CORE, 0, swapper);
        assertDoesNotThrow(() -> level.run(3, pg));
        assertEquals(List.of("start", "cleared true"), log);
        assertTrue(late.starts >= 1, "틱 끝에 넣은 행동이 다음 틱부터 돈다");
        assertTrue(b.behaviors().stream().noneMatch(i -> i.name().equals("swapper")));
        assertTrue(b.behaviors().stream().noneMatch(i -> i.activity().equals(ActivityKey.CELEBRATE)), "축하 활동 행동이 바닐라 것까지 빠졌다");
        assertTrue(b.activities().contains(ActivityKey.CELEBRATE), "활동 자체는 남는다");
    }

    @Test
    void disabledOrThrowingPluginBehaviorRemovesItself() {
        var level = new Clocked();
        Villager v = put(level, new Villager(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(v);
        var off = new TestPlugins.Gamma();
        off.setEnabled(true);
        Probe p = new Probe("off", 100);
        b.addBehavior(off, ActivityKey.CORE, 0, p);
        level.run(3, v);
        assertEquals(1, p.starts);
        disable(off);
        level.run(2, v);
        assertTrue(b.behaviors().stream().noneMatch(BehaviorInfo::plugin), "꺼진 플러그인 행동은 빠진다");
        assertEquals(0, p.stops, "꺼진 플러그인 콜백은 더 부르지 않는다");

        BrainBehavior bad = new BrainBehavior() {
            @Override public String name() { return "bad"; }
            @Override public void start(MobAi mob, MobBrain brain) { throw new IllegalStateException("boom"); }
        };
        b.addBehavior(ON, ActivityKey.CORE, 0, bad);
        assertDoesNotThrow(() -> level.run(3, v));
        assertTrue(b.behaviors().stream().noneMatch(i -> i.name().equals("bad")));
    }

    @Test
    void refreshedVillagerBrainKeepsPluginParts() {
        var level = new Clocked();
        Villager v = put(level, new Villager(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(v);
        ActivityKey guard = ActivityKey.of("test", "guard");
        Probe p = new Probe("kept", 1000);
        b.addBehavior(ON, guard, 0, p);
        MemoryKey<Integer> count = MemoryKey.plugin("test", "count", Integer.class);
        b.addSensor(ON, new BrainSensor() {
            @Override public SensorKey key() { return SensorKey.of("test", "counter"); }
            @Override public int scanRate() { return 1; }
            @Override public Set<MemoryKey<?>> memories() { return Set.of(count); }
            @Override public void sense(MobAi mob, MobBrain brain) { brain.setMemory(count, brain.memory(count).orElse(0) + 1); }
        });
        b.forceActivity(guard);
        level.run(3, v);
        var before = v.getBrain();
        v.refreshBrain(level);
        assertNotSame(before, v.getBrain(), "주민이 두뇌를 새로 만들었다");
        assertEquals(Set.of(ActivityKey.CORE, guard), b.activeActivities(), "붙잡은 활동이 새 두뇌에서도 켜진다");
        assertEquals(Optional.of(guard), b.forcedActivity());
        assertTrue(b.sensors().contains(SensorKey.of("test", "counter")));
        int c = b.memory(count).orElseThrow();
        assertTrue(c >= 2, "플러그인 기억이 옮겨 갔다: " + c);
        assertEquals(1, p.stops, "새로 만들 때 돌던 행동은 멈춘다(바닐라 stopAll)");
        level.run(2, v);
        assertEquals(2, p.starts, "옮겨 간 행동이 새 두뇌에서 다시 시작");
        assertTrue(b.memory(count).orElseThrow() > c);
        assertTrue(b.behaviors().stream().anyMatch(i -> !i.plugin() && i.activity().equals(ActivityKey.CORE)), "바닐라 핵심 행동은 다시 붙었다");
    }

    // ------------------------------------------------------------------ 센서

    @Test
    void sensorsCanBeListedRemovedAndAdded() {
        var level = new Clocked();
        Warden w = put(level, new Warden(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(w);
        assertTrue(b.sensors().containsAll(List.of(SensorKey.NEAREST_PLAYERS, SensorKey.WARDEN_ENTITY_SENSOR)), b.sensors().toString());

        int[] calls = {0};
        MemoryKey<UUID> seen = MemoryKey.plugin("test", "seen", UUID.class);
        BrainSensor s = new BrainSensor() {
            @Override public SensorKey key() { return SensorKey.of("test", "watch"); }
            @Override public int scanRate() { return 5; }
            @Override public Set<MemoryKey<?>> memories() { return Set.of(seen, MemoryKey.NEAREST_ATTACKABLE); }
            @Override public void sense(MobAi mob, MobBrain brain) {
                calls[0]++;
                brain.setMemory(seen, mob.uniqueId());
            }
        };
        b.addSensor(ON, s);
        assertThrows(IllegalArgumentException.class, () -> b.addSensor(ON, s), "같은 이름표");
        assertEquals(SensorKey.of("test", "watch"), b.sensors().get(b.sensors().size() - 1), "바닐라 센서 뒤");
        level.run(20, w);
        assertTrue(calls[0] >= 3 && calls[0] <= 4, "5틱에 한 번: " + calls[0]);
        assertEquals(Optional.of(w.uuid()), b.memory(seen));

        assertTrue(b.removeSensor(SensorKey.WARDEN_ENTITY_SENSOR));
        assertFalse(b.sensors().contains(SensorKey.WARDEN_ENTITY_SENSOR));
        assertTrue(b.hasMemory(MemoryKey.NEAREST_ATTACKABLE), "센서가 채우던 기억 등록은 남는다");
        assertTrue(b.removeSensor(SensorKey.of("test", "watch")));
        int at = calls[0];
        level.run(20, w);
        assertEquals(at, calls[0]);
        assertFalse(b.removeSensor(SensorKey.of("test", "watch")));
    }

    @Test
    void throwingOrDisabledSensorIsDropped() {
        var level = new Clocked();
        Axolotl a = put(level, new Axolotl(level, level.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(a);
        b.addSensor(ON, new BrainSensor() {
            @Override public SensorKey key() { return SensorKey.of("test", "bad"); }
            @Override public int scanRate() { return 1; }
            @Override public void sense(MobAi mob, MobBrain brain) { throw new IllegalStateException("boom"); }
        });
        var off = new TestPlugins.Gamma();
        off.setEnabled(true);
        int[] n = {0};
        b.addSensor(off, new BrainSensor() {
            @Override public SensorKey key() { return SensorKey.of("test", "off"); }
            @Override public int scanRate() { return 1; }
            @Override public void sense(MobAi mob, MobBrain brain) { n[0]++; }
        });
        assertDoesNotThrow(() -> level.run(3, a));
        assertFalse(b.sensors().contains(SensorKey.of("test", "bad")));
        assertTrue(n[0] >= 1);
        disable(off);
        level.run(3, a);
        assertFalse(b.sensors().contains(SensorKey.of("test", "off")));
    }

    // ------------------------------------------------------------------ 바닐라 그대로 · 스레드

    @Test
    void untouchedBrainBehavesExactlyAsBefore() {
        // 같은 시드로 두 피글린을 나란히 돌린다: 한쪽은 손잡이만 얻고(읽기만) 다른 쪽은 아무것도 안 한다 → 기억·활동이 같다.
        var l1 = new Clocked();
        var l2 = new Clocked();
        Piglin a = put(l1, new Piglin(l1, l1.nextEntityId()), 0.5, 0.5);
        Piglin c = put(l2, new Piglin(l2, l2.nextEntityId()), 0.5, 0.5);
        MobBrain b = brain(a);
        b.memories();
        b.behaviors();
        b.activeActivities();
        l1.run(60, a);
        l2.run(60, c);
        assertEquals(c.getBrain().getActiveActivities().toString(), a.getBrain().getActiveActivities().toString());
        assertEquals(((NbtCompound) c.getBrain().save().tags().get("memories")).tags().keySet(),
                ((NbtCompound) a.getBrain().save().tags().get("memories")).tags().keySet());
    }

    @Test
    void otherThreadsAreRejected() {
        var level = new BlockPlacementTest.TestLevel() {
            @Override public boolean isOwnerThread() { return false; }
        };
        Villager v = new Villager(level, level.nextEntityId());
        MobBrain b = new MobBrainImpl(null, v);
        assertThrows(ThreadOwnershipException.class, () -> ai(v).brain());
        assertThrows(ThreadOwnershipException.class, b::memories);
        assertThrows(ThreadOwnershipException.class, () -> b.setMemory(MemoryKey.HOME, GlobalBlockPos.of("minecraft:overworld", 0, 0, 0)));
        assertThrows(ThreadOwnershipException.class, () -> b.forceActivity(ActivityKey.IDLE));
        assertThrows(ThreadOwnershipException.class, () -> b.addBehavior(ON, ActivityKey.IDLE, 0, new Probe("x", 0)));
    }

    @Test
    void activityAndKeyParsing() {
        assertEquals(ActivityKey.of("myplugin", "guard"), ActivityKey.parse("MyPlugin:Guard"));
        assertEquals(ActivityKey.WORK, ActivityKey.parse("work"));
        assertSame(Activity.WORK, MobBrainImpl.toServer(ActivityKey.WORK));
        assertSame(MobBrainImpl.toServer(ActivityKey.of("p", "a")), MobBrainImpl.toServer(ActivityKey.of("p", "a")), "플러그인 활동은 같은 객체");
        for (var f : ActivityKey.class.getFields()) {
            if (f.getType() != ActivityKey.class) continue;
            try {
                assertNotNull(Activity.byKey(f.get(null).toString()), "상수 " + f.getName() + " 는 이 판의 활동");
            } catch (IllegalAccessException ex) {
                throw new AssertionError(ex);
            }
        }
        assertEquals("minecraft:home", MemoryKey.HOME.key());
        assertThrows(IllegalArgumentException.class, () -> SensorKey.of("bad space", "x"));
    }
}
