package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 몹의 아이템 사용(바닐라 LivingEntity.startUsingItem·updatingUsingItem·completeUsingItem, Item.getUseDuration·finishUsingItem). */
class MobItemUseTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    private static ItemStack stack(String item, int n) { return new ItemStack(ItemContext.id("minecraft:item", item), n); }

    private static Zombie zombie(BlockPlacementTest.TestLevel level) {
        var z = new Zombie(level, level.nextEntityId());
        z.setPos(0.5, -60, 0.5);
        z.setNoAi(true);
        level.addFreshEntity(z);
        return z;
    }

    @Test
    void useDurationsMatchVanillaItems() {
        assertEquals(32, MobItemUse.duration(stack("bread", 1)), "음식 기본 1.6초");
        assertEquals(16, MobItemUse.duration(stack("dried_kelp", 1)), "eat_seconds 0.8");
        assertEquals(32, MobItemUse.duration(stack("potion", 1)));
        assertEquals(32, MobItemUse.duration(stack("milk_bucket", 1)));
        assertEquals(32, MobItemUse.duration(stack("ominous_bottle", 1)));
        assertEquals(40, MobItemUse.duration(stack("honey_bottle", 1)));
        assertEquals(72000, MobItemUse.duration(stack("bow", 1)));
        assertEquals(28, MobItemUse.duration(stack("crossbow", 1)), "장전 25 + 3");
        assertEquals(1200, MobItemUse.duration(stack("spyglass", 1)));
        assertEquals(140, MobItemUse.duration(stack("goat_horn", 1)));
        assertEquals(0, MobItemUse.duration(stack("stone", 1)));
        assertEquals(0, MobItemUse.duration(ItemStack.EMPTY));
    }

    @Test
    void zombieEatsBreadInVanillaTime() {
        var level = new BlockPlacementTest.TestLevel();
        var z = zombie(level);
        z.setItemSlot(EquipmentSlot.MAINHAND, stack("bread", 2));
        z.startUsingItem(0);
        assertEquals(0, z.getTicksUsingItem());
        for (int i = 0; i < 31; i++) z.tick();
        assertTrue(z.isUsingItem());
        assertEquals(31, z.getTicksUsingItem());
        z.tick();
        assertFalse(z.isUsingItem(), "32틱째에 다 먹는다");
        assertEquals(1, z.getItemBySlot(EquipmentSlot.MAINHAND).count(), "하나 소모");
    }

    @Test
    void forcedEmptyHandKeepsUsingAndSwapStops() {
        var level = new BlockPlacementTest.TestLevel();
        var z = zombie(level);
        z.startUsingItem(1);
        assertFalse(z.isUsingItem(), "빈 손은 force 없이 시작하지 않는다");
        z.startUsingItem(1, true);
        for (int i = 0; i < 40; i++) z.tick();
        assertTrue(z.isUsingItem(), "바닐라: 빈 손끼리는 같은 아이템이라 계속 쓰는 중");
        z.setItemSlot(EquipmentSlot.OFFHAND, stack("bread", 1));
        z.tick();
        assertFalse(z.isUsingItem(), "손의 아이템이 바뀌면 멈춘다");

        z.setItemSlot(EquipmentSlot.MAINHAND, stack("milk_bucket", 1));
        z.startUsingItem(0);
        for (int i = 0; i < 32; i++) z.tick();
        assertFalse(z.isUsingItem());
        assertTrue(z.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "몹은 우유를 마셔도 양동이를 받지 않는다(하나 소모)");

        var potion = org.domi.furnace.server.item.ItemTools.with(stack("potion", 1), "minecraft:potion_contents", java.util.Map.of("potion", "minecraft:invisibility"));
        z.setItemSlot(EquipmentSlot.MAINHAND, potion);
        z.startUsingItem(0);
        for (int i = 0; i < 32; i++) z.tick();
        assertFalse(z.isUsingItem());
        assertEquals(potion, z.getItemBySlot(EquipmentSlot.MAINHAND), "몹이 마신 물약은 소모되지 않는다(PotionItem — 플레이어만 consume)");
    }
}
