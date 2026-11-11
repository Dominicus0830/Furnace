// 몹 쪽 게임 이벤트 방출: 바닐라 호출 지점과 같은 종류·원인 엔티티·위치로 level.gameEvent 가 불리는가.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobGameEventsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    record Ev(GameEvent event, Vec3 pos, GameEventContext ctx) {}

    static final class RecLevel extends BlockPlacementTest.TestLevel {
        final List<Ev> events = new ArrayList<>();

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, GameEventContext ctx) {
            events.add(new Ev(event, pos, ctx));
            super.gameEvent(event, pos, ctx);
        }

        List<Ev> of(GameEvent e) { return events.stream().filter(x -> x.event == e).toList(); }
    }

    private static final Entity.Damage MOB = new Entity.Damage("mob_attack", false, false, false);

    private static <T extends Entity> T spawn(RecLevel level, String type, double x, double z) {
        @SuppressWarnings("unchecked") T e = (T) Entities.create(level, type);
        e.moveTo(x, -60, z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    @Test
    void entityDamageEmittedOnlyWhenHealthLost() {
        var level = new RecLevel();
        Zombie z = spawn(level, "minecraft:zombie", 0.5, 0.5);
        level.events.clear();
        assertTrue(z.hurtBy(MOB, 3.0F, null));
        var evs = level.of(GameEvent.ENTITY_DAMAGE);
        assertEquals(1, evs.size());
        assertSame(z, evs.get(0).ctx.sourceEntity());
        assertEquals(z.x(), evs.get(0).pos.x());
        // 무적 시간 안의 더 약한 피해는 실패 → 이벤트 없음
        assertFalse(z.hurtBy(MOB, 1.0F, null));
        assertEquals(1, level.of(GameEvent.ENTITY_DAMAGE).size());
    }

    @Test
    void explodeEmittedAtCenterWithSource() {
        var level = new RecLevel();
        Creeper c = spawn(level, "minecraft:creeper", 0.5, 0.5);
        level.events.clear();
        level.explode(c, 4.5, -60.0, 4.5, 3.0F, false, level.destroyType("MOB"));
        var evs = level.of(GameEvent.EXPLODE);
        assertEquals(1, evs.size());
        assertSame(c, evs.get(0).ctx.sourceEntity());
        assertEquals(4.5, evs.get(0).pos.x());
        assertEquals(-60.0, evs.get(0).pos.y());
    }

    @Test
    void creeperFuseEmittedOnceAtSwellStart() {
        var level = new RecLevel();
        Creeper c = spawn(level, "minecraft:creeper", 0.5, 0.5);
        c.setSwellDir(1);
        level.events.clear();
        c.tick();
        c.tick();
        assertEquals(1, level.of(GameEvent.PRIME_FUSE).size());
        assertSame(c, level.of(GameEvent.PRIME_FUSE).get(0).ctx.sourceEntity());
    }

    @Test
    void projectileShootOnFirstTickWithOwnerAndLandOnEntity() {
        var level = new RecLevel();
        Zombie owner = spawn(level, "minecraft:zombie", 0.5, 0.5);
        Zombie victim = spawn(level, "minecraft:zombie", 6.5, 0.5);
        var a = new Arrow(level, level.nextEntityId(), 2.5, -59, 0.5);
        a.setOwner(owner);
        level.addFreshEntity(a);
        level.events.clear();
        a.tick();
        a.tick();
        var shoot = level.of(GameEvent.PROJECTILE_SHOOT);
        assertEquals(1, shoot.size());
        assertSame(owner, shoot.get(0).ctx.sourceEntity());
        level.events.clear();
        a.onHit(new Projectile.Hit(victim, null, 0, 6.5, -59, 0.5));
        var land = level.of(GameEvent.PROJECTILE_LAND);
        assertEquals(1, land.size());
        assertSame(a, land.get(0).ctx.sourceEntity());
        assertEquals(6.5, land.get(0).pos.x());
    }

    @Test
    void endermanTeleportEmittedAtOldPosition() {
        var level = new RecLevel();
        Enderman e = spawn(level, "minecraft:enderman", 0.5, 0.5);
        Zombie other = spawn(level, "minecraft:zombie", 2.5, 0.5);
        double ox = e.x(), oy = e.y(), oz = e.z();
        level.events.clear();
        boolean ok = false;
        for (int i = 0; i < 50 && !ok; i++) { ox = e.x(); oy = e.y(); oz = e.z(); level.events.clear(); ok = e.teleportTowardsEntity(other); }
        assertTrue(ok);
        var evs = level.of(GameEvent.TELEPORT);
        assertEquals(1, evs.size());
        assertSame(e, evs.get(0).ctx.sourceEntity());
        assertEquals(ox, evs.get(0).pos.x());
        assertEquals(oy, evs.get(0).pos.y());
        assertEquals(oz, evs.get(0).pos.z());
    }

    /** 흡수 체력이 피해를 다 받으면 체력·ENTITY_DAMAGE 없음, 넘치는 만큼만 체력과 이벤트(btn.f). */
    @Test
    void entityDamageUsesValueAfterAbsorption() {
        var level = new RecLevel();
        Zombie z = spawn(level, "minecraft:zombie", 0.5, 0.5);
        var bypass = new Entity.Damage("starve", false, false, true);
        z.absorptionAmount = 5.0F;
        float hp = z.health();
        level.events.clear();
        z.hurtBy(bypass, 3.0F, null);
        assertEquals(hp, z.health());
        assertEquals(2.0F, z.absorptionAmount);
        assertTrue(level.of(GameEvent.ENTITY_DAMAGE).isEmpty());
        z.invulnerableTime = 0;   // 무적 시간 창(바닐라는 bypasses_cooldown 태그만 건너뛴다)을 비워 두 번째도 새 피해로
        z.hurtBy(bypass, 4.0F, null);
        assertEquals(hp - 2.0F, z.health());
        assertEquals(0.0F, z.absorptionAmount);
        assertEquals(1, level.of(GameEvent.ENTITY_DAMAGE).size());
    }
}
