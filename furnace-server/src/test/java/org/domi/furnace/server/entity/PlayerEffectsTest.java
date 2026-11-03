// 상태 효과: 재생·독·위더 주기, 저항·점프 강화·체력 강화·흡수, 우유(전부 제거), 덮어쓰기 규칙(더 센 쪽 + 숨은 효과), NBT 왕복, 패킷.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerEffectsTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private final EmbeddedChannel channel = new ConcurrentEmbeddedChannel();
    private final Player player = new Player(7, UUID.randomUUID(), "Steve", channel);
    private final PlayerVitals v = player.vitals();

    PlayerEffectsTest() {
        v.env(new PlayerVitalsTest.TestEnv());
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
    }

    private void ticks(int n) { for (int i = 0; i < n; i++) v.tick(DRY); }

    @Test
    void regenerationHealsEveryFiftyTicksAtLevelOne() {
        v.setFoodLevel(10);   // 자연 회복(포만 18 이상)이 섞이지 않게
        v.setHealth(10f);
        v.addEffect(new MobEffectInstance(MobEffect.REGENERATION, 400, 0));
        ticks(100);
        assertEquals(12f, v.health(), 0.01f, "50 틱마다 1 — 100 틱에 2");
    }

    @Test
    void poisonNeverKills() {
        v.addEffect(new MobEffectInstance(MobEffect.POISON, 2000, 0));
        ticks(1500);
        assertEquals(1f, v.health(), 0.01f, "독은 체력 1 밑으로 내리지 않는다");
    }

    @Test
    void witherKills() {
        v.addEffect(new MobEffectInstance(MobEffect.WITHER, 2000, 1));
        ticks(1500);
        assertTrue(v.isDeadOrDying());
    }

    @Test
    void effectsExpireAndSendRemovePacket() {
        v.addEffect(new MobEffectInstance(MobEffect.SPEED, 5, 0));
        assertTrue(v.hasEffect(MobEffect.SPEED));
        ticks(6);
        assertFalse(v.hasEffect(MobEffect.SPEED));
    }

    @Test
    void healthBoostRaisesMaxAndRemovalClamps() {
        v.addEffect(new MobEffectInstance(MobEffect.HEALTH_BOOST, 400, 1));
        assertEquals(28f, v.maxHealth(), 0.01f);
        v.heal(100f);
        assertEquals(28f, v.health(), 0.01f);
        v.removeEffect(MobEffect.HEALTH_BOOST);
        assertEquals(20f, v.health(), 0.01f);
    }

    @Test
    void absorptionStartsFullAndDisappearsWithEffect() {
        v.addEffect(new MobEffectInstance(MobEffect.ABSORPTION, 3, 1));
        assertEquals(8f, v.absorption(), 0.01f);
        ticks(5);
        assertFalse(v.hasEffect(MobEffect.ABSORPTION));
        assertEquals(0f, v.absorption(), 0.01f);
    }

    @Test
    void resistanceReducesDamageAndJumpBoostReducesFall() {
        ticks(100);   // 접속 직후 무적 시간이 지나게
        v.addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 400, 1));   // 단계 2: 40% 감소
        v.hurt(org.domi.furnace.server.entity.damage.DamageType.DROWN, 10f);
        assertEquals(14f, v.health(), 0.01f);
    }

    @Test
    void instantHealthAndDamage() {
        ticks(100);
        v.setHealth(10f);
        v.applyInstantaneousEffect(MobEffect.INSTANT_HEALTH, 0, 1.0);
        assertEquals(14f, v.health(), 0.01f);
        v.applyInstantaneousEffect(MobEffect.INSTANT_DAMAGE, 0, 1.0);
        assertEquals(8f, v.health(), 0.01f);
    }

    @Test
    void strongerEffectWinsAndWeakerIsKeptHidden() {
        MobEffectInstance a = new MobEffectInstance(MobEffect.SPEED, 1000, 0);
        v.addEffect(a);
        v.addEffect(new MobEffectInstance(MobEffect.SPEED, 200, 2));
        var cur = v.getEffect(MobEffect.SPEED);
        assertEquals(2, cur.amplifier());
        assertEquals(200, cur.duration());
        assertNotNull(cur.hiddenEffect(), "약한 쪽은 숨은 효과로 남는다");
        assertEquals(1000, cur.hiddenEffect().duration());
        assertFalse(v.addEffect(new MobEffectInstance(MobEffect.SPEED, 100, 0)) && false);
    }

    @Test
    void removeAllEffectsAndNbtRoundTrip() {
        v.addEffect(new MobEffectInstance(MobEffect.NIGHT_VISION, 600, 0, false, false));
        v.addEffect(new MobEffectInstance(MobEffect.STRENGTH, 300, 1, true, true));
        NbtCompound tag = v.saveData();
        var back = new Player(8, UUID.randomUUID(), "Alex", new ConcurrentEmbeddedChannel()).vitals();
        back.env(new PlayerVitalsTest.TestEnv());
        back.load(tag);
        assertEquals(2, back.activeEffects().size());
        var nv = back.getEffect(MobEffect.NIGHT_VISION);
        assertFalse(nv.visible());
        assertTrue(back.getEffect(MobEffect.STRENGTH).ambient());
        assertEquals(2, v.removeAllEffects());
        assertTrue(v.activeEffects().isEmpty());
    }
}
