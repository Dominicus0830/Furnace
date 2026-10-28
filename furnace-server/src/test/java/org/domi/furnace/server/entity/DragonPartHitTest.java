// 드래곤 파트 판정: 투사체가 파트에 맞고(본체는 isPickable=false), 머리는 배율 없음·그 밖은 /4+1, 앉은 단계 화살 무시, 폭발은 본체+파트 다중 피해.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DragonPartHitTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Player player(DimensionTestLevel level, double x, double y, double z) {
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, y, z, 0, 0, true);
        level.player = p;
        return p;
    }

    /** 파트 위치를 잡으려고 한 틱 돈다(맴돌기 단계, 제자리). */
    private static EnderDragon dragon(DimensionTestLevel level) {
        EnderDragon d = new EnderDragon(level, level.nextEntityIds(9));
        // 섹션 조회는 상자 ±2 로만 넓혀(바닐라와 같다) 큰 드래곤은 중심 청크 밖에서 안 잡힐 수 있다 — 파트가 같은 청크에 오게 둔다
        d.moveTo(0.5, 100, 8.5, 0.0F, 0.0F);
        d.setNoAi(false);
        level.addFreshEntity(d);
        level.tickEntities(1);
        return d;
    }

    /** part 쪽으로 -z 에서 +z 로 날아가는 화살(속도 3 → 피해 6). */
    private static Arrow arrowAt(DimensionTestLevel level, Player owner, EnderDragonPart part) {
        Arrow a = new Arrow(level, level.nextEntityId());
        a.setOwner(owner);
        a.moveTo(part.x(), part.y(0.5), part.z() - 4.0, 0, 0);
        a.setDeltaMovement(0, 0, 3.0);
        level.addFreshEntity(a);
        return a;
    }

    private static Projectile.Hit hit(Arrow a) {
        return a.findHitEntity(a.x(), a.y(), a.z(), a.x() + a.vx(), a.y() + a.vy(), a.z() + a.vz() * 2);
    }

    @Test
    void arrowNoLongerPassesThroughAndHitsHeadUnscaled() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 40.5);
        EnderDragon d = dragon(level);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        // 머리는 yRot 0 에서 -z 쪽 6.5 — -z 바깥에서 오는 화살은 머리부터 맞는다
        Arrow a = arrowAt(level, p, d.head);
        Projectile.Hit h = hit(a);
        assertNotNull(h, "드래곤을 통과하지 않는다");
        assertSame(d.head, h.target());
        a.onHit(h);
        assertEquals(200.0F - 6.0F, d.health(), 1e-4, "머리는 배율 없음");
    }

    @Test
    void wingHitIsQuarterPlusOne() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 40.5);
        EnderDragon d = dragon(level);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        Arrow a = new Arrow(level, level.nextEntityId());
        a.setOwner(p);
        a.moveTo(d.wing1.x() + 4.0, d.wing1.y(0.5), d.wing1.z(), 0, 0);   // +x 바깥에서 날개로
        a.setDeltaMovement(-3.0, 0, 0);
        level.addFreshEntity(a);
        Projectile.Hit h = a.findHitEntity(a.x(), a.y(), a.z(), a.x() - 6.0, a.y(), a.z());
        assertTrue(d.wing1.y() > 90.0, "파트 위치가 잡혔다");
        assertSame(d.wing1, h.target());
        a.onHit(h);
        assertEquals(200.0F - (6.0F / 4.0F + 1.0F), d.health(), 1e-4);
    }

    @Test
    void sittingDragonIgnitesAndIgnoresArrowOnPart() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 40.5);
        EnderDragon d = dragon(level);
        d.getPhaseManager().setPhase(DragonPhase.SITTING_SCANNING);
        Arrow a = arrowAt(level, p, d.head);
        Projectile.Hit h = hit(a);
        assertSame(d.head, h.target());
        a.onHit(h);
        assertEquals(200.0F, d.health(), "앉은 단계는 화살 피해 0");
        assertTrue(a.remainingFireTicks() > 0, "직접 원인(화살)이 onHurt 까지 간다");
        assertFalse(a.isRemoved(), "피해가 없으면 튕겨 나간다");
    }

    @Test
    void explosionHitsBodyAndPartsOnceThanksToInvulnerability() {
        var level = new DimensionTestLevel("minecraft:the_end");
        player(level, 0.5, 100, 60.5);
        EnderDragon d = dragon(level);
        d.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        level.explode(null, d.head.x(), d.head.y(0.5), d.head.z(), 4.0F, false, Explosion.BlockInteraction.KEEP);
        float lost = 200.0F - d.health();
        assertTrue(lost > 0.0F, "폭발은 언제나 드래곤을 깎는다");
        // 본체만 맞으면 최대 57/4 + 1 = 15.25 — 머리 파트(배율 없음)가 폭발 중심에 있어야 이를 넘는다
        assertTrue(lost > 57.0F / 4.0F + 1.0F, "파트(머리)가 배율 없이 맞았다: " + lost);
        // 무적 시간 규칙: 여러 파트가 맞아도 가장 큰 한 번 몫까지만 깎인다(머리 근처라 배율 없는 머리 피해가 가장 크다)
        assertTrue(lost <= 4.0F * 2.0F * 7.0F + 1.0F + 1e-3, "한 폭발의 최대 피해를 넘지 않는다: " + lost);
        float bodyOnly = 200.0F;
        var level2 = new DimensionTestLevel("minecraft:the_end");
        player(level2, 0.5, 100, 60.5);
        EnderDragon d2 = dragon(level2);
        d2.getPhaseManager().setPhase(DragonPhase.HOLDING_PATTERN);
        d2.hurtBy(Entity.Damage.EXPLOSION, 20.0F, null);
        bodyOnly -= d2.health();
        assertEquals(20.0F / 4.0F + 1.0F, bodyOnly, 1e-4, "본체만이면 몸통 배율");
    }

    @Test
    void partsAreNotEntities() {
        var level = new DimensionTestLevel("minecraft:the_end");
        EnderDragon d = dragon(level);
        for (EnderDragonPart part : d.subEntities) {
            assertFalse(((Object) part) instanceof Entity, "레벨·추적·저장에 들어가지 않는다 — 클라이언트가 본체 id+1..8 로 만든다");
            assertNull(level.entities().get(part.id()));
            assertTrue(part.isPickable());
            assertFalse(part.canBeSeenAsEnemy(), "몹 AI 가 겨냥하지 않는다");
        }
        assertFalse(d.isPickable());
    }

    @Test
    void lastHurtMobIgnoresParts() {
        var level = new DimensionTestLevel("minecraft:the_end");
        var p = player(level, 0.5, 100, 40.5);
        EnderDragon d = dragon(level);
        p.setLastHurtMob(d.head);
        assertNull(p.getLastHurtMob(), "바닐라: LivingEntity 가 아니면 null");
    }
}
