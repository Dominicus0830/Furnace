package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * A15 후속(PR #253 검토 R2): Paper MemorySection.set 은 객체 참조를 그대로 보관한다. set → get 이 같은 참조를 주고,
 * 등록하지 않은 클래스도 메모리에서는 되돌려 받으며, YAML 은 쓰는 순간의 serialize() 를 쓴다.
 */
class A15ConfigRetainTest {

    /** 등록하지 않은 플러그인 클래스. */
    public static final class Home implements ConfigurationSerializable {
        public String name;
        public int level;

        public Home(String name, int level) {
            this.name = name;
            this.level = level;
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("level", level);
            return m;
        }

        public static Home deserialize(Map<String, Object> m) { return new Home((String) m.get("name"), (Integer) m.get("level")); }
    }

    @Test
    void getReturnsTheSameReferenceAndSeesLaterChanges() {
        YamlConfiguration cfg = new YamlConfiguration();
        BoundingBox box = new BoundingBox(0, 0, 0, 1, 1, 1);
        cfg.set("box", box);
        box.expand(1);
        assertSame(box, cfg.get("box"));
        assertSame(box, cfg.getSerializable("box", BoundingBox.class));
        assertEquals(-1.0, ((BoundingBox) cfg.get("box")).getMinX());
        assertSame(box, cfg.getValues(false).get("box"));
        // 저장은 쓰는 순간의 값
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("minX: -1.0"), yaml);
        box.expand(1);
        assertTrue(cfg.saveToString().contains("minX: -2.0"));
    }

    @Test
    void unregisteredClassComesBackInMemoryButNotAsASection() {
        YamlConfiguration cfg = new YamlConfiguration();
        Home home = new Home("base", 3);
        cfg.set("homes.main", home);
        assertSame(home, cfg.get("homes.main"));
        assertSame(home, cfg.getSerializable("homes.main", Home.class));
        assertSame(home, cfg.getConfigurationSection("homes").get("main"));
        assertNull(cfg.getConfigurationSection("homes.main"));
        assertFalse(cfg.isConfigurationSection("homes.main"));
        home.level = 4;
        String yaml = cfg.saveToString();
        assertTrue(yaml.contains("level: 4"), yaml);
        assertTrue(yaml.contains("'==': " + Home.class.getName()), yaml);
    }

    @Test
    void overwriteAndRemoveDropTheReference() {
        YamlConfiguration cfg = new YamlConfiguration();
        Vector a = new Vector(1, 2, 3);
        cfg.set("v", a);
        cfg.set("v", 5);
        assertEquals(5, cfg.get("v"));
        Vector b = new Vector(4, 5, 6);
        cfg.set("v", b);
        assertSame(b, cfg.get("v"));
        cfg.set("v", null);
        assertNull(cfg.get("v"));
        cfg.set("w", a);
        cfg.set("w.x", 1);   // 객체 자리에 구역이 들어오면 객체는 없어진다
        assertTrue(cfg.get("w") instanceof ConfigurationSection);
    }

    @Test
    void customPathSeparatorKeepsReferenceToo() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.options().pathSeparator('/');
        BoundingBox box = new BoundingBox(0, 0, 0, 1, 1, 1);
        cfg.set("a/b", box);
        assertSame(box, cfg.get("a/b"));
    }

    @Test
    void loadedYamlStillRevivesRegisteredClassesAsNewObjects() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("box", new BoundingBox(0, 0, 0, 1, 1, 1));
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(cfg.saveToString());
        assertEquals(new BoundingBox(0, 0, 0, 1, 1, 1), back.get("box"));
    }

    @Test
    void registeredAfterTheFactStillSavesAndReloads() throws Exception {
        ConfigurationSerialization.registerClass(Home.class);
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            Home home = new Home("x", 1);
            cfg.set("h", home);
            assertSame(home, cfg.get("h"));
            YamlConfiguration back = new YamlConfiguration();
            back.loadFromString(cfg.saveToString());
            Home read = (Home) back.get("h");
            assertEquals("x", read.name);
            assertEquals(1, read.level);
        } finally {
            ConfigurationSerialization.unregisterClass(Home.class);
        }
    }
}
