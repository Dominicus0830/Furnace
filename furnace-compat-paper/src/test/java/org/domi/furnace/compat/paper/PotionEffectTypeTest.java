package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.domi.furnace.server.effect.MobEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Bukkit PotionEffectType 상수가 서버의 1.21.1 효과 레지스트리와 같은 키·번호(레지스트리 번호 + 1)·분류를 갖는지, PotionEffect 기본값이 Paper 와 같은지. */
class PotionEffectTypeTest {

    @BeforeAll
    static void registries() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void everyServerEffectHasTheSameKeyIdAndCategory() {
        assertEquals(MobEffect.all().size(), PotionEffectType.values().length, "개수");
        for (MobEffect m : MobEffect.all()) {
            PotionEffectType t = PotionEffectType.getByKey(NamespacedKey.fromString(m.name()));
            assertNotNull(t, m.name());
            assertEquals(m.registryId() + 1, t.getId(), m.name());
            assertSame(t, PotionEffectType.getById(t.getId()));
            assertEquals(m.category().name(), t.getCategory().name(), m.name());
            // 즉시 효과: 바닐라 InstantenousMobEffect 하위(즉시 회복·피해·포화). 서버 MobEffect.isInstantaneous 는 포화를 빼므로 따로 본다
            if (!m.name().equals("minecraft:saturation")) assertEquals(m.isInstantaneous(), t.isInstant(), m.name());
        }
        assertTrue(PotionEffectType.SATURATION.isInstant(), "가정: 바닐라 SaturationMobEffect 는 InstantenousMobEffect 를 잇는다");
    }

    @Test
    void lookupsAndNamesMatchPaper() {
        assertSame(PotionEffectType.INVISIBILITY, PotionEffectType.getByName("invisibility"));
        assertSame(PotionEffectType.SLOWNESS, PotionEffectType.getByName("slowness"));
        assertEquals("SLOW", PotionEffectType.SLOWNESS.getName(), "옛 이름");
        assertEquals("INVISIBILITY", PotionEffectType.INVISIBILITY.getName());
        assertEquals("CraftPotionEffectType[minecraft:invisibility]", PotionEffectType.INVISIBILITY.toString());
        assertEquals(PotionEffectTypeCategory.BENEFICIAL, PotionEffectType.INVISIBILITY.getCategory());
        assertEquals(14, PotionEffectType.INVISIBILITY.getId());
    }

    @Test
    void constructorDefaultsAndSerialization() {
        PotionEffect three = new PotionEffect(PotionEffectType.SPEED, 100, 1);
        assertTrue(three.isAmbient(), "인자 셋이면 ambient=true(Bukkit)");
        assertTrue(three.hasParticles());
        assertTrue(three.hasIcon());
        PotionEffect five = new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false);
        assertFalse(five.isAmbient());
        assertFalse(five.hasParticles());
        assertFalse(five.hasIcon(), "인자 다섯이면 아이콘은 입자를 따른다");
        assertFalse(five.isInfinite());
        assertTrue(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0).isInfinite());

        Map<String, Object> m = five.serialize();
        assertEquals("minecraft:invisibility", m.get("effect"));
        assertEquals(Integer.MAX_VALUE, m.get("duration"));
        assertEquals(false, m.get("has-particles"));
        assertEquals(five, new PotionEffect(m));
        assertEquals(five.hashCode(), new PotionEffect(m).hashCode());
        assertNotEquals(five, five.withAmplifier(1));
        assertEquals(1, PotionEffectType.INSTANT_HEALTH.createEffect(200, 0).getDuration(), "즉시 효과는 길이 1");
    }
}
