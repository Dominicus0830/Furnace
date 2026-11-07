// M18: 몹 쇠뇌(Crossbow) — 손에 든 화살·폭죽 로켓 장전, 다중 발사 퍼짐, 무한, 일반 화살 기본.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossbowMobTest {

    @BeforeEach
    void install() throws Exception {
        assumeTrue(org.domi.furnace.server.recipe.TestRecipes.manager() != null);
    }

    private static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", name), n); }

    private static ItemStack enchanted(String name, String ench, int lvl) {
        return EnchantTools.update(stack(name, 1), m -> m.put("minecraft:" + ench, lvl));
    }

    private static Pillager shooter(BlockPlacementTest.TestLevel level, ItemStack crossbow) {
        var p = new Pillager(level, level.nextEntityId());
        p.moveTo(0.5, -60, 0.5, 0, 0);
        p.setItemSlot(EquipmentSlot.MAINHAND, crossbow);
        level.addFreshEntity(p);
        return p;
    }

    private static List<Entity> shots(BlockPlacementTest.TestLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof AbstractArrow || e instanceof FireworkRocketEntity).toList();
    }

    @Test
    void emptyHandsLoadOneDefaultArrow() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, stack("crossbow", 1));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 25);
        assertTrue(Crossbow.isCharged(p.getItemBySlot(EquipmentSlot.MAINHAND)));
        Crossbow.performShooting(p, 0, 1.6F, 0.0F, null);
        assertFalse(Crossbow.isCharged(p.getItemBySlot(EquipmentSlot.MAINHAND)));
        assertEquals(1, shots(level).size());
        assertTrue(shots(level).get(0) instanceof Arrow);
    }

    @Test
    void tooShortChargeDoesNotLoad() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, stack("crossbow", 1));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 24);
        assertFalse(Crossbow.isCharged(p.getItemBySlot(EquipmentSlot.MAINHAND)));
    }

    @Test
    void heldTippedArrowsAreUsedAndConsumed() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, stack("crossbow", 1));
        p.setItemSlot(EquipmentSlot.OFFHAND, stack("spectral_arrow", 3));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 25);
        assertEquals(2, p.getItemBySlot(EquipmentSlot.OFFHAND).count());
        Crossbow.performShooting(p, 0, 1.6F, 0.0F, null);
        assertTrue(shots(level).get(0) instanceof SpectralArrow);
    }

    @Test
    void infinityLeavesTheArrowAndMakesItUnpickable() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, enchanted("crossbow", "infinity", 1));
        p.setItemSlot(EquipmentSlot.OFFHAND, stack("arrow", 3));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 25);
        assertEquals(3, p.getItemBySlot(EquipmentSlot.OFFHAND).count());
        Crossbow.performShooting(p, 0, 1.6F, 0.0F, null);
        assertEquals(AbstractArrow.Pickup.CREATIVE_ONLY, ((AbstractArrow) shots(level).get(0)).pickup);
    }

    @Test
    void multishotFansOutThreeArrowsAndSpendsOne() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, enchanted("crossbow", "multishot", 1));
        p.setItemSlot(EquipmentSlot.OFFHAND, stack("arrow", 5));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 25);
        assertEquals(4, p.getItemBySlot(EquipmentSlot.OFFHAND).count(), "첫 발만 진짜 화살을 쓴다");
        Crossbow.performShooting(p, 0, 1.6F, 0.0F, null);
        var s = shots(level);
        assertEquals(3, s.size());
        // 퍼짐 10°: 세 방향이 서로 다르다(가운데 + 양쪽)
        var dirs = s.stream().map(e -> String.format("%.4f", Math.atan2(e.vx(), e.vz()))).distinct().toList();
        assertEquals(3, dirs.size(), dirs.toString());
        assertEquals(2, s.stream().filter(e -> ((AbstractArrow) e).pickup == AbstractArrow.Pickup.CREATIVE_ONLY).count(), "나머지 둘은 줍기 불가");
    }

    @Test
    void fireworkRocketIsLoadedAndFired() {
        var level = new BlockPlacementTest.TestLevel();
        var p = shooter(level, stack("crossbow", 1));
        p.setItemSlot(EquipmentSlot.OFFHAND, stack("firework_rocket", 2));
        Crossbow.releaseUsing(p, EquipmentSlot.MAINHAND, 25);
        assertEquals(1, p.getItemBySlot(EquipmentSlot.OFFHAND).count());
        Crossbow.performShooting(p, 0, 1.6F, 0.0F, null);
        assertTrue(shots(level).get(0) instanceof FireworkRocketEntity);
        assertEquals("minecraft:crossbow", ItemTools.itemName(p.getItemBySlot(EquipmentSlot.MAINHAND)));
    }
}
