// 큰 불덩이 되치기: 플레이어가 쳐서 시선 방향(단위 벡터)으로 돌리고 주인을 바꾼다. 되튄 불덩이에 맞은 가스트는 피해 1000.
// 기대값은 바닐라 1.21.1 디컴파일(cnq.c AIM_DEFLECT, cne.b onDeflection, cjx.a/g 가스트 hurt, cmx.e 플레이어 공격)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FireballDeflectTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(float yaw, float pitch) {
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.applyRotation(yaw, pitch, true);
        return p;
    }

    @Test
    void aimDeflectSetsUnitLookMotionOwnerAndAcceleration() {
        var level = new BlockPlacementTest.TestLevel();
        Ghast ghast = new Ghast(level, level.nextEntityId());
        LargeFireball fb = new LargeFireball(level, level.nextEntityId());
        fb.setOwner(ghast);
        fb.setDeltaMovement(3.0, 0.0, 0.0);
        fb.accelerationPower = 0.5;
        Player p = player(0.0F, 0.0F);   // 남쪽(+z) 수평
        assertTrue(fb.isPickable());
        assertTrue(fb.deflectAim(p, p, true));
        assertEquals(0.0, fb.vx(), 1e-9);
        assertEquals(0.0, fb.vy(), 1e-9);
        assertEquals(1.0, fb.vz(), 1e-9);
        assertSame(p, fb.getOwner());
        assertEquals(0.1, fb.accelerationPower, 0.0);   // 쳐서 튕기면 처음 가속
        assertTrue(fb.hasImpulse);
    }

    @Test
    void aimDeflectYaw90FacesWestAndPitchTilts() {
        var level = new BlockPlacementTest.TestLevel();
        LargeFireball fb = new LargeFireball(level, level.nextEntityId());
        Player p = player(90.0F, -90.0F);   // 위로 보면 +y
        fb.deflectAim(p, p, true);
        assertEquals(1.0, fb.vy(), 1e-6);
        fb = new LargeFireball(level, level.nextEntityId());
        p = player(90.0F, 0.0F);
        fb.deflectAim(p, p, true);
        assertEquals(-1.0, fb.vx(), 1e-6);   // yaw 90 = 서쪽(-x)
    }

    @Test
    void notByAttackHalvesAcceleration() {
        var level = new BlockPlacementTest.TestLevel();
        LargeFireball fb = new LargeFireball(level, level.nextEntityId());
        fb.accelerationPower = 0.4;
        Player p = player(0.0F, 0.0F);
        fb.deflectAim(p, p, false);
        assertEquals(0.2, fb.accelerationPower, 1e-12);
    }

    @Test
    void ghastDiesFromDeflectedFireball() {
        var level = new BlockPlacementTest.TestLevel();
        Ghast ghast = new Ghast(level, level.nextEntityId());
        level.addFreshEntity(ghast);
        LargeFireball fb = new LargeFireball(level, level.nextEntityId());
        Player p = player(0.0F, 0.0F);
        fb.setOwner(p);
        assertEquals(10.0F, ghast.health(), 0.0F);
        ghast.hurtByVia(new Entity.Damage("fireball", true, false, false), 6.0F, p, fb);
        assertTrue(ghast.isDeadOrDying());   // 피해 1000
    }

    @Test
    void ghastTakesNormalDamageFromOtherFireballOwners() {
        var level = new BlockPlacementTest.TestLevel();
        Ghast ghast = new Ghast(level, level.nextEntityId());
        level.addFreshEntity(ghast);
        Ghast other = new Ghast(level, level.nextEntityId());
        LargeFireball fb = new LargeFireball(level, level.nextEntityId());
        fb.setOwner(other);
        ghast.hurtByVia(new Entity.Damage("fireball", true, false, false), 6.0F, other, fb);
        assertFalse(ghast.isDeadOrDying());
    }
}
