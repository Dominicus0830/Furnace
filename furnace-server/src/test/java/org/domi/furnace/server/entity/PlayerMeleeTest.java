// 플레이어 근접 공격(PlayerMelee): 쿨다운 배율, pvp 설정, 방패 막기, 인챈트 피해, 무기 내구 비용, 쓸기.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerMeleeTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1, null); }

    private static Player player(int id, double x, float yaw, PlayerVitalsTest.TestEnv env) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new EmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        p.applyMove(x, -60, 0.5, yaw, 0, true);
        for (int i = 0; i < 100; i++) p.vitals().tick(DRY);
        return p;
    }

    private static PlayerMelee.World world(ZombieCombatTest.ArenaLevel level, Player... ps) {
        return new PlayerMelee.World() {
            public org.domi.furnace.server.block.ChunkLevel level() { return level; }
            public Iterable<Player> players() { return List.of(ps); }
        };
    }

    @Test
    void fullCooldownSwordHitsPlayerForSeven() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);   // +x 를 본다
        var v = player(2, 1.5, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        var r = PlayerMelee.attack(world(level, a, v), a, v);
        assertTrue(r.hurt());
        assertEquals(1, r.weaponDamage(), "검은 내구 1");
        assertEquals(13.0F, v.vitals().health(), 1e-4, "1 + 6, 플레이어가 때리면 난이도 배율 없음");
        assertEquals(0, a.attackStrengthTicker, "공격하면 쿨다운 초기화");
    }

    @Test
    void zeroCooldownScalesDamage() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 0;
        float h = a.attackStrengthScale(0.5F);
        assertEquals(0.5F / 12.5F, h, 1e-4, "검 공격 속도 1.6 → 12.5틱");
        PlayerMelee.attack(world(level, a, v), a, v);
        assertEquals(20.0F - 7.0F * (0.2F + h * h * 0.8F), v.vitals().health(), 1e-4);
    }

    @Test
    void pvpOffBlocksPlayerDamage() {
        var env = new PlayerVitalsTest.TestEnv();
        env.pvp = false;
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        a.attackStrengthTicker = 100;
        assertFalse(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertEquals(20.0F, v.vitals().health());
    }

    @Test
    void shieldBlocksAndAxeDisablesIt() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);   // -x(공격자 쪽)를 본다
        v.inventory().set(v.inventory().heldWindowSlot(), item("minecraft:shield"));
        assertTrue(Shields.tryStart(v, 0, v.inventory().get(v.inventory().heldWindowSlot())));
        v.usingTicks -= 5;
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:iron_axe"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        assertFalse(PlayerMelee.attack(world(level, a, v), a, v).hurt());
        assertEquals(20.0F, v.vitals().health());
        assertEquals(-1, v.usingHand, "도끼에 방패가 풀린다");
        assertTrue(v.isOnCooldown(Shields.shieldId()));
    }

    @Test
    void enchantBonuses() {
        var level = new ZombieCombatTest.ArenaLevel();
        var zombie = new Zombie(level, level.nextEntityId());
        var sharp3 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:sharpness", 3));
        assertEquals(2.0F, PlayerMelee.enchantDamageBonus(sharp3, zombie), 1e-6, "날카로움 0.5 × 3 + 0.5");
        var smite2 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:smite", 2));
        assertEquals(5.0F, PlayerMelee.enchantDamageBonus(smite2, zombie), 1e-6, "좀비는 강타 대상");
        var p = player(3, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertEquals(0.0F, PlayerMelee.enchantDamageBonus(smite2, p), 1e-6);
        var kb2 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:knockback", 2));
        assertEquals(2.0F, PlayerMelee.knockback(kb2), 1e-6);
        var sweep3 = EnchantTools.update(item("minecraft:iron_sword"), m -> m.put("minecraft:sweeping_edge", 3));
        assertEquals(0.75F, PlayerMelee.sweepingRatio(sweep3), 1e-6);
        assertEquals(2, PlayerMelee.weaponDurabilityCost(item("minecraft:iron_pickaxe")));
        assertEquals(0, PlayerMelee.weaponDurabilityCost(item("minecraft:stick")));
    }

    @Test
    void attackSpeedResetsOnItemChange() {
        var a = player(1, 0.5, 0, new PlayerVitalsTest.TestEnv());
        a.tickAttackStrength();
        a.attackStrengthTicker = 50;
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        assertEquals(0, a.attackStrengthTicker);
        a.tickAttackStrength();
        assertEquals(1, a.attackStrengthTicker);
    }

    @Test
    void sweepHitsNearbyPlayer() {
        var env = new PlayerVitalsTest.TestEnv();
        var level = new ZombieCombatTest.ArenaLevel();
        var a = player(1, 0.5, -90, env);
        var v = player(2, 1.5, 90, env);
        var o = player(3, 2.3, 90, env);
        a.inventory().set(a.inventory().heldWindowSlot(), item("minecraft:diamond_sword"));
        a.tickAttackStrength();
        a.attackStrengthTicker = 100;
        PlayerMelee.attack(world(level, a, v, o), a, v);
        assertEquals(19.0F, o.vitals().health(), 1e-4, "쓸기 피해 1 + 0 × 7");
    }

    /** 다른 사람에게 보이는 엔티티 데이터: 왼손 사용 중 = 1|2, 웅크림 자세 5, 회전 공격 자세 4·비트 4, 새로 보는 사람용 묶음. */
    @Test
    void entityDataForObservers() {
        var p = player(4, 0.5, 0, new PlayerVitalsTest.TestEnv());
        assertNull(p.pairingData());
        p.usingHand = 1;
        assertEquals(3, p.livingFlags());
        p.sneaking(true);
        assertEquals(5, p.pose());
        assertEquals(2, p.sharedFlags() & 2);
        p.syncLivingFlags();
        var d = p.pairingData();
        assertNotNull(d);
        assertEquals(List.of(0, 6, 8), d.items().stream().map(it -> it.index()).toList());
        p.usingHand = -1;
        assertEquals(2, p.livingFlags(), "손 비트는 사용이 끝나도 남는다");
        p.startAutoSpinAttack(20, 8.0F, ItemStack.EMPTY);
        assertEquals(4, p.pose());
        assertEquals(6, p.livingFlags());
    }
}
