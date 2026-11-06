// 주민 브레인: 시간표 활동 전환(20틱 간격), POI 티켓 점유·반환, 직업 획득·해제, 잠자기, 공황, 철 골렘 소환, 고정 시드 난수 순서, 기억 저장 왕복.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.behavior.AcquirePoi;
import org.domi.furnace.server.entity.ai.behavior.WorkAtPoi;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.poi.PoiTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerBrainTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 시각을 테스트가 정하는 레벨. */
    static final class Clocked extends BlockPlacementTest.TestLevel {
        long day, game;

        @Override
        protected org.domi.furnace.server.block.LevelServices services() {
            return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                @Override public long dayTime() { return day; }

                @Override public long gameTime() { return game; }
            };
        }

        /** 한 틱: 시각을 하나 올리고 주어진 몹들을 틱한다. */
        void step(Mob... mobs) {
            game++;
            day++;
            for (Mob m : mobs) if (!m.isRemoved()) m.tick();
        }
    }

    private static Villager villager(ChunkLevel level, double x, double z) {
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(x, -60, z, 0, 0);
        level.addFreshEntity(v);
        return v;
    }

    private static int bedHead(String color) { return B.prop("part").with(B.defaultState("minecraft:" + color + "_bed"), "head"); }

    /** 북쪽을 보는 침대: 발 (x, -60, z+1), 머리 (x, -60, z). 머리 칸을 돌려준다. */
    private static Pos placeBed(ChunkLevel level, int x, int z) {
        int foot = B.defaultState("minecraft:red_bed");
        level.setBlock(new Pos(x, -60, z + 1), foot, 3);
        level.setBlock(new Pos(x, -60, z), bedHead("red"), 3);
        return new Pos(x, -60, z);
    }

    @Test
    void scheduleSwitchesActivityOnlyEveryTwentyTicks() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        var brain = v.villagerBrain();
        brain.setMemory(MemoryModuleType.JOB_SITE, new GlobalPos(level.dimension(), new Pos(5, -60, 5)));
        level.game = 1000;
        level.day = 2000;
        brain.updateActivityFromSchedule(level.day, level.game);
        assertTrue(brain.isActive(Activity.WORK), "2000틱부터 일(직업 블록 기억이 있을 때)");
        level.day = 12000;
        brain.updateActivityFromSchedule(level.day, 1020);
        assertTrue(brain.isActive(Activity.WORK), "20틱이 안 지나면 시간표를 다시 보지 않는다");
        brain.updateActivityFromSchedule(level.day, 1021);
        assertTrue(brain.isActive(Activity.REST), "12000틱부터 휴식");
        level.day = 9000;
        brain.updateActivityFromSchedule(level.day, 1042);
        assertTrue(brain.isActive(Activity.IDLE), "모임 장소가 없으면 만남 대신 기본(한가)");
        brain.eraseMemory(MemoryModuleType.JOB_SITE);
        level.day = 2000;
        brain.updateActivityFromSchedule(level.day, 1063);
        assertTrue(brain.isActive(Activity.IDLE), "직업 블록이 없으면 일하지 않는다");
        assertTrue(brain.isActive(Activity.CORE));
    }

    @Test
    void babyUsesBabyScheduleAndPlays() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        v.setAge(-24000);
        assertTrue(v.isBaby());
        level.day = 3000;
        v.villagerBrain().updateActivityFromSchedule(level.day, 100);
        assertTrue(v.villagerBrain().isActive(Activity.PLAY), "아기 시간표: 3000틱부터 놀이");
    }

    @Test
    void acquiresHomeBedTicketAndReleasesItOnDeath() {
        var level = new Clocked();
        Pos bed = placeBed(level, 3, 0);
        assertEquals(PoiTypes.HOME, level.poiManager().getType(bed));
        var v = villager(level, 0.5, 0.5);
        for (int i = 0; i < 200 && v.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(); i++) level.step(v);
        assertEquals(Optional.of(new GlobalPos(level.dimension(), bed)), v.getBrain().getMemory(MemoryModuleType.HOME));
        assertEquals(0, level.poiManager().getFreeTickets(bed), "침대 티켓을 잡는다");
        v.hurt(new Entity.Damage("generic", false, false, false), 100f);
        assertTrue(v.isDeadOrDying());
        assertEquals(1, level.poiManager().getFreeTickets(bed), "죽으면 티켓을 돌려준다");
    }

    @Test
    void unemployedTakesJobFromComposterAndLosesItWhenRemoved() {
        var level = new Clocked();
        Pos comp = new Pos(3, -60, 0);
        level.setBlock(comp, B.defaultState("minecraft:composter"), 3);
        var v = villager(level, 0.5, 0.5);
        level.day = 1000;
        for (int i = 0; i < 1500 && !v.profession().equals("farmer"); i++) level.step(v);
        assertEquals("farmer", v.profession(), "퇴비통에 닿으면 농부가 된다");
        assertEquals(Optional.of(new GlobalPos(level.dimension(), comp)), v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
        assertTrue(v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty());
        assertEquals(0, level.poiManager().getFreeTickets(comp));
        // 경험치 0·레벨 1 이면 블록을 잃으면 무직으로 돌아간다
        level.setBlock(comp, B.defaultState("minecraft:air"), 3);
        for (int i = 0; i < 40 && !v.profession().equals("none"); i++) level.step(v);
        assertEquals("none", v.profession());
        assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty());
    }

    @Test
    void experiencedVillagerKeepsProfessionWithoutJobSite() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        v.setVillagerData("plains", "librarian", 2);
        v.overrideXp(20);
        for (int i = 0; i < 40; i++) level.step(v);
        assertEquals("librarian", v.profession(), "경험치가 있으면 직업을 유지한다");
        assertEquals(2, v.villagerLevel());
    }

    @Test
    void sleepingOccupiesBedAndWakingFreesIt() {
        var level = new Clocked();
        Pos bed = placeBed(level, 3, 0);
        var v = villager(level, 3.5, 2.5);
        v.startSleeping(bed);
        assertTrue(v.isSleeping());
        assertTrue(B.prop("occupied").is(level.getBlockState(bed)));
        assertEquals(Entity.POSE_SLEEPING, v.getPose());
        assertEquals(level.game, (long) v.getBrain().getMemory(MemoryModuleType.LAST_SLEPT).orElseThrow());
        v.stopSleeping();
        assertFalse(v.isSleeping());
        assertFalse(B.prop("occupied").is(level.getBlockState(bed)));
        assertNotEquals(bed, v.blockPosition(), "일어서면 침대 밖에 선다");
        assertTrue(v.getBrain().hasMemoryValue(MemoryModuleType.LAST_WOKEN));
    }

    @Test
    void restActivitySleepsInHomeBedAndWakesInTheMorning() {
        var level = new Clocked();
        Pos bed = placeBed(level, 3, 0);
        var v = villager(level, 3.5, 1.2);
        level.poiManager().take(t -> t == PoiTypes.HOME, (t, p) -> p.equals(bed), bed, 1);
        v.getBrain().setMemory(MemoryModuleType.HOME, new GlobalPos(level.dimension(), bed));
        level.day = 13000;
        for (int i = 0; i < 300 && !v.isSleeping(); i++) level.step(v);
        assertTrue(v.isSleeping(), "밤(휴식)에는 집 침대에서 잔다");
        level.day = 24000 + 100;
        for (int i = 0; i < 40 && v.isSleeping(); i++) level.step(v);
        assertFalse(v.isSleeping(), "아침(휴식이 끝나면) 일어난다");
    }

    @Test
    void hurtVillagerPanics() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        level.day = 1000;
        level.step(v);
        assertFalse(v.villagerBrain().isActive(Activity.PANIC));
        v.hurt(new Entity.Damage("generic", false, false, false), 1f);
        for (int i = 0; i < 25 && !v.villagerBrain().isActive(Activity.PANIC); i++) level.step(v);
        assertTrue(v.villagerBrain().isActive(Activity.PANIC), "맞으면 공황");
    }

    @Test
    void golemSpawnsWhenEnoughVillagersSleptAndNoGolemSeen() {
        var level = new Clocked();
        level.game = 5000;
        Villager[] vs = {villager(level, 0.5, 0.5), villager(level, 1.5, 0.5), villager(level, 0.5, 1.5)};
        assertFalse(vs[0].wantsToSpawnGolem(level.game), "자지 않았으면 원하지 않는다");
        for (Villager v : vs) v.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, 4000L);
        assertTrue(vs[0].wantsToSpawnGolem(level.game));
        vs[0].spawnGolemIfNeeded(level, level.game, 4);
        assertTrue(level.getEntitiesOfClass(IronGolem.class, vs[0].boundingBox().inflate(20, 20, 20), x -> true).isEmpty(), "주민이 모자라면 안 생긴다");
        vs[0].spawnGolemIfNeeded(level, level.game, 3);
        var golems = level.getEntitiesOfClass(IronGolem.class, vs[0].boundingBox().inflate(20, 20, 20), x -> true);
        assertEquals(1, golems.size(), "세 명이면 골렘이 생긴다");
        assertFalse(golems.get(0).isPlayerCreated());
        for (Villager v : vs) {
            assertTrue(v.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY));
            assertEquals(599L, v.getBrain().getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY));
            assertFalse(v.wantsToSpawnGolem(level.game));
        }
    }

    @Test
    void breedingNeedsFoodAndAdultAge() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        assertFalse(v.canBreed(), "음식이 없으면 번식 못 함");
        v.getInventory().addItem(B.stack("minecraft:bread", 3));
        assertTrue(v.canBreed(), "빵 3개 = 12점");
        v.eatAndDigestFood();
        assertEquals(0, v.foodLevel());
        assertEquals(0, v.getInventory().countItem("minecraft:bread"));
        assertFalse(v.canBreed());
        v.getInventory().addItem(B.stack("minecraft:carrot", 12));
        v.setAge(6000);
        assertFalse(v.canBreed(), "번식 뒤 대기 중이면 못 함");
        assertTrue(v.wantsToPickUp(B.stack("minecraft:potato", 1)));
        assertFalse(v.wantsToPickUp(B.stack("minecraft:stone", 1)));
    }

    @Test
    void acquirePoiFirstTriggerOnlyDrawsStartDelayFromLevelRandom() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        ((LegacyRandomSource) level.random()).setSeed(42L);
        var b = AcquirePoi.create(t -> t == PoiTypes.HOME, MemoryModuleType.HOME, false, Optional.empty());
        assertFalse(b.tryStart(level, v, level.game));
        RandomSource ref = RandomSource.create(42L);
        ref.nextInt(20);
        assertEquals(ref.nextInt(1000), level.random().nextInt(1000), "첫 호출은 시작 지연 nextInt(20) 하나만 쓴다");
    }

    @Test
    void workAtPoiDrawsCoinThenDuration() {
        var level = new Clocked();
        level.game = 1000;
        Pos site = new Pos(0, -60, 1);
        level.setBlock(site, B.defaultState("minecraft:lectern"), 3);
        var v = villager(level, 0.5, 0.5);
        v.setVillagerData("plains", "librarian", 1);
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE, new GlobalPos(level.dimension(), site));
        long seed = 0;
        while (RandomSource.create(seed).nextInt(2) != 0) seed++;
        ((LegacyRandomSource) level.random()).setSeed(seed);
        var w = new WorkAtPoi();
        assertTrue(w.tryStart(level, v, level.game), "300틱이 지났고 동전이 0 이면 일한다");
        RandomSource ref = RandomSource.create(seed);
        ref.nextInt(2);
        ref.nextInt(1);
        assertEquals(ref.nextInt(1000), level.random().nextInt(1000), "레벨 난수: nextInt(2) → 지속시간 nextInt(1)");
        assertEquals(level.game, (long) v.getBrain().getMemory(MemoryModuleType.LAST_WORKED_AT_POI).orElseThrow());
    }

    @Test
    void brainMemoriesAndVillagerStateSurviveSaveRoundTrip() {
        var level = new Clocked();
        var v = villager(level, 0.5, 0.5);
        v.setVillagerData("plains", "farmer", 1);
        var brain = v.getBrain();
        GlobalPos home = new GlobalPos(level.dimension(), new Pos(4, -60, 4)), job = new GlobalPos(level.dimension(), new Pos(6, -60, 2));
        brain.setMemory(MemoryModuleType.HOME, home);
        brain.setMemory(MemoryModuleType.JOB_SITE, job);
        brain.setMemory(MemoryModuleType.MEETING_POINT, new GlobalPos(level.dimension(), new Pos(9, -60, 9)));
        brain.setMemory(MemoryModuleType.LAST_SLEPT, 1234L);
        brain.setMemory(MemoryModuleType.LAST_WORKED_AT_POI, 777L);
        brain.setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, 300L);
        v.getInventory().addItem(B.stack("minecraft:wheat", 5));
        v.setAge(-100);
        NbtCompound tag = v.saveWithoutId(new NbtCompound());
        var back = new Villager(level, level.nextEntityId());
        back.load(tag);
        var b2 = back.getBrain();
        assertEquals(Optional.of(home), b2.getMemory(MemoryModuleType.HOME));
        assertEquals(Optional.of(job), b2.getMemory(MemoryModuleType.JOB_SITE));
        assertTrue(b2.hasMemoryValue(MemoryModuleType.MEETING_POINT));
        assertEquals(Optional.of(1234L), b2.getMemory(MemoryModuleType.LAST_SLEPT));
        assertEquals(Optional.of(777L), b2.getMemory(MemoryModuleType.LAST_WORKED_AT_POI));
        assertEquals(Optional.of(true), b2.getMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY));
        assertEquals(300L, b2.getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY));
        assertEquals(5, back.getInventory().countItem("minecraft:wheat"));
        assertTrue(back.isBaby());
        assertEquals(-100, back.getAge());
        assertEquals("farmer", back.profession());
        assertTrue(back.villagerBrain().isActive(Activity.CORE), "불러온 뒤 브레인 활동이 다시 걸린다");
    }
}
