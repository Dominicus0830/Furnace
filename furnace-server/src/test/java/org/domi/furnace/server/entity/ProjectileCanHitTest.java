// 투사체 canHitEntity(바닐라·Paper 재정의 5곳)와 관통 화살이 한 틱에 여러 엔티티를 꿰뚫는 바닐라 고리.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.api.GameVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProjectileCanHitTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    private static Zombie zombie(SkeletonCombatTest.ArenaLevel level, double x) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(x, -60, 0.5, 0, 0);
        level.addFreshEntity(z);
        return z;
    }

    /** 바닐라 AbstractArrow.tick: 튕겨 나갈 때만 멈추고, 관통 화살은 꿰뚫은 엔티티를 빼고(canHitEntity) 같은 틱에 다음 엔티티를 찾는다. */
    @Test
    void piercingArrowHitsEveryEntityOnItsPathInOneTick() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var z1 = zombie(level, 2.0);
        var z2 = zombie(level, 3.5);
        var a = new Arrow(level, level.nextEntityId(), 0.5, -59.0, 0.5);
        a.setPierceLevel((byte) 1);
        a.setDeltaMovement(4.0, 0, 0);
        level.addFreshEntity(a);
        a.tick();
        assertTrue(z1.health() < z1.maxHealth(), "첫 좀비");
        assertTrue(z2.health() < z2.maxHealth(), "같은 틱에 둘째 좀비도");
        assertFalse(a.canHitEntity(z1), "꿰뚫은 엔티티는 다시 못 맞힌다");
        assertFalse(a.canHitEntity(z2));
    }

    @Test
    void nonPiercingArrowStopsAtTheFirst() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var z1 = zombie(level, 2.0);
        var z2 = zombie(level, 3.5);
        var a = new Arrow(level, level.nextEntityId(), 0.5, -59.0, 0.5);
        a.setDeltaMovement(4.0, 0, 0);
        level.addFreshEntity(a);
        a.tick();
        assertTrue(z1.health() < z1.maxHealth());
        assertEquals(z2.maxHealth(), z2.health(), "관통이 없으면 첫 엔티티에서 멈춘다");
    }

    @Test
    void subclassRulesFollowVanilla() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var z = zombie(level, 2.0);
        var item = new ItemEntity(level, level.nextEntityId(), 1, -60, 1, new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "stone"), 1));
        level.addFreshEntity(item);
        var crystal = new EndCrystal(level, level.nextEntityId());
        crystal.moveTo(3, -60, 3, 0, 0);
        level.addFreshEntity(crystal);

        var snowball = new Snowball(level, level.nextEntityId());
        var hook = new FishingHook(level, level.nextEntityId());
        var fireball = new SmallFireball(level, level.nextEntityId());
        var bullet = new ShulkerBullet(level, level.nextEntityId());
        var wind = new WindCharge(level, level.nextEntityId());

        assertTrue(snowball.canHitEntity(z));
        assertFalse(snowball.canHitEntity(item), "아이템은 맞출 수 없는 엔티티");
        assertTrue(hook.canHitEntity(item), "낚시찌는 살아 있는 아이템도");
        assertTrue(snowball.canHitEntity(crystal));
        assertFalse(wind.canHitEntity(crystal), "바람 돌격은 엔드 수정 제외");
        assertFalse(wind.canHitEntity(new WindCharge(level, level.nextEntityId())), "바람 돌격끼리 제외");
        assertTrue(fireball.canHitEntity(z));
        z.noPhysics = true;
        assertFalse(fireball.canHitEntity(z), "불덩이류는 물리 없는 대상 제외");
        assertFalse(bullet.canHitEntity(z), "셜커 탄환도");
        assertTrue(snowball.canHitEntity(z), "눈덩이는 noPhysics 를 보지 않는다");
    }
}
