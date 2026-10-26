// FollowMobGoal.canUse: 투명한 몹은 따라가지 않는다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.entity.ai.goal.FollowMobGoal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FollowMobGoalTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void skipsInvisibleMobs() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var parrot = new Parrot(level, level.nextEntityId());
        parrot.moveTo(0.5, -55, 0.5, 0, 0);
        level.addFreshEntity(parrot);
        var chicken = new Chicken(level, level.nextEntityId());
        chicken.moveTo(2.5, -55, 0.5, 0, 0);
        level.addFreshEntity(chicken);
        var goal = new FollowMobGoal(parrot, 1.0, 3.0F, 7.0F);
        assertTrue(goal.canUse());
        chicken.setInvisible(true);
        assertFalse(goal.canUse());
    }
}
