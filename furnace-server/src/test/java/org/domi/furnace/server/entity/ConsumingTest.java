// 먹기·마시기: 황금 사과(효과 둘)·배고프지 않으면 못 먹음·우유(효과 전부 제거)·사용 도중 손이 바뀌면 취소.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class ConsumingTest {
    private final Player player = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
    private final PlayerVitals v = player.vitals();
    private final PlayerVitalsTest.TestEnv env = new PlayerVitalsTest.TestEnv();

    ConsumingTest() throws Exception {
        assumeTrue(TestRecipes.manager() != null);
        v.env(env);
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
        player.inventory().setSelected(0);
    }

    private void hold(ItemStack s) { player.inventory().set(player.inventory().heldWindowSlot(), s); }

    private void run(int n) { for (int i = 0; i < n; i++) Consuming.tick(player, null); }

    @Test
    void goldenAppleGivesRegenerationAndAbsorption() {
        v.setFoodLevel(10);
        hold(TestRecipes.item("golden_apple", 2));
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(31);
        assertEquals(2, player.inventory().get(player.inventory().heldWindowSlot()).count(), "아직 다 안 먹었다");
        run(1);
        assertEquals(1, player.inventory().get(player.inventory().heldWindowSlot()).count());
        assertEquals(14, v.foodLevel());
        assertTrue(v.hasEffect(MobEffect.REGENERATION));
        assertTrue(v.hasEffect(MobEffect.ABSORPTION));
    }

    /** Paper PotionItem(U3c): 지속 효과를 먼저 다 걸고 즉시 효과는 뒤에 — 최대 체력이 28 로 먼저 서서 18 + 8 = 26(바닐라 순서면 20 에서 막힌다). 빈 칸엔 유리병. */
    @Test
    void potionAppliesInstantEffectsAfterLastingLikePaper() {
        v.setHealth(18.0F);
        ItemStack potion = org.domi.furnace.server.item.ItemTools.with(TestRecipes.item("potion", 1), "minecraft:potion_contents",
                java.util.Map.of("custom_effects", java.util.List.of(
                        java.util.Map.of("id", "minecraft:instant_health", "amplifier", 1, "duration", 1),
                        java.util.Map.of("id", "minecraft:health_boost", "amplifier", 1, "duration", 600))));
        hold(potion);
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(32);
        assertEquals(28.0F, v.maxHealth());
        assertEquals(26.0F, v.health());
        assertEquals("minecraft:glass_bottle", org.domi.furnace.server.item.ItemTools.itemName(player.inventory().get(player.inventory().heldWindowSlot())));
    }

    private static ItemStack potion(String id, int n) {
        return org.domi.furnace.server.item.ItemTools.with(TestRecipes.item("potion", n), "minecraft:potion_contents", java.util.Map.of("potion", id));
    }

    private static String name(ItemStack s) { return s.isEmpty() ? "minecraft:air" : org.domi.furnace.server.item.ItemTools.itemName(s); }

    /** 마신 뒤 처리만(Consuming.finishPotion) — 레벨이 필요한 경우(죽음·유리병 떨굼). keep 은 레벨과 사망 처리의 keepInventory. */
    private org.domi.furnace.server.block.BlockPlacementTest.TestLevel drinkAt(ItemStack s, boolean keep, java.util.List<java.util.List<ItemStack>> deaths) {
        env.rules.put("keepInventory", keep);
        var level = new org.domi.furnace.server.block.BlockPlacementTest.TestLevel() {
            @Override protected org.domi.furnace.server.block.LevelServices services() {
                return new org.domi.furnace.server.block.LevelServices.Delegating(org.domi.furnace.server.block.LevelServices.DEFAULT) {
                    @Override public boolean gameRule(String n) { return n.equals("keepInventory") ? keep : super.gameRule(n); }
                };
            }
        };
        v.deathListener((k, drops, originals, toKeep) -> deaths.add(java.util.List.copyOf(originals)));
        hold(s);
        Consuming.finishPotion(player, level, player.inventory(), player.inventory().heldWindowSlot(), s);
        return level;
    }

    private static int bottlesOnGround(org.domi.furnace.server.block.BlockPlacementTest.TestLevel level) {
        int n = 0;
        for (Entity e : level.entities().all()) if (e instanceof ItemEntity ie && name(ie.getItem()).equals("minecraft:glass_bottle")) n += ie.getItem().count();
        return n;
    }

    /** Paper: 소모가 즉시 효과 앞 — 마시다 죽으면 사망 드롭에 물약이 없고, 유리병 하나는 발밑에 따로 떨어진다. 칸은 비고 drink 이벤트는 없다. */
    @Test
    void dyingFromPotionDropsBottleSeparatelyAndNoPotion() {
        for (int i = 0; i < 61; i++) v.tick(PlayerVitals.Surroundings.NONE);   // 접속 직후 무적(60틱)이 지나게
        v.setHealth(1.0F);
        var deaths = new java.util.ArrayList<java.util.List<ItemStack>>();
        var level = drinkAt(potion("minecraft:harming", 1), false, deaths);
        assertTrue(v.health() <= 0.0F);
        assertEquals(1, deaths.size(), "한 번 죽었다");
        assertTrue(deaths.get(0).stream().noneMatch(x -> name(x).equals("minecraft:potion")), "사망 드롭에 물약이 없다: " + deaths.get(0));
        assertTrue(deaths.get(0).stream().noneMatch(x -> name(x).equals("minecraft:glass_bottle")), "유리병은 사망 드롭이 아니다");
        assertEquals(1, bottlesOnGround(level), "유리병 하나는 발밑에 따로");
        assertTrue(player.inventory().get(player.inventory().heldWindowSlot()).isEmpty());
    }

    /** keepInventory 면 죽어도 떨어뜨리지 않고 보통처럼 칸에 유리병. */
    @Test
    void dyingFromPotionWithKeepInventoryPutsBottleInSlot() {
        for (int i = 0; i < 61; i++) v.tick(PlayerVitals.Surroundings.NONE);   // 접속 직후 무적(60틱)이 지나게
        v.setHealth(1.0F);
        var level = drinkAt(potion("minecraft:harming", 1), true, new java.util.ArrayList<>());
        assertTrue(v.health() <= 0.0F);
        assertEquals(0, bottlesOnGround(level));
        assertEquals("minecraft:glass_bottle", name(player.inventory().get(player.inventory().heldWindowSlot())));
    }

    /** 물약이 남고 인벤토리가 가득이면 Paper 처럼 유리병이 사라진다(Inventory.add 반환값을 버린다 — 떨어뜨리지 않는다). */
    @Test
    void fullInventoryLosesBottle() {
        for (int i = 9; i < 45; i++) player.inventory().set(i, TestRecipes.item("stone", 64));
        var level = drinkAt(potion("minecraft:water", 2), false, new java.util.ArrayList<>());
        ItemStack held = player.inventory().get(player.inventory().heldWindowSlot());
        assertEquals("minecraft:potion", name(held));
        assertEquals(1, held.count(), "하나 소모");
        assertEquals(0, bottlesOnGround(level), "떨어뜨리지 않는다");
        for (int i = 0; i < 46; i++) assertFalse(name(player.inventory().get(i)).equals("minecraft:glass_bottle"), "칸 " + i);
    }

    /** 크리에이티브: 소모도 유리병도 없다. */
    @Test
    void creativeKeepsPotionAndGetsNoBottle() {
        v.setGameMode(GameMode.CREATIVE);
        var level = drinkAt(potion("minecraft:water", 1), false, new java.util.ArrayList<>());
        ItemStack held = player.inventory().get(player.inventory().heldWindowSlot());
        assertEquals("minecraft:potion", name(held));
        assertEquals(1, held.count());
        assertEquals(0, bottlesOnGround(level));
    }

    /** 포화는 즉시 효과: 마시면 효과 목록에 남지 않고 음식만 (단계+1) 차오른다. */
    @Test
    void drinkingSaturationIsInstantAndLeavesNoEffect() {
        v.setFoodLevel(10);
        ItemStack potion = org.domi.furnace.server.item.ItemTools.with(TestRecipes.item("potion", 1), "minecraft:potion_contents",
                java.util.Map.of("custom_effects", java.util.List.of(java.util.Map.of("id", "minecraft:saturation", "amplifier", 1, "duration", 200))));
        hold(potion);
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(40);
        assertEquals(12, v.foodLevel());
        assertFalse(v.hasEffect(MobEffect.SATURATION));
    }

    @Test
    void fullHungerCannotEatNormalFood() {
        assertFalse(Consuming.tryStart(player, 0, TestRecipes.item("apple", 1)));
        assertTrue(Consuming.tryStart(player, 0, TestRecipes.item("golden_apple", 1)), "황금 사과는 언제나 먹는다");
    }

    /** 바닐라 HoneyBottleItem.use = ItemUtils.startUsingInstantly: 허기 검사가 없어 배가 불러도 마신다. */
    @Test
    void honeyBottleIsDrunkEvenWhenFull() {
        assertTrue(Consuming.tryStart(player, 0, TestRecipes.item("honey_bottle", 1)));
    }

    @Test
    void changingHandCancelsEating() {
        v.setFoodLevel(10);
        hold(TestRecipes.item("apple", 1));
        Consuming.tryStart(player, 0, TestRecipes.item("apple", 1));
        hold(ItemStack.EMPTY);
        run(40);
        assertEquals(10, v.foodLevel());
        assertEquals(-1, player.usingHand);
    }

    /** A12-05: 먹기·마시기가 used 통계를 올린다(바닐라 Player.eat·PotionItem·MilkBucketItem·HoneyBottleItem). 꿀은 두 번. */
    @Test
    void eatingAndDrinkingAwardUsedStats(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        var st = new org.domi.furnace.server.stats.PlayerStats(dir, UUID.randomUUID());
        player.stats(st);
        v.setFoodLevel(10);
        hold(TestRecipes.item("apple", 1));
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(40);
        assertEquals(1, st.get("minecraft:used:minecraft:apple"));

        hold(TestRecipes.item("milk_bucket", 1));
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(40);
        assertEquals(1, st.get("minecraft:used:minecraft:milk_bucket"));

        hold(TestRecipes.item("potion", 1));
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(40);
        assertEquals(1, st.get("minecraft:used:minecraft:potion"));

        v.setFoodLevel(10);
        hold(TestRecipes.item("honey_bottle", 1));
        assertTrue(Consuming.tryStart(player, 0, player.inventory().get(player.inventory().heldWindowSlot())));
        run(60);
        assertEquals(2, st.get("minecraft:used:minecraft:honey_bottle"), "바닐라 꿀 병은 음식 경로와 고유 경로에서 각각 한 번");

        v.setFoodLevel(10);
        hold(TestRecipes.item("apple", 1));
        Consuming.tryStart(player, 0, TestRecipes.item("apple", 1));
        hold(ItemStack.EMPTY);   // 도중 취소: 안 오른다
        run(40);
        assertEquals(1, st.get("minecraft:used:minecraft:apple"));
    }

    @Test
    void ominousBottleGivesBadOmenWithComponentAmplifierAndLeavesNoBottle() {
        ItemStack bottle = org.domi.furnace.server.item.ItemTools.with(TestRecipes.item("ominous_bottle", 2), "minecraft:ominous_bottle_amplifier", 3);
        hold(bottle);
        assertTrue(Consuming.tryStart(player, 0, bottle), "배고픔과 무관하게 마실 수 있다");
        run(31);
        assertFalse(v.hasEffect(MobEffect.BAD_OMEN), "32틱이 지나야 한다");
        run(1);
        MobEffectInstance bad = v.getEffect(MobEffect.BAD_OMEN);
        assertNotNull(bad);
        assertEquals(120000, bad.duration());
        assertEquals(3, bad.amplifier());
        assertFalse(bad.visible(), "입자는 없다");
        assertTrue(bad.showIcon(), "아이콘은 있다");
        assertFalse(bad.ambient());
        assertEquals(1, player.inventory().get(player.inventory().heldWindowSlot()).count(), "병은 하나 줄고 빈 병은 남지 않는다");
    }

    @Test
    void ominousBottleWithoutComponentIsAmplifierZeroAndCreativeKeepsIt() {
        v.setGameMode(GameMode.CREATIVE);
        ItemStack bottle = TestRecipes.item("ominous_bottle", 1);
        hold(bottle);
        assertTrue(Consuming.tryStart(player, 0, bottle));
        run(32);
        assertEquals(0, v.getEffect(MobEffect.BAD_OMEN).amplifier());
        assertEquals(1, player.inventory().get(player.inventory().heldWindowSlot()).count(), "창작 모드는 소비하지 않는다");
    }

    @Test
    void spyglassStartsUsingFor1200TicksAndCountsUse() {
        ItemStack glass = TestRecipes.item("spyglass", 1);
        hold(glass);
        assertTrue(Consuming.tryStart(player, 0, glass));
        assertEquals(0, player.usingHand);
        assertEquals(1200, player.usingTicks);
        assertEquals(1, (player.livingFlags() & 1), "사용 중 표시");
        run(1199);
        assertEquals(0, player.usingHand, "아직 쓰는 중");
        run(1);
        assertEquals(-1, player.usingHand, "1200틱 뒤 끝");
    }

    @Test
    void spyglassStopsWhenHandChanges() {
        ItemStack glass = TestRecipes.item("spyglass", 1);
        hold(glass);
        Consuming.tryStart(player, 0, glass);
        hold(ItemStack.EMPTY);
        run(1);
        assertEquals(-1, player.usingHand);
    }

    @Test
    void enderEyeUseMarkStaysUntilReleasedOrSwapped() {
        ItemStack eye = TestRecipes.item("ender_eye", 1);
        hold(eye);
        player.startUsingItem(0, 0);   // EnderEyeItem.use → startUsingItem(hand), 사용 시간 0
        run(50);
        assertEquals(0, player.usingHand, "놓기 전까지 사용 중 표시가 남는다");
        hold(TestRecipes.item("stone", 1));
        run(1);
        assertEquals(-1, player.usingHand, "다른 아이템으로 바뀌면 끝");
    }
}
