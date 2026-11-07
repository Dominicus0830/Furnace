package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

/** EntityType·Enchantment 는 서버 레지스트리 1.21.1 과 같아야 한다(생성기 tools/compat/gen-bukkit-registries.py 를 다시 돌리지 않고 버전만 올리는 실수를 막는다). */
class RegistryValueTypesTest {

    private static Set<String> keys(String resource, String registry) throws Exception {
        Set<String> out = new TreeSet<>();
        try (InputStream in = RegistryValueTypesTest.class.getResourceAsStream(resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                l = l.strip();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] p = l.split("\\s+");
                String k = registry == null ? p[0] : p[0].equals(registry) ? p[1] : null;
                if (k != null && k.startsWith("minecraft:")) out.add(k.substring("minecraft:".length()));
            }
        }
        return out;
    }

    @Test
    void entityTypeMatchesTheRegistry() throws Exception {
        Set<String> reg = keys("/registries/entity_types-1.21.1.txt", null);
        Set<String> have = new TreeSet<>();
        for (EntityType t : EntityType.values()) {
            if (t == EntityType.UNKNOWN) continue;
            have.add(t.name().toLowerCase(Locale.ROOT));
            assertEquals(NamespacedKey.minecraft(t.name().toLowerCase(Locale.ROOT)), t.getKey());
        }
        assertEquals(reg, have);
        assertSame(EntityType.ARMOR_STAND, EntityType.fromKey("minecraft:armor_stand"));
        assertSame(EntityType.ZOMBIE, EntityType.fromKey("ZOMBIE"));
        assertNull(EntityType.fromKey("nope"));
        assertThrows(IllegalArgumentException.class, EntityType.UNKNOWN::getKey);
    }

    @Test
    void enchantmentMatchesTheRegistry() throws Exception {
        Set<String> reg = keys("/registries/1.21.1.txt", "minecraft:enchantment");
        Set<String> have = new TreeSet<>();
        for (Enchantment e : Enchantment.all()) have.add(e.getKey().getKey());
        assertEquals(reg, have);
        assertSame(Enchantment.SHARPNESS, Enchantment.getByKey(NamespacedKey.minecraft("sharpness")));
        assertSame(Enchantment.UNBREAKING, Enchantment.fromKey("minecraft:unbreaking"));
        assertNull(Enchantment.getByKey(new NamespacedKey("other", "sharpness")));
        assertNull(Enchantment.fromKey("nope"));
        // 서버가 알려 주지 않는 값은 지어내지 않는다
        assertThrows(UnsupportedFeature.class, Enchantment.SHARPNESS::getMaxLevel);
    }
}
