// 길들인 펫 사망 메시지의 재료: 몹 전투 기록(CombatTracker)이 원인 엔티티 이름과 함께 쌓이는가.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PetDeathMessageTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage MOB = new Entity.Damage("mob_attack", false, false, false);

    @Test
    void combatTrackerKeepsAttackerForDeathMessage() {
        var level = new BlockPlacementTest.TestLevel();
        var wolf = (Wolf) Entities.create(level, "minecraft:wolf");
        wolf.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(wolf);
        var zombie = (Zombie) Entities.create(level, "minecraft:zombie");
        zombie.moveTo(2.5, -60, 0.5, 0, 0);
        level.addFreshEntity(zombie);

        assertTrue(wolf.hurtByVia(MOB, 1000.0F, zombie, zombie));
        assertTrue(wolf.isDeadOrDying());
        Text msg = wolf.getCombatTracker().getDeathMessage(wolf.displayName());
        assertEquals("death.attack.mob", msg.translationKey());
        var args = ((org.domi.furnace.api.text.Content.Translatable) msg.content()).args();
        assertEquals(2, args.size());
        assertEquals(zombie.displayName().translationKey(), ((Text) args.get(1)).translationKey());
    }

    @Test
    void noRecordMeansGenericMessage() {
        var level = new BlockPlacementTest.TestLevel();
        var cat = (Cat) Entities.create(level, "minecraft:cat");
        assertEquals("death.attack.generic", cat.getCombatTracker().getDeathMessage(cat.displayName()).translationKey());
    }
}
