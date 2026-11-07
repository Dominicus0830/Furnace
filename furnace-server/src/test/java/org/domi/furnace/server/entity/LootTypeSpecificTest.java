// 살아 있는 엔티티가 전리품 EntityPredicate.type_specific 에 내주는 값(변종 이름·크기 등) 시험.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.loot.LootEntity;
import org.junit.jupiter.api.Test;

class LootTypeSpecificTest {

    private static final BlockPlacementTest.TestLevel LEVEL = new BlockPlacementTest.TestLevel();
    private static int ids = 9000;

    private static int id() { return ids++; }

    @Test
    void catVariantIsRegistryId() {
        Cat cat = new Cat(LEVEL, id());
        cat.setVariant(0);
        LootEntity e = LootEntities.of(cat);
        assertEquals("minecraft:tabby", e.typeSpecific("variant"));
        cat.setVariant(5);
        assertEquals("minecraft:calico", LootEntities.of(cat).typeSpecific("variant"));
    }

    @Test
    void rabbitVariantIsEnumName() {
        Rabbit r = new Rabbit(LEVEL, id());
        r.setVariant(4);
        assertEquals("gold", LootEntities.of(r).typeSpecific("variant"));
        r.setVariant(99);
        assertEquals("evil", LootEntities.of(r).typeSpecific("variant"));
    }

    @Test
    void slimeSizeAndNonVariantEntities() {
        Slime s = new Slime(LEVEL, id());
        s.setSize(3, true);
        assertEquals(3, LootEntities.of(s).typeSpecific("size"));
        assertNull(LootEntities.of(s).typeSpecific("variant"));
        assertNull(LootEntities.of(s).typeSpecific("is_captain"));
        assertNull(LootEntities.of(new Cat(LEVEL, id())).typeSpecific("size"));
    }
}
