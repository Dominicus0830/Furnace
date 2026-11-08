// MobEffect 정의가 바닐라 1.21.1 레지스트리(builtin-1.21.1.txt)와 같은 목록·순서인지, 1.21 에 새로 생긴 효과의 색·분류가 바닐라 값인지.
package org.domi.furnace.server.effect;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MobEffectRegistryTest {

    @Test
    void effectListMatchesVanillaRegistryInOrder() throws Exception {
        List<String> vanilla = new ArrayList<>();
        try (var in = new BufferedReader(new InputStreamReader(
                MobEffectRegistryTest.class.getClassLoader().getResourceAsStream("registries/builtin-1.21.1.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("minecraft:mob_effect ")) vanilla.add(line.substring("minecraft:mob_effect ".length()));
            }
        }
        List<String> ours = MobEffect.all().stream().map(MobEffect::name).toList();
        assertEquals(vanilla, ours);
        assertEquals(39, ours.size());
    }

    @Test
    void newInTrialChambersEffectsUseVanillaColorAndCategory() {
        assertEffect(MobEffect.TRIAL_OMEN, 1484454, MobEffect.Category.NEUTRAL);
        assertEffect(MobEffect.RAID_OMEN, 14565464, MobEffect.Category.NEUTRAL);
        assertEffect(MobEffect.WIND_CHARGED, 12438015, MobEffect.Category.HARMFUL);
        assertEffect(MobEffect.WEAVING, 7891290, MobEffect.Category.HARMFUL);
        assertEffect(MobEffect.OOZING, 10092451, MobEffect.Category.HARMFUL);
        assertEffect(MobEffect.INFESTED, 9214860, MobEffect.Category.HARMFUL);
    }

    /** 바닐라 1.21.1 에서 InstantenousMobEffect 를 잇는 효과는 즉시 치유·즉시 피해·포화 셋뿐이다. */
    @Test
    void onlyHealDamageAndSaturationAreInstantaneous() {
        List<String> instant = MobEffect.all().stream().filter(MobEffect::isInstantaneous).map(MobEffect::name).toList();
        assertEquals(List.of("minecraft:instant_health", "minecraft:instant_damage", "minecraft:saturation"), instant);
    }

    @Test
    void saturationInstantApplicationFeedsPlayerOnce() {
        int[] eaten = new int[1];
        float[] mod = new float[1];
        MobEffect.Host h = new MobEffect.Host() {
            public float health() { return 20; }
            public float maxHealth() { return 20; }
            public void heal(float a) { }
            public boolean hurtBy(String k, float a) { return false; }
            public boolean isPlayer() { return true; }
            public void causeFoodExhaustion(float a) { }
            public void eat(int n, float m) { eaten[0] += n; mod[0] = m; }
            public float absorption() { return 0; }
            public void setAbsorption(float a) { }
        };
        MobEffect.SATURATION.applyInstantenousEffect(h, 1, 1.0);
        assertEquals(2, eaten[0]);
        assertEquals(1.0F, mod[0]);
    }

    private static void assertEffect(MobEffect e, int color, MobEffect.Category category) {
        assertNotNull(e);
        assertEquals(color, e.color(), e.name());
        assertEquals(category, e.category(), e.name());
    }
}
