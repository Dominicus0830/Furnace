// 주민 소문·평판: 값 더하기(최댓값)·감쇠·전달, 평판 사건, 특별 가격 공식, 저장 왕복, 레이드·숨기 활동 전환.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.behavior.OneShot;
import org.domi.furnace.server.entity.ai.behavior.VillagerRaidBehaviors;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.gossip.GossipContainer;
import org.domi.furnace.server.entity.gossip.GossipContainer.GossipType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.domi.furnace.server.world.raid.Raid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerGossipTest {
    private static final UUID A = new UUID(1, 2), B = new UUID(3, 4);

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static int value(GossipContainer g, UUID u, GossipType t) { return g.getGossipEntries().getOrDefault(u, java.util.Map.of()).getOrDefault(t, 0); }

    @Test
    void addClampsToMaxAndReputationIsWeighted() {
        GossipContainer g = new GossipContainer();
        g.add(A, GossipType.MAJOR_POSITIVE, 20);
        g.add(A, GossipType.MAJOR_POSITIVE, 20);
        assertEquals(20, value(g, A, GossipType.MAJOR_POSITIVE), "최댓값 20");
        for (int i = 0; i < 12; i++) g.add(A, GossipType.TRADING, 2);
        assertEquals(24, value(g, A, GossipType.TRADING));
        g.add(A, GossipType.TRADING, 2);
        assertEquals(25, value(g, A, GossipType.TRADING), "26 > 25 이면 max(25, 24)");
        g.add(A, GossipType.MAJOR_NEGATIVE, 3);
        // 20×5 + 25×1 + 3×(−5)
        assertEquals(100 + 25 - 15, g.getReputation(A, t -> true));
        assertEquals(-15, g.getReputation(A, t -> t == GossipType.MAJOR_NEGATIVE));
        g.add(B, GossipType.MINOR_NEGATIVE, 1);
        assertFalse(g.getGossipEntries().containsKey(B), "2 미만은 버린다");
        g.remove(A, GossipType.MAJOR_NEGATIVE, 3);
        assertEquals(0, value(g, A, GossipType.MAJOR_NEGATIVE));
        assertEquals(1, g.getCountForType(GossipType.TRADING, d -> d >= 25));
    }

    @Test
    void decayPerDayRemovesLowValues() {
        GossipContainer g = new GossipContainer();
        g.add(A, GossipType.MINOR_NEGATIVE, 25);
        g.add(A, GossipType.MAJOR_POSITIVE, 20);
        g.add(B, GossipType.MINOR_POSITIVE, 2);
        g.decay();
        assertEquals(5, value(g, A, GossipType.MINOR_NEGATIVE), "25 − 20");
        assertEquals(20, value(g, A, GossipType.MAJOR_POSITIVE), "큰 긍정은 감쇠 0");
        assertFalse(g.getGossipEntries().containsKey(B), "2 − 1 = 1 < 2 → 대상 제거");
        g.decay();
        assertEquals(0, value(g, A, GossipType.MINOR_NEGATIVE));
    }

    @Test
    void transferSubtractsDecayAndKeepsMax() {
        GossipContainer from = new GossipContainer(), to = new GossipContainer();
        from.add(A, GossipType.TRADING, 25);
        from.add(B, GossipType.MINOR_POSITIVE, 6);
        to.add(B, GossipType.MINOR_NEGATIVE, 30);
        to.transferFrom(from, new LegacyRandomSource(42L), 10);
        assertEquals(5, value(to, A, GossipType.TRADING), "25 − 전달 감쇠 20");
        assertEquals(0, value(to, B, GossipType.MINOR_POSITIVE), "6 − 5 = 1 < 2 는 옮기지 않는다");
        assertEquals(30, value(to, B, GossipType.MINOR_NEGATIVE));
        to.add(A, GossipType.TRADING, 10);   // 15
        to.transferFrom(from, new LegacyRandomSource(1L), 10);
        assertEquals(15, value(to, A, GossipType.TRADING), "합칠 때는 큰 값");
        to.transferFrom(new GossipContainer(), new LegacyRandomSource(1L), 10);   // 빈 쪽에서는 아무것도 없다
        assertEquals(15, value(to, A, GossipType.TRADING));
    }

    @Test
    void reputationEventsAndSpecialPrice() {
        var level = new BlockPlacementTest.TestLevel();
        var v = new Villager(level, level.nextEntityId());
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        v.onReputationEvent(Villager.ReputationEvent.ZOMBIE_VILLAGER_CURED, p);
        assertEquals(20 * 5 + 25, v.getPlayerReputation(p));
        v.onReputationEvent(Villager.ReputationEvent.TRADE, p);
        assertEquals(20 * 5 + 25 + 2, v.getPlayerReputation(p));
        MerchantOffers offers = new MerchantOffers();
        MerchantOffer o = new MerchantOffer(new ItemCost("minecraft:emerald", 10), org.domi.furnace.server.block.B.stack("minecraft:bread", 1), 12, 1, 0.05F);
        offers.add(o);
        v.setOffers(offers);
        v.updateSpecialPrices(p);
        assertEquals(-6, o.specialPriceDiff(), "floor(127 × 0.05) = 6");
        o.resetSpecialPriceDiff();
        p.vitals().addEffect(new MobEffectInstance(MobEffect.HERO_OF_THE_VILLAGE, 1000, 1));
        v.updateSpecialPrices(p);
        // 평판 6 + 영웅 floor((0.3 + 0.0625) × 10) = 3
        assertEquals(-9, o.specialPriceDiff());
        v.onReputationEvent(Villager.ReputationEvent.VILLAGER_HURT, p);
        assertEquals(127 - 25, v.getPlayerReputation(p));
        v.onReputationEvent(Villager.ReputationEvent.VILLAGER_KILLED, p);
        assertEquals(127 - 25 - 125, v.getPlayerReputation(p));
    }

    @Test
    void gossipsSaveRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        var v = new Villager(level, level.nextEntityId());
        v.getGossips().add(A, GossipType.MAJOR_NEGATIVE, 40);
        v.getGossips().add(A, GossipType.TRADING, 7);
        v.getGossips().add(B, GossipType.MINOR_POSITIVE, 9);
        NbtCompound tag = new NbtCompound();
        v.addAdditionalSaveData(tag);
        var list = tag.getList("Gossips");
        assertEquals(3, list.size());
        NbtCompound first = (NbtCompound) list.get(0);
        assertTrue(first.tags().get("Target") instanceof int[]);
        assertTrue(first.tags().containsKey("Type") && first.tags().containsKey("Value"));
        assertTrue(tag.contains("LastGossipDecay"));
        var w = new Villager(level, level.nextEntityId());
        w.readAdditionalSaveData(tag);
        assertEquals(v.getGossips().getGossipEntries(), w.getGossips().getGossipEntries());
    }

    private static Raid raid(BlockPlacementTest.TestLevel level, String status, int groups) {
        NbtCompound t = new NbtCompound();
        t.putInt("Id", 1);
        t.putBoolean("Started", true);
        t.putBoolean("Active", true);
        t.putInt("GroupsSpawned", groups);
        t.putInt("NumGroups", 3);
        t.putString("Status", status);
        t.putInt("CX", 0);
        t.putInt("CY", -60);
        t.putInt("CZ", 0);
        Raid r = new Raid(level, t);
        level.raids().all().put(r.id(), r);
        return r;
    }

    /** 레벨 난수 1/20 짜리 한 번 행동을 될 때까지 부른다. */
    private static void triggerUntil(OneShot<Mob> b, BlockPlacementTest.TestLevel level, Villager v) {
        for (int i = 0; i < 2000; i++) if (b.tryStart(level, v, level.gameTime())) return;
        fail("트리거되지 않음");
    }

    @Test
    void raidActivitiesSwitch() {
        var level = new VillagerBrainTest.Clocked();
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(0.5, -60, 0.5, 0, 0);
        var brain = v.villagerBrain();
        // 레이드 없음 + 종소리 → 숨기
        brain.setMemory(MemoryModuleType.HEARD_BELL_TIME, 0L);
        assertTrue(VillagerRaidBehaviors.reactToBell().tryStart(level, v, 0));
        assertTrue(brain.isActive(Activity.HIDE));
        // 첫 웨이브 전 → PRE_RAID
        raid(level, "ongoing", 0);
        triggerUntil(VillagerRaidBehaviors.setRaidStatus(), level, v);
        assertTrue(brain.isActive(Activity.PRE_RAID));
        // 웨이브가 나와 있고 쉬는 시간이 아님 → RAID
        level.raids().all().clear();
        raid(level, "ongoing", 1);
        assertNotNull(VillagerRaidBehaviors.raidAt(level, v));
        triggerUntil(VillagerRaidBehaviors.setRaidStatus(), level, v);
        assertTrue(brain.isActive(Activity.RAID));
        assertTrue(VillagerRaidBehaviors.raidExistsAndActive(level, v));
        // 레이드가 사라지면 IDLE 기본 + 시간표
        level.raids().all().clear();
        level.game = 1000;   // 시간표 갱신은 20틱 간격
        level.day = 1000;
        triggerUntil(VillagerRaidBehaviors.resetRaidStatus(), level, v);
        assertFalse(brain.isActive(Activity.RAID));
        // 이긴 레이드
        raid(level, "victory", 3);
        assertTrue(VillagerRaidBehaviors.raidVictory(level, v));
        assertFalse(VillagerRaidBehaviors.raidExistsAndActive(level, v));
    }
}
