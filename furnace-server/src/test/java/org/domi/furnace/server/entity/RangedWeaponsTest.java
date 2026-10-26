// 활·쇠뇌: 충전 시간별 힘, 화살 속도·치명타·줍기 규칙, 탄약 소모(무한·창조 예외), 마법(힘·화염), 쇠뇌 장전→발사, 다중 발사, 빠른 장전.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RangedWeaponsTest {

    @BeforeEach
    void install() throws Exception {
        // 화살 태그(#minecraft:arrows)가 필요해 로컬 바닐라 데이터로 레지스트리·태그를 건다
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

    private static ItemStack enchanted(String name, String ench, int lvl) {
        return EnchantTools.update(stack(name, 1), m -> m.put("minecraft:" + ench, lvl));
    }

    private static List<AbstractArrow> arrows(BlockPlacementTest.TestLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof AbstractArrow).map(e -> (AbstractArrow) e).toList();
    }

    private static double speed(Entity e) { return Math.sqrt(e.vx() * e.vx() + e.vy() * e.vy() + e.vz() * e.vz()); }

    /** 쓰기 시작 → n 틱 → 놓기. */
    private static void useFor(BlockPlacementTest.TestLevel level, Player p, PlayerActor actor, int ticks) {
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        for (int i = 0; i < ticks && p.usingHand >= 0; i++) RangedWeapons.tick(p, level, actor.itemInHand(0));
        RangedWeapons.release(level, actor, p);
    }

    @Test
    void bowPowerCurve() {
        assertEquals(0.0F, RangedWeapons.bowPower(0));
        assertEquals((0.05F * 0.05F + 0.1F) / 3.0F, RangedWeapons.bowPower(1), 1e-7);
        assertEquals((0.25F + 1.0F) / 3.0F, RangedWeapons.bowPower(10), 1e-7);
        assertEquals(1.0F, RangedWeapons.bowPower(20));
        assertEquals(1.0F, RangedWeapons.bowPower(100));
        assertTrue(RangedWeapons.bowPower(2) < 0.1F && RangedWeapons.bowPower(3) >= 0.1F);
    }

    @Test
    void fullyDrawnBowShootsCritArrowAndUsesAmmoAndDurability() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, stack("bow", 1), ItemStack.EMPTY, 0);
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertEquals(0, p.usingHand);
        assertEquals(RangedWeapons.BOW_USE_TICKS, p.usingTicks);
        for (int i = 0; i < 20; i++) RangedWeapons.tick(p, level, actor.itemInHand(0));
        RangedWeapons.release(level, actor, p);
        assertEquals(-1, p.usingHand);
        var list = arrows(level);
        assertEquals(1, list.size());
        AbstractArrow a = list.get(0);
        assertEquals(3.0, speed(a), 0.1, "속도 = 힘 × 3");
        assertTrue(a.vz() > 2.8, "yaw 0 은 +z");
        assertTrue(a.isCritArrow());
        assertEquals(AbstractArrow.Pickup.ALLOWED, a.pickup);
        assertEquals(p.eyeY() - 0.1F, a.y(), 1e-6);
        assertEquals(4, p.inventory().get(9).count());
        assertEquals(1, ItemTools.damage(actor.itemInHand(0)));
        assertEquals(2.0, a.modifyDamage(a.getBaseDamage()), 1e-9, "힘 없음");
    }

    @Test
    void halfDrawnBowIsNotCritAndTooShortDoesNothing() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, stack("bow", 1), ItemStack.EMPTY, 0);
        useFor(level, p, actor, 2);
        assertTrue(arrows(level).isEmpty(), "힘 0.1 미만");
        assertEquals(5, p.inventory().get(9).count());
        useFor(level, p, actor, 10);
        AbstractArrow a = arrows(level).get(0);
        assertFalse(a.isCritArrow());
        assertEquals(RangedWeapons.bowPower(10) * 3.0, speed(a), 0.1);
    }

    @Test
    void bowNeedsAmmoInSurvivalButNotInCreative() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        var actor = new PlayerActor(p, stack("bow", 1), ItemStack.EMPTY, 0);
        RangedWeapons.use(level, actor, p, 0);
        assertEquals(-1, p.usingHand, "탄약이 없으면 못 당긴다");
        var c = player(GameMode.CREATIVE);
        var cActor = new PlayerActor(c, stack("bow", 1), ItemStack.EMPTY, 0);
        useFor(level, c, cActor, 20);
        AbstractArrow a = arrows(level).get(0);
        assertEquals(AbstractArrow.Pickup.CREATIVE_ONLY, a.pickup);
        assertEquals(0, ItemTools.damage(cActor.itemInHand(0)), "창조는 내구도가 안 준다");
    }

    @Test
    void infinityKeepsPlainArrowsButNotTipped() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, enchanted("bow", "infinity", 1), ItemStack.EMPTY, 0);
        useFor(level, p, actor, 20);
        assertEquals(5, p.inventory().get(9).count());
        assertEquals(AbstractArrow.Pickup.CREATIVE_ONLY, arrows(level).get(0).pickup);
        p.inventory().set(9, stack("spectral_arrow", 2));
        useFor(level, p, actor, 20);
        assertEquals(1, p.inventory().get(9).count(), "무한은 일반 화살만");
        assertTrue(arrows(level).stream().anyMatch(x -> x instanceof SpectralArrow));
    }

    @Test
    void offhandArrowsAreUsedFirst() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, stack("bow", 1), stack("spectral_arrow", 3), 0);
        useFor(level, p, actor, 20);
        assertEquals(2, actor.itemInHand(1).count());
        assertEquals(5, p.inventory().get(9).count());
    }

    @Test
    void powerAndFlameApplyToTheArrow() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        ItemStack bow = EnchantTools.update(stack("bow", 1), m -> { m.put("minecraft:power", 5); m.put("minecraft:flame", 1); });
        var actor = new PlayerActor(p, bow, ItemStack.EMPTY, 0);
        useFor(level, p, actor, 20);
        AbstractArrow a = arrows(level).get(0);
        assertEquals(4.5, a.modifyDamage(a.getBaseDamage()), 1e-6, "힘 V: 2 + 0.5 × 5");
        assertEquals(2000, a.remainingFireTicks(), "화염: 100초");
    }

    @Test
    void crossbowLoadsOnReleaseThenShootsOnNextUse() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, stack("crossbow", 1), ItemStack.EMPTY, 0);
        assertEquals(25, RangedWeapons.chargeDuration(actor.itemInHand(0)));
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertEquals(28, p.usingTicks);
        for (int i = 0; i < 24; i++) RangedWeapons.tick(p, level, actor.itemInHand(0));
        RangedWeapons.release(level, actor, p);
        assertFalse(RangedWeapons.isCharged(actor.itemInHand(0)), "24틱은 모자란다");
        assertEquals(5, p.inventory().get(9).count());
        useFor(level, p, actor, 25);
        assertTrue(RangedWeapons.isCharged(actor.itemInHand(0)));
        assertEquals(4, p.inventory().get(9).count());
        assertTrue(arrows(level).isEmpty());
        assertTrue(RangedWeapons.use(level, actor, p, 0));
        assertEquals(-1, p.usingHand, "장전돼 있으면 바로 쏜다");
        assertFalse(RangedWeapons.isCharged(actor.itemInHand(0)));
        AbstractArrow a = arrows(level).get(0);
        assertEquals(3.15, speed(a), 0.1);
        assertTrue(a.isCritArrow(), "플레이어 쇠뇌는 늘 치명타");
        assertEquals(1, ItemTools.damage(actor.itemInHand(0)));
    }

    @Test
    void multishotLoadsThreeAndFansOut() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        var actor = new PlayerActor(p, enchanted("crossbow", "multishot", 1), ItemStack.EMPTY, 0);
        useFor(level, p, actor, 25);
        assertEquals(3, RangedWeapons.chargedProjectiles(actor.itemInHand(0)).size());
        assertEquals(4, p.inventory().get(9).count(), "탄약은 하나만");
        RangedWeapons.use(level, actor, p, 0);
        var list = arrows(level);
        assertEquals(3, list.size());
        assertEquals(1L, list.stream().filter(x -> x.pickup == AbstractArrow.Pickup.ALLOWED).count());
        assertEquals(2L, list.stream().filter(x -> x.pickup == AbstractArrow.Pickup.CREATIVE_ONLY).count());
        double[] yaw = list.stream().mapToDouble(x -> Math.toDegrees(Math.atan2(x.vx(), x.vz()))).sorted().toArray();
        assertEquals(-10.0, yaw[0], 1.5);
        assertEquals(0.0, yaw[1], 1.5);
        assertEquals(10.0, yaw[2], 1.5);
        assertEquals(3, ItemTools.damage(actor.itemInHand(0)), "발마다 내구도 1");
    }

    @Test
    void quickChargeAndPiercing() {
        var level = new BlockPlacementTest.TestLevel();
        var p = player(GameMode.SURVIVAL);
        p.inventory().set(9, stack("arrow", 5));
        ItemStack cb = EnchantTools.update(stack("crossbow", 1), m -> { m.put("minecraft:quick_charge", 3); m.put("minecraft:piercing", 4); });
        assertEquals(10, RangedWeapons.chargeDuration(cb));
        assertEquals(13, RangedWeapons.crossbowUseDuration(cb));
        var actor = new PlayerActor(p, cb, ItemStack.EMPTY, 0);
        useFor(level, p, actor, 10);
        assertTrue(RangedWeapons.isCharged(actor.itemInHand(0)));
        RangedWeapons.use(level, actor, p, 0);
        assertEquals(4, arrows(level).get(0).getPierceLevel());
    }

    @Test
    void crossbowShotRotationMatchesVanillaVectors() {
        float[] up = RangedWeapons.viewVector(-90.0F, 0.0F);
        float[] v = RangedWeapons.rotate(RangedWeapons.viewVector(0.0F, 0.0F), 0.0F, up);
        assertArrayEquals(RangedWeapons.viewVector(0.0F, 0.0F), v, 0.0F, "0도는 그대로");
        float[] r = RangedWeapons.rotate(new float[] {0, 0, 1}, (float) Math.toRadians(90), new float[] {0, 1, 0});
        assertEquals(1.0F, r[0], 1e-6);
        assertEquals(0.0F, r[2], 1e-6);
    }
}
