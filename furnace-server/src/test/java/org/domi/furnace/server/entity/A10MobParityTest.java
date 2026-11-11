// A10 회귀(재현 시험 docs/audit/findings/A10-repro/A10ReproTest·A10RegionSpawnerTest 에서). 바닐라 1.21.1 바이트코드에서 읽은 기대값과 비교한다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.spawn.CatSpawner;
import org.domi.furnace.server.entity.spawn.CustomSpawnerHost;
import org.domi.furnace.server.entity.spawn.PatrolSpawner;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A10MobParityTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final BlockPlacementTest.TestLevel LEVEL = new BlockPlacementTest.TestLevel();

    private static boolean validSpawnOn(String block, String type) {
        var level = new BlockPlacementTest.TestLevel();
        Pos below = new Pos(3, -61, 3);
        level.setBlock(below, B.defaultState(B.id(block)), 3);
        return SpawnPlacements.isValidSpawn(level.getBlockState(below), level, below, type);
    }

    /**
     * A10-03: BlockState.isValidSpawn. 바닐라 Blocks(dga) 는 jack_o_lantern·redstone_lamp·carved_pumpkin·soul_sand·mud 만 always(dga.b),
     * glowstone·sea_lantern·beacon·shroomlight·froglight 는 기본 술어(dtb$d.a: 윗면 단단 && 빛 < 14)라 빛 15 로 불가.
     * 네더(블록 빛 한도 15)에서 이 바닥 위 가스트·피글린·마그마 큐브가 바닐라와 같게 막힌다.
     */
    @Test
    void lightBlocksAreNotValidSpawnInVanilla() {
        for (String b : List.of("minecraft:glowstone", "minecraft:sea_lantern", "minecraft:beacon", "minecraft:shroomlight",
                "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight")) {
            assertFalse(validSpawnOn(b, "minecraft:zombie"), b + ": 바닐라는 빛 15 라 스폰 불가");
            assertFalse(validSpawnOn(b, "minecraft:zombified_piglin"), b + ": 네더 몹도 불가");
        }
    }

    /** copper_grate 는 never(dga.a), 나머지 구리 격자 7종은 ofFullCopy(copper_grate)(dtb$d.a(dtb)) 로 never 를 물려받는다. */
    @Test
    void copperGratesAreNeverValidSpawn() {
        for (String ox : List.of("", "exposed_", "weathered_", "oxidized_")) {
            for (String wax : List.of("", "waxed_")) {
                String b = "minecraft:" + wax + ox + "copper_grate";
                assertFalse(validSpawnOn(b, "minecraft:zombie"), b + ": 바닐라 never");
            }
        }
    }

    @Test
    void alwaysBlocksStayValid() {
        for (String b : List.of("minecraft:jack_o_lantern", "minecraft:redstone_lamp", "minecraft:carved_pumpkin", "minecraft:soul_sand", "minecraft:mud")) {
            assertTrue(validSpawnOn(b, "minecraft:zombie"), b + ": 바닐라 always");
        }
        assertTrue(validSpawnOn("minecraft:stone", "minecraft:zombie"), "보통 블록은 기본 술어");
    }

    /** A10-02: Cat.reassessTameGoals(cfh.gA) 는 avoidPlayersGoal 을 우선순위 4(iconst_4)로 넣는다 — 유혹(4)을 끊지 못한다. */
    @Test
    void catAvoidPlayersGoalPriorityIs4() {
        Cat cat = new Cat(LEVEL, LEVEL.nextEntityId());
        cat.setTame(false, false);
        var t = MobGoalTableTest.table(cat.goalSelector);
        assertTrue(t.contains("4 CatAvoidEntityGoal"), "바닐라 4 CatAvoidEntityGoal: " + t);
        assertFalse(t.contains("3 CatAvoidEntityGoal"), t.toString());
        cat.setTame(true, false);
        assertFalse(MobGoalTableTest.table(cat.goalSelector).stream().anyMatch(s -> s.endsWith("CatAvoidEntityGoal")), "길들면 뺀다");
    }

    /** A10-04: WitherSkeleton.registerGoals(cku.B) 는 대상 3 NearestAttackableTargetGoal<AbstractPiglin(clm)> 를 super 앞에 넣는다. */
    @Test
    void witherSkeletonTargetsPiglins() {
        var t = MobGoalTableTest.table(new WitherSkeleton(LEVEL, LEVEL.nextEntityId()).targetSelector);
        assertEquals(List.of("3 NearestAttackableTargetGoal<AbstractPiglin>", "1 HurtByTargetGoal", "2 NearestAttackableTargetGoal<Player>",
                "3 NearestAttackableTargetGoal<IronGolem>", "3 NearestAttackableTargetGoal<Turtle>"), t);
        // 보통 해골은 그대로
        assertFalse(MobGoalTableTest.table(new Skeleton(LEVEL, LEVEL.nextEntityId()).targetSelector).contains("3 NearestAttackableTargetGoal<AbstractPiglin>"));
    }

    /** A10-09: Silverfish(ckk)·Endermite(cju).registerGoals 는 FloatGoal 다음에 1 ClimbOnTopOfPowderSnowGoal(cab) 을 넣는다. */
    @Test
    void silverfishAndEndermiteClimbPowderSnow() {
        for (Mob m : List.of(new Silverfish(LEVEL, LEVEL.nextEntityId()), new Endermite(LEVEL, LEVEL.nextEntityId()))) {
            var t = MobGoalTableTest.table(m.goalSelector);
            assertEquals(List.of("1 FloatGoal", "1 ClimbOnTopOfPowderSnowGoal"), t.subList(0, 2), m.getClass().getSimpleName() + ": " + t);
        }
    }

    /** A10-11: 바닐라 Axolotl(cgq) 기억 목록에 nearest_visible_player·nearest_visible_targetable_player 가 있다. */
    @Test
    void axolotlRegistersVisiblePlayerMemories() {
        var a = new Axolotl(LEVEL, LEVEL.nextEntityId());
        var mem = a.axolotlBrain().memoryEntries().keySet();
        assertTrue(mem.contains(MemoryModuleType.NEAREST_VISIBLE_PLAYER), mem.toString());
        assertTrue(mem.contains(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER), mem.toString());
    }

    // ------------------------------------------------------------------ A10-05: 순찰·고양이 스포너는 차원에 하나

    private static final class Day extends BlockPlacementTest.TestLevel {
        @Override
        protected org.domi.furnace.server.block.LevelServices services() {
            return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                @Override public long dayTime() { return 6 * 24000L + 1000L; }
                @Override public int difficulty() { return 2; }
            };
        }
    }

    private static Player player(double x) {
        var p = new Player(1, UUID.randomUUID(), "P" + x, new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, 0, 0, true);
        return p;
    }

    /** 차원 쪽 host: 차원 전체 플레이어로 고르고, 넘긴 일은 기록만 한다(그 플레이어 region 으로 보내는 대신). */
    private static final class DimensionHost implements CustomSpawnerHost {
        final ChunkLevel level;
        final List<Player> players;
        final List<Player> picked = new ArrayList<>();

        DimensionHost(ChunkLevel level, List<Player> players) {
            this.level = level;
            this.players = players;
        }

        @Override public RandomSource random() { return level.random(); }
        @Override public boolean gameRule(String name) { return level.gameRule(name); }
        @Override public long dayTime() { return level.dayTime(); }
        @Override public boolean isDay() { return level.isDay(); }
        @Override public int skyDarken() { return level.skyDarken(); }
        @Override public boolean hasSkyLight() { return level.hasSkyLight(); }
        @Override public List<Player> players() { return players; }
        @Override public void atPlayer(Player p, BiConsumer<ChunkLevel, RandomSource> work) { picked.add(p); }
    }

    /**
     * 바닐라 ServerLevel 에는 PatrolSpawner(dzj) 가 하나이고 10~11분마다 level.players() 전체에서 한 명을 뽑는다. 예전 Furnace 는 region 마다
     * 스포너가 있어 플레이어마다 region 이 따로면 플레이어 A 가 약 2배(62 → 119) 뽑혔다. 이제 DimensionSpawners 의 스포너 하나가 차원 전체에서 뽑는다.
     */
    @Test
    void patrolPicksFromWholeDimensionOncePerFire() {
        var level = new Day();
        ((LegacyRandomSource) level.random()).setSeed(20261108L);
        Player a = player(0.5), b = player(5000.5);
        var host = new DimensionHost(level, List.of(a, b));
        var spawner = new PatrolSpawner(pos -> true);
        int fires = 0;
        for (int t = 0; t < 13_200 * 600; t++) {
            int before = spawner.nextTick();
            spawner.tick(host, true, null);
            if (spawner.nextTick() > before) fires++;
        }
        long picksA = host.picked.stream().filter(p -> p == a).count();
        long picksB = host.picked.size() - picksA;
        // 굴림마다 1/5 로 한 명 — 둘이 합쳐 굴림 수의 약 1/5, 각자 약 1/10
        assertTrue(host.picked.size() <= fires / 5.0 * 1.3 && host.picked.size() >= fires / 5.0 * 0.7, host.picked.size() + " / " + fires);
        assertTrue(Math.abs(picksA - picksB) < host.picked.size() * 0.35, "A " + picksA + " B " + picksB);
    }

    @Test
    void catSpawnerPicksOneAlivePlayerPerFire() {
        var level = new Day();
        Player a = player(0.5), b = player(5000.5);
        b.vitals().setHealth(0.0F);
        var host = new DimensionHost(level, List.of(a, b));
        var spawner = new CatSpawner();
        for (int t = 0; t < 1200 * 10; t++) spawner.tick(host, true, null);
        assertEquals(10, host.picked.size(), "1200 틱마다 한 명");
        assertTrue(host.picked.stream().allMatch(p -> p == a), "죽은 플레이어는 고르지 않는다(getPlayers(isAlive))");
    }

    /** region 레벨에는 이제 스포너 필드가 없다 — 새 region 이 타이머 0 으로 곧바로 굴리지 않는다(A10-12). */
    @Test
    void regionLevelNoLongerOwnsCustomSpawners() {
        for (var f : org.domi.furnace.server.world.RegionLevel.class.getDeclaredFields()) {
            String t = f.getType().getSimpleName();
            assertFalse(t.equals("PhantomSpawner") || t.equals("PatrolSpawner") || t.equals("CatSpawner"), f.getName() + ": " + t);
        }
    }
}
