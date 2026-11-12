package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** 옛 이름 표의 새 이름이 모두 이 호환 계층에 정적 필드로 있다(없으면 바뀐 GETSTATIC 이 NoSuchFieldError). */
class ApiRenamesTest {

    @Test
    void everyRenameTargetIsARealStaticField() throws Exception {
        Field fields = ApiRenames.class.getDeclaredField("FIELDS");
        fields.setAccessible(true);
        @SuppressWarnings("unchecked")
        var all = (java.util.Map<String, java.util.Map<String, String>>) fields.get(null);
        int checked = 0;
        for (var e : all.entrySet()) {
            Class<?> owner = Class.forName(e.getKey().replace('/', '.'));
            for (var r : e.getValue().entrySet()) {
                Field f = owner.getField(r.getValue());
                assertTrue(Modifier.isStatic(f.getModifiers()) && f.getType() == owner, owner + "." + r.getValue());
                // 옛 이름이 새 이름과 겹쳐 남아 있으면 안 된다(바꾸면 다른 상수가 된다) — Paper 의 FieldRename 도 지금 있는 이름은 바꾸지 않는다
                assertThrows(NoSuchFieldException.class, () -> owner.getField(r.getKey()), owner + "." + r.getKey());
                checked++;
            }
        }
        assertEquals(110, checked);
    }

    @Test
    void valueOfBridgesTakeOldAndNewNames() {
        assertEquals(org.bukkit.Particle.POOF, ApiRenames.valueOfParticle("EXPLOSION_NORMAL"));
        assertEquals(org.bukkit.Particle.DUST, ApiRenames.valueOfParticle("DUST"));
        assertEquals(org.bukkit.block.Biome.NETHER_WASTES, ApiRenames.valueOfBiome("NETHER"));
        assertEquals(org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN, ApiRenames.valueOfEntityType("PIG_ZOMBIE"));
        assertEquals(org.bukkit.potion.PotionType.LEAPING, ApiRenames.valueOfPotionType("JUMP"));
        assertThrows(IllegalArgumentException.class, () -> ApiRenames.valueOfParticle("NOPE"));
        assertThrows(NullPointerException.class, () -> ApiRenames.valueOfBiome(null));   // Enum.valueOf 와 같다
        assertEquals("SHARPNESS", ApiRenames.field("org/bukkit/enchantments/Enchantment", "DAMAGE_ALL"));
        assertEquals("STRENGTH", ApiRenames.field("org/bukkit/potion/PotionEffectType", "INCREASE_DAMAGE"));
        assertEquals("STONE", ApiRenames.field("org/bukkit/Material", "STONE"));
    }
}
