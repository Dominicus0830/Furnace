// 피해 계산 단계 이벤트(EntityDamageCalculateEvent): 구독자가 아무것도 안 바꾸면 결과가 바닐라 경로와 비트 단위로 같고,
// 단계를 바꾸면 뒤 단계가 새 값으로 계산된다. 플레이어(PlayerVitals)와 몹(LivingEntity) 두 경로 모두.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.api.event.entity.DamageRules;
import org.domi.furnace.api.event.entity.DamageStage;
import org.domi.furnace.api.event.entity.EntityDamageCalculateEvent;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.plugin.GameEvents;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DamageStagesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 단계 이벤트만 구독하는 GameEvents. 이벤트를 만들어 handler 에 보여 주고 돌려준다. */
    private static GameEvents events(List<EntityDamageCalculateEvent> seen, Consumer<EntityDamageCalculateEvent> handler) {
        return new GameEvents() {
            @Override public boolean wantsDamageCalc() { return true; }

            @Override
            public EntityDamageCalculateEvent entityDamageCalc(LivingEntity e, String cause, Rider causing, Rider direct, float base, float absorption, DamageRules rules) {
                return run(cause, base, absorption, rules);
            }

            @Override
            public EntityDamageCalculateEvent playerDamageCalc(Player p, String cause, Rider causing, Rider direct, float base, float absorption, DamageRules rules) {
                return run(cause, base, absorption, rules);
            }

            private EntityDamageCalculateEvent run(String cause, float base, float absorption, DamageRules rules) {
                var ev = new EntityDamageCalculateEvent(null, "minecraft:" + cause, null, null, base, absorption, rules);
                seen.add(ev);
                handler.accept(ev);
                return ev;
            }
        };
    }

    // ------------------------------------------------------------------ 플레이어

    private static Player player(PlayerVitalsTest.TestEnv env) {
        Player p = new Player(7, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        TotemAndProtectionTest.pastSpawnInvulnerability(p.vitals());
        return p;
    }

    /** 방어구(보호 IV 가죽 투구 + 철 가슴), 저항, 흡수 체력을 입힌 플레이어. */
    private static Player dressed(PlayerVitalsTest.TestEnv env) {
        Player p = player(env);
        p.inventory().set(5, TotemAndProtectionTest.enchanted("minecraft:leather_helmet", "minecraft:protection", 4));
        p.inventory().set(6, TotemAndProtectionTest.item("minecraft:iron_chestplate", 1));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 1000, 0));
        p.vitals().addEffect(new MobEffectInstance(MobEffect.ABSORPTION, 1000, 0));   // 흡수 체력 4
        assertEquals(4f, p.vitals().absorption(), 0f);
        return p;
    }

    @Test
    void untouchedEventMatchesVanillaPathBitForBit() {
        for (String type : new String[] {"generic", "cactus", "fall", "magic", "explosion", "arrow", "out_of_world"}) {
            for (float amount : new float[] {0.5f, 3f, 7.3f, 19f}) {
                var plain = new PlayerVitalsTest.TestEnv();
                var staged = new PlayerVitalsTest.TestEnv();
                List<EntityDamageCalculateEvent> seen = new ArrayList<>();
                staged.events = events(seen, ev -> {});
                Player a = dressed(plain), b = dressed(staged);
                a.vitals().hurt(DamageType.byKey(type), amount);
                b.vitals().hurt(DamageType.byKey(type), amount);
                assertEquals(a.vitals().health(), b.vitals().health(), 0f, type + " " + amount);
                assertEquals(a.vitals().absorption(), b.vitals().absorption(), 0f, type + " " + amount + " 흡수 체력");
                assertFalse(seen.isEmpty(), "이벤트가 불렸다: " + type);
            }
        }
    }

    @Test
    void playerStagesReportVanillaBreakdown() {
        var env = new PlayerVitalsTest.TestEnv();
        List<EntityDamageCalculateEvent> seen = new ArrayList<>();
        env.events = events(seen, ev -> {});
        Player p = dressed(env);
        float armor = p.armorValue();
        assertTrue(armor > 0f);
        p.vitals().hurt(DamageType.byKey("cactus"), 10f);
        var ev = seen.get(0);
        assertEquals(10f, ev.base(), 0f);
        assertTrue(ev.applies(DamageStage.ARMOR) && ev.applies(DamageStage.RESISTANCE) && ev.applies(DamageStage.ENCHANTMENT));
        assertFalse(ev.applies(DamageStage.ENTITY));
        assertEquals(org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterAbsorb(10f, armor, 0f), ev.output(DamageStage.ARMOR), 0f);
        assertEquals(ev.output(DamageStage.ARMOR) * 0.8f, ev.output(DamageStage.RESISTANCE), 1e-5f, "저항 I = 20%");
        assertEquals(4f, ev.absorbed(), 0f, "흡수 체력 4 가 먼저 빠진다");
        assertEquals(20f - ev.finalDamage(), p.vitals().health(), 1e-5f);
        assertEquals(0f, p.vitals().absorption(), 0f);
    }

    @Test
    void playerSkipArmorAndScaleEnchantment() {
        var env = new PlayerVitalsTest.TestEnv();
        List<EntityDamageCalculateEvent> seen = new ArrayList<>();
        env.events = events(seen, ev -> {
            ev.skip(DamageStage.ARMOR);
            ev.scaleReduction(DamageStage.ENCHANTMENT, 0f);
            ev.setReduction(DamageStage.ABSORPTION, 0f);   // 흡수 체력을 건드리지 않는다
        });
        Player p = dressed(env);
        p.vitals().hurt(DamageType.byKey("cactus"), 10f);
        var ev = seen.get(0);
        assertEquals(10f, ev.output(DamageStage.ARMOR), 0f);
        assertEquals(10f * 0.8f, ev.finalDamage(), 1e-5f, "방어력·인챈트를 건너뛰고 저항만");
        assertEquals(20f - 8f, p.vitals().health(), 1e-5f);
        assertEquals(4f, p.vitals().absorption(), 0f, "흡수 체력은 그대로");
    }

    @Test
    void playerForceArmorOnBypassingDamageAndSetBase() {
        var env = new PlayerVitalsTest.TestEnv();
        env.events = events(new ArrayList<>(), ev -> {
            ev.setApplied(DamageStage.ARMOR, true);   // out_of_world 는 bypasses_armor
            ev.setBase(ev.base() / 2f);
        });
        Player p = player(env);
        p.inventory().set(6, TotemAndProtectionTest.item("minecraft:iron_chestplate", 1));
        float armor = p.armorValue();
        p.vitals().hurt(DamageType.byKey("generic"), 10f);   // generic 도 방어력을 쳐 주지만 base 를 5 로
        float expected = org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterAbsorb(5f, armor, 0f);
        assertEquals(20f - expected, p.vitals().health(), 1e-5f);
    }

    // ------------------------------------------------------------------ 몹

    private static final class EventLevel extends BlockPlacementTest.TestLevel {
        GameEvents events = GameEvents.NONE;

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public GameEvents gameEvents() { return events; }
            };
        }
    }

    private static Zombie zombie(EventLevel level, float armor, int resistanceAmp) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        z.attributes().get(Attribute.ARMOR).setBaseValue(armor);
        z.absorptionAmount = 2f;
        if (resistanceAmp >= 0) z.addEffect(new MobEffectInstance(MobEffect.RESISTANCE, 1000, resistanceAmp));
        return z;
    }

    @Test
    void mobUntouchedEventMatchesVanillaPath() {
        for (String type : new String[] {"generic", "cactus", "magic", "fall", "explosion", "out_of_world"}) {
            for (float amount : new float[] {1.5f, 6f, 13.7f}) {
                var plain = new EventLevel();
                var staged = new EventLevel();
                List<EntityDamageCalculateEvent> seen = new ArrayList<>();
                staged.events = events(seen, ev -> {});
                var a = zombie(plain, 12f, 1);
                var b = zombie(staged, 12f, 1);
                a.hurt(new Entity.Damage(type, false, false, false), amount);
                b.hurt(new Entity.Damage(type, false, false, false), amount);
                assertEquals(a.health(), b.health(), 0f, type + " " + amount);
                assertEquals(a.absorptionAmount, b.absorptionAmount, 0f, type + " " + amount + " 흡수 체력");
                assertFalse(seen.isEmpty(), type);
            }
        }
    }

    @Test
    void mobStageChangesApply() {
        var level = new EventLevel();
        List<EntityDamageCalculateEvent> seen = new ArrayList<>();
        level.events = events(seen, ev -> ev.scaleReduction(DamageStage.ARMOR, 0.5f));
        var z = zombie(level, 20f, -1);
        z.absorptionAmount = 0f;
        z.hurt(new Entity.Damage("mob_attack", false, false, false), 10f);
        var ev = seen.get(0);
        float full = org.domi.furnace.server.entity.damage.CombatRules.getDamageAfterAbsorb(10f, 20f, 0f);
        assertEquals(10f - (10f - full) * 0.5f, ev.finalDamage(), 1e-5f, "방어력이 줄이는 양의 절반만");
        assertEquals(z.maxHealth() - ev.finalDamage(), z.health(), 1e-5f);
    }

    @Test
    void witchHasEntityStageForMagic() {
        var level = new EventLevel();
        List<EntityDamageCalculateEvent> seen = new ArrayList<>();
        level.events = events(seen, ev -> {});
        var w = new Witch(level, level.nextEntityId());
        w.moveTo(0.5, -60, 0.5, 0, 0);
        w.hurt(new Entity.Damage("magic", false, false, false), 10f);
        var ev = seen.get(0);
        assertTrue(ev.applies(DamageStage.ENTITY));
        assertEquals(10f * 0.15f, ev.finalDamage(), 0f);
        assertEquals(w.maxHealth() - 10f * 0.15f, w.health(), 1e-5f);
        // 마법이 아닌 피해에는 적용되지 않는다
        seen.clear();
        var w2 = new Witch(level, level.nextEntityId());
        w2.moveTo(0.5, -60, 0.5, 0, 0);
        w2.hurt(new Entity.Damage("cactus", false, false, false), 4f);
        assertFalse(seen.get(0).applies(DamageStage.ENTITY));
        // 구독자 없는 경로도 마녀 경감은 그대로(바닐라 경로 회귀)
        var plain = new EventLevel();
        var w3 = new Witch(plain, plain.nextEntityId());
        w3.moveTo(0.5, -60, 0.5, 0, 0);
        w3.hurt(new Entity.Damage("magic", false, false, false), 10f);
        assertEquals(w.health(), w3.health(), 0f);
    }

    @Test
    void shieldedOrZeroDamageSkipsEvent() {
        var level = new EventLevel();
        List<EntityDamageCalculateEvent> seen = new ArrayList<>();
        level.events = events(seen, ev -> {});
        var z = zombie(level, 0f, -1);
        z.hurt(new Entity.Damage("generic", false, false, false), 0f);
        assertTrue(seen.isEmpty(), "피해 0 은 계산할 것이 없다");
    }
}
