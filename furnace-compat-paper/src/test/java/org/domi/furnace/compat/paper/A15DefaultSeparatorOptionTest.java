package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

/**
 * A15-05 / R6 "둘 다 지원": {@code plugins.compat.config.paper-default-separator}.
 * 꺼짐(기본) = 플러그인이 정한 구분자로 기본값 트리를 쪼갠다(Furnace). 켬 = Paper 처럼 언제나 '.' (기대값은 PR #254 검토의 Paper 설정 클래스 실행 결과 시나리오 10·11·14·26·31·48).
 */
class A15DefaultSeparatorOptionTest {

    private static YamlConfiguration y(char sep) {
        YamlConfiguration c = new YamlConfiguration();
        c.options().pathSeparator(sep);
        return c;
    }

    private static FurnaceTestServer paper() {
        return FurnaceTestServer.builder().compatOption(ConfigPaths.PAPER_DEFAULT_SEPARATOR_OPTION, "true").start();
    }

    @Test
    void offByDefaultKeepsFurnaceBehavior() {
        assertFalse(ConfigPaths.paperDefaultSeparator(), "서버가 없으면 꺼짐");
        var c = y('/');
        c.addDefault("groups/default/x", 1);
        assertEquals('/', c.getDefaults().options().pathSeparator());
        assertTrue(c.contains("groups/default"));
        c.options().copyDefaults(true);
        assertEquals(List.of("groups", "groups/default", "groups/default/x"), List.copyOf(c.getKeys(true)));
        assertEquals("groups:\n  default:\n    x: 1\n", c.saveToString());
        try (var server = FurnaceTestServer.builder().start()) {
            assertFalse(ConfigPaths.paperDefaultSeparator(), "옵션을 안 적은 서버도 꺼짐");
        }
    }

    @Test
    void s10_dottedKeyInDefaults() {
        try (var server = paper()) {
            var c = y('/');
            c.addDefault("limits.max/players", 20);
            assertEquals(20, c.getInt("limits.max/players"));
            assertTrue(c.contains("limits.max/players"));
            assertFalse(c.contains("limits.max/players", true));
            assertFalse(c.isConfigurationSection("limits.max"));
            assertEquals(List.of("limits", "limits.max/players"), List.copyOf(c.getDefaults().getKeys(true)));
            assertEquals('.', c.getDefaults().options().pathSeparator());
            c.options().copyDefaults(true);
            assertEquals(List.of("limits", "limits.max/players"), List.copyOf(c.getKeys(true)));
            assertEquals("limits:\n  max/players: 20\n", c.saveToString());
        }
    }

    @Test
    void s11_slashKeyInDefaultsIsOneKey() {
        try (var server = paper()) {
            var c = y('/');
            c.addDefault("groups/default/x", 1);
            assertEquals(1, c.get("groups/default/x"));
            assertNull(c.getConfigurationSection("groups"));
            assertFalse(c.contains("groups/default"));
            c.options().copyDefaults(true);
            assertEquals(List.of("groups/default/x"), List.copyOf(c.getKeys(true)));
            assertEquals("groups/default/x: 1\n", c.saveToString());
        }
    }

    @Test
    void s14_addDefaultsFromConfiguration() {
        try (var server = paper()) {
            var src = y('/');
            src.set("a.b/c", 1);
            src.set("top", 2);
            var c = y('/');
            c.addDefaults(src);
            assertEquals(1, c.get("a.b/c"));
            assertTrue(c.contains("a.b/c"));
            assertEquals(2, c.get("top"));
            c.options().copyDefaults(true);
            assertEquals(List.of("a", "a.b/c", "top"), List.copyOf(c.getKeys(true)));
            assertEquals("a:\n  b/c: 1\ntop: 2\n", c.saveToString());
        }
    }

    @Test
    void s26_sectionAddDefault() {
        try (var server = paper()) {
            var c = y('/');
            c.set("s.t/u", 0);
            var s = c.getConfigurationSection("s.t");
            s.addDefault("d.e", 7);
            assertEquals(7, c.get("s.t/d.e"));
            assertEquals(7, s.get("d.e"));
            assertEquals(List.of("s", "s.t/d", "s.t/d.e"), List.copyOf(c.getDefaults().getKeys(true)));
        }
    }

    @Test
    void s31_saveMergesDefaultsWithOwnDottedSection() {
        try (var server = paper()) {
            var c = y('/');
            c.addDefault("k.v/a", 1);
            c.set("k.v/b", 2);
            c.options().copyDefaults(true);
            assertEquals("k:\n  v/a: 1\nk.v:\n  b: 2\n", c.saveToString());
        }
    }

