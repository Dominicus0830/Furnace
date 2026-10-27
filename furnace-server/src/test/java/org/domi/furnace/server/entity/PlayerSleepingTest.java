// 플레이어 잠 시험: 잘 수 있는 시각, 침대 쓰기(거리·막힘·괴물·낮), 잠 카운터와 깨기, 침대가 부서지면 깨기, 침대 폭발.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerSleepingTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    static final class Lvl extends BlockPlacementTest.TestLevel {
        long dayTime;
        DimensionType type = DimensionType.OVERWORLD;
        String dim;
        Player player;
        int sleepListUpdates;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public long dayTime() { return dayTime; }
                @Override public String dimension() { return dim != null ? dim : type.levelKey(); }
                @Override public DimensionType dimensionType() { return type; }
                @Override public void forEachPlayer(Consumer<Player> c) { if (player != null) c.accept(player); }
            };
        }

        @Override public void updateSleepingPlayerList() { sleepListUpdates++; }
    }

    static final Pos FOOT = new Pos(0, -60, 0), HEAD = new Pos(0, -60, 1);

    static void placeBed(Lvl level) {
        level.setBlock(FOOT, REG.stateIdOrAir("minecraft:red_bed", Map.of("facing", "south", "part", "foot", "occupied", "false")), 3);
        level.setBlock(HEAD, REG.stateIdOrAir("minecraft:red_bed", Map.of("facing", "south", "part", "head", "occupied", "false")), 3);
    }

    static Player player(Lvl level, double x, double y, double z) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 0, 0, true);
        p.currentLevel = level;
        level.player = p;
        return p;
    }

    @Test
    void sleepWindowMatchesVanillaClearWeather() {
        var level = new Lvl();
        // 맑은 날: 12542 부터 23459 까지 밤(skyDarken ≥ 4)
        level.dayTime = 12541;
        assertTrue(PlayerSleeping.isDay(level));
        level.dayTime = 12542;
        assertFalse(PlayerSleeping.isDay(level));
        level.dayTime = 23459;
        assertFalse(PlayerSleeping.isDay(level));
        level.dayTime = 23460;
        assertTrue(PlayerSleeping.isDay(level));
        level.dayTime = 24000 + 13000;   // 다음 날도 같다
        assertFalse(PlayerSleeping.isDay(level));
        level.type = DimensionType.NETHER;   // 고정 시각 차원은 늘 낮이 아니다
        assertFalse(PlayerSleeping.isDay(level));
    }

    @Test
    void sleepsAtNightSetsSpawnAndOccupiesBed() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 13000;
        var p = player(level, 1.5, -60, 0.5);
        assertNull(PlayerSleeping.startSleepInBed(p, level, HEAD));
        assertTrue(p.isSleeping());
        assertEquals(HEAD, p.sleepingPos());
        assertEquals(2, p.pose());
        assertEquals(0.5, p.x(), 1e-9);
        assertEquals(-60 + 0.6875, p.y(), 1e-9);
        assertEquals(1.5, p.z(), 1e-9);
        assertTrue(B.prop("occupied").is(level.getBlockState(HEAD)));
        assertArrayEquals(new int[] {0, -60, 1}, p.vitals().respawnPos());
        assertFalse(p.vitals().respawnForced());
        assertEquals("minecraft:overworld", p.vitals().respawnDimension());
        assertEquals(1, level.sleepListUpdates);
        // 이미 자는 중이면 OTHER_PROBLEM(메시지 없음)
        assertEquals(PlayerSleeping.Problem.OTHER_PROBLEM, PlayerSleeping.startSleepInBed(p, level, HEAD));
        assertNull(PlayerSleeping.Problem.OTHER_PROBLEM.message());
    }

    @Test
    void problemsInVanillaOrder() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 1000;
        var far = player(level, 5.0, -60, 0.5);
        assertEquals(PlayerSleeping.Problem.TOO_FAR_AWAY, PlayerSleeping.startSleepInBed(far, level, HEAD));
        assertNull(far.vitals().respawnPos(), "멀면 스폰 지점을 정하지 않는다");
        // 낮: 스폰 지점은 정하고 no_sleep
        var p = player(level, 1.5, -60, 0.5);
        assertEquals(PlayerSleeping.Problem.NOT_POSSIBLE_NOW, PlayerSleeping.startSleepInBed(p, level, HEAD));
        assertEquals("block.minecraft.bed.no_sleep", PlayerSleeping.Problem.NOT_POSSIBLE_NOW.messageKey());
        assertArrayEquals(new int[] {0, -60, 1}, p.vitals().respawnPos());
        assertFalse(p.isSleeping());
        // 발 칸 위가 막히면 obstructed(범위 검사 다음)
        level.setBlock(FOOT.above(), B.defaultState("minecraft:stone"), 3);
        assertEquals(PlayerSleeping.Problem.OBSTRUCTED, PlayerSleeping.startSleepInBed(p, level, HEAD));
        level.setBlock(FOOT.above(), B.defaultState("minecraft:air"), 3);
        // natural 이 아닌 차원(네더·엔드)은 잘 수 없다 — 칸 높이는 그대로 두고 natural 만 끈 차원 종류
        var o = DimensionType.OVERWORLD;
        level.type = new DimensionType(o.levelKey(), o.typeKey(), o.minY(), o.height(), o.logicalHeight(), o.hasSkyLight(), o.hasCeiling(),
                o.ultraWarm(), false, o.coordinateScale(), o.fixedTime(), o.bedWorks(), o.respawnAnchorWorks(), o.piglinSafe(), o.gen());
        assertEquals(PlayerSleeping.Problem.NOT_POSSIBLE_HERE, PlayerSleeping.startSleepInBed(p, level, HEAD));
    }

    @Test
    void nearbyMonsterPreventsSleepUnlessCreative() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 18000;
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(6.5, -60, 1.5, 0, 0);
        level.addFreshEntity(z);
        var p = player(level, 1.5, -60, 0.5);
        assertEquals(PlayerSleeping.Problem.NOT_SAFE, PlayerSleeping.startSleepInBed(p, level, HEAD));
        assertEquals("block.minecraft.bed.not_safe", PlayerSleeping.Problem.NOT_SAFE.messageKey());
        p.vitals().loadGameTypes(null, GameMode.CREATIVE, false);
        assertNull(PlayerSleeping.startSleepInBed(p, level, HEAD));
    }

    @Test
    void counterReachesHundredAndMorningWakes() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 13000;
        var p = player(level, 1.5, -60, 0.5);
        assertNull(PlayerSleeping.startSleepInBed(p, level, HEAD));
        for (int i = 0; i < 99; i++) PlayerSleeping.tick(p, level);
        assertFalse(p.isSleepingLongEnough());
        PlayerSleeping.tick(p, level);
        assertTrue(p.isSleepingLongEnough());
        for (int i = 0; i < 20; i++) PlayerSleeping.tick(p, level);
        assertEquals(100, p.sleepCounter(), "100 에서 멈춘다");
        level.dayTime = 24000;
        PlayerSleeping.tick(p, level);
        assertFalse(p.isSleeping());
        assertEquals(100, p.sleepCounter(), "자연스럽게 깨면 100");
        assertFalse(B.prop("occupied").is(level.getBlockState(HEAD)));
        assertEquals(0, p.pose());
        // 일어선 자리: 침대 옆 칸 위(바닥 높이 0) — 침대 쪽을 본다
        assertNotEquals(-60 + 0.6875, p.y());
        for (int i = 0; i < 9; i++) PlayerSleeping.tick(p, level);
        assertEquals(109, p.sleepCounter());
        PlayerSleeping.tick(p, level);
        assertEquals(0, p.sleepCounter(), "110 이 되면 0");
    }

    @Test
    void brokenBedWakesImmediately() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 13000;
        var p = player(level, 1.5, -60, 0.5);
        assertNull(PlayerSleeping.startSleepInBed(p, level, HEAD));
        for (int i = 0; i < 50; i++) PlayerSleeping.tick(p, level);
        level.setBlock(HEAD, B.defaultState("minecraft:air"), 3);
        PlayerSleeping.tick(p, level);
        assertFalse(p.isSleeping());
        assertEquals(0, p.sleepCounter(), "침대가 없어지면 바로 깨서 0");
        // 침대가 없으면 자리를 옮기지 않는다(LivingEntity.stopSleeping 은 침대가 있을 때만 일어설 자리를 찾는다)
        assertEquals(0.5, p.x(), 1e-9);
        assertEquals(-60 + 0.6875, p.y(), 1e-9);
        assertEquals(1.5, p.z(), 1e-9);
    }

    @Test
    void stopSleepFacesTheBed() {
        assertEquals(0.0F, PlayerSleeping.lookAtYaw(0.5, 0, -0.5, new Pos(0, 0, 0)), 1e-4, "남쪽(+z)을 보면 0");
        assertEquals(90.0F, PlayerSleeping.lookAtYaw(1.5, 0, 0.5, new Pos(0, 0, 0)), 1e-4, "서쪽(-x)을 보면 90");
        assertEquals(-180.0F, PlayerSleeping.lookAtYaw(0.5, 0, 1.5, new Pos(0, 0, 0)), 1e-4);
    }

    @Test
    void bedUseThroughBlockActorSleepsOrExplodesInNether() {
        var level = new Lvl();
        placeBed(level);
        level.dayTime = 13000;
        var p = player(level, 1.5, -60, 0.5);
        var actor = new PlayerActor(p);
        int foot = level.getBlockState(FOOT);
        // 발 칸을 눌러도 머리 칸으로 잔다
        assertEquals(InteractionResult.SUCCESS, B.behavior(foot).useWithoutItem(foot, level, FOOT, actor, new Hit(FOOT, Dir.UP, 0.5, -59.5, 0.5, false)));
        assertTrue(p.isSleeping());
        assertEquals(HEAD, p.sleepingPos());

        var nether = new Lvl();
        nether.dim = "minecraft:the_nether";   // BedBlock.canSetSpawn 은 차원 키로 본다(칸 높이는 오버월드 그대로)
        placeBed(nether);
        var q = player(nether, 1.5, -60, 0.5);
        int head = nether.getBlockState(HEAD);
        assertEquals(InteractionResult.SUCCESS, B.behavior(head).useWithoutItem(head, nether, HEAD, new PlayerActor(q),
                new Hit(HEAD, Dir.UP, 0.5, -59.5, 1.5, false)));
        assertFalse(B.isTag(nether.getBlockState(HEAD), "minecraft:beds"), "두 칸 다 지운다");
        assertFalse(B.isTag(nether.getBlockState(FOOT), "minecraft:beds"));
        assertFalse(q.isSleeping());
    }
}
