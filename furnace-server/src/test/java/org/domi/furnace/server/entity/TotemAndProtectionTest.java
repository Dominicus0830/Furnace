// 불사의 토템(checkTotemDeathProtection)과 플레이어 피해 경감(CombatRules·보호 인챈트) — 바닐라 1.21.1 식으로 계산한 기댓값.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.damage.CombatRules;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TotemAndProtectionTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private final Player player = new Player(7, UUID.randomUUID(), "Steve", new EmbeddedChannel());
    private final PlayerVitals v = player.vitals();
    private final PlayerVitalsTest.TestEnv env = new PlayerVitalsTest.TestEnv();

    TotemAndProtectionTest() {
        v.env(env);
        v.loadGameTypes(null, GameMode.SURVIVAL, false);
        pastSpawnInvulnerability(v);
    }

    private static final PlayerVitals.Surroundings DRY = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);

    /** 접속 무적(60틱) 지나기. */
    static void pastSpawnInvulnerability(PlayerVitals pv) { for (int i = 0; i < 60; i++) pv.tick(DRY); }

    static Player fresh(int id) {
        Player p = new Player(id, UUID.randomUUID(), "P" + id, new EmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        pastSpawnInvulnerability(p.vitals());
        return p;
    }

    static ItemStack item(String name, int count) { return new ItemStack(ItemContext.id("minecraft:item", name), count); }

    static ItemStack enchanted(String name, String ench, int level) {
        return ItemTools.with(item(name, 1), "minecraft:enchantments", Map.of("levels", Map.of(ench, level)));
    }

    private boolean sawEvent(int event) {
        return env.trackerPackets.stream().anyMatch(p -> p instanceof EntityEvent e && e.entityId() == player.entityId() && e.event() == event);
    }

    // ------------------------------------------------------------------ 토템

    @Test
    void playerTotemInMainHandSavesAndAppliesEffects() {
        PlayerInventory inv = player.inventory();
        inv.set(inv.heldWindowSlot(), item("minecraft:totem_of_undying", 1));
        v.addEffect(new MobEffectInstance(MobEffect.SPEED, 1000, 2));
        v.hurt(DamageType.GENERIC, 100f);
        assertEquals(1f, v.health(), 0.0f, "체력 1");
        assertFalse(v.isDeadOrDying());
        assertTrue(inv.get(inv.heldWindowSlot()).isEmpty(), "토템 하나 소모");
        assertFalse(v.hasEffect(MobEffect.SPEED), "기존 효과 제거");
        assertEquals(1, v.getEffect(MobEffect.REGENERATION).amplifier());
        assertEquals(900, v.getEffect(MobEffect.REGENERATION).duration());
        assertEquals(1, v.getEffect(MobEffect.ABSORPTION).amplifier());
        assertEquals(100, v.getEffect(MobEffect.ABSORPTION).duration());
        assertEquals(0, v.getEffect(MobEffect.FIRE_RESISTANCE).amplifier());
        assertEquals(800, v.getEffect(MobEffect.FIRE_RESISTANCE).duration());
        assertEquals(8f, v.absorption(), 0.0f, "흡수 II = 8");
        assertTrue(sawEvent(35), "엔티티 이벤트 35");
        assertFalse(sawEvent(3), "죽음 이벤트 없음");
    }

    @Test
    void mainHandBeforeOffhandAndOffhandAlone() {
        PlayerInventory inv = player.inventory();
        inv.set(inv.heldWindowSlot(), item("minecraft:totem_of_undying", 1));
        inv.set(45, item("minecraft:totem_of_undying", 1));
        v.hurt(DamageType.GENERIC, 100f);
        assertTrue(inv.get(inv.heldWindowSlot()).isEmpty());
        assertEquals(1, inv.get(45).count(), "주 손이 먼저");

        Player p2 = fresh(8);
        p2.inventory().set(45, item("minecraft:totem_of_undying", 2));
        p2.vitals().hurt(DamageType.GENERIC, 100f);
        assertEquals(1f, p2.vitals().health(), 0.0f);
        assertEquals(1, p2.inventory().get(45).count());
    }

    @Test
    void bypassesInvulnerabilityIgnoresTotem() {
        PlayerInventory inv = player.inventory();
        inv.set(inv.heldWindowSlot(), item("minecraft:totem_of_undying", 1));
        v.kill();
        assertTrue(v.isDeadOrDying(), "generic_kill 은 토템을 무시");
        assertEquals(1, inv.get(inv.heldWindowSlot()).count());
        assertTrue(sawEvent(3));
        assertFalse(sawEvent(35));
    }

    @Test
    void noTotemDies() {
        v.hurt(DamageType.GENERIC, 100f);
        assertTrue(v.isDeadOrDying());
        assertTrue(sawEvent(3));
    }

    @Test
    void mobTotemSavesZombie() {
        var level = new BlockPlacementTest.TestLevel();
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(z);
        z.setItemSlot(EquipmentSlot.OFFHAND, item("minecraft:totem_of_undying", 1));
        z.hurt(Entity.Damage.CACTUS, 1000f);
        assertEquals(1f, z.health(), 0.0f);
        assertFalse(z.isDeadOrDying());
        assertTrue(z.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty());
        assertNull(z.getEffect(MobEffect.REGENERATION), "언데드는 재생을 받지 않는다(canBeAffected)");
        assertEquals(1, z.getEffect(MobEffect.ABSORPTION).amplifier());
        assertEquals(800, z.getEffect(MobEffect.FIRE_RESISTANCE).duration());
        z.hurt(Entity.Damage.GENERIC_KILL, 1000f);
        assertTrue(z.isDeadOrDying());
    }

    // ------------------------------------------------------------------ 피해 경감

    /** generic 은 bypasses_armor 라 방어력은 빠지고 보호 인챈트만: 10 × (1 − L/25). */
    @Test
    void protectionLevelTableOnGenericDamage() {
        float[] expected = {10.0f, 9.6f, 9.2f, 8.8f, 8.4f};
        for (int lv = 0; lv <= 4; lv++) {
            Player p = fresh(20 + lv);
            p.inventory().set(5, lv == 0 ? item("minecraft:leather_helmet", 1) : enchanted("minecraft:leather_helmet", "minecraft:protection", lv));
            p.vitals().hurt(DamageType.GENERIC, 10f);
            assertEquals(20f - expected[lv], p.vitals().health(), 1.0e-4f, "보호 " + lv);
        }
    }

    @Test
    void protectionCapsAtTwenty() {
        PlayerInventory inv = player.inventory();
        for (int ws = 5; ws <= 8; ws++) inv.set(ws, ItemTools.with(item(new String[] {"minecraft:leather_helmet", "minecraft:leather_chestplate",
                "minecraft:leather_leggings", "minecraft:leather_boots"}[ws - 5], 1), "minecraft:enchantments", Map.of("levels", Map.of("minecraft:protection", 10))));
        v.hurt(DamageType.GENERIC, 10f);
        assertEquals(20f - 2.0f, v.health(), 1.0e-4f, "40 → 20 으로 잘려 80% 경감");
    }

    /** cactus 는 방어력 적용: 가죽 투구(방어 1) → 9.92, 보호 IV → × 0.84. */
    @Test
    void armorThenProtection() {
        player.inventory().set(5, enchanted("minecraft:leather_helmet", "minecraft:protection", 4));
        assertEquals(1f, player.armorValue(), 0.0f);
        v.hurt(DamageType.byKey("cactus"), 10f);
        float exp = CombatRules.getDamageAfterMagicAbsorb(CombatRules.getDamageAfterAbsorb(10f, 1f, 0f), 4f);
        assertEquals(8.3328f, exp, 1.0e-4f);
        assertEquals(20f - exp, v.health(), 1.0e-4f);
    }

    @Test
    void featherFallingReducesFallOnly() {
        player.inventory().set(8, enchanted("minecraft:leather_boots", "minecraft:feather_falling", 4));
        v.hurt(DamageType.byKey("fall"), 10f);
        assertEquals(20f - 5.2f, v.health(), 1.0e-4f, "낙하 완충 IV = 12 → 48% 경감");
        Player p = fresh(30);
        p.inventory().set(8, enchanted("minecraft:leather_boots", "minecraft:feather_falling", 4));
        p.vitals().hurt(DamageType.GENERIC, 10f);
        assertEquals(10f, p.vitals().health(), 1.0e-4f, "낙하가 아니면 그대로");
    }

    @Test
    void blastProtectionReducesExplosion() {
        env.difficulty = Difficulty.NORMAL;   // explosion 은 scaling always
        player.inventory().set(6, enchanted("minecraft:leather_chestplate", "minecraft:blast_protection", 4));
        float armor = player.armorValue();
        v.hurt(DamageType.byKey("explosion"), 10f);
        float exp = CombatRules.getDamageAfterMagicAbsorb(CombatRules.getDamageAfterAbsorb(10f, armor, 0f), 8f);
        assertEquals(20f - exp, v.health(), 1.0e-4f);
        assertTrue(exp < CombatRules.getDamageAfterAbsorb(10f, armor, 0f));
    }

    @Test
    void resistanceThenProtection() {
        v.addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 100, 0));
        player.inventory().set(5, enchanted("minecraft:leather_helmet", "minecraft:protection", 4));
        v.hurt(DamageType.GENERIC, 10f);
        assertEquals(20f - 10f * 0.8f * 0.84f, v.health(), 1.0e-4f);
    }
}
