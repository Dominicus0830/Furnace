package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * A15-05: {@code options().pathSeparator(c)} 를 바꾸면 Paper 처럼 그 문자로 경로를 나눈다(점이 든 키가 한 단계). 기대값은 Paper 1.21.1
 * {@code MemorySection} 의 경로 해석(루트 옵션의 구분자로 단계를 나눈다)에서 옮겼다.
 */
class A15PathSeparatorParityTest {

    private static YamlConfiguration slash() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.options().pathSeparator('/');
        return cfg;
    }

    @Test
    void optionAcceptsAnySeparator() {
        YamlConfiguration cfg = new YamlConfiguration();
        assertEquals('.', cfg.options().pathSeparator());
        assertEquals(cfg.options(), cfg.options().pathSeparator('|'), "Paper: 옵션 객체를 돌려준다(연쇄 호출)");
        assertEquals('|', cfg.options().pathSeparator());
    }

    @Test
    void dottedKeysAreSingleSteps() throws Exception {
        YamlConfiguration cfg = slash();
        cfg.set("minecraft:stone.cost", 5);
        cfg.set("shops.main/items/diamond", 9);
        assertEquals(5, cfg.getInt("minecraft:stone.cost"));
        assertEquals(9, cfg.getInt("shops.main/items/diamond"));
        assertTrue(cfg.isConfigurationSection("shops.main"));
        assertTrue(cfg.isConfigurationSection("shops.main/items"));
        assertEquals(Set.of("minecraft:stone.cost", "shops.main"), cfg.getKeys(false));
        assertEquals(Set.of("minecraft:stone.cost", "shops.main", "shops.main/items", "shops.main/items/diamond"), cfg.getKeys(true));
        // 저장하면 점이 든 키는 한 키로 쓰인다
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("minecraft:stone.cost: 5"), yaml);
        assertTrue(yaml.contains("shops.main:"), yaml);
        YamlConfiguration back = slash();
        back.loadFromString(yaml);
        assertEquals(9, back.getInt("shops.main/items/diamond"));
        assertEquals(5, back.getInt("minecraft:stone.cost"));
        // 점은 더 이상 경로 구분자가 아니다
        assertFalse(cfg.contains("shops"));
        assertFalse(cfg.contains("shops.main.items"));
        assertNull(cfg.get("shops.main.items.diamond"));
    }

    @Test
    void sectionsKnowTheirPathAndName() {
        YamlConfiguration cfg = slash();
        cfg.set("a.b/c.d/e", 1);
        ConfigurationSection mid = cfg.getConfigurationSection("a.b/c.d");
        assertNotNull(mid);
        assertEquals("a.b/c.d", mid.getCurrentPath());
        assertEquals("c.d", mid.getName());
        assertEquals(1, mid.getInt("e"));
        assertEquals(Set.of("e"), mid.getKeys(false));
        mid.set("f/g", 2);
        assertEquals(2, cfg.getInt("a.b/c.d/f/g"));
        assertEquals(Set.of("e", "f", "f/g"), mid.getKeys(true));
    }

    @Test
    void removeContainsAndCreateSection() {
        YamlConfiguration cfg = slash();
        ConfigurationSection s = cfg.createSection("x.y/z.w");
        s.set("k", true);
        assertTrue(cfg.getBoolean("x.y/z.w/k"));
        assertTrue(cfg.contains("x.y/z.w"));
        cfg.set("x.y/z.w/k", null);
        assertFalse(cfg.contains("x.y/z.w/k"));
        assertTrue(cfg.contains("x.y/z.w"), "비운 구역은 남는다");
        cfg.set("x.y", null);
        assertFalse(cfg.contains("x.y/z.w"));
        assertTrue(cfg.getKeys(true).isEmpty());
    }

    @Test
    void defaultsUseTheSameSteps() {
        YamlConfiguration cfg = slash();
        cfg.addDefault("limits.max/players", 20);
        assertEquals(20, cfg.getInt("limits.max/players"));
        assertTrue(cfg.contains("limits.max/players"));
        assertFalse(cfg.contains("limits.max/players", true));
        assertEquals(30, cfg.getInt("limits.max/players", 30), "Paper: 명시한 기본값이 기본값 구역보다 앞선다(A15-03)");
        cfg.set("limits.max/players", 50);
        assertEquals(50, cfg.getInt("limits.max/players"));
        // 별도 기본값 설정
        YamlConfiguration jar = new YamlConfiguration();
        jar.options().pathSeparator('/');
        jar.set("a.b/c", "from-jar");
        YamlConfiguration user = slash();
        user.setDefaults(jar);
        assertEquals("from-jar", user.getString("a.b/c"));
    }

    @Test
    void commentsFollowTheSameSteps() {
        YamlConfiguration cfg = slash();
        cfg.set("a.b/c", 1);
        cfg.setComments("a.b/c", List.of("hello"));
        cfg.setInlineComments("a.b/c", List.of("inline"));
        assertEquals(List.of("hello"), cfg.getComments("a.b/c"));
        assertEquals(List.of("inline"), cfg.getInlineComments("a.b/c"));
        assertEquals(List.of(), cfg.getComments("nothing"));
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("# hello"), yaml);
    }

    @Test
    void defaultSeparatorStillSplitsOnDots() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("a.b.c", 1);
        assertEquals(Set.of("a", "a.b", "a.b.c"), cfg.getKeys(true));
        assertEquals(1, cfg.getConfigurationSection("a.b").getInt("c"));
        assertEquals("a.b", cfg.getConfigurationSection("a.b").getCurrentPath());
    }
}
