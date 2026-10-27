// 방패 막기: 정면/후면, 5틱 지연, 피해 0, 내구도 규칙, 도끼 무력화, bypasses_shield, 크리에이티브, 몹 막기.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShieldTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
    private static final Entity.Damage MOB = new Entity.Damage("mob_attack", false, false, false);

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1, null); }

    /** x=7.5 에서 -x 쪽(yaw 90)을 보는 플레이어, 주 손에 방패. */
    private static Player player(ZombieCombatTest.ArenaLevel level, GameMode mode) { return player(level, mode, new EmbeddedChannel()); }

    private static Player player(ZombieCombatTest.ArenaLevel level, GameMode mode, EmbeddedChannel ch) {
        var p = new Player(7, UUID.randomUUID(), "Steve", ch);
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, mode, false);
        p.applyMove(7.5, -60, 0.5, 90, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        p.inventory().set(p.inventory().heldWindowSlot(), item("minecraft:shield"));
        level.player = p;
        return p;
    }

    private static void raise(Player p, int heldTicks) {
        assertTrue(Shields.tryStart(p, 0, p.inventory().get(p.inventory().heldWindowSlot())));
        p.usingTicks -= heldTicks;
    }

    private static Zombie zombie(ZombieCombatTest.ArenaLevel level, double x) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(x, -60, 0.5, 0, 0);
        return z;
    }

    private static int shieldDamage(Player p) { return ItemTools.damage(p.inventory().get(p.inventory().heldWindowSlot())); }

    @Test
    void frontBlockedBackNot() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.SURVIVAL);
        raise(p, 5);
        assertTrue(p.isBlocking());
        float hp = p.vitals().health();
        assertFalse(p.hurtByMob(MOB, 5.0F, zombie(level, 0.5)), "정면은 막힌다");
        assertEquals(hp, p.vitals().health());
        assertTrue(p.hurtByMob(MOB, 5.0F, zombie(level, 14.5)), "뒤에서는 맞는다");
        assertTrue(p.vitals().health() < hp);
    }

    @Test
    void fiveTickDelay() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.SURVIVAL);
        raise(p, 4);
        assertFalse(p.isBlocking(), "4틱은 아직");
        assertTrue(p.hurtByMob(MOB, 5.0F, zombie(level, 0.5)));
        p.usingTicks--;
        assertTrue(p.isBlocking());
    }

    @Test
    void durabilityOnlyFromThreeDamage() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.SURVIVAL);
        raise(p, 5);
        p.hurtByMob(MOB, 2.5F, zombie(level, 0.5));
        assertEquals(0, shieldDamage(p), "3 미만은 내구도 그대로");
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        for (int i = 0; i < 20; i++) p.vitals().tick(DRY);   // 무적 시간 지나기
        Shields.hurtCurrentlyUsedShield(p, 3.5F, null);
        assertEquals(4, shieldDamage(p), "1 + floor(3.5)");
    }

    @Test
    void axeDisablesShield() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.SURVIVAL);
        raise(p, 5);
        var z = zombie(level, 0.5);
        z.setItemSlot(EquipmentSlot.MAINHAND, item("minecraft:iron_axe"));
        assertFalse(p.hurtByMob(MOB, 5.0F, z));
        assertEquals(-1, p.usingHand, "사용이 풀린다");
        assertTrue(p.isOnCooldown(Shields.shieldId()), "100틱 대기");
        for (int i = 0; i < 99; i++) p.tickCooldowns();
        assertTrue(p.isOnCooldown(Shields.shieldId()));
        p.tickCooldowns();
        assertFalse(p.isOnCooldown(Shields.shieldId()));
        assertFalse(Shields.isAxe(item("minecraft:iron_pickaxe")));
    }

    @Test
    void bypassesShieldNotBlocked() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.SURVIVAL);
        raise(p, 5);
        var z = zombie(level, 0.5);
        float hp = p.vitals().health();
        assertTrue(p.vitals().hurt(DamageType.MAGIC, 2.0F, z), "마법은 안 막힌다");
        assertTrue(p.vitals().health() < hp);
        assertFalse(Shields.isDamageSourceBlocked(true, 90, 7.5, 0.5, new Shields.Source("minecraft:starve", true, 0.5, 0.5, z, true, false, null)));
        assertFalse(Shields.isDamageSourceBlocked(true, 90, 7.5, 0.5, new Shields.Source("minecraft:out_of_world", true, 0.5, 0.5, z, true, false, null)));
        assertFalse(Shields.isDamageSourceBlocked(true, 90, 7.5, 0.5, Shields.Source.none("minecraft:mob_attack")), "위치 없는 피해");
        assertFalse(Shields.isDamageSourceBlocked(true, 90, 7.5, 0.5, new Shields.Source("minecraft:arrow", true, 0.5, 0.5, null, false, true, null)), "관통 화살");
        assertTrue(Shields.isDamageSourceBlocked(true, 90, 7.5, 0.5, new Shields.Source("minecraft:arrow", true, 0.5, 0.5, null, false, false, null)));
    }

    @Test
    void creativeShieldKeepsDurability() {
        var level = new ZombieCombatTest.ArenaLevel();
        var p = player(level, GameMode.CREATIVE);
        raise(p, 5);
        assertFalse(p.hurtByMob(MOB, 5.0F, zombie(level, 0.5)), "크리에이티브는 애초에 안 맞는다");
        Shields.hurtCurrentlyUsedShield(p, 10.0F, null);
        assertEquals(0, shieldDamage(p), "무한 재료면 내구도 그대로");
    }

    @Test
    void mobBlocksWithShield() {
        var level = new ZombieCombatTest.ArenaLevel();
        var attacker = player(level, GameMode.SURVIVAL);
        level.player = null;
        var z = zombie(level, 0.5);
        z.setItemSlot(EquipmentSlot.MAINHAND, item("minecraft:shield"));
        z.startUsingItem(0);
        assertFalse(z.isBlocking());
        for (int i = 0; i < 5; i++) z.tick();
        assertTrue(z.isBlocking());
        z.moveTo(0.5, -60, 0.5, -90, 0);   // +x(공격자 쪽)를 본다
        float hp = z.health();
        assertFalse(z.hurtBy(new Entity.Damage("player_attack", false, false, false), 4.0F, attacker));
        assertEquals(hp, z.health());
        z.moveTo(0.5, -60, 0.5, 90, 0);   // 등을 돌림
        assertTrue(z.hurtBy(new Entity.Damage("player_attack", false, false, false), 4.0F, attacker));
        assertTrue(z.health() < hp);
    }

    @Test
    void blockingMobKnockbackNearlyCancels() {
        // 바닐라 blockedByShield: defender.knockback(0.5, defender−attacker) 로 공격자 쪽 0.5, 이어 일반 0.4 로 반대 → 순 -0.15(공격자 반대 쪽으로 조금)
        var level = new ZombieCombatTest.ArenaLevel();
        var attacker = player(level, GameMode.SURVIVAL);
        level.player = null;
        var z = zombie(level, 0.5);
        z.setItemSlot(EquipmentSlot.MAINHAND, item("minecraft:shield"));
        z.startUsingItem(0);
        for (int i = 0; i < 5; i++) z.tick();
        z.moveTo(0.5, -60, 0.5, -90, 0);
        z.setOnGround(false);
        z.setDeltaMovement(0.0, 0.0, 0.0);
        assertFalse(z.hurtBy(new Entity.Damage("player_attack", false, false, false), 4.0F, attacker));
        assertEquals(-0.15, z.vx(), 1e-6, "공격자(+x) 반대로 순 0.15 만");
    }

    private static int motionPackets(EmbeddedChannel ch) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            io.netty.buffer.ByteBuf bb = (io.netty.buffer.ByteBuf) o;
            if (org.domi.furnace.server.net.io.Types.readVarInt(bb) == org.domi.furnace.server.net.protocol.play.SetEntityMotion.ID) n++;
            bb.release();
        }
        return n;
    }

    @Test
    void fullyBlockedPlayerGetsNoMotionPacket_unblockedGetsOne() {
        // 바닐라: 완전히 막으면 markHurt 가 안 서서 속도 패킷 0건(안 밀림), 못 막은 피격은 hurtMarked 로 틱 끝에 한 번
        var level = new ZombieCombatTest.ArenaLevel();
        var ch = new EmbeddedChannel();
        var p = player(level, GameMode.SURVIVAL, ch);
        raise(p, 5);
        motionPackets(ch);   // 비우기
        assertFalse(p.hurtByMob(MOB, 5.0F, zombie(level, 0.5)), "정면은 막힌다");
        p.knockback(0.4, 0.5 - p.x(), 0.5 - p.z());   // 이어지는 일반 넉백(막혀도 deltaMovement 에는 쌓인다)
        p.sendPendingKnockback();
        assertEquals(0, motionPackets(ch), "막으면 속도 패킷 없음");
        for (int i = 0; i < 30; i++) p.vitals().tick(DRY);   // 무적 시간 지나기
        p.applyMove(7.5, -60, 0.5, 270, 0, true);   // 등을 돌림 → 못 막는다
        assertTrue(p.hurtByMob(MOB, 1.0F, zombie(level, 0.5)));
        p.knockback(0.4, 0.5 - p.x(), 0.5 - p.z());
        p.sendPendingKnockback();
        assertEquals(1, motionPackets(ch), "못 막은 피격은 한 번");
        p.sendPendingKnockback();
        assertEquals(0, motionPackets(ch));
    }
}
