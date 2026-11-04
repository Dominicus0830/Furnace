// 플레이어 속성 저장·복원(바닐라 LivingEntity "attributes"): 기본값과 /attribute 수정자는 남고, 장비·효과·달리기 수정자(transient)는 빠진다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.junit.jupiter.api.Test;

class PlayerAttributeSaveTest {

    private static Player player(PlayerVitalsTest.TestEnv env) {
        var p = new Player(5, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.vitals().env(env);
        return p;
    }

    private static NbtCompound entry(NbtList list, Attribute a) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof NbtCompound t && a.name().equals(t.tags().get("id"))) return t;
        }
        return null;
    }

    private static boolean hasModifier(NbtCompound entry, String id) {
        if (!(entry.tags().get("modifiers") instanceof NbtList mods)) return false;
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i) instanceof NbtCompound m && id.equals(m.tags().get("id"))) return true;
        }
        return false;
    }

    @Test
    void savesBaseAndCommandModifiersButNotTransientOnes() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.attributes().get(Attribute.MAX_HEALTH).setBaseValue(40.0);
        p.attributes().get(Attribute.ATTACK_DAMAGE).addOrReplaceModifier(new AttributeModifier("test:cmd", 2.5, AttributeModifier.ADD_VALUE));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.SPEED, 600, 1));
        p.sprinting(true);
        p.syncAttributes();

        NbtCompound out = new NbtCompound();
        p.saveAttributes(out);
        NbtList list = (NbtList) out.tags().get("attributes");
        assertNotNull(list);
        assertEquals(40.0, ((Number) entry(list, Attribute.MAX_HEALTH).tags().get("base")).doubleValue());
        assertTrue(hasModifier(entry(list, Attribute.ATTACK_DAMAGE), "test:cmd"));
        NbtCompound speed = entry(list, Attribute.MOVEMENT_SPEED);
        assertFalse(hasModifier(speed, "minecraft:sprinting"), "달리기는 transient");
        assertFalse(speed.tags().containsKey("modifiers"), "효과 수정자도 transient");
    }

    @Test
    void notSyncedYetLeavesFileValueAlone() {
        var p = player(new PlayerVitalsTest.TestEnv());
        NbtCompound out = new NbtCompound();
        p.saveAttributes(out);
        assertFalse(out.tags().containsKey("attributes"));
    }

    @Test
    void reloadRestoresBaseModifiersAndHealthAboveTwenty() {
        var env = new PlayerVitalsTest.TestEnv();
        var a = player(env);
        a.attributes().get(Attribute.MAX_HEALTH).setBaseValue(40.0);
        a.attributes().get(Attribute.SCALE).addOrReplaceModifier(new AttributeModifier("test:big", 1.0, AttributeModifier.ADD_VALUE));
        a.syncAttributes();
        NbtCompound saved = new NbtCompound();
        a.saveAttributes(saved);
        saved.putFloat("Health", 35.0f);

        var b = player(new PlayerVitalsTest.TestEnv());
        b.loadAttributes(saved);
        b.vitals().load(saved);   // 접속 순서와 같다: 속성 → 체력
        assertEquals(40.0, b.attributes().baseValue(Attribute.MAX_HEALTH));
        assertEquals(2.0, b.attributes().value(Attribute.SCALE));
        assertEquals(35.0f, b.vitals().health(), "최대 체력 40 이 먼저 들어가 20 으로 잘리지 않는다");
        b.syncAttributes();
        NbtCompound again = new NbtCompound();
        b.saveAttributes(again);
        assertTrue(hasModifier(entry((NbtList) again.tags().get("attributes"), Attribute.SCALE), "test:big"), "다시 저장해도 남는다");
    }
}
