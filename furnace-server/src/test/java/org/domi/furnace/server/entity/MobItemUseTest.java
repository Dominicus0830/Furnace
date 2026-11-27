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
    private static Pig pig(BlockPlacementTest.TestLevel level) {
        var p = new Pig(level, level.nextEntityId());
        p.setPos(0.5, -60, 0.5);
        p.setNoAi(true);
        level.addFreshEntity(p);
        return p;
    }

    private static void drink(LivingEntity e, ItemStack potion) {
        e.setItemSlot(EquipmentSlot.MAINHAND, potion);
        e.startUsingItem(0);
        for (int i = 0; i < 32; i++) e.tick();
    }

    /**
     * PotionItem.finishUsingItem — Paper 1.21.1 패치(javap: lambda$finishUsingItem$0 이 즉시 효과를 List.add, 지속 효과만 addEffect, 그 뒤 반복문에서
     * applyInstantenousEffect). 목록 순서가 [즉시 회복, 생명력 강화] 여도 최대 체력이 먼저 오른 뒤 회복된다. 바닐라는 람다에서 바로 적용해 10 에서 막힌다(user-todo U3c).
     */
    @Test
    void potionAppliesLastingEffectsBeforeInstant() {
        var level = new BlockPlacementTest.TestLevel();
        var p = pig(level);
        p.setHealth(5.0F);
        var potion = org.domi.furnace.server.item.ItemTools.with(stack("potion", 1), "minecraft:potion_contents", java.util.Map.of("custom_effects", java.util.List.of(
                java.util.Map.of("id", "minecraft:instant_health", "amplifier", 1, "duration", 1),
                java.util.Map.of("id", "minecraft:health_boost", "amplifier", 1, "duration", 600))));
        drink(p, potion);
        assertEquals(18.0F, p.maxHealth(), "생명력 강화 II: 10 + 8");
        assertEquals(13.0F, p.health(), "Paper: 최대 체력이 먼저 18 로 오른 뒤 8 회복(바닐라 순서면 10 에서 막힌다)");
    }

    /** Paper 전용 분기(PotionItem.finishUsingItem): 마신 쪽이 죽었고 keepInventory 가 아니면 유리병을 떨어뜨리고 손을 비운다. */
    @Test
    void potionKillingDrinkerDropsBottle() {
        var level = new BlockPlacementTest.TestLevel();
        var p = pig(level);
        p.setHealth(1.0F);
        drink(p, org.domi.furnace.server.item.ItemTools.with(stack("potion", 1), "minecraft:potion_contents", java.util.Map.of("potion", "minecraft:harming")));
        assertTrue(p.health() <= 0.0F);
        assertTrue(p.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "손은 빈다");
        int bottle = ItemContext.id("minecraft:item", "glass_bottle"), bottles = 0;
        for (Entity x : level.entities().all()) if (x instanceof ItemEntity ie && ie.getItem().itemId() == bottle) bottles += ie.getItem().count();
        assertEquals(1, bottles, "유리병 하나를 떨어뜨린다");
    }
}
