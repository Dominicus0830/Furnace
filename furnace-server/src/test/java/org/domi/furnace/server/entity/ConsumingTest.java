// 먹기·마시기: 황금 사과(효과 둘)·배고프지 않으면 못 먹음·우유(효과 전부 제거)·사용 도중 손이 바뀌면 취소.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class ConsumingTest {
    private final Player player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
    private final PlayerVitals v = player.vitals();

    ConsumingTest() throws Exception {
        assumeTrue(TestRecipes.manager() != null);
        v.env(new PlayerVitalsTest.TestEnv());
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

    @Test
    void fullHungerCannotEatNormalFood() {
        assertFalse(Consuming.tryStart(player, 0, TestRecipes.item("apple", 1)));
        assertTrue(Consuming.tryStart(player, 0, TestRecipes.item("golden_apple", 1)), "황금 사과는 언제나 먹는다");
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
}
