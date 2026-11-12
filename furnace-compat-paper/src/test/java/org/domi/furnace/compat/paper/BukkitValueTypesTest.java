package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Art;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.domi.furnace.api.world.ParticleEffect;
import org.junit.jupiter.api.Test;

/**
 * H4 묶음 C 값 타입: 생성된 열거형이 서버의 1.21.1 데이터와 같고(생성기 tools/compat/gen-bukkit-values.py 를 다시 돌리지 않고 버전만 올리는 실수를 막는다),
 * 손으로 쓴 값 타입이 Bukkit 과 같은 규칙으로 동작한다.
 */
class BukkitValueTypesTest {

    private static List<String[]> lines(String resource) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (InputStream in = BukkitValueTypesTest.class.getResourceAsStream(resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                l = l.strip();
                if (!l.isEmpty() && !l.startsWith("#")) out.add(l.split("\\s+"));
            }
        }
        return out;
    }

    private static Set<String> builtin(String registry) throws Exception {
        Set<String> out = new TreeSet<>();
        for (String[] p : lines("/registries/builtin-1.21.1.txt")) if (p[0].equals(registry)) out.add(p[1]);
        return out;
    }

    private static <T extends org.bukkit.Keyed> Set<String> keys(Iterable<T> values) {
        Set<String> out = new TreeSet<>();
        for (T t : values) out.add(t.getKey().toString());
        return out;
    }

    // ---- 레지스트리와 같은가

    @Test
    void soundMatchesTheRegistry() throws Exception {
        assertEquals(builtin("minecraft:sound_event"), keys(List.of(Sound.values())));
        assertEquals(Sound.values().length, keys(List.of(Sound.values())).size());
        assertSame(Sound.ENTITY_PLAYER_LEVELUP, Registry.SOUNDS.get(NamespacedKey.minecraft("entity.player.levelup")));
        assertEquals("minecraft:block.note_block.harp", Sound.BLOCK_NOTE_BLOCK_HARP.getKey().toString());
    }

    @Test
    void particleMatchesTheRegistryWithBukkitDataTypes() throws Exception {
        Set<String> reg = new TreeSet<>();
        for (String[] p : lines("/registries/particle_types-1.21.1.txt")) reg.add(p[0]);
        assertEquals(reg, keys(List.of(Particle.values())));
        assertEquals(Particle.DustOptions.class, Particle.DUST.getDataType());
        assertEquals(Particle.DustTransition.class, Particle.DUST_COLOR_TRANSITION.getDataType());
        assertEquals(org.bukkit.block.data.BlockData.class, Particle.BLOCK.getDataType());
        assertEquals(org.bukkit.block.data.BlockData.class, Particle.FALLING_DUST.getDataType());
        assertEquals(org.bukkit.inventory.ItemStack.class, Particle.ITEM.getDataType());
        assertEquals(Color.class, Particle.ENTITY_EFFECT.getDataType());
        assertEquals(org.bukkit.Vibration.class, Particle.VIBRATION.getDataType());
        assertEquals(Float.class, Particle.SCULK_CHARGE.getDataType());
        assertEquals(Integer.class, Particle.SHRIEK.getDataType());
        assertEquals(Void.class, Particle.FLAME.getDataType());
        // 서버 API 의 옵션 판정과 Bukkit 의 data 형이 같은 입자를 가리킨다
        for (Particle p : Particle.values()) {
            assertEquals(p.getDataType() != Void.class, ParticleEffect.WITH_OPTIONS.contains(p.getKey().toString()), p.name());
        }
    }

    @Test
    void biomeMatchesTheRegistryPlusCustom() throws Exception {
        Set<String> reg = new TreeSet<>();
        for (String[] p : lines("/registries/1.21.1.txt")) if (p[0].equals("minecraft:worldgen/biome")) reg.add(p[1]);
        Set<String> have = keys(Registry.BIOME);
        assertEquals(reg, have);
        assertEquals(reg.size() + 1, Biome.values().length);
        assertNull(Registry.BIOME.get(NamespacedKey.minecraft("custom")));
        assertSame(Biome.PLAINS, Registry.BIOME.match("Plains"));
    }

    @Test
    void artMatchesPaintingVariants() throws Exception {
        List<String[]> rows = lines("/entities/painting_variants-1.21.1.txt");
        assertEquals(rows.size(), Art.values().length);
        for (String[] p : rows) {
            Art a = Registry.ART.get(NamespacedKey.minecraft(p[0]));
            assertEquals(Integer.parseInt(p[1]), a.getBlockWidth(), p[0]);
            assertEquals(Integer.parseInt(p[2]), a.getBlockHeight(), p[0]);
            assertSame(a, Art.getById(a.getId()));
        }
        assertSame(Art.KEBAB, Art.getById(0));   // 1.21 전 번호는 그대로
        assertSame(Art.KEBAB, Art.getByName("kebab"));
    }

    @Test
    void attributeMatchesTheRegistry() throws Exception {
        assertEquals(builtin("minecraft:attribute"), keys(List.of(Attribute.values())));
        assertEquals("minecraft:generic.max_health", Attribute.GENERIC_MAX_HEALTH.getKey().toString());
        assertEquals("attribute.name.generic.max_health", Attribute.GENERIC_MAX_HEALTH.getTranslationKey());
    }

    @Test
    void potionTypeMatchesServerPotions() throws Exception {
        assertEquals(builtin("minecraft:potion"), keys(List.of(PotionType.values())));
        for (PotionType t : PotionType.values()) {
            var server = org.domi.furnace.server.effect.Potions.effectsOf(t.getKey().toString());
            List<PotionEffect> mine = t.getPotionEffects();
            assertEquals(server.size(), mine.size(), t.name());
            for (int i = 0; i < server.size(); i++) {
                var s = server.get(i);
                PotionEffect m = mine.get(i);
                assertEquals(s.effect().name(), m.getType().getKey().toString(), t.name());
                assertEquals(s.duration(), m.getDuration(), t.name());
                assertEquals(s.amplifier(), m.getAmplifier(), t.name());
                assertEquals(s.ambient(), m.isAmbient(), t.name());
                assertEquals(s.visible(), m.hasParticles(), t.name());
                assertEquals(s.showIcon(), m.hasIcon(), t.name());
            }
        }
        assertTrue(PotionType.SWIFTNESS.isUpgradeable() && PotionType.SWIFTNESS.isExtendable());
        assertFalse(PotionType.HEALING.isExtendable());
        assertTrue(PotionType.HEALING.isInstant());
        assertSame(PotionEffectType.SPEED, PotionType.SWIFTNESS.getEffectType());
        assertNull(PotionType.WATER.getEffectType());
    }

    @Test
    void dyeColorsMatchServerDyeTables() throws Exception {
        var tex = org.domi.furnace.server.recipe.SpecialRecipes.class.getDeclaredField("TEXTURE");
        tex.setAccessible(true);
        int[] texture = (int[]) tex.get(null);
        List<String> dyes = org.domi.furnace.server.recipe.SpecialRecipes.DYES;
        assertEquals(dyes.size(), DyeColor.values().length);
        for (DyeColor d : DyeColor.values()) {
            int i = dyes.indexOf(d.name().toLowerCase(java.util.Locale.ROOT));
            assertEquals(i, d.getWoolData(), d.name());
            assertEquals(15 - i, d.getDyeData(), d.name());
            assertEquals(texture[i], d.getColor().asRGB(), d.name());
            assertEquals(org.domi.furnace.server.recipe.SpecialRecipes.FIREWORK[i], d.getFireworkColor().asRGB(), d.name());
        }
        assertSame(DyeColor.LIGHT_GRAY, DyeColor.legacyValueOf("SILVER"));
    }

    @Test
    void registryLookupsFollowBukkit() {
        assertSame(Registry.SOUNDS, org.bukkit.Bukkit.getRegistry(Sound.class));
        assertSame(Registry.ENCHANTMENT, org.bukkit.Bukkit.getRegistry(org.bukkit.enchantments.Enchantment.class));
        assertNull(org.bukkit.Bukkit.getRegistry(org.bukkit.Keyed.class));
        assertThrows(IllegalArgumentException.class, () -> Registry.PARTICLE_TYPE.getOrThrow(NamespacedKey.minecraft("nope")));
        assertSame(org.bukkit.enchantments.Enchantment.SHARPNESS, Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness")));
        assertSame(PotionEffectType.SPEED, Registry.EFFECT.get(NamespacedKey.minecraft("speed")));
        assertNull(Registry.ENTITY_TYPE.get(NamespacedKey.minecraft("unknown")));
        assertSame(org.bukkit.Material.DIAMOND_SWORD, Registry.MATERIAL.match("Diamond Sword"));
        assertEquals(NamespacedKey.minecraft("poof"), Registry.PARTICLE_TYPE.getKeyOrThrow(Particle.POOF));
    }

    // ---- 손으로 쓴 값 타입

    @Test
    void colorFollowsBukkit() {
        Color c = Color.fromRGB(0x12, 0x34, 0x56);
        assertEquals(0x123456, c.asRGB());
        assertEquals(0xFF123456, c.asARGB());
        assertEquals(0x563412, c.asBGR());
        assertEquals(c, Color.fromBGR(0x563412));
        assertEquals(0x80123456, Color.fromARGB(0x80123456).asARGB());
        assertNotEquals(c, c.setAlpha(0x80));
        assertThrows(IllegalArgumentException.class, () -> Color.fromRGB(0x1000000));
        assertThrows(IllegalArgumentException.class, () -> Color.fromRGB(256, 0, 0));
        assertEquals("Color:[argb0xFF123456]", c.toString());
        // Bukkit 식: 정수 평균 (127,0,127), 최댓값 평균 255 → 이득 255/127 → (255,0,255). 셋이면 정수 나눗셈 버림이 드러난다
        assertEquals(Color.fromRGB(255, 0, 255), Color.RED.mixColors(Color.BLUE));
        assertEquals(Color.fromRGB(100, 50, 0), Color.fromRGB(100, 50, 0).mixColors());
        assertEquals(Color.fromRGB(227, 44, 178), Color.RED.mixColors(Color.fromRGB(0, 51, 200)));   // 실수 나눗셈이면 (227,45,178)
        assertEquals(Color.fromRGB(152, 31, 118), Color.RED.mixColors(Color.fromRGB(0, 51, 200), Color.fromRGB(3, 3, 3)));
        Map<String, Object> m = c.serialize();
        assertEquals(c, Color.deserialize(m));
        assertEquals(c, ConfigurationSerialization.deserializeObject(m, Color.class));
    }

    @Test
    void noteFollowsBukkit() {
        Note fs0 = Note.sharp(0, Note.Tone.F);
        assertEquals(0, fs0.getId());
        assertEquals(11, Note.natural(0, Note.Tone.F).getId());
        assertEquals(12, Note.sharp(1, Note.Tone.F).getId());
        assertEquals(24, Note.sharp(2, Note.Tone.F).getId());
        assertThrows(IllegalArgumentException.class, () -> Note.sharp(2, Note.Tone.G));
        assertThrows(IllegalArgumentException.class, () -> new Note(25));
        assertEquals(Note.Tone.G, new Note(1).getTone());
        assertTrue(Note.flat(1, Note.Tone.G).isSharped());   // G♭ = F#
        assertEquals(1.0F, new Note(12).getPitch(), 1e-6);
        assertEquals(0.5F, new Note(0).getPitch(), 1e-6);
        assertEquals(2.0F, new Note(24).getPitch(), 1e-6);
    }

    @Test
    void fireworkEffectBuilderAndSerialization() {
        FireworkEffect e = FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.RED, Color.BLUE).withFade(Color.WHITE).flicker(true).build();
        assertEquals(FireworkEffect.Type.STAR, e.getType());
        assertEquals(List.of(Color.RED, Color.BLUE), e.getColors());
        assertTrue(e.hasFlicker() && !e.hasTrail());
        assertThrows(IllegalStateException.class, () -> FireworkEffect.builder().build());
        ConfigurationSerializable back = ConfigurationSerialization.deserializeObject(e.serialize(), FireworkEffect.class);
        assertEquals(e, back);
    }

    @Test
    void attributeModifierKeepsKeyAndSlot() {
        AttributeModifier m = new AttributeModifier(new NamespacedKey("plug", "speed"), 0.1, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.HAND);
        assertEquals(new NamespacedKey("plug", "speed"), m.getKey());
        assertEquals(EquipmentSlotGroup.HAND, m.getSlotGroup());
        assertEquals(m, ConfigurationSerialization.deserializeObject(m.serialize(), AttributeModifier.class));
        assertTrue(EquipmentSlotGroup.ARMOR.test(org.bukkit.inventory.EquipmentSlot.HEAD));
        assertFalse(EquipmentSlotGroup.ARMOR.test(org.bukkit.inventory.EquipmentSlot.HAND));
    }

    // ---- Bukkit 소리·입자 → Furnace 값

    @Test
    void effectsConvertLikeCraftBukkit() {
        var s = CompatEffects.sound(CompatEffects.key(Sound.ENTITY_PLAYER_LEVELUP), SoundCategory.PLAYERS, 1.5F, 3F);
        assertEquals("minecraft:entity.player.levelup", s.key());
        assertEquals(org.domi.furnace.api.audience.Sound.Source.PLAYER, s.source());
        assertEquals(2.0F, s.pitch());   // Bukkit 은 검사하지 않는다 — 클라이언트가 2 로 죄는 것과 같다
        assertThrows(IllegalArgumentException.class, () -> CompatEffects.particle(Particle.DUST, null));
        assertThrows(IllegalArgumentException.class, () -> CompatEffects.particle(Particle.DUST, Color.RED));
        assertThrows(IllegalArgumentException.class, () -> CompatEffects.particle(Particle.FLAME, Color.RED));
        assertEquals(ParticleEffect.of("minecraft:flame"), CompatEffects.particle(Particle.FLAME, null));
        assertEquals(new ParticleEffect.Dust(0xFF0000, 2F), CompatEffects.particle(Particle.DUST, new Particle.DustOptions(Color.RED, 2F)));
        assertEquals(new ParticleEffect.DustTransition(0xFF0000, 0x0000FF, 1F),
                CompatEffects.particle(Particle.DUST_COLOR_TRANSITION, new Particle.DustTransition(Color.RED, Color.BLUE, 1F)));
        assertEquals(new ParticleEffect.ColorEffect(0x80FF0000), CompatEffects.particle(Particle.ENTITY_EFFECT, Color.RED.setAlpha(0x80)));
        // DustTransition 은 DustOptions 이기도 하다 — DUST 에 주면 처음 색의 보통 먼지(CraftParticle 과 같다)
        assertEquals(new ParticleEffect.Dust(0xFF0000, 1F), CompatEffects.particle(Particle.DUST, new Particle.DustTransition(Color.RED, Color.BLUE, 1F)));
    }
}
