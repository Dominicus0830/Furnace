package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** 서버 없이 확인되는 Bukkit 값 타입들: ChatColor·NamespacedKey·Material·YamlConfiguration. */
class ApiValueTypesTest {

    @Test
    void chatColorTranslatesAndStrips() {
        assertEquals("§aHi §lthere", ChatColor.translateAlternateColorCodes('&', "&aHi &lthere"));
        assertEquals("Hi there", ChatColor.stripColor("§aHi §lthere"));
        assertEquals("§a", ChatColor.GREEN.toString());
        assertEquals(ChatColor.RED, ChatColor.getByChar('c'));
        assertTrue(ChatColor.BOLD.isFormat());
        assertTrue(ChatColor.GOLD.isColor());
        assertFalse(ChatColor.RESET.isColor());
        assertEquals("§c§l", ChatColor.getLastColors("x§cy§lz"));
        // '&' 뒤가 색 코드가 아니면 그대로 둔다
        assertEquals("a&zb", ChatColor.translateAlternateColorCodes('&', "a&zb"));
    }

    @Test
    void namespacedKeyParses() {
        assertEquals("minecraft:stone", NamespacedKey.minecraft("stone").toString());
        assertEquals(NamespacedKey.minecraft("stone"), NamespacedKey.fromString("stone"));
        assertEquals("my:thing", NamespacedKey.fromString("MY:Thing").toString());
        assertNull(NamespacedKey.fromString("bad key"));
        assertNull(NamespacedKey.fromString("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedKey("ns", "Bad Key"));
    }

    private static Set<String> lines(String resource) throws Exception {
        Set<String> out = new TreeSet<>();
        try (InputStream in = ApiValueTypesTest.class.getResourceAsStream(resource);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                l = l.strip();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String k = l.split("\\s+")[0];
                if (k.startsWith("minecraft:")) out.add(k.substring("minecraft:".length()));
            }
        }
        return out;
    }

    /** Material 은 블록·아이템 레지스트리의 합집합과 같아야 한다(생성기를 다시 돌리지 않고 버전만 올리는 실수를 막는다). */
    @Test
    void materialMatchesTheRegistries() throws Exception {
        Set<String> blocks = lines("/blocks/1.21.1.txt");
        Set<String> items = lines("/items/1.21.1.txt");
        Set<String> union = new TreeSet<>(blocks);
        union.addAll(items);
        Set<String> mats = new TreeSet<>();
        for (Material m : Material.values()) {
            String k = m.name().toLowerCase();
            mats.add(k);
            assertEquals(blocks.contains(k), m.isBlock(), k);
            assertEquals(items.contains(k), m.isItem(), k);
            assertEquals("minecraft:" + k, m.getKey().toString());
        }
        assertEquals(union, mats);
        assertEquals(64, Material.STONE.getMaxStackSize());
        assertEquals(1, Material.DIAMOND_SWORD.getMaxStackSize());
        assertEquals(1561, Material.DIAMOND_SWORD.getMaxDurability());
        assertEquals(16, Material.ENDER_PEARL.getMaxStackSize());
        assertTrue(Material.CAVE_AIR.isAir());
        assertEquals(Material.OAK_PLANKS, Material.matchMaterial("minecraft:oak planks"));
        assertEquals(Material.STONE, Material.matchMaterial("STONE"));
        assertNull(Material.matchMaterial("nothing"));
        assertNull(Material.getMaterial("stone"));
    }

    @Test
    void yamlConfigurationBehavesLikeBukkit() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                # 머리말
                a: 1
                list: [x, y]
                s:
                  t: true
                  d: 2.5
                  name: "hi §a"
                """);
        assertEquals(1, y.getInt("a"));
        assertEquals(List.of("x", "y"), y.getStringList("list"));
        assertTrue(y.getBoolean("s.t"));
        assertEquals(2.5, y.getDouble("s.d"));
        assertEquals("hi §a", y.getString("s.name"));
        assertEquals(Set.of("a", "list", "s"), y.getKeys(false));
        assertEquals(Set.of("a", "list", "s", "s.t", "s.d", "s.name"), y.getKeys(true));
        assertTrue(y.contains("s.t"));
        assertFalse(y.contains("s.zzz"));
        assertEquals("fallback", y.getString("zzz", "fallback"));
        assertEquals(5, y.getInt("zzz", 5));

        // 기본값(addDefault) — 값이 없을 때만 보인다
        y.addDefault("zzz", "def");
        assertEquals("def", y.getString("zzz"));
        y.set("zzz", "real");
        assertEquals("real", y.getString("zzz"));

        // 저장 문자열을 다시 읽어도 같다
        String saved = y.saveToString();
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(saved);
        assertEquals("real", again.getString("zzz"));
        assertEquals(List.of("x", "y"), again.getStringList("list"));
        assertEquals(2.5, again.getDouble("s.d"));

        y.set("s", null);
        assertFalse(y.contains("s.t"));
    }
}
