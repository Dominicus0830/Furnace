// M14 몹 후속 잔여: 거미 무리 효과, 철 골렘·워든 doPush, 슬라임의 철 골렘 피해, 아홀로틀 죽은 척, 상인 라마 사라짐,
// 방랑 상인 가방, 낙타 승객·질주 쿨다운·앉은 몸 크기, 스니퍼 파는 몸 크기, 피글린 탑승 행동.
// 기대값은 바닐라 1.21.1 디컴파일(cko·cfq·cmb·ckm·cgq·chq·cmf·cgv·chv·bwp·bvq)을 손으로 옮겼다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ai.behavior.DismountOrSkipMounting;
import org.domi.furnace.server.entity.ai.behavior.Mount;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobLeftoversM14Test {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        long time = 100;
        final LevelServices svc = new LevelServices.Delegating(LevelServices.DEFAULT) {
            @Override public long gameTime() { return time; }
            @Override public int gameRuleInt(String name) { return name.equals("maxEntityCramming") ? 24 : super.gameRuleInt(name); }
        };
        @Override protected LevelServices services() { return svc; }
    }

    private static <T extends Entity> T put(Lvl l, T e, double x, double z) {
        e.moveTo(x, -60, z, 0, 0);
        assertTrue(l.addFreshEntity(e));
        return e;
    }

    private static void seed(Entity e, long s) { ((LegacyRandomSource) e.random()).setSeed(s); }

    /** 첫 nextInt(bound) 가 0 인(또는 아닌) 시드. */
    private static long seedFirstNextIntZero(int bound, boolean zero) {
        for (long s = 1; ; s++) if ((new Random(s).nextInt(bound) == 0) == zero) return s;
    }

    // ------------------------------------------------------------------ 거미

    @Test
    void spiderSharesGroupEffectAndCaveSpiderPassesGroupThrough() {
        Lvl l = new Lvl();
        Spider s = put(l, new Spider(l, l.nextEntityId()), 0.5, 0.5);
        var g = new Spider.EffectsGroupData();
        g.effect = MobEffect.STRENGTH;
        Object out = s.finalizeSpawnGroup(l.random(), l.getCurrentDifficultyAt(s.blockPosition()), g);
        assertSame(g, out, "받은 무리 데이터를 그대로 돌려준다");
        assertTrue(s.hasEffect(MobEffect.STRENGTH), "무리의 효과를 받는다");

        // 무리 데이터가 없으면 새로 만든다(보통 난이도라 효과는 고르지 않는다)
        Spider s2 = put(l, new Spider(l, l.nextEntityId()), 2.5, 0.5);
        Object fresh = s2.finalizeSpawnGroup(l.random(), l.getCurrentDifficultyAt(s2.blockPosition()), null);
        assertInstanceOf(Spider.EffectsGroupData.class, fresh);
        assertNull(((Spider.EffectsGroupData) fresh).effect);

        CaveSpider c = put(l, new CaveSpider(l, l.nextEntityId()), 4.5, 0.5);
        assertSame(g, c.finalizeSpawnGroup(l.random(), l.getCurrentDifficultyAt(c.blockPosition()), g));
        assertFalse(c.hasEffect(MobEffect.STRENGTH), "동굴 거미는 효과를 받지 않는다");
    }

    @Test
    void spiderRandomEffectTable() {
        // setRandomEffect: nextInt(5) 0·1 신속, 2 힘, 3 재생, 4 투명
        MobEffect[] want = {MobEffect.SPEED, MobEffect.SPEED, MobEffect.STRENGTH, MobEffect.REGENERATION, MobEffect.INVISIBILITY};
        for (long s = 1; s < 40; s++) {
            var g = new Spider.EffectsGroupData();
            g.setRandomEffect(new LegacyRandomSource(s));
            assertSame(want[new Random(s).nextInt(5)], g.effect);
        }
    }

    // ------------------------------------------------------------------ 밀기 훅

    @Test
    void ironGolemTargetsPushedEnemyOneInTwenty() {
        Lvl l = new Lvl();
        IronGolem g = put(l, new IronGolem(l, l.nextEntityId()), 0.5, 0.5);
        Zombie z = put(l, new Zombie(l, l.nextEntityId()), 0.9, 0.5);
        seed(g, seedFirstNextIntZero(20, false));
        g.pushEntities();
        assertNull(g.getTarget(), "1/20 에 안 걸리면 그대로");
        seed(g, seedFirstNextIntZero(20, true));
        g.pushEntities();
        assertSame(z, g.getTarget());
    }

    @Test
    void ironGolemIgnoresPushedCreeper() {
        Lvl l = new Lvl();
        IronGolem g = put(l, new IronGolem(l, l.nextEntityId()), 0.5, 0.5);
        put(l, new Creeper(l, l.nextEntityId()), 0.9, 0.5);
        seed(g, seedFirstNextIntZero(20, true));
        g.pushEntities();
        assertNull(g.getTarget());
    }

    @Test
    void wardenTouchSetsCooldownAngerAndDisturbance() {
        Lvl l = new Lvl();
        Warden w = put(l, new Warden(l, l.nextEntityId()), 0.5, 0.5);
        Cow c = put(l, new Cow(l, l.nextEntityId()), 1.0, 0.5);
        w.pushEntities();
        assertTrue(w.getBrain().hasMemoryValue(MemoryModuleType.TOUCH_COOLDOWN));
        assertEquals(35, w.angerManagement().getActiveAnger(c));
        assertEquals(c.blockPosition(), w.getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION).orElseThrow());
        // 쿨다운 동안은 더 오르지 않는다
        w.pushEntities();
        assertEquals(35, w.angerManagement().getActiveAnger(c));
    }

    @Test
    void slimePushedByGolemHitsIt() {
        Lvl l = new Lvl();
        Slime s = new Slime(l, l.nextEntityId());
        s.setSize(2, true);
        put(l, s, 0.5, 0.5);
        IronGolem g = put(l, new IronGolem(l, l.nextEntityId()), 1.0, 0.5);
        float before = g.health();
        g.pushEntities();   // 골렘이 슬라임을 민다 → slime.push(golem) → 피해
        assertTrue(g.health() < before, "슬라임이 민 골렘을 때린다");
    }

    @Test
    void tinySlimeDoesNotHitGolem() {
        Lvl l = new Lvl();
        Slime s = new Slime(l, l.nextEntityId());
        s.setSize(1, true);
        put(l, s, 0.5, 0.5);
        IronGolem g = put(l, new IronGolem(l, l.nextEntityId()), 0.8, 0.5);
        float before = g.health();
        g.pushEntities();
        assertEquals(before, g.health());
    }

    // ------------------------------------------------------------------ 아홀로틀

    @Test
    void playingDeadAxolotlIsNotAnEnemy() {
        Lvl l = new Lvl();
        Axolotl a = put(l, new Axolotl(l, l.nextEntityId()), 0.5, 0.5);
        assertTrue(a.canBeSeenAsEnemy());
        a.setPlayingDead(true);
        assertFalse(a.canBeSeenAsEnemy());
        assertTrue(a.isPushable(), "바닐라 1.21.1 은 밀기를 막지 않는다");
    }

    // ------------------------------------------------------------------ 상인·라마

    @Test
    void traderLlamaFollowsTraderDespawnDelay() {
        Lvl l = new Lvl();
        WanderingTrader t = put(l, new WanderingTrader(l, l.nextEntityId()), 0.5, 0.5);
        t.setDespawnDelay(500);
        TraderLlama ll = put(l, new TraderLlama(l, l.nextEntityId()), 3.5, 0.5);
        ll.setLeashedTo(t, true);
        ll.aiStep();
        NbtCompound tag = new NbtCompound();
        ll.addAdditionalSaveData(tag);
        assertEquals(499, tag.getInt("DespawnDelay"), "상인 시간 - 1");

        TraderLlama free = put(l, new TraderLlama(l, l.nextEntityId()), 6.5, 0.5);
        free.setDespawnDelay(1);
        free.aiStep();
        assertTrue(free.isRemoved(), "0 이 되면 사라진다");
    }

    @Test
    void wanderingTraderInventoryRoundTripsAndIsAddressable() {
        Lvl l = new Lvl();
        WanderingTrader t = put(l, new WanderingTrader(l, l.nextEntityId()), 0.5, 0.5);
        ItemStack apple = new ItemStack(ItemContext.id("minecraft:item", "apple"), 3);
        var slot = ItemSlots.of(t, 302);
        assertNotNull(slot, "villager.2 칸");
        assertTrue(slot.set(apple));
        assertNull(ItemSlots.of(t, 308), "8칸 밖");
        NbtCompound tag = new NbtCompound();
        t.addAdditionalSaveData(tag);
        WanderingTrader back = new WanderingTrader(l, l.nextEntityId());
        back.readAdditionalSaveData(tag);
        // SimpleContainer 저장은 칸 번호 없이 빈 칸을 건너뛰고, 읽을 때 addItem 으로 앞에서부터 채운다(바닐라 그대로)
        assertEquals(3, back.getInventory().get(0).count());
        assertEquals(ItemContext.id("minecraft:item", "apple"), back.getInventory().get(0).itemId());
    }

    // ------------------------------------------------------------------ 낙타

    @Test
    void camelTakesUpToTwoRidersFrontAndBack() {
        Lvl l = new Lvl();
        Camel c = put(l, new Camel(l, l.nextEntityId()), 0.5, 0.5);
        Cow a = put(l, new Cow(l, l.nextEntityId()), 3.5, 0.5);
        Zombie b = put(l, new Zombie(l, l.nextEntityId()), 6.5, 0.5);
        assertTrue(b.startRiding(c));
        assertTrue(a.startRiding(c));
        // 앞(0번 좀비) z 0.5, 뒤(1번 소, 동물이라 +0.2) z -0.5. 몸 방향 0 이라 z 그대로.
        assertEquals(0.5, c.getPassengerAttachmentPoint(b, 1.0F).z(), 1e-6);
        assertEquals(-0.5, c.getPassengerAttachmentPoint(a, 1.0F).z(), 1e-6);
        // 선 자세 높이: 2.375 - 0.375
        assertEquals(2.375F - 0.375F, c.getPassengerAttachmentPoint(b, 1.0F).y(), 1e-6);
    }

    @Test
    void camelDashSetsCooldownThroughSyncedData() {
        Lvl l = new Lvl();
        Camel c = put(l, new Camel(l, l.nextEntityId()), 0.5, 0.5);
        c.tick();   // 첫 틱이 지나야 onSyncedDataUpdated 가 쿨다운을 건다
        c.handleStartJump(50);
        assertTrue(c.isDashing());
        assertEquals(55, c.getJumpCooldown());
    }

    @Test
    void sittingSaddledCamelCannotDash() {
        Lvl l = new Lvl();
        Camel c = put(l, new Camel(l, l.nextEntityId()), 0.5, 0.5);
        assertFalse(c.canJump());   // 안장 없음
        c.equipSaddle(new ItemStack(ItemContext.id("minecraft:item", "saddle"), 1));
        assertTrue(c.canJump());
        c.sitDown();
        assertFalse(c.canJump());   // 앉아 있으면 refuseToMove
    }

    @Test
    void sittingCamelIsShorter() {
        Lvl l = new Lvl();
        Camel c = put(l, new Camel(l, l.nextEntityId()), 0.5, 0.5);
        assertEquals(2.375F, c.bbHeight(), 1e-6);
        c.sitDown();
        assertEquals(2.375F - 1.43F, c.bbHeight(), 1e-6);
        c.standUpInstantly();
        assertEquals(2.375F, c.bbHeight(), 1e-6);
    }

    @Test
    void diggingSnifferIsShorter() {
        Lvl l = new Lvl();
        Sniffer s = put(l, new Sniffer(l, l.nextEntityId()), 0.5, 0.5);
        assertEquals(1.75F, s.bbHeight(), 1e-6);
        s.transitionTo(Sniffer.State.DIGGING);
        assertEquals(1.75F - 0.4F, s.bbHeight(), 1e-6);
        s.transitionTo(Sniffer.State.IDLING);
        assertEquals(1.75F, s.bbHeight(), 1e-6);
    }

    // ------------------------------------------------------------------ 피글린 탑승 행동

    @Test
    void mountRidesTargetWithinOneBlock() {
        Lvl l = new Lvl();
        Piglin p = put(l, new Piglin(l, l.nextEntityId()), 0.5, 0.5);
        p.setBaby(true);
        Hoglin h = put(l, new Hoglin(l, l.nextEntityId()), 0.9, 0.5);
        h.setBaby(true);
        p.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        p.piglinBrain().setMemory(MemoryModuleType.RIDE_TARGET, (Entity) h);
        assertTrue(Mount.create(0.8F).tryStart(l, p, l.gameTime()));
        assertSame(h, p.vehicle());
        // 어른이 된 호글린이면 내린다(wantsToStopRiding)
        h.setBaby(false);
        assertTrue(DismountOrSkipMounting.<Piglin>create(8, (pg, e) -> e instanceof Mob m && !m.isBaby()).tryStart(l, p, l.gameTime()));
        assertNull(p.vehicle());
        assertFalse(p.getBrain().hasMemoryValue(MemoryModuleType.RIDE_TARGET));
    }

    @Test
    void mountWalksTowardFarTarget() {
        Lvl l = new Lvl();
        Piglin p = put(l, new Piglin(l, l.nextEntityId()), 0.5, 0.5);
        Hoglin h = put(l, new Hoglin(l, l.nextEntityId()), 4.5, 0.5);
        p.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        p.piglinBrain().setMemory(MemoryModuleType.RIDE_TARGET, (Entity) h);
        assertTrue(Mount.create(0.8F).tryStart(l, p, l.gameTime()));
        assertNull(p.vehicle());
        assertEquals(0.8F, p.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getSpeedModifier(), 0.0F);
    }
}
