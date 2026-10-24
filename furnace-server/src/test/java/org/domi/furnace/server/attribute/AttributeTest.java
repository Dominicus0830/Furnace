// 속성: 수정자 연산 순서·범위 자르기·저장/불러오기·바뀐 것만 동기화·주민 기본값·밀려남 저항.
package org.domi.furnace.server.attribute;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AttributeTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void modifierOrderIsAddThenBaseMultiplierThenTotalMultiplier() {
        var map = new AttributeMap().add(Attribute.MAX_HEALTH, 20);
        var i = map.get(Attribute.MAX_HEALTH);
        i.addOrReplaceModifier(new AttributeModifier("a", 4, AttributeModifier.ADD_VALUE));
        i.addOrReplaceModifier(new AttributeModifier("b", 0.5, AttributeModifier.ADD_MULTIPLIED_BASE));
        i.addOrReplaceModifier(new AttributeModifier("c", 1.0, AttributeModifier.ADD_MULTIPLIED_TOTAL));
        assertEquals(((20 + 4) + (20 + 4) * 0.5) * 2.0, i.value(), 1e-9);
        i.removeModifier("c");
        assertEquals(36.0, i.value(), 1e-9);
    }

    @Test
    void valuesAreClampedToRange() {
        var map = new AttributeMap().add(Attribute.KNOCKBACK_RESISTANCE, 0);
        map.get(Attribute.KNOCKBACK_RESISTANCE).addOrReplaceModifier(new AttributeModifier("x", 5, AttributeModifier.ADD_VALUE));
        assertEquals(1.0, map.value(Attribute.KNOCKBACK_RESISTANCE));
        map.get(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(-3);
        map.get(Attribute.KNOCKBACK_RESISTANCE).removeModifiers();
        assertEquals(0.0, map.value(Attribute.KNOCKBACK_RESISTANCE));
    }

    @Test
    void onlyChangedSyncableAttributesAreReported() {
        var map = new AttributeMap().add(Attribute.MAX_HEALTH, 20).add(Attribute.FOLLOW_RANGE, 32);
        assertTrue(map.takeDirty().isEmpty());
        map.get(Attribute.FOLLOW_RANGE).setBaseValue(48);
        assertTrue(map.takeDirty().isEmpty(), "따라가기 범위는 서버 전용");
        map.get(Attribute.MAX_HEALTH).setBaseValue(30);
        assertEquals(1, map.takeDirty().size());
        assertTrue(map.takeDirty().isEmpty());
    }

    @Test
    void villagerAttributesRoundTrip() {
        var level = new BlockPlacementTest.TestLevel();
        var v = new Villager(level, level.nextEntityId());
        assertEquals(0.5, v.attributeValue(Attribute.MOVEMENT_SPEED), 1e-9);
        assertEquals(48.0, v.attributeValue(Attribute.FOLLOW_RANGE), 1e-9);
        v.attributes().get(Attribute.MAX_HEALTH).addOrReplaceModifier(new AttributeModifier("minecraft:test", 10, AttributeModifier.ADD_VALUE));
        assertEquals(30f, v.maxHealth());
        var back = new Villager(level, level.nextEntityId());
        back.load(v.saveWithoutId(new NbtCompound()));
        assertEquals(30f, back.maxHealth());
    }

    @Test
    void knockbackResistanceReducesPush() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new Villager(level, level.nextEntityId());
        var b = new Villager(level, level.nextEntityId());
        b.attributes().get(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        a.knockback(0.4, 1, 0);
        b.knockback(0.4, 1, 0);
        assertTrue(a.vx() < -0.3);
        assertEquals(0.0, b.vx(), 1e-9);
    }
}
