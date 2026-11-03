// 바람 구슬·브리즈 바람 구슬 되치기(redirectable_projectile)와 브리즈의 투사체 튕김.
// 기대값은 바닐라 1.21.1 디컴파일(cnp.a deflect, cnq.b REVERSE·cnq.c AIM, coh.a 5틱, ckz.a 브리즈 튕김, cof 바람 구슬)을 손으로 계산했다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindChargeDeflectTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(float yaw, float pitch) {
        Player p = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.applyRotation(yaw, pitch, true);
        return p;
    }

    @Test
    void redirectableTagMembersArePickable() {
        var level = new BlockPlacementTest.TestLevel();
        assertTrue(new WindCharge(level, level.nextEntityId()).isPickable());
        assertTrue(new BreezeWindCharge(level, level.nextEntityId()).isPickable());
        assertTrue(new LargeFireball(level, level.nextEntityId()).isPickable());
        assertFalse(new SmallFireball(level, level.nextEntityId()).isPickable(), "작은 불덩이는 태그에 없다");
    }

    @Test
    void playerWindChargeCannotBeDeflectedForFirstFiveTicks() {
        var level = new BlockPlacementTest.TestLevel();
        var wc = new WindCharge(level, level.nextEntityId());
        wc.setDeltaMovement(3.0, 0.0, 0.0);
        var p = player(0.0F, 0.0F);
        assertFalse(wc.deflectAim(p, p, true), "noDeflectTicks 5");
        assertEquals(3.0, wc.vx(), 1e-9, "속도는 그대로");
        wc.noDeflectTicks(0);
        assertTrue(wc.deflectAim(p, p, true));
        assertEquals(0.0, wc.vx(), 1e-9);
        assertEquals(0.0, wc.vy(), 1e-9);
        assertEquals(1.0, wc.vz(), 1e-9, "시선(남쪽 +z) 단위 벡터");
        assertSame(p, wc.getOwner(), "주인이 되친 플레이어로");
    }

    @Test
    void breezeWindChargeIsDeflectedByAimImmediately() {
        var level = new BlockPlacementTest.TestLevel();
        var bw = new BreezeWindCharge(level, level.nextEntityId());
        bw.setDeltaMovement(0.0, 0.0, 2.0);
        var p = player(-90.0F, 0.0F);   // 동쪽(+x)
        assertTrue(bw.deflectAim(p, p, true));
        assertEquals(1.0, bw.vx(), 1e-6);
        assertEquals(0.0, bw.vz(), 1e-6);
    }

    @Test
    void breezeReversesArrowsButNotWindCharges() {
        var level = new BlockPlacementTest.TestLevel();
        var breeze = new Breeze(level, level.nextEntityId());
        var arrow = new Arrow(level, level.nextEntityId());
        assertEquals(Projectile.Deflection.BREEZE, breeze.projectileDeflection(arrow));
        assertEquals(Projectile.Deflection.NONE, breeze.projectileDeflection(new WindCharge(level, level.nextEntityId())));
        assertEquals(Projectile.Deflection.NONE, breeze.projectileDeflection(new BreezeWindCharge(level, level.nextEntityId())));

        arrow.setDeltaMovement(2.0, -1.0, 4.0);
        arrow.setYRot(30.0F);
        arrow.yRotO = 30.0F;
        assertTrue(arrow.hitTargetOrDeflectSelf(new Projectile.Hit(breeze, null, 0, 0.0, 0.0, 0.0)), "맞은 브리즈가 튕겨 낸다");
        assertEquals(-1.0, arrow.vx(), 1e-9);
        assertEquals(0.5, arrow.vy(), 1e-9);
        assertEquals(-2.0, arrow.vz(), 1e-9);
        float turn = arrow.yRot() - 30.0F;
        assertTrue(turn >= 170.0F && turn < 190.0F, "170 + nextFloat * 20");
        assertEquals(turn, arrow.yRotO - 30.0F, 1e-4, "이전 회전도 같은 만큼");
    }

    @Test
    void sameEntityIsNotDeflectedTwice() {
        var level = new BlockPlacementTest.TestLevel();
        var breeze = new Breeze(level, level.nextEntityId());
        var arrow = new Arrow(level, level.nextEntityId());
        arrow.setDeltaMovement(4.0, 0.0, 0.0);
        var hit = new Projectile.Hit(breeze, null, 0, 0.0, 0.0, 0.0);
        assertTrue(arrow.hitTargetOrDeflectSelf(hit));
        assertEquals(-2.0, arrow.vx(), 1e-9);
        assertTrue(arrow.hitTargetOrDeflectSelf(hit), "같은 브리즈에게는 다시 튕기지 않는다(lastDeflectedBy)");
        assertEquals(-2.0, arrow.vx(), 1e-9);
    }
}
