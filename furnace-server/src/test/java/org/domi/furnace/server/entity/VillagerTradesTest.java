// 주민 거래표(VillagerTrades)와 Villager.updateTrades: 직업·레벨별 표, 후보 뽑기의 난수 호출 순서·결정성, 레벨업 때 거래 추가, 거래 불가 직업,
// 마법 부여 책 거래의 마법 유효성(데이터 팩 필요).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.VanillaChestLootTest;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.VillagerTrades;
import org.domi.furnace.server.trade.VillagerTrades.ItemListing;
import org.domi.furnace.server.trade.VillagerTrades.TradeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VillagerTradesTest {

    @BeforeEach
    void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 호출 순서를 기록하는 난수(bound 가 있는 nextInt 는 "i<bound>", 그 밖은 이름). */
    static final class Recording implements RandomSource {
        final LegacyRandomSource r;
        final List<String> calls = new ArrayList<>();
        Recording(long seed) { r = new LegacyRandomSource(seed); }
        public int nextInt() { calls.add("i"); return r.nextInt(); }
        public int nextInt(int bound) { calls.add("i" + bound); return r.nextInt(bound); }
        public long nextLong() { calls.add("l"); return r.nextLong(); }
        public float nextFloat() { calls.add("f"); return r.nextFloat(); }
        public double nextDouble() { calls.add("d"); return r.nextDouble(); }
        public boolean nextBoolean() { calls.add("b"); return r.nextBoolean(); }
        public double nextGaussian() { calls.add("g"); return r.nextGaussian(); }
    }

    private static Villager villager(BlockPlacementTest.TestLevel level, String profession, int lvl) {
        var v = new Villager(level, level.nextEntityId());
        v.setPos(0.5, -55, 0.5);
        v.setVillagerData("plains", profession, lvl);
        return v;
    }

    private static String sig(org.domi.furnace.server.trade.MerchantOffers o) {
        StringBuilder sb = new StringBuilder();
        for (MerchantOffer m : o) {
            sb.append(m.baseCostA().item()).append('x').append(m.baseCostA().count());
            m.costB().ifPresent(b -> sb.append('+').append(b.item()).append('x').append(b.count()));
            sb.append("->").append(ItemTools.itemName(m.result())).append('x').append(m.result().count())
                    .append(' ').append(m.maxUses()).append('/').append(m.xp()).append('/').append(m.priceMultiplier())
                    .append(ItemTools.component(m.result(), "minecraft:dyed_color")).append(';');
        }
        return sb.toString();
    }

    @Test
    void tableCoversAllTradingProfessionsAndLevels() {
        List<String> professions = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher", "leatherworker", "librarian",
                "mason", "shepherd", "toolsmith", "weaponsmith");
        for (String p : professions) {
            var table = VillagerTrades.TRADES.get(p);
            assertNotNull(table, p);
            assertEquals(5, table.length, p);
            for (int lvl = 0; lvl < 5; lvl++) assertTrue(table[lvl].length > 0, p + " 레벨 " + (lvl + 1));
        }
        assertEquals(professions.size(), VillagerTrades.TRADES.size());
        assertNull(VillagerTrades.TRADES.get("none"));
        assertNull(VillagerTrades.TRADES.get("nitwit"));
        assertEquals(2, VillagerTrades.WANDERING_TRADER_TRADES.length);
        assertEquals(64, VillagerTrades.WANDERING_TRADER_TRADES[0].length);
        assertEquals(6, VillagerTrades.WANDERING_TRADER_TRADES[1].length);
    }

    @Test
    void farmerLevelOneListingsMatchVanilla() {
        var ctx = new TradeContext("plains", null);
        ItemListing[] l = VillagerTrades.TRADES.get("farmer")[0];
        assertEquals(5, l.length);
        var wheat = l[0].getOffer(ctx, new LegacyRandomSource(1));
        assertEquals("minecraft:wheat", wheat.baseCostA().item());
        assertEquals(20, wheat.baseCostA().count());
        assertEquals("minecraft:emerald", ItemTools.itemName(wheat.result()));
        assertEquals(1, wheat.result().count());
        assertEquals(16, wheat.maxUses());
        assertEquals(2, wheat.xp());
        assertEquals(0.05F, wheat.priceMultiplier());
        var bread = l[4].getOffer(ctx, new LegacyRandomSource(1));
        assertEquals("minecraft:emerald", bread.baseCostA().item());
        assertEquals(1, bread.baseCostA().count());
        assertEquals("minecraft:bread", ItemTools.itemName(bread.result()));
        assertEquals(6, bread.result().count());
        assertEquals(16, bread.maxUses());
        assertEquals(1, bread.xp());
    }

    @Test
    void fishermanBoatDependsOnVillagerType() {
        ItemListing boat = VillagerTrades.TRADES.get("fisherman")[4][1];
        assertEquals("minecraft:jungle_boat", boat.getOffer(new TradeContext("desert", null), new LegacyRandomSource(1)).baseCostA().item());
        assertEquals("minecraft:spruce_boat", boat.getOffer(new TradeContext("snow", null), new LegacyRandomSource(1)).baseCostA().item());
        assertNull(boat.getOffer(new TradeContext(null, null), new LegacyRandomSource(1)));
    }

    @Test
    void mapTradesAreOmittedButKeepTheirSlot() {
        ItemListing[] cartographer2 = VillagerTrades.TRADES.get("cartographer")[1];
        assertEquals(2, cartographer2.length);
        assertNotNull(cartographer2[0].getOffer(new TradeContext("plains", null), new LegacyRandomSource(1)));
        assertNull(cartographer2[1].getOffer(new TradeContext("plains", null), new LegacyRandomSource(1)));
    }

    @Test
    void pickingListingsConsumesRandomInVanillaOrder() {
        // 레벨 1 농부 후보 5개(난수를 안 쓰는 것뿐): 5개 중 하나, 남은 4개 중 하나 — nextInt(5), nextInt(4) 만 호출한다
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        var rec = new Recording(42);
        v.updateTrades(rec);
        assertEquals(List.of("i5", "i4"), rec.calls);
        assertEquals(2, v.offers().size());
        // 같은 시드면 같은 결과, 직접 풀어 본 순서와도 같다
        var ref = new LegacyRandomSource(42);
        var pool = new ArrayList<>(List.of(VillagerTrades.TRADES.get("farmer")[0]));
        var expect = new org.domi.furnace.server.trade.MerchantOffers();
        for (int i = 0; i < 2; i++) expect.add(pool.remove(ref.nextInt(pool.size())).getOffer(new TradeContext("plains", null), ref));
        assertEquals(sig(expect), sig(v.offers()));
    }

    @Test
    void sameSeedSameOffers() {
        var level = new BlockPlacementTest.TestLevel();
        for (String p : VillagerTrades.TRADES.keySet()) {
            for (int lvl = 1; lvl <= 5; lvl++) {
                var a = villager(level, p, lvl);
                var b = villager(level, p, lvl);
                a.updateTrades(new LegacyRandomSource(7));
                b.updateTrades(new LegacyRandomSource(7));
                assertEquals(sig(a.offers()), sig(b.offers()), p + lvl);
                assertFalse(a.offers().isEmpty(), p + lvl);
                assertTrue(a.offers().size() <= 2, p + lvl);
            }
        }
    }

    @Test
    void leatherworkerDyesArmor() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);   // 태그(dyeable)도 설치
        var ctx = new TradeContext("plains", null);
        ItemListing tunic = VillagerTrades.TRADES.get("leatherworker")[0][2];
        var rec = new Recording(5);
        var offer = tunic.getOffer(ctx, rec);
        assertEquals("minecraft:emerald", offer.baseCostA().item());
        assertTrue(rec.calls.get(0).equals("i16") && rec.calls.contains("f"), rec.calls.toString());
        assertNotNull(ItemTools.component(offer.result(), "minecraft:dyed_color"));
    }

    @Test
    void newVillagerGetsTradesLazilyAndSavesThem() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        assertEquals(2, v.offers().size());
        var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
        v.saveWithoutId(tag);
        var back = new Villager(level, level.nextEntityId());
        back.load(tag);
        assertEquals(sig(v.offers()), sig(back.offers()));
    }

    @Test
    void nonTradingProfessionsHaveNoOffers() {
        var level = new BlockPlacementTest.TestLevel();
        for (String p : List.of("none", "nitwit")) {
            var v = villager(level, p, 1);
            assertTrue(v.offers().isEmpty());
            assertFalse(v.canTrade());
        }
    }

    @Test
    void changingProfessionRegeneratesOffers() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        String farmer = sig(v.offers());
        v.setVillagerData("plains", "cleric", 1);
        assertNotEquals(farmer, sig(v.offers()));
        assertTrue(sig(v.offers()).contains("minecraft:"));
    }

    @Test
    void levelUpAddsTradesAfterFortyTicks() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        assertEquals(2, v.offers().size());
        v.overrideXp(9);
        var offer = v.offers().get(0);
        v.notifyTrade(offer);   // 경험치 9 + 거래 경험치 >= 10 → 레벨업 대기
        assertEquals(1, v.villagerLevel());
        for (int i = 0; i < 39; i++) v.tick();
        assertEquals(1, v.villagerLevel());
        v.tick();
        assertEquals(2, v.villagerLevel());
        assertEquals(4, v.offers().size());
    }

    @Test
    void noLevelUpBelowThresholdOrAtMaxLevel() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        v.overrideXp(0);
        v.notifyTrade(v.offers().get(0));
        for (int i = 0; i < 60; i++) v.tick();
        assertEquals(1, v.villagerLevel());
        var master = villager(level, "farmer", 5);
        master.overrideXp(1000);
        master.notifyTrade(master.offers().get(0));
        for (int i = 0; i < 60; i++) master.tick();
        assertEquals(5, master.villagerLevel());
    }

    @Test
    void restockUpdatesDemandAndResetsUses() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level, "farmer", 1);
        var offer = v.offers().get(0);
        for (int i = 0; i < offer.maxUses(); i++) offer.increaseUses();
        assertTrue(v.needsToRestock());
        v.restock();
        assertEquals(0, offer.uses());
        assertEquals(offer.maxUses(), offer.demand());   // demand + uses - (max - uses) = max... 0 + 16 - 0
        assertFalse(v.needsToRestock());
    }

    // ------------------------------------------------------------------ 데이터 팩이 필요한 거래(마법 부여)

    private static final class DataLevel extends BlockPlacementTest.TestLevel {
        final org.domi.furnace.server.block.LevelServices services;
        DataLevel(LootData data) {
            services = new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                @Override public LootData loot() { return data; }
            };
        }
        @Override protected org.domi.furnace.server.block.LevelServices services() { return services; }
    }

    @Test
    void enchantedBookTradesUseValidTradeableEnchantments() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var regs = data.registries();
        var tradeable = regs.tag("minecraft:enchantment", "minecraft:tradeable");
        assertFalse(tradeable.isEmpty());
        var ctx = new TradeContext("plains", data);
        ItemListing book = VillagerTrades.TRADES.get("librarian")[0][1];   // enchantBookForEmeralds(1)
        for (long seed = 1; seed <= 300; seed++) {
            var rec = new Recording(seed);
            MerchantOffer o = book.getOffer(ctx, rec);
            assertEquals("minecraft:enchanted_book", ItemTools.itemName(o.result()));
            Map<String, Integer> levels = EnchantTools.levels(o.result(), EnchantTools.STORED);
            assertEquals(1, levels.size());
            String id = levels.keySet().iterator().next();
            int lvl = levels.get(id);
            assertTrue(tradeable.contains(id), id);
            assertTrue(lvl >= 1 && lvl <= regs.enchantment(id).maxLevel(), id + lvl);
            // 호출 순서: 태그에서 하나, 수준, 비용
            assertEquals(List.of("i" + tradeable.size(), "i" + regs.enchantment(id).maxLevel(), "i" + (5 + lvl * 10)), rec.calls);
            int base = 2 + 3 * lvl;
            assertTrue(o.baseCostA().count() >= base && o.baseCostA().count() <= 64);
            assertEquals("minecraft:book", o.costB().orElseThrow().item());
            assertEquals(1, o.xp());
            assertEquals(12, o.maxUses());
            assertEquals(0.2F, o.priceMultiplier());
        }
    }

    @Test
    void enchantedEquipmentTradeIsEnchantedAndDeterministic() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var ctx = new TradeContext("plains", data);
        ItemListing rod = VillagerTrades.TRADES.get("fisherman")[2][1];   // 낚싯대
        for (long seed = 1; seed <= 100; seed++) {
            MerchantOffer a = rod.getOffer(ctx, new LegacyRandomSource(seed));
            MerchantOffer b = rod.getOffer(ctx, new LegacyRandomSource(seed));
            assertEquals(sig(one(a)), sig(one(b)));
            assertEquals(ItemTools.component(a.result(), "minecraft:enchantments"), ItemTools.component(b.result(), "minecraft:enchantments"));
            assertTrue(a.baseCostA().count() >= 3 + 5 && a.baseCostA().count() <= 3 + 19);
            var lv = EnchantTools.levels(a.result(), EnchantTools.ENCHANTMENTS);
            for (String id : lv.keySet()) assertTrue(data.registries().enchantment(id).supports("minecraft:fishing_rod", data.registries()), id);
        }
    }

    @Test
    void villagerUsesLevelLootDataForEnchantedTrades() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var level = new DataLevel(data);
        var v = villager(level, "librarian", 1);
        v.updateTrades(new LegacyRandomSource(3));
        assertFalse(v.offers().isEmpty());
    }

    private static org.domi.furnace.server.trade.MerchantOffers one(MerchantOffer o) {
        var l = new org.domi.furnace.server.trade.MerchantOffers();
        l.add(o);
        return l;
    }
}
