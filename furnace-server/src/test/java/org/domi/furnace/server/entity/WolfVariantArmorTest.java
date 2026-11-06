// 늑대 변종(바이옴별 선택, NBT variant)과 늑대 갑옷(피해 흡수, 금 단계). 기대값은 바닐라 1.21.1 디컴파일
// (cgj.a 변종 데이터 wolf_variant/*.json, cgh.f 의 actuallyHurt, bsp.b 의 Crackiness)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WolfVariantArmorTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static int idx(String name) {
        for (int i = 0; i < Wolf.VARIANTS.length; i++) if (Wolf.VARIANTS[i].equals(name)) return i;
        throw new AssertionError(name);
    }

    @Test
    void variantByBiome() {
        assertEquals(idx("pale"), Wolf.variantForBiome("minecraft:taiga"));
        assertEquals(idx("snowy"), Wolf.variantForBiome("minecraft:grove"));
        assertEquals(idx("black"), Wolf.variantForBiome("minecraft:old_growth_pine_taiga"));
        assertEquals(idx("ashen"), Wolf.variantForBiome("minecraft:snowy_taiga"));
        assertEquals(idx("woods"), Wolf.variantForBiome("minecraft:forest"));
        assertEquals(idx("chestnut"), Wolf.variantForBiome("minecraft:old_growth_spruce_taiga"));
        assertEquals(idx("spotted"), Wolf.variantForBiome("minecraft:savanna"));      // #is_savanna
        assertEquals(idx("rusty"), Wolf.variantForBiome("minecraft:jungle"));         // #is_jungle
        assertEquals(idx("striped"), Wolf.variantForBiome("minecraft:badlands"));     // #is_badlands
        assertEquals(idx("pale"), Wolf.variantForBiome("minecraft:plains"));          // 어디에도 없으면 pale
    }

    @Test
    void variantNbtRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        Wolf w = new Wolf(level, level.nextEntityId());
        assertEquals(idx("pale"), w.getVariant());   // 기본값
        w.setVariant(idx("rusty"));
        var tag = w.save();
        assertEquals("minecraft:rusty", tag.tags().get("variant"));
        Wolf r = (Wolf) Entities.load(level, tag);
        assertEquals(idx("rusty"), r.getVariant());
    }

    private static ItemStack armor(int damage) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", "wolf_armor"), 1);
        return damage == 0 ? s : ItemTools.withDamage(s, damage);
    }

    @Test
    void crackinessThresholds() {
        int max = ItemTools.maxDamage(armor(0));
        assertEquals(64, max);
        assertEquals(0, Wolf.crackiness(armor(3)));    // 61/64 = 0.953 >= 0.95
        assertEquals(1, Wolf.crackiness(armor(4)));    // 60/64 = 0.9375
        assertEquals(1, Wolf.crackiness(armor(19)));   // 45/64 = 0.703 >= 0.69
        assertEquals(2, Wolf.crackiness(armor(20)));   // 44/64 = 0.6875
        assertEquals(2, Wolf.crackiness(armor(43)));   // 21/64 = 0.328 >= 0.32
        assertEquals(3, Wolf.crackiness(armor(44)));   // 20/64 = 0.3125
        assertEquals(0, Wolf.crackiness(ItemStack.EMPTY));
    }

    @Test
    void armorAbsorbsAllDamageAndWears() {
        var level = new BlockPlacementTest.TestLevel();
        Wolf w = new Wolf(level, level.nextEntityId());
        level.addFreshEntity(w);
        w.setItemSlot(EquipmentSlot.BODY, armor(0));
        assertTrue(w.hasArmor());
        float hp = w.health();
        w.hurtBy(new Entity.Damage("mob_attack", false, false, false), 5.0F, null);
        assertEquals(hp, w.health(), 0.0F);   // 늑대는 피해를 받지 않는다
        assertEquals(5, ItemTools.damage(w.getItemBySlot(EquipmentSlot.BODY)));   // ceil(5) = 5
    }

    @Test
    void bypassWolfArmorDamageHurtsWolf() {
        var level = new BlockPlacementTest.TestLevel();
        Wolf w = new Wolf(level, level.nextEntityId());
        level.addFreshEntity(w);
        w.setItemSlot(EquipmentSlot.BODY, armor(0));
        float hp = w.health();
        w.hurtBy(new Entity.Damage("magic", false, false, false), 2.0F, null);   // #bypasses_wolf_armor
        assertTrue(w.health() < hp);
        assertEquals(0, ItemTools.damage(w.getItemBySlot(EquipmentSlot.BODY)));
    }

    @Test
    void fractionalDamageRoundsUp() {
        var level = new BlockPlacementTest.TestLevel();
        Wolf w = new Wolf(level, level.nextEntityId());
        level.addFreshEntity(w);
        w.setItemSlot(EquipmentSlot.BODY, armor(0));
        w.hurtBy(new Entity.Damage("mob_attack", false, false, false), 2.1F, null);
        assertEquals(3, ItemTools.damage(w.getItemBySlot(EquipmentSlot.BODY)));   // Mth.ceil(2.1) = 3
    }
}
