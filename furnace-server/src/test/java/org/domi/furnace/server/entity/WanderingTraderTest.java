// 방랑 상인과 스포너: 거래 구성(5+1), 저장/불러오기, 사라짐, 스포너의 지연·확률 갱신(난수 고정)과 스폰 위치.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.spawn.SpawnPlacements;
import org.domi.furnace.server.entity.spawn.WanderingTraderSpawner;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WanderingTraderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static WanderingTrader trader(BlockPlacementTest.TestLevel level) {
        var t = new WanderingTrader(level, level.nextEntityId());
        t.setPos(0.5, -60, 0.5);
        return t;
    }

    private static Player player(double x, double z) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, z, 0, 0, true);
        return p;
    }

    @Test
    void offersAreFiveFromFirstGroupPlusOneFromSecond() {
        var level = new BlockPlacementTest.TestLevel();
        var a = trader(level);
        a.updateTrades(RandomSource.create(42));
        // 지도 거래가 null 이면 둘째 묶음에서 못 뽑을 수 있다 — 최소 5, 최대 6
        assertTrue(a.offers().size() == 5 || a.offers().size() == 6, "거래 수: " + a.offers().size());
        // 같은 난수면 같은 거래
        var b = trader(level);
        b.updateTrades(RandomSource.create(42));
        assertEquals(a.offers().size(), b.offers().size());
        for (int i = 0; i < a.offers().size(); i++) {
            assertEquals(a.offers().get(i).baseCostA(), b.offers().get(i).baseCostA());
            assertEquals(a.offers().get(i).result().count(), b.offers().get(i).result().count());
        }
        // 첫 묶음 5개는 서로 다른 후보라 최대 사용 횟수 등이 표와 맞다
        assertTrue(a.canTrade());
        assertFalse(a.showProgressBar());
        assertEquals(0, a.villagerXp());
    }

    @Test
    void savesAndLoadsOffersDespawnDelayAndWanderTarget() {
        var level = new BlockPlacementTest.TestLevel();
        var t = trader(level);
        t.updateTrades(RandomSource.create(7));
        t.setDespawnDelay(12345);
        t.setWanderTarget(new Pos(10, -60, -20));
        NbtCompound tag = t.saveWithoutId(new NbtCompound());
        assertEquals(12345, tag.getIntOr0("DespawnDelay"));
        assertArrayEquals(new int[] {10, -60, -20}, (int[]) tag.tags().get("wander_target"));
        var back = new WanderingTrader(level, level.nextEntityId());
        back.load(tag);
        assertEquals(12345, back.despawnDelay());
        assertEquals(new Pos(10, -60, -20), back.wanderTarget());
        assertEquals(t.offers().size(), back.offers().size());
        for (int i = 0; i < t.offers().size(); i++) {
            assertEquals(t.offers().get(i).baseCostA(), back.offers().get(i).baseCostA());
            assertEquals(t.offers().get(i).maxUses(), back.offers().get(i).maxUses());
        }
    }

    @Test
    void despawnsAfterDelayButNotWhileTrading() {
        var level = new BlockPlacementTest.TestLevel();
        var t = trader(level);
        level.addFreshEntity(t);
        t.setDespawnDelay(3);
        t.tick();
        t.tick();
        assertEquals(1, t.despawnDelay());
        assertFalse(t.isRemoved());
        t.tick();
        assertTrue(t.isRemoved(), "0 이 되면 사라진다");

        var busy = trader(level);
        level.addFreshEntity(busy);
        busy.setDespawnDelay(2);
        busy.setTradingPlayer(new org.domi.furnace.server.menu.MenuPlayer() {
            public boolean isCreative() { return false; }
            public boolean isSpectator() { return false; }
            public boolean isDeadOrDying() { return false; }
            public boolean hasDisconnected() { return false; }
            public void drop(ItemStack s, boolean b) { }
            public void placeItemBackInInventory(ItemStack s) { }
            public ItemStack addToInventory(ItemStack s) { return s; }
            public ItemStack inventoryItem(int i) { return ItemStack.EMPTY; }
            public void setInventoryItem(int i, ItemStack s) { }
            public org.domi.furnace.server.menu.EquipSlot equipmentSlotFor(ItemStack s) { return null; }
            public boolean hasBindingCurse(ItemStack s) { return false; }
            public double distanceToSqr(double x, double y, double z) { return 0; }
        });
        for (int i = 0; i < 10; i++) busy.tick();
        assertEquals(2, busy.despawnDelay(), "거래 중에는 줄지 않는다");
        assertFalse(busy.isRemoved());
    }

    @Test
    void spawnerDelayAndChanceFollowSeededRandom() {
        var level = new BlockPlacementTest.TestLevel();
        var spawner = new WanderingTraderSpawner(RandomSource.create(7));
        var mirror = RandomSource.create(7);
        int chance = 25;
        for (int day = 0; day < 6; day++) {
            int spawned = 0;
            for (int i = 0; i < 24000; i++) {
                spawned += spawner.tick(level, List.of());
                if (i < 23999) assertTrue(spawner.spawnDelay() > 0);
            }
            // 플레이어가 없으면 spawn 은 true 라 바로 확률이 25 로 돌아간다. 확률 굴림은 nextInt(100) > chance 면 실패.
            boolean fail = mirror.nextInt(100) > chance;
            if (fail) {
                chance = Math.min(75, chance + 25);
                assertEquals(0, spawned);
            } else {
                chance = 25;
                assertEquals(1, spawned);
            }
            assertEquals(chance, spawner.spawnChance(), "day " + day);
            assertEquals(24000, spawner.spawnDelay());
        }
    }

    @Test
    void spawnerRespectsDoTraderSpawningRule() {
        var level = new BlockPlacementTest.TestLevel() {
            @Override
            protected org.domi.furnace.server.block.LevelServices services() {
                return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                    @Override public boolean gameRule(String name) { return !name.equals("doTraderSpawning"); }
                };
            }
        };
        var spawner = new WanderingTraderSpawner(RandomSource.create(1));
        for (int i = 0; i < 30000; i++) assertEquals(0, spawner.tick(level, List.of()));
        assertEquals(24000, spawner.spawnDelay(), "규칙이 꺼지면 지연도 줄지 않는다");
    }

    @Test
    void spawnPositionIsValidSurfaceNearAnchor() {
        var level = new BlockPlacementTest.TestLevel();
        var spawner = new WanderingTraderSpawner(RandomSource.create(3));
        for (int i = 0; i < 20; i++) {
            Pos anchor = new Pos(8, -60, 8);
            Pos p = spawner.findSpawnPositionNear(level, anchor, 4);
            assertNotNull(p);
            assertTrue(Math.abs(p.x() - 8) <= 4 && Math.abs(p.z() - 8) <= 4);
            assertEquals(level.getWorldSurfaceHeight(p.x(), p.z()), p.y());
            assertTrue(SpawnPlacements.isSpawnPositionOk("minecraft:wandering_trader", level, p));
        }
        // 청크가 없는 먼 곳은 칸을 못 찾는다
        assertNull(spawner.findSpawnPositionNear(level, new Pos(10000, -60, 10000), 4));
    }

    @Test
    void spawnerPlacesTraderWithLlamasNearPlayer() {
        for (long seed = 0; seed < 400; seed++) {
            var level = new BlockPlacementTest.TestLevel();
            var spawner = new WanderingTraderSpawner(RandomSource.create(seed));
            spawner.setState(1, 75);   // 첫 1200틱 뒤 바로 굴린다(확률 75)
            var p = player(8.5, 8.5);
            int n = 0;
            for (int i = 0; i < 1200; i++) n += spawner.tick(level, List.of(p));
            if (n == 0) continue;
            WanderingTrader trader = null;
            int llamas = 0;
            for (Entity e : level.entities().tickSnapshot()) {
                if (e instanceof WanderingTrader w) trader = w;
                if (e instanceof TraderLlama) llamas++;
            }
            assertNotNull(trader);
            assertEquals(2, llamas, "짐 라마 2마리(자리가 없으면 줄 수 있어 시험 지형에선 2)");
            assertEquals(48000, trader.despawnDelay());
            assertEquals(p.blockPosition(), trader.wanderTarget());
            assertTrue(trader.isWithinRestriction(p.blockPosition()));
            assertEquals(25, spawner.spawnChance(), "성공하면 확률이 25 로 돌아간다");
            return;
        }
        fail("400개 시드 안에 스폰이 한 번도 없다");
    }

    @Test
    void drinkingInvisibilityPotionTakes32TicksThenMilkClears() {
        var level = new BlockPlacementTest.TestLevel();
        var t = trader(level);
        t.setNoAi(true);   // 목표(UseItemGoal)가 끼어들지 않게 — 여기선 마시기 자체만 본다
        level.addFreshEntity(t);
        var potion = org.domi.furnace.server.item.ItemTools.with(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "potion"), 1),
                "minecraft:potion_contents", java.util.Map.of("potion", "minecraft:invisibility"));
        t.setItemSlot(EquipmentSlot.MAINHAND, potion);
        t.startUsingItem(0);
        for (int i = 0; i < 31; i++) t.tick();
        assertTrue(t.isUsingItem());
        assertFalse(t.isInvisible());
        t.tick();
        assertFalse(t.isUsingItem(), "32틱째에 다 마신다");
        t.tick();   // 투명 플래그는 효과 틱에서 갱신된다
        assertTrue(t.isInvisible());
        t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "milk_bucket"), 1));
        t.startUsingItem(0);
        for (int i = 0; i < 33; i++) t.tick();
        assertFalse(t.isInvisible(), "우유는 효과를 지운다");
    }

    private static final class Clocked extends BlockPlacementTest.TestLevel {
        long day;

        @Override
        protected org.domi.furnace.server.block.LevelServices services() {
            return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                @Override public long dayTime() { return day; }
            };
        }
    }

    @Test
    void goalsDrinkInvisibilityAtNightAndMilkByDay() {
        var level = new Clocked();
        level.day = 18000;
        var t = trader(level);
        level.addFreshEntity(t);
        for (int i = 0; i < 100; i++) t.tick();
        assertTrue(t.isInvisible(), "밤에는 투명 물약을 마신다");
        assertFalse(t.isUsingItem());
        assertTrue(t.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "다 마시면 손이 빈다");
        level.day = 1000;
        for (int i = 0; i < 100; i++) t.tick();
        assertFalse(t.isInvisible(), "낮에는 우유로 푼다");
        assertFalse(t.isUsingItem());
    }

    /** 게임 시간이 한 틱씩 가는 레벨(공유 타이머의 틱당 한 번 제한을 보려고). */
    private static final class Ticking extends BlockPlacementTest.TestLevel {
        long time;
        @Override public long gameTime() { return time; }
    }

    @Test
    void levelDataPersistsTraderTimerAndId() {
        var d = org.domi.furnace.server.world.storage.LevelData.create("w", 1L, true, false, GameMode.SURVIVAL, false,
                org.domi.furnace.server.world.Difficulty.NORMAL, List.of("vanilla"), List.of());
        assertEquals(24000, d.wanderingTrader.spawnDelay(), "새 월드는 24000 / 25 로 시작(바닐라 생성자 0/0 규칙)");
        assertEquals(25, d.wanderingTrader.spawnChance());
        assertNull(d.wanderingTrader.traderId());
        var id = java.util.UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        d.wanderingTrader.setSpawnDelay(-1200);
        d.wanderingTrader.setSpawnChance(75);
        d.wanderingTrader.setTraderId(id);
        NbtCompound nbt = d.toNbt(0L);
        assertEquals(-1200, nbt.getIntOr0("WanderingTraderSpawnDelay"));
        assertEquals(75, nbt.getIntOr0("WanderingTraderSpawnChance"));
        assertArrayEquals(new int[] {0x12345678, 0x9abcdef0, 0x12345678, 0x9abcdef0}, nbt.getIntArray("WanderingTraderId"));
        var back = org.domi.furnace.server.world.storage.LevelData.fromNbt(nbt);
        assertEquals(-1200, back.wanderingTrader.spawnDelay());
        assertEquals(75, back.wanderingTrader.spawnChance());
        assertEquals(id, back.wanderingTrader.traderId());
        // 0 / 0 이면 기본값으로
        nbt.putInt("WanderingTraderSpawnDelay", 0);
        nbt.putInt("WanderingTraderSpawnChance", 0);
        var zero = org.domi.furnace.server.world.storage.LevelData.fromNbt(nbt);
        assertEquals(24000, zero.wanderingTrader.spawnDelay());
        assertEquals(25, zero.wanderingTrader.spawnChance());
    }

    @Test
    void sharedTimerRunsOncePerGameTickAcrossSpawners() {
        var level = new Ticking();
        var state = new org.domi.furnace.server.world.WanderingTraderState();
        state.setSpawnDelay(24000);
        state.setSpawnChance(25);
        var a = new WanderingTraderSpawner(RandomSource.create(1));
        var b = new WanderingTraderSpawner(RandomSource.create(2));
        a.attach(state);
        b.attach(state);
        var p = player(8.5, 8.5);
        for (int i = 0; i < 1200; i++) {
            level.time = i + 1;
            a.tick(level, List.of());   // 플레이어 없는 region 은 타이머를 소비하지 않는다
            a.tick(level, List.of(p));
            b.tick(level, List.of(p));   // 같은 틱의 두 번째 region 은 타이머를 돌리지 못한다
        }
        assertEquals(22800, state.spawnDelay(), "1200틱에 1200 만 줄어든다(스포너 둘이어도)");
        assertEquals(1200, state.tickDelay());
    }

    @Test
    void sharedStateRecordsSpawnedTraderId() {
        for (long seed = 0; seed < 400; seed++) {
            var level = new Ticking();
            var state = new org.domi.furnace.server.world.WanderingTraderState();
            state.setSpawnDelay(1);
            state.setSpawnChance(75);
            var spawner = new WanderingTraderSpawner(RandomSource.create(seed));
            spawner.attach(state);
            var p = player(8.5, 8.5);
            int n = 0;
            for (int i = 0; i < 1200; i++) {
                level.time = i + 1;
                n += spawner.tick(level, List.of(p));
            }
            if (n == 0) {
                assertNull(state.traderId());
                continue;
            }
            WanderingTrader trader = level.entities().tickSnapshot().stream().filter(e -> e instanceof WanderingTrader)
                    .map(e -> (WanderingTrader) e).findFirst().orElseThrow();
            assertEquals(trader.uuid(), state.traderId());
            assertEquals(25, state.spawnChance());
            assertEquals(24000, state.spawnDelay());
            return;
        }
        fail("400개 시드 안에 스폰이 한 번도 없다");
    }
}
