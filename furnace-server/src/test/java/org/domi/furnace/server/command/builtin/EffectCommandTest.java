// /effect: 몹(@e)에도 걸고 지운다 — 소유 region 에서 걸고 반환값·메시지는 바닐라대로, 살아 있지 않은 엔티티는 세지 않는다.
// 걸린 효과는 몸의 효과 입자 목록(엔티티 데이터 10)·환경 표시(11)로 클라이언트에 간다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.ArmorStand;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EffectCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private <T extends org.domi.furnace.server.entity.Entity> T spawn(java.util.function.Function<org.domi.furnace.server.block.ChunkLevel, T> make, double x)
            throws InterruptedException {
        Object[] out = new Object[1];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            T e = make.apply(lvl);
            e.moveTo(x, -60, 0.5, 0, 0);
            lvl.addFreshEntity(e);
            out[0] = e;
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        @SuppressWarnings("unchecked") T t = (T) out[0];
        return t;
    }

    private int run(CommandSource src, String cmd) { return core.commands().performPrefixedCommandAsync(src, cmd).join().result(); }

    private static boolean sent(EmbeddedChannel ch, String needle) {
        ch.runPendingTasks();
        boolean found = false;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof ByteBuf bb) {
                if (bb.toString(StandardCharsets.ISO_8859_1).contains(needle)) found = true;
                bb.release();
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static List<ParticleOptions> particles(LivingEntity e) { return (List<ParticleOptions>) e.entityData().get(LivingEntity.DATA_EFFECT_PARTICLES); }

    @Test
    void givesAndClearsEffectsOnMobs_withVanillaCountsAndMessages() throws Exception {
        Cow a = spawn(l -> new Cow(l, l.nextEntityId()), 0.5), b = spawn(l -> new Cow(l, l.nextEntityId()), 1.5);
        var ch = new EmbeddedChannel();
        var p = new Player(10, UUID.randomUUID(), "Steve", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        var src = CommandSource.player(core, p).withPermission(2);
        sent(ch, "");

        assertEquals(2, run(src, "effect give @e[type=cow] minecraft:speed 10 1"));
        for (Cow c : new Cow[] {a, b}) {
            var inst = c.getEffect(MobEffect.SPEED);
            assertNotNull(inst, "몹에게도 걸린다");
            assertEquals(200, inst.duration());
            assertEquals(1, inst.amplifier());
        }
        assertTrue(sent(ch, "commands.effect.give.success.multiple"));
        // 입자: 효과 색의 entity_effect(불투명)
        assertEquals(List.of(new ParticleOptions.Color(0xFF000000 | MobEffect.SPEED.color())), particles(a));
        assertEquals(false, a.entityData().get(LivingEntity.DATA_EFFECT_AMBIENT));

        // 이미 더 센 효과가 있으면 실패(give.failed)
        assertEquals(0, run(src, "effect give @e[type=cow] minecraft:speed 5 0"));

        // 특정 효과 지우기 — 하나만
        assertEquals(1, run(src, "effect clear @e[type=cow,limit=1,sort=nearest] minecraft:speed"));
        assertTrue(sent(ch, "commands.effect.clear.specific.success.single"));
        // 전부 지우기: 남은 소 하나만 지워진다 → 반환값 1, 입자 목록은 비고
        assertEquals(1, run(src, "effect clear @e[type=cow]"));
        assertTrue(sent(ch, "commands.effect.clear.everything.success.multiple"));
        assertNull(a.getEffect(MobEffect.SPEED));
        assertNull(b.getEffect(MobEffect.SPEED));
        assertEquals(List.of(), particles(a));
        assertEquals(List.of(), particles(b));
        // 아무것도 없으면 실패
        assertEquals(0, run(src, "effect clear @e[type=cow]"));
    }

    /** 포화는 즉시 효과: 기본 /effect give 는 음식 +1 만 주고 효과 목록에 남지 않는다. */
    @Test
    void saturationGivenToPlayerIsInstantAndNotListed() throws Exception {
        var ch = new EmbeddedChannel();
        var p = new Player(10, UUID.randomUUID(), "Steve", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        var src = CommandSource.player(core, p).withPermission(2);
        p.vitals().setFoodLevel(5);
        assertEquals(1, run(src, "effect give @s minecraft:saturation"));
        assertEquals(6, p.vitals().foodLevel());
        assertFalse(p.vitals().hasEffect(MobEffect.SATURATION));
        assertEquals(1, run(src, "effect give @s minecraft:saturation 30 2"));
        assertEquals(9, p.vitals().foodLevel());
        assertFalse(p.vitals().hasEffect(MobEffect.SATURATION));
    }

    @Test
    void hiddenParticlesAreNotSent_andNonLivingEntitiesAreSkipped() throws Exception {
        Cow c = spawn(l -> new Cow(l, l.nextEntityId()), 0.5);
        ArmorStand s = spawn(l -> new ArmorStand(l, l.nextEntityId()), 2.5);
        var src = CommandSource.console(core);
        // 갑옷 거치대도 LivingEntity — 소·거치대 둘 다 세진다
        assertEquals(2, run(src, "effect give @e[type=!player] minecraft:glowing 30 0 true"));
        assertNotNull(c.getEffect(MobEffect.GLOWING));
        assertNotNull(s.getEffect(MobEffect.GLOWING));
        assertEquals(List.of(), particles(c), "입자 숨김이면 목록이 비어 있다");
        assertTrue(c.isCurrentlyGlowing());
        // 살아 있지 않은 엔티티만 고르면 give.failed
        var item = spawn(l -> new org.domi.furnace.server.entity.ItemEntity(l, l.nextEntityId(), 4.5, -60, 0.5,
                new org.domi.furnace.server.entity.ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:stone"), 1)), 4.5);
        assertNotNull(item);
        assertEquals(0, run(src, "effect give @e[type=item] minecraft:speed"));
    }

    @Test
    void instantEffectsHarmMobs_andUndeadAreInverted() throws Exception {
        Cow c = spawn(l -> new Cow(l, l.nextEntityId()), 0.5);
        Zombie z = spawn(l -> new Zombie(l, l.nextEntityId()), 2.5);
        var src = CommandSource.console(core);
        float cow = c.health(), zombie = z.health();
        assertEquals(1, run(src, "effect give @e[type=cow] minecraft:instant_damage 1 0"));
        assertEquals(cow - 6, c.health(), 0.01f);
        assertEquals(1, run(src, "effect give @e[type=zombie] minecraft:instant_health 1 0"));
        assertEquals(zombie - 6, z.health(), 0.01f, "언데드는 치유가 피해(6 << 단계)로");
        float z2 = z.health();
        assertEquals(1, run(src, "effect give @e[type=zombie] minecraft:instant_damage 1 0"));
        assertEquals(Math.min(z.maxHealth(), z2 + 4), z.health(), 0.01f, "언데드는 피해가 치유(4 << 단계)로");
    }

    @Test
    void ambientEffectsAreFlaggedAndUseTranslucentParticles() throws Exception {
        Cow c = spawn(l -> new Cow(l, l.nextEntityId()), 0.5);
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, r -> {
            c.addEffect(new org.domi.furnace.server.effect.MobEffectInstance(MobEffect.HASTE, 200, 0, true, true));
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(new ParticleOptions.Color(MobEffect.AMBIENT_PARTICLE_ALPHA << 24 | MobEffect.HASTE.color())), particles(c));
        assertEquals(true, c.entityData().get(LivingEntity.DATA_EFFECT_AMBIENT));
    }
}
