// 1.21 효과 4종 발동(wind_charged·weaving·oozing·infested): 죽어서 빠질 때·맞을 때 바닐라처럼 돌풍·거미줄·슬라임·좀벌레가 나온다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.effect.MobEffectTriggers;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MobEffectTriggersTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage GENERIC = new Entity.Damage("generic", false, false, false);

    /** 게임 규칙을 바꿀 수 있는 시험장. */
    static class RuleLevel extends SkeletonCombatTest.ArenaLevel {
        boolean mobGriefing = true;
        int cramming = 24;

        @Override
        protected LevelServices services() {
            var base = super.services();
            return new LevelServices.Delegating(base) {
                @Override public boolean gameRule(String name) { return name.equals("mobGriefing") ? mobGriefing : base.gameRule(name); }
                @Override public int gameRuleInt(String name) { return name.equals("maxEntityCramming") ? cramming : base.gameRuleInt(name); }
            };
        }
    }

    private static Zombie zombieAt(ChunkLevelHolder h, double x, double z, MobEffect effect) {
        var zombie = new Zombie(h.level, h.level.nextEntityId());
        zombie.moveTo(x, -60, z, 0, 0);
        zombie.setOnGround(true);
        h.level.addFreshEntity(zombie);
        if (effect != null) assertTrue(zombie.addEffect(new MobEffectInstance(effect, 3600, 0)));
        return zombie;
    }

    private record ChunkLevelHolder(RuleLevel level) {}

    /** 죽이고 죽음 틱 20 을 돌려 remove(KILLED) 까지 간다. */
    private static void killAndRemove(LivingEntity e) {
        e.hurt(GENERIC, 1000.0F);
        assertTrue(e.isDeadOrDying());
        for (int i = 0; i < 20 && !e.isRemoved(); i++) e.tickDeath();
        assertTrue(e.isRemoved());
        assertEquals(Entity.RemovalReason.KILLED, e.removalReason());
    }

    private static long count(RuleLevel level, Class<?> cls) {
        return level.entities().tickSnapshot().stream().filter(cls::isInstance).filter(e -> !e.isRemoved()).count();
    }

    private static int cobwebs(RuleLevel level, Pos c) {
        int web = B.defaultState("minecraft:cobweb"), n = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (level.getBlockState(new Pos(c.x() + x, c.y() + y, c.z() + z)) == web) n++;
        }
        return n;
    }

    @Test
    void oozingSpawnsTwoSizeTwoSlimesOnDeath() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var zombie = zombieAt(h, 0.5, 0.5, MobEffect.OOZING);
        killAndRemove(zombie);
        var slimes = h.level.entities().tickSnapshot().stream().filter(e -> e.type() == EntityType.SLIME).toList();
        assertEquals(2, slimes.size());
        for (Entity s : slimes) {
            assertEquals(2, ((Slime) s).getSize());
            assertEquals(-59.5, s.y(), 1e-9);   // y + 0.5
        }
        assertTrue(zombie.getActiveEffects().isEmpty(), "triggerOnDeathMobEffects 가 효과를 비운다");
    }

    @Test
    void oozingRespectsEntityCramming() {
        var h = new ChunkLevelHolder(new RuleLevel());
        h.level.cramming = 3;
        var near = new Slime(h.level, h.level.nextEntityId());
        near.setSize(1, true);
        near.moveTo(1.5, -60, 0.5, 0, 0);
        h.level.addFreshEntity(near);
        var zombie = zombieAt(h, 0.5, 0.5, MobEffect.OOZING);
        killAndRemove(zombie);
        assertEquals(3, h.level.entities().tickSnapshot().stream().filter(e -> e.type() == EntityType.SLIME).count(), "3 − 주변 1 = 2 마리");
        // 주변 슬라임이 규칙 값 이상이면(셀 때 최대 cramming 마리까지만 센다) 하나도 안 나온다
        h.level.cramming = 1;
        var z2 = zombieAt(h, 0.5, 0.5, MobEffect.OOZING);
        killAndRemove(z2);
        assertEquals(3, h.level.entities().tickSnapshot().stream().filter(e -> e.type() == EntityType.SLIME).count());
    }

    @Test
    void discardedDoesNotTrigger() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var zombie = zombieAt(h, 0.5, 0.5, MobEffect.OOZING);
        zombie.discard();
        assertEquals(0, count(h.level, Slime.class));
    }

    @Test
    void weavingPlacesTwoOrThreeCobwebsOnSturdyFloor() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var zombie = zombieAt(h, 5.5, 5.5, MobEffect.WEAVING);
        Pos c = zombie.blockPosition();
        killAndRemove(zombie);
        int n = cobwebs(h.level, c);
        // 15 번 뽑아 아래가 튼튼한 y = -60 층(9 칸 중)만 붙는다 — 2~3 개(드물게 15 번 안에 못 채우면 그보다 적다)
        assertTrue(n >= 1 && n <= 3, "거미줄 " + n);
        int web = B.defaultState("minecraft:cobweb");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            assertNotEquals(web, h.level.getBlockState(new Pos(c.x() + x, c.y() + 1, c.z() + z)), "공중에는 안 붙는다");
        }
    }

    @Test
    void weavingNeedsMobGriefingForMobs() {
        var h = new ChunkLevelHolder(new RuleLevel());
        h.level.mobGriefing = false;
        var zombie = zombieAt(h, 5.5, 5.5, MobEffect.WEAVING);
        Pos c = zombie.blockPosition();
        killAndRemove(zombie);
        assertEquals(0, cobwebs(h.level, c));
    }

    @Test
    void weavingCobwebSlowsLessForWeavers() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var plain = zombieAt(h, 5.5, 5.5, null);
        var weaver = zombieAt(h, 8.5, 5.5, MobEffect.WEAVING);
        int web = B.defaultState("minecraft:cobweb");
        var behavior = org.domi.furnace.server.block.BlockBehaviors.of(web);
        behavior.entityInside(web, h.level, plain.blockPosition(), plain);
        behavior.entityInside(web, h.level, weaver.blockPosition(), weaver);
        assertArrayEquals(new double[] {0.25, 0.05F, 0.25}, plain.stuckSpeedMultiplier(), 1e-9);
        assertArrayEquals(new double[] {0.5, 0.25, 0.5}, weaver.stuckSpeedMultiplier(), 1e-9);
    }

    @Test
    void windChargedBurstsAndPushesNeighbours() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var victim = zombieAt(h, 0.5, 0.5, MobEffect.WIND_CHARGED);
        var other = zombieAt(h, 2.5, 0.5, null);
        other.setDeltaMovement(0, 0, 0);
        float hp = other.health();
        killAndRemove(victim);
        assertTrue(other.vx() > 0.1, "돌풍에 +x 로 밀린다: " + other.vx());
        assertEquals(hp, other.health(), "윈드 폭발은 피해가 없다");
    }

    @Test
    void infestedSpawnsSilverfishWhenHurt() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var zombie = zombieAt(h, 0.5, 0.5, MobEffect.INFESTED);
        zombie.addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 3600, 4));   // 저항 V: 피해 0 — 계속 맞아도 안 죽는다
        for (int i = 0; i < 200 && count(h.level, Silverfish.class) == 0; i++) {
            zombie.invulnerableTime = 0;   // 같은 피해를 매번 새 피해로 받게(바닐라 무적 시간 창은 bypasses_cooldown 만 건너뛴다)
            zombie.hurt(new Entity.Damage("generic", false, false, true), 1.0F);
        }
        var fish = h.level.entities().tickSnapshot().stream().filter(e -> e instanceof Silverfish).toList();
        assertFalse(fish.isEmpty(), "10% 확률 — 200 번이면 나온다");
        assertTrue(fish.size() <= 2);
        for (Entity f : fish) assertEquals(-60 + zombie.bbHeight() / 2.0, f.y(), 1e-6);
    }

    @Test
    void infestedOnPlayerHurt() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(10.5, -60, 0.5, 90, 0, true);
        p.currentLevel = h.level;
        h.level.player = p;
        p.vitals().addEffect(new MobEffectInstance(MobEffect.INFESTED, 3600, 0));
        for (int i = 0; i < 200 && count(h.level, Silverfish.class) == 0; i++) {
            p.vitals().hurt(DamageType.byKey("generic"), 0.01F);
            for (int t = 0; t < 10; t++) p.vitals().tick(new PlayerVitals.Surroundings(true, false, false, false, null, false, -64));
        }
        assertTrue(count(h.level, Silverfish.class) > 0);
    }

    @Test
    void playerWindChargedOnRespawnSkipsTheDeadPlayer() {
        var h = new ChunkLevelHolder(new RuleLevel());
        var p = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(0.5, -60, 0.5, 90, 0, true);
        p.currentLevel = h.level;
        h.level.player = p;
        p.vitals().addEffect(new MobEffectInstance(MobEffect.WIND_CHARGED, 3600, 0));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.OOZING, 3600, 0));
        var other = zombieAt(h, 2.5, 0.5, null);
        other.setDeltaMovement(0, 0, 0);
        MobEffectTriggers.triggerOnDeath(h.level, p, Entity.RemovalReason.KILLED);
        assertTrue(other.vx() > 0.1, "몹은 밀린다");
        assertEquals(2, count(h.level, Slime.class));
    }

    @Test
    void jomlRotateYMatchesQuadrants() {
        // rotateY(π/2): (1, 0) → (cos·1, −sin·1) = (≈0, −1); rotateY(π): cosFromSin 이 음수 사분면을 고른다
        float[] a = rotate(1.0F, 0.0F, (float) (Math.PI / 2));
        assertEquals(0.0F, a[0], 1e-6F);
        assertEquals(-1.0F, a[1], 1e-6F);
        float[] b = rotate(1.0F, 0.0F, (float) Math.PI - 0.01F);
        assertTrue(b[0] < -0.99F);
    }

    private static float[] rotate(float x, float z, float g) {
        try {
            var m = MobEffectTriggers.class.getDeclaredMethod("rotateY", float.class, float.class, float.class);
            m.setAccessible(true);
            return (float[]) m.invoke(null, x, z, g);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
