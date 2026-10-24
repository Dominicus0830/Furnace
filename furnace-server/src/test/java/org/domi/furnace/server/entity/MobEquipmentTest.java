// 몹 장비: 장비 속성(방어구 기본 수정자·무기 공격력)이 틱에서 붙고 빠지며, 저장/불러오기, 드롭 확률 1 초과면 반드시 떨군다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.Test;

class MobEquipmentTest {

    private static void tick(Zombie z) {
        z.setOldPosAndRot();
        z.tickCount++;
        z.tick();
    }

    private Zombie zombie(BlockPlacementTest.TestLevel level) throws Exception {
        assumeTrue(TestRecipes.manager() != null);
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.setOnGround(true);
        return z;
    }

    @Test
    void equipmentAddsAndRemovesAttributeModifiers() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        var z = zombie(level);
        double base = z.attributeValue(Attribute.ARMOR);
        z.setItemSlot(EquipmentSlot.CHEST, TestRecipes.item("iron_chestplate", 1));
        z.setItemSlot(EquipmentSlot.MAINHAND, TestRecipes.item("iron_sword", 1));
        tick(z);
        assertEquals(base + 6.0, z.attributeValue(Attribute.ARMOR), 1e-9);
        assertEquals(3.0 + 5.0, z.attributeValue(Attribute.ATTACK_DAMAGE), 1e-9, "철 검 공격력 +5");
        z.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        tick(z);
        assertEquals(base, z.attributeValue(Attribute.ARMOR), 1e-9);
    }

    @Test
    void equipmentSavesAndLoads() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        var z = zombie(level);
        z.setItemSlot(EquipmentSlot.HEAD, TestRecipes.item("diamond_helmet", 1));
        z.setDropChance(EquipmentSlot.HEAD, 0.25F);
        var back = zombie(level);
        back.load(z.saveWithoutId(new NbtCompound()));
        assertEquals(z.getItemBySlot(EquipmentSlot.HEAD).itemId(), back.getItemBySlot(EquipmentSlot.HEAD).itemId());
        assertEquals(0.25F, back.getEquipmentDropChance(EquipmentSlot.HEAD));
        assertEquals(0.085F, back.getEquipmentDropChance(EquipmentSlot.FEET));
    }

    @Test
    void guaranteedDropWhenChanceAboveOne() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        var z = zombie(level);
        z.setItemSlot(EquipmentSlot.FEET, TestRecipes.item("iron_boots", 1));
        z.setDropChance(EquipmentSlot.FEET, 2.0F);
        z.hurt(new Entity.Damage("fall", false, false, false), 100f);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(-50, -80, -50, 50, 80, 50), e -> true);
        assertEquals(1, items.size());
        assertTrue(z.getItemBySlot(EquipmentSlot.FEET).isEmpty());
    }
}
