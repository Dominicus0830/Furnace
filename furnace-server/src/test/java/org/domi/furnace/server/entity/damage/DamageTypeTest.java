// damage_type 47종이 데이터에서 바닐라 값 그대로 올라오는가(속성·태그·레지스트리 순번), 사망 메시지 키가 바닐라 규칙대로인가.
package org.domi.furnace.server.entity.damage;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Text;
import org.junit.jupiter.api.Test;

class DamageTypeTest {

    private static List<String> registryIds() throws Exception {
        List<String> ids = new ArrayList<>();
        try (var r = new BufferedReader(new InputStreamReader(DamageTypeTest.class.getClassLoader().getResourceAsStream("registries/1.21.1.txt"), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) if (l.startsWith("minecraft:damage_type ")) ids.add(l.substring(l.indexOf(' ') + 1).strip());
        }
        return ids;
    }

    @Test
    void allFortySevenTypesLoadedInRegistryOrder() throws Exception {
        List<String> reg = registryIds();
        assertEquals(47, reg.size());
        List<String> loaded = new ArrayList<>();
        for (DamageType t : DamageType.all()) loaded.add(t.key());
        assertEquals(reg, loaded);
        int i = 0;
        for (DamageType t : DamageType.all()) assertEquals(i++, t.registryId(), t.key());
    }

    @Test
    void propertiesMatchVanillaJson() {
        DamageType fall = DamageType.byKey("fall");
        assertSame(DamageType.FALL, fall);
        assertEquals("fall", fall.msgId());
        assertEquals(DamageType.DeathMessageType.FALL_VARIANTS, fall.deathMessageType());
        assertEquals(DamageType.Scaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER, fall.scaling());
        assertEquals(0.0f, fall.exhaustion());
        assertEquals(DamageType.Effects.THORNS, DamageType.byKey("thorns").effects());
        assertEquals(DamageType.Effects.POKING, DamageType.byKey("minecraft:sweet_berry_bush").effects());
        assertEquals(DamageType.Effects.FREEZING, DamageType.byKey("freeze").effects());
        assertEquals(DamageType.Effects.DROWNING, DamageType.DROWN.effects());
        assertEquals(DamageType.Effects.BURNING, DamageType.byKey("unattributed_fireball").effects());
        assertEquals("onFire", DamageType.byKey("unattributed_fireball").msgId());
        assertEquals(DamageType.Effects.HURT, DamageType.ARROW.effects());
        assertEquals(DamageType.Scaling.ALWAYS, DamageType.byKey("sonic_boom").scaling());
        assertEquals(DamageType.Scaling.ALWAYS, DamageType.EXPLOSION.scaling());
        assertEquals(DamageType.DeathMessageType.INTENTIONAL_GAME_DESIGN, DamageType.BAD_RESPAWN_POINT.deathMessageType());
        assertEquals("explosion.player", DamageType.PLAYER_EXPLOSION.msgId());
        assertEquals("dryout", DamageType.byKey("dry_out").msgId());
        assertEquals(0.1f, DamageType.byKey("trident").exhaustion());
        // 난이도 배율: when_caused_by_living_non_player 는 원인 조건, always 는 무조건
        assertTrue(DamageType.MOB_ATTACK.scalesWithDifficulty(true));
        assertFalse(DamageType.MOB_ATTACK.scalesWithDifficulty(false));
        assertTrue(DamageType.EXPLOSION.scalesWithDifficulty(false));
    }

    @Test
    void tagsComeFromDamageTypeTags() {
        assertTrue(DamageType.byKey("in_fire").isFire());
        assertTrue(DamageType.byKey("lava").isFire());
        assertFalse(DamageType.MAGIC.isFire());
        assertTrue(DamageType.ARROW.isProjectile());
        assertTrue(DamageType.byKey("trident").isProjectile());
        assertTrue(DamageType.MAGIC.bypassesArmor());
        assertFalse(DamageType.MOB_ATTACK.bypassesArmor());
        assertTrue(DamageType.OUT_OF_WORLD.bypassesInvulnerability());
        assertTrue(DamageType.GENERIC_KILL.bypassesInvulnerability());
        assertFalse(DamageType.GENERIC.bypassesInvulnerability());
        assertTrue(DamageType.DROWN.noImpact());
        assertTrue(DamageType.DROWN.isDrowning());
        assertTrue(DamageType.byKey("stalagmite").isFall());
        assertTrue(DamageType.OUT_OF_WORLD.alwaysMostSignificantFall());
        assertTrue(DamageType.MAGIC.noKnockback());
        assertFalse(DamageType.byKey("thorns").noKnockback());
        assertTrue(DamageType.byKey("sonic_boom").bypassesEnchantments());
        assertTrue(DamageType.STARVE.bypassesEffects());
        assertTrue(DamageType.byKey("fireworks").isExplosion());
        assertTrue(DamageType.keyHasTag("fall", "is_fall"));
        assertFalse(DamageType.keyHasTag("not_a_type", "is_projectile"), "없는 키는 어느 태그에도 없다");
    }

    @Test
    void unknownKeyFallsBackToGeneric() {
        assertSame(DamageType.GENERIC, DamageType.byKey("nope"));
        assertNull(DamageType.find("nope"));
        assertNull(DamageType.find("other:fall"));
        assertSame(DamageType.FALL, DamageType.find("minecraft:fall"));
    }

    private static List<Object> args(Text t) { return ((Content.Translatable) t.content()).args(); }

    @Test
    void deathMessageKeysForEveryType() {
        Text victim = Text.text("Steve"), mob = Text.text("Zombie"), credit = Text.text("Alex");
        for (DamageType t : DamageType.all()) {
            if (t.deathMessageType() != DamageType.DeathMessageType.DEFAULT) continue;
            CombatTracker env = new CombatTracker();
            env.recordDamage(t, 1, null, 0, 0, true);
            assertEquals("death.attack." + t.msgId(), env.getDeathMessage(victim).translationKey(), t.key());
            assertEquals("death.attack." + t.msgId() + ".player", env.getDeathMessage(victim, credit).translationKey(), t.key());
            CombatTracker hit = new CombatTracker();
            hit.recordDamage(t, 1, null, 0, 0, true, mob);
            Text m = hit.getDeathMessage(victim, credit);
            assertEquals("death.attack." + t.msgId(), m.translationKey());
            assertEquals(List.of(victim, mob), args(m));
        }
        CombatTracker bad = new CombatTracker();
        bad.recordDamage(DamageType.BAD_RESPAWN_POINT, 1, null, 0, 0, true);
        assertEquals("death.attack.badRespawnPoint.message", bad.getDeathMessage(victim).translationKey());
        // 직접 엔티티만 있으면 그 이름
        CombatTracker direct = new CombatTracker();
        direct.recordDamage(DamageType.ARROW, 1, null, 0, 0, true, null, Text.text("Arrow"));
        assertEquals(List.of(victim, Text.text("Arrow")), args(direct.getDeathMessage(victim)));
    }

    @Test
    void fallVariants() {
        Text victim = Text.text("Steve"), a = Text.text("A"), b = Text.text("B");
        CombatTracker accident = new CombatTracker();
        accident.recordDamage(DamageType.FALL, 7, null, 10, 0, true);
        assertEquals("death.fell.accident.generic", accident.getDeathMessage(victim).translationKey());
        // A 가 쳐서 떨어지고(직전 피해가 낙하 기록이 된다) 낙하로 죽음: 마지막 원인 없음 → assist
        CombatTracker assist = new CombatTracker();
        assist.recordDamage(DamageType.MOB_ATTACK, 2, null, 0, 0, true, a);
        assist.recordDamage(DamageType.FALL, 7, null, 10, 1, true);
        Text m = assist.getDeathMessage(victim);
        assertEquals("death.fell.assist", m.translationKey());
        assertEquals(List.of(victim, a), args(m));
        // 같은 A 가 마무리 → finish
        CombatTracker finish = new CombatTracker();
        finish.recordDamage(DamageType.MOB_ATTACK, 2, null, 0, 0, true, a);
        finish.recordDamage(DamageType.FALL, 7, null, 10, 1, true, a);
        assertEquals("death.fell.finish", finish.getDeathMessage(victim).translationKey());
        // A 가 떨어뜨리고 B 가 마무리 → assist(A)
        CombatTracker other = new CombatTracker();
        other.recordDamage(DamageType.MOB_ATTACK, 2, null, 0, 0, true, a);
        other.recordDamage(DamageType.FALL, 7, null, 10, 1, true, b);
        assertEquals(List.of(victim, a), args(other.getDeathMessage(victim)));
        // 원인 없이 다른 피해 뒤 낙하 → killer
        CombatTracker killer = new CombatTracker();
        killer.recordDamage(DamageType.MAGIC, 2, null, 0, 0, true);
        killer.recordDamage(DamageType.FALL, 7, null, 10, 1, true);
        assertEquals("death.fell.killer", killer.getDeathMessage(victim).translationKey());
    }
}
