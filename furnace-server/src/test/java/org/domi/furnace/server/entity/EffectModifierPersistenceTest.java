// 상태 효과 속성 수정자 저장 회귀 시험(O05·O06 검토 R6): 바닐라 MobEffect.addAttributeModifiers 는 addPermanentModifier 라
// 효과 수정자가 속성 NBT 에 저장되고, LivingEntity.readAdditionalSaveData 는 속성 load 만 하고 효과를 다시 달지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.Test;

class EffectModifierPersistenceTest {

    private static final double BASE = (double) 0.2F;   // 소 기본 이동 속도(float 상수)

    /** 속성 NBT 에 저장된 수정자 id 들. */
    private static java.util.List<String> savedModifierIds(Cow c) {
        var out = new java.util.ArrayList<String>();
        var list = c.attributes().save();
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k) instanceof NbtCompound t && t.tags().get("modifiers") instanceof NbtList mods) {
                for (int j = 0; j < mods.size(); j++) out.add((String) ((NbtCompound) mods.get(j)).tags().get("id"));
            }
        }
        return out;
    }

    private static Cow cow(BlockPlacementTest.TestLevel level) {
        return new Cow(level, level.nextEntityId());
    }

    @Test
    void speedEffectModifierSurvivesSaveAndLoad() {
        var level = new BlockPlacementTest.TestLevel();
        var a = cow(level);
        a.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 1));
        double boosted = a.attributeValue(Attribute.MOVEMENT_SPEED);
        assertEquals(BASE * (1 + 0.2 * 2), boosted, 1e-6);

        var b = cow(level);
        b.load(a.save());
        assertTrue(b.hasEffect(MobEffect.SPEED));
        assertEquals(boosted, b.attributeValue(Attribute.MOVEMENT_SPEED), 1e-9, "다시 불러온 몹의 속도 수정자");
    }

    @Test
    void savedModifierListsTheEffectModifier() {
        var level = new BlockPlacementTest.TestLevel();
        var a = cow(level);
        a.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 0));
        var inst = a.attributes().get(Attribute.MOVEMENT_SPEED);
        assertFalse(inst.isTransient("minecraft:effect.speed"));
        assertTrue(savedModifierIds(a).contains("minecraft:effect.speed"));
    }

    @Test
    void removingTheEffectAfterReloadRemovesTheModifier() {
        var level = new BlockPlacementTest.TestLevel();
        var a = cow(level);
        a.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 0));
        var b = cow(level);
        b.load(a.save());
        assertTrue(b.removeEffect(MobEffect.SPEED));
        assertEquals(BASE, b.attributeValue(Attribute.MOVEMENT_SPEED), 1e-9);
        assertFalse(savedModifierIds(b).contains("minecraft:effect.speed"));
    }

    @Test
    void reapplyingDoesNotStackAndRefreshesAmplifier() {
        var level = new BlockPlacementTest.TestLevel();
        var a = cow(level);
        a.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 0));
        a.addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 2));
        assertEquals(BASE * (1 + 0.2 * 3), a.attributeValue(Attribute.MOVEMENT_SPEED), 1e-6);
    }
}
