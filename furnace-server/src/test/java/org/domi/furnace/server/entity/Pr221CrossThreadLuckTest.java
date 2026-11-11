// PR #221 검토 재현: PlayerAttributes.value(p, LUCK) 는 몹 사망 전리품(ServerCore.entityDrops → lootEntity(lastHurtByPlayer).luck())
// 처럼 플레이어 주인이 아닌 region 스레드에서도 불린다. 이제 맵 AttributeInstance.modifiers()(LinkedHashMap)와 managedModifiers(HashMap)를
// 잠금 없이 읽으므로, 주인 스레드가 같은 때 수정자를 바꾸면(효과 동기·/attribute) ConcurrentModificationException 이 날 수 있다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.junit.jupiter.api.Test;

class Pr221CrossThreadLuckTest {
    @Test
    void otherRegionLuckReadSurvivesOwnerModifierChanges() throws Exception {
        var env = new PlayerVitalsTest.TestEnv();
        var p = new Player(5, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        var luck = p.attributes().get(Attribute.LUCK);
        for (int i = 0; i < 8; i++) luck.addOrReplaceModifier(new AttributeModifier("test:m" + i, 0.1, AttributeModifier.ADD_VALUE));   // /attribute 로 단 것
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread mobRegion = new Thread(() -> {   // 몹이 죽는 다른 region: 마지막 때린 플레이어의 luck
            try {
                while (!stop.get()) PlayerAttributes.value(p, Attribute.LUCK);
            } catch (Throwable t) { err.set(t); }
        });
        mobRegion.start();
        long end = System.nanoTime() + 3_000_000_000L;
        int i = 0;
        while (System.nanoTime() < end && err.get() == null) {   // 플레이어 주인 region: 행운 효과 붙였다 떼기 + /attribute 수정자 바꾸기
            p.vitals().addEffect(new MobEffectInstance(MobEffect.LUCK, 600, 0));
            p.syncAttributes();
            p.vitals().removeEffect(MobEffect.LUCK);
            p.syncAttributes();
            luck.removeModifier("test:m" + (i % 8));
            luck.addOrReplaceModifier(new AttributeModifier("test:m" + (i++ % 8), 0.1, AttributeModifier.ADD_VALUE));
        }
        stop.set(true);
        mobRegion.join();
        assertNull(err.get(), () -> "다른 스레드 luck 읽기 실패: " + err.get());
    }
}