    @Test
    void s48_trailingSeparatorDefaultIsALiteralKey() {
        try (var server = paper()) {
            var c = y('/');
            c.addDefault("a/", 1);
            assertNull(c.get("a"));
        }
    }

    /** PR #264 검토 R1(시나리오 60): setDefaults 로 받은 '/' 설정에는 그 설정 자신의 구분자로 쓴다(Paper defaults.set). */
    @Test
    void s60_addDefaultAfterSetDefaultsUsesThatConfigsSeparator() {
        try (var server = paper()) {
            var jar = y('/');
            jar.set("j/k", 0);
            var c = y('/');
            c.setDefaults(jar);
            c.addDefault("x.y/z", 1);
            assertEquals(1, c.get("x.y/z"));
            assertTrue(c.contains("x.y/z"));
            assertEquals(List.of("j", "j/k", "x.y", "x.y/z"), List.copyOf(jar.getKeys(true)));
            assertTrue(c.getDefaults() == jar);
            c.options().copyDefaults(true);
            assertEquals("j:\n  k: 0\nx.y:\n  z: 1\n", c.saveToString());
        }
    }

    /** 시나리오 77: 같은 경우 구역 단위 addDefault. */
    @Test
    void s77_sectionAddDefaultAfterSetDefaults() {
        try (var server = paper()) {
            var jar = y('/');
            var c = y('/');
            c.setDefaults(jar);
            c.set("s.t/u", 0);
            var s = c.getConfigurationSection("s.t");
            s.addDefault("d.e/f", 7);
            assertEquals(List.of("s.t", "s.t/d.e", "s.t/d.e/f"), List.copyOf(jar.getKeys(true)));
            assertEquals(7, s.get("d.e/f"));
        }
    }

    /** PR #264 검토 R3(시나리오 76): '.' 루트라도 setDefaults 로 받은 '/' 설정에는 그 구분자로 쓴다 — 옵션과 상관없이 Paper 와 같다. */
    @Test
    void s76_dotRootWithSlashDefaultsConfig() {
        for (boolean on : new boolean[] {false, true}) {
            var b = FurnaceTestServer.builder();
            if (on) b.compatOption(ConfigPaths.PAPER_DEFAULT_SEPARATOR_OPTION, "true");
            try (var server = b.start()) {
                var jar = y('/');
                var c = y('.');
                c.setDefaults(jar);
                c.addDefault("a/b.c", 1);
                assertEquals(List.of("a", "a/b.c"), List.copyOf(jar.getKeys(true)), "on=" + on);
                assertEquals(1, c.get("a/b.c"), "on=" + on);
            }
        }
    }

    /** PR #264 검토 R4(시나리오 66): getDefaults() 는 같은 객체이고 그 구분자 변경이 남는다(Paper). */
    @Test
    void s66_getDefaultsIsStable() {
        try (var server = paper()) {
            var c = y('/');
            c.addDefault("u.v", 1);
            assertTrue(c.getDefaults() == c.getDefaults());
            c.getDefaults().options().pathSeparator('/');
            assertEquals('/', c.getDefaults().options().pathSeparator());
            assertNull(c.get("u.v"));
            c.getDefaults().set("w.x", 5);
            assertEquals(5, c.get("w.x"));
        }
    }

    /** 꺼짐일 때 포장 객체는 루트 구분자를 따라간다(플러그인이 안 바꿨다면) — 구분자를 늦게 바꿔도 어긋나지 않는다. */
    @Test
    void offWrapperFollowsRootSeparator() {
        var c = new YamlConfiguration();
        c.addDefault("a.b", 1);
        assertEquals('.', c.getDefaults().options().pathSeparator());
        c.options().pathSeparator('/');
        c.addDefault("x/y", 2);
        assertEquals('/', c.getDefaults().options().pathSeparator());
        assertEquals(2, c.get("x/y"));
        assertTrue(c.getDefaults() == c.getDefaults());
    }

    @Test
    void dotSeparatorIsUnaffected() {
        try (var server = paper()) {
            var c = new YamlConfiguration();
            c.addDefault("a.b", 1);
            assertEquals(1, c.getInt("a.b"));
            assertTrue(c.isConfigurationSection("a") || c.getDefaults().isConfigurationSection("a"));
        }
    }
}
