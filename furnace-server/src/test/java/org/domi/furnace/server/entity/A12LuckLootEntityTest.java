// A12-09: 플레이어의 전리품 보기(LootEntity)가 행운 속성 값을 돌려준다(상자·몹 처치 전리품의 luck).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class A12LuckLootEntityTest {
    @Test
    void luckEffectRaisesLootEntityLuck() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(TestRecipes.manager() != null);
        Player p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        assertEquals(0.0F, LootEntities.of(p).luck());
        p.vitals().addEffect(new MobEffectInstance(MobEffect.LUCK, 600, 1));   // 행운 II: +2
        assertEquals(2.0F, LootEntities.of(p).luck());
    }
}
