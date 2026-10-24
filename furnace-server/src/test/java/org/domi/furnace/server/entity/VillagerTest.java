// 주민(LivingEntity·Mob·Merchant): 저장/불러오기(거래 목록 포함), 피해·무적 시간·죽음 뒤 제거, 거래 알림(경험치 구슬), 거래 가능 조건.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillagerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage HIT = new Entity.Damage("player_attack", false, false, false);

    private Villager farmer(BlockPlacementTest.TestLevel level) {
        var v = new Villager(level, level.nextEntityId());
        v.setPos(0.5, -55, 0.5);
        v.setVillagerData("plains", "farmer", 2);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost("minecraft:wheat", 20), new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "emerald"), 1), 16, 2, 0.05F));
        v.setOffers(offers);
        return v;
    }

    @Test
    void savesAndLoadsWithOffers() {
        var level = new BlockPlacementTest.TestLevel();
        var v = farmer(level);
        v.overrideXp(15);
        NbtCompound tag = v.saveWithoutId(new NbtCompound());
        var back = new Villager(level, level.nextEntityId());
        back.load(tag);
        assertEquals("farmer", back.profession());
        assertEquals(2, back.villagerLevel());
        assertEquals(15, back.villagerXp());
        assertEquals(1, back.offers().size());
        var o = back.offers().get(0);
        assertEquals("minecraft:wheat", o.baseCostA().item());
        assertEquals(20, o.baseCostA().count());
        assertEquals(16, o.maxUses());
        assertEquals(2, o.xp());
        assertEquals(20f, back.health());
    }

    @Test
    void hurtSetsInvulnerabilityAndOnlyBiggerHitsCountDuringIt() {
        var level = new BlockPlacementTest.TestLevel();
        var v = farmer(level);
        assertTrue(v.hurt(HIT, 5f));
        assertEquals(15f, v.health(), 0.001f);
        assertEquals(20, v.invulnerableTime);
        assertFalse(v.hurt(HIT, 4f), "무적 시간 중 더 약한 피해는 무시");
        v.invulnerableTime = 15;
        assertTrue(v.hurt(HIT, 8f), "더 센 피해는 차이만큼");
        assertEquals(12f, v.health(), 0.001f);
    }

    @Test
    void deathRemovesAfterTwentyTicks() {
        var level = new BlockPlacementTest.TestLevel();
        var v = farmer(level);
        v.hurt(HIT, 100f);
        assertTrue(v.isDeadOrDying());
        for (int i = 0; i < 19; i++) v.tick();
        assertFalse(v.isRemoved());
        v.tick();
        v.tick();
        assertTrue(v.isRemoved());
    }

    @Test
    void tradeNotificationGivesVillagerXpAndOrb() {
        var level = new BlockPlacementTest.TestLevel();
        var v = farmer(level);
        var offer = v.offers().get(0);
        v.notifyTrade(offer);
        assertEquals(1, offer.uses());
        assertEquals(2, v.villagerXp());
        assertFalse(level.getEntitiesOfClass(ExperienceOrb.class, new AABB(-50, -80, -50, 50, 80, 50), o -> true).isEmpty());
    }

    @Test
    void tradingRequiresProfessionAndOffers() {
        var level = new BlockPlacementTest.TestLevel();
        var v = farmer(level);
        assertTrue(v.canTrade());
        v.setVillagerData("plains", "none", 1);
        assertFalse(v.canTrade());
        v.setVillagerData("plains", "farmer", 1);
        v.setOffers(new MerchantOffers());
        assertFalse(v.canTrade());
    }
}
