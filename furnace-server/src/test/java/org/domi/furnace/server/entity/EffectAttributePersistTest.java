// 바닐라 MobEffect.addAttributeModifiers 는 addPermanentModifier 라 효과가 단 속성 수정자는 속성 NBT 에 저장된다(R6).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EffectAttributePersistTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    @Test
    void effectModifierIsSavedAndNotTransient() {
        var level = new BlockPlacementTest.TestLevel();
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(cow);
        assertTrue(cow.addEffect(new MobEffectInstance(MobEffect.SPEED, 200, 1)));
        var inst = cow.attributes().get(Attribute.MOVEMENT_SPEED);
        assertEquals(1, inst.modifiers().size());
        String id = inst.modifiers().keySet().iterator().next();
        assertFalse(inst.isTransient(id), "바닐라 addPermanentModifier");
        NbtList saved = cow.attributes().save();
        boolean found = false;
        for (int k = 0; k < saved.size(); k++) {
            var t = (NbtCompound) saved.get(k);
            if (Attribute.MOVEMENT_SPEED.name().equals(t.tags().get("id")) && t.tags().get("modifiers") instanceof NbtList mods && mods.size() == 1) found = true;
        }
        assertTrue(found, "속성 NBT 에 효과 수정자가 들어 있다");
    }
}
