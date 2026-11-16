package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Art;
import org.bukkit.FireworkEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

/** PR #241 독립 검토 재현(R1~R10) — 각 단언은 Paper 1.21.1 동작이다. */
class ReviewPr241ReproTest {

    /** R1: CraftWorld 가 10인자(extra, data) 판을 override 해 force=false 로 부른다(인터페이스 default 의 true 는 쓰이지 않음). */
    @Test
    void r1_worldSpawnParticleDefaultForceIsFalse() {
        AtomicReference<Boolean> force = new AtomicReference<>();
        InvocationHandler h = (proxy, m, args) -> {
            if (m.getName().equals("spawnParticle") && m.getParameterCount() == 13) { force.set((Boolean) args[12]); return null; }
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            return null;
        };
        World w = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, h);
        w.spawnParticle(Particle.FLAME, 0, 64, 0, 1);
        assertEquals(Boolean.FALSE, force.get(), "Paper 1.21.1 World#spawnParticle(…) 기본 force");
    }

    /** R2: FieldRenameData.getReplacement 가 이름을 대문자로 바꾼 뒤 찾으므로 valueOf 다리는 소문자도 받는다. */
    @Test
    void r2_valueOfBridgeUppercases() {
        assertEquals(Particle.DUST, assertDoesNotThrow(() -> ApiRenames.valueOfParticle("redstone")));
        assertEquals(Particle.FLAME, assertDoesNotThrow(() -> ApiRenames.valueOfParticle("flame")));
        assertEquals(PotionType.LEAPING, assertDoesNotThrow(() -> ApiRenames.valueOfPotionType("jump")));
    }

    /** R3: 1.21.1 FieldRename 의 Attribute 표(HORSE.JUMP_STRENGTH → GENERIC.JUMP_STRENGTH, '.'→'_'). */
    @Test
    void r3_attributeHorseJumpStrengthRename() {
        assertEquals("GENERIC_JUMP_STRENGTH", ApiRenames.field("org/bukkit/attribute/Attribute", "HORSE_JUMP_STRENGTH"));
    }

    /** R4: Paper General-ItemMeta-fixes — 색 없는 FireworkEffect 를 허용한다. */
    @Test
    void r4_fireworkWithoutColors() {
        FireworkEffect e = assertDoesNotThrow(() -> FireworkEffect.builder().with(FireworkEffect.Type.STAR).build());
        assertEquals(0, e.getColors().size());
    }

    /** R5: getUniqueId 는 UUID 모양이 아닌 키면 nameUUIDFromBytes(키 문자열). */
    @Test
    void r5_attributeModifierUuidOfNonUuidKey() {
        var m = new AttributeModifier(new NamespacedKey("myplugin", "speed"), 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
        UUID u = assertDoesNotThrow(m::getUniqueId);
        assertEquals(UUID.nameUUIDFromBytes("myplugin:speed".getBytes(StandardCharsets.UTF_8)), u);
    }

    /** R5: Paper 3인자 생성자(Attribute-Modifier-API-improvements). */
    @Test
    void r5_attributeModifierThreeArgCtor() {
        assertDoesNotThrow(() -> AttributeModifier.class.getConstructor(NamespacedKey.class, double.class, AttributeModifier.Operation.class));
    }

    /** R6: 1.21.1 Bukkit 열거형 순서·Art 번호. */
    @Test
    void r6_ordinalsAndArtIds() {
        assertEquals(0, Particle.POOF.ordinal());
        assertEquals(19, PotionType.WATER_BREATHING.ordinal());
        assertEquals(30, Art.BAROQUE.getId());
        assertEquals(Art.BAROQUE, Art.getById(30));
    }

    /** R10: Bukkit Note#getPitch 는 float 지수로 계산(note 1 에서 1ulp 차이). */
    @Test
    void r10_notePitchFloatExponent() {
        assertEquals((float) Math.pow(2, (1 - 12) / 12f), new Note(1).getPitch());
        assertFalse(Float.isNaN(new Note(1).getPitch()));
    }
}
