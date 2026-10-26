// 낚싯대(던지기·거두기·손 바뀜), 낚시찌 입질 타이머(난수 순서·범위)·열린 물 판정, 삼지창(충전 10틱 임계·속도 2.5·줍기·충성 귀환).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FishingTridentTest {

    @BeforeEach
    void install() throws Exception {
        assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);
    }

    private static Player player(GameMode mode) {
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        p.inventory().setSelected(0);
        return p;
    }

    private static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    private static PlayerActor holding(Player p, ItemStack s) {
        p.inventory().set(p.inventory().heldWindowSlot(), s);
        return new PlayerActor(p, s, ItemStack.EMPTY, 0);
    }

    private static double speed(Entity e) { return Math.sqrt(e.vx() * e.vx() + e.vy() * e.vy() + e.vz() * e.vz()); }

    // ------------------------------------------------------------------ 낚싯대

    @Test
    void castThenRetrieve() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var actor = holding(p, stack("fishing_rod", 1));
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        FishingHook hook = p.fishing;
        assertNotNull(hook);
        assertTrue(level.entities().tickSnapshot().contains(hook));
        assertEquals(p.eyeY(), hook.y(), 1e-9, "눈 높이에서");
        assertTrue(hook.vz() > 1.05 && hook.vz() < 1.15, "시선(+z)으로 0.6 + 0.5(±흔들림): " + hook.vz());
        assertEquals(p.id(), hook.spawnData(), "추가 데이터 = 소유자 id");
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertNull(p.fishing);
        assertTrue(hook.isRemoved());
        assertEquals(0, ItemTools.damage(actor.itemInHand(0)), "아무것도 안 걸렸으면 내구도 0");
    }

    @Test
    void hookStopsWhenRodLeavesHand() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var actor = holding(p, stack("fishing_rod", 1));
        RangedWeapons.use(level, actor, p, 0);
        FishingHook hook = p.fishing;
        hook.tick();
        assertFalse(hook.isRemoved());
        p.inventory().set(p.inventory().heldWindowSlot(), stack("stick", 1));
        hook.tick();
        assertTrue(hook.isRemoved());
        assertNull(p.fishing);
    }

    @Test
    void lureAndLuckFromEnchantments() {
        var rod = EnchantTools.update(stack("fishing_rod", 1), m -> { m.put("minecraft:lure", 3); m.put("minecraft:luck_of_the_sea", 2); });
        assertEquals(300, FishingRods.lureSpeed(rod));
        assertEquals(2, FishingRods.luck(rod));
        assertEquals(0, FishingRods.lureSpeed(stack("fishing_rod", 1)));
    }

    /** 첫 catchingFish: 비(nextFloat)·하늘(nextFloat) 뒤 timeUntilLured = nextInt(501) + 100 − 미끼. 이후 단계별 범위. */
    @Test
    void biteTimersFollowVanillaRandomOrder() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        holding(p, stack("fishing_rod", 1));
        var hook = new FishingHook(p, level, level.nextEntityId(), 0, 100);
        level.addFreshEntity(hook);
        ((LegacyRandomSource) hook.random()).setSeed(42L);
        var ref = new LegacyRandomSource(42L);
        Pos pos = new Pos(0, -60, 0);
        hook.catchingFish(pos);
        ref.nextFloat();
        ref.nextFloat();
        assertEquals(ref.nextInt(501) + 100 - 100, hook.timeUntilLured);
        int guard = 0;
        while (hook.timeUntilHooked <= 0 && guard++ < 1000) hook.catchingFish(pos);
        assertTrue(hook.timeUntilHooked >= 20 && hook.timeUntilHooked <= 80, "timeUntilHooked 20..80: " + hook.timeUntilHooked);
        assertTrue(hook.timeUntilLured <= 0);
        while (hook.nibble <= 0 && guard++ < 2000) hook.catchingFish(pos);
        assertTrue(hook.nibble >= 20 && hook.nibble <= 40, "nibble 20..40: " + hook.nibble);
        assertTrue(hook.isBiting());
        assertEquals(true, hook.entityData().get(FishingHook.DATA_BITING));
        while (hook.nibble > 0 && guard++ < 3000) hook.catchingFish(pos);
        assertFalse(hook.isBiting());
        assertEquals(0, hook.timeUntilLured);
        assertEquals(0, hook.timeUntilHooked);
    }

    @Test
    void openWaterNeedsClearColumn() {
        var level = new BlockPlacementTest.TestLevel();
        int water = B.defaultState("minecraft:water");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -63; y <= -61; y++) level.setBlock(new Pos(x, y, z), water, 3);
        var p = player(GameMode.SURVIVAL);
        var hook = new FishingHook(p, level, level.nextEntityId(), 0, 0);
        assertTrue(hook.calculateOpenWater(new Pos(0, -61, 0)));
        level.setBlock(new Pos(1, -60, 1), B.defaultState("minecraft:stone"), 3);
        assertFalse(hook.calculateOpenWater(new Pos(0, -61, 0)));
    }

    // ------------------------------------------------------------------ 삼지창

    private static ThrownTrident trident(BlockPlacementTest.TestLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof ThrownTrident).map(e -> (ThrownTrident) e).findFirst().orElse(null);
    }

    private static void useFor(BlockPlacementTest.TestLevel level, Player p, PlayerActor actor, int ticks) {
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertEquals(Tridents.USE_TICKS, p.usingTicks);
        for (int i = 0; i < ticks; i++) RangedWeapons.tick(p, level, actor.itemInHand(0));
        RangedWeapons.release(level, actor, p);
    }

    @Test
    void tridentNeedsTenTicks() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var actor = holding(p, stack("trident", 1));
        useFor(level, p, actor, 9);
        assertNull(trident(level));
        assertFalse(actor.itemInHand(0).isEmpty());
        useFor(level, p, actor, 10);
        ThrownTrident t = trident(level);
        assertNotNull(t);
        assertEquals(2.5, speed(t), 0.1, "속도 2.5");
        assertTrue(actor.itemInHand(0).isEmpty(), "생존은 손에서 빠진다");
        assertEquals(AbstractArrow.Pickup.ALLOWED, t.pickup);
        assertEquals(1, ItemTools.damage(t.getPickupItem()), "내구도 1 깎인 것을 줍는다");
    }

    @Test
    void creativeKeepsTridentAndPickupIsCreativeOnly() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.CREATIVE);
        var actor = holding(p, stack("trident", 1));
        useFor(level, p, actor, 12);
        ThrownTrident t = trident(level);
        assertNotNull(t);
        assertEquals(AbstractArrow.Pickup.CREATIVE_ONLY, t.pickup);
        assertFalse(actor.itemInHand(0).isEmpty());
        assertEquals(0, ItemTools.damage(actor.itemInHand(0)));
    }

    @Test
    void riptideAndBrokenTridentDoNotStart() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var actor = holding(p, EnchantTools.update(stack("trident", 1), m -> m.put("minecraft:riptide", 1)));
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertEquals(-1, p.usingHand, "물·비 밖의 급류는 실패");
        var worn = ItemTools.withDamage(stack("trident", 1), ItemTools.maxDamage(stack("trident", 1)) - 1);
        actor = holding(p, worn);
        RangedWeapons.use(level, actor, p, 0);
        assertEquals(-1, p.usingHand, "부서지기 직전이면 실패");
    }

    /** 급류: 물에 닿아 있으면 쓰기 시작, 놓으면 삼지창은 손에 남고(내구도 1) 20틱 회전 공격·회전 비트(4). */
    @Test
    void riptideInWaterStartsSpin() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.wasTouchingWater = true;
        var actor = holding(p, EnchantTools.update(stack("trident", 1), m -> m.put("minecraft:riptide", 2)));
        useFor(level, p, actor, 10);
        assertNull(trident(level), "급류는 던지지 않는다");
        assertFalse(actor.itemInHand(0).isEmpty());
        assertEquals(1, ItemTools.damage(actor.itemInHand(0)));
        assertEquals(Tridents.SPIN_TICKS, p.autoSpinAttackTicks);
        assertEquals(Tridents.SPIN_DAMAGE, p.autoSpinAttackDmg);
        assertEquals(4, p.livingFlags() & 4);
        assertEquals("item.trident.riptide_2", Tridents.sound(actor.itemInHand(0)));
    }

    /** 회전 공격은 닿은 생물이 없으면 20틱 뒤 끝나고 비트가 꺼진다. 물 밖에서 놓으면 아무 일도 없다. */
    @Test
    void riptideSpinExpiresAndNeedsWaterOnRelease() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.startAutoSpinAttack(Tridents.SPIN_TICKS, Tridents.SPIN_DAMAGE, ItemStack.EMPTY);
        var w = new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return java.util.List.of(p); }
        };
        int[] hits = {0};
        for (int i = 0; i < Tridents.SPIN_TICKS; i++) Tridents.tickSpin(w, p, t -> hits[0]++);
        assertEquals(0, hits[0]);
        assertEquals(0, p.autoSpinAttackTicks);
        assertEquals(0, p.livingFlags() & 4);

        p.wasTouchingWater = true;
        var actor = holding(p, EnchantTools.update(stack("trident", 1), m -> m.put("minecraft:riptide", 1)));
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        p.wasTouchingWater = false;
        for (int i = 0; i < 12; i++) RangedWeapons.tick(p, level, actor.itemInHand(0));
        RangedWeapons.release(level, actor, p);
        assertEquals(0, p.autoSpinAttackTicks, "놓을 때 물·비 밖이면 실패");
        assertEquals(0, ItemTools.damage(actor.itemInHand(0)));
    }

    /** 충성: 땅에 박혀 4틱이 넘으면(dealtDamage) 소유자에게 돌아오기 시작(noPhysics). 충성이 없으면 그대로. */
    @Test
    void loyaltyReturnsAfterLanding() {
        for (int loyalty : new int[] {0, 3}) {
            var level = new BlockPlacementTest.TestLevel();
            var p = player(GameMode.SURVIVAL);
            ItemStack s = loyalty > 0 ? EnchantTools.update(stack("trident", 1), m -> m.put("minecraft:loyalty", loyalty)) : stack("trident", 1);
            var t = new ThrownTrident(level, level.nextEntityId(), p, s);
            t.setPos(5.5, -59.5, 5.5);
            t.setDeltaMovement(0, -0.5, 0);
            level.addFreshEntity(t);
            for (int i = 0; i < 12; i++) t.tick();
            if (loyalty > 0) {
                assertTrue(t.isNoPhysics(), "충성 삼지창은 돌아온다");
                assertTrue(t.clientSideReturnTridentTickCount > 0);
            } else {
                assertFalse(t.isNoPhysics());
            }
        }
    }
}
