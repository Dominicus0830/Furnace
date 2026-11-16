package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** PR #254 검토(R1~R5) 회귀. 기대값은 Paper 설정 클래스(paper-api org.bukkit.configuration 원문)를 그대로 돌려 얻었다. */
class A15PathSeparatorReviewTest {

    private static YamlConfiguration y(char sep) {
        YamlConfiguration c = new YamlConfiguration();
        c.options().pathSeparator(sep);
        return c;
    }

    /** R1: addDefaults(Configuration) 의 MemorySection 빠른 길이 '.' 경로로 옮겨 점 든 키가 쪼개진다. Paper: 1, true. */
    @Test
    void r1_addDefaultsFromCustomSeparatorConfig() {
        YamlConfiguration src = y('/');
        src.set("a.b/c", 1);
        YamlConfiguration c = y('/');
        c.addDefaults(src);
        assertEquals(1, c.get("a.b/c"));
        assertTrue(c.contains("a.b/c"));
    }

    /** R2: copyDefaults 저장이 점 든 구역 아래 기본값을 버린다. Paper: "p.q:\n  r: def\n  s: own\n". */
    @Test
    void r2_copyDefaultsSaveKeepsDefaultsUnderDottedSection() {
        YamlConfiguration jar = y('/');
        jar.set("p.q/r", "def");
        jar.set("p.q/s", "def2");
        YamlConfiguration c = y('/');
        c.setDefaults(jar);
        c.set("p.q/s", "own");
        c.options().copyDefaults(true);
        assertEquals("p.q:\n  r: def\n  s: own\n", c.saveToString());
    }

    /** R2 대조('.' 모드, 기존부터): 점 든 literal 키로 읽힌 구역도 같은 문제. Paper: 두 파일 모두 쪼개 읽어 r 이 남는다. */
    @Test
    void r2b_dotModeLiteralDottedSection() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        jar.loadFromString("p.q:\n  r: def\n");
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString("p.q:\n  s: own\n");
        c.setDefaults(jar);
        c.options().copyDefaults(true);
        assertTrue(c.saveToString().contains("r: def"), c.saveToString());
    }

    /** R3: 기본값 설정의 구분자가 '.' 이면 Paper 는 contains=false(get 도 null). Furnace 는 contains=true, get=null 로 어긋난다. */
    @Test
    void r3_containsAgreesWithGetWhenDefaultsUseDots() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        jar.loadFromString("a:\n  b: x\n");
        YamlConfiguration c = y('/');
        c.setDefaults(jar);
        assertNull(c.get("a/b"));
        assertFalse(c.contains("a/b"));
        assertFalse(c.isSet("a/b"));
    }

    /** R4: 빈 단계. Paper: 앞·가운데 빈 단계는 현재 구역, 끝 구분자 조회는 그 구역, 끝 구분자 쓰기는 IllegalArgumentException. */
    @Test
    void r4_emptySegments() {
        YamlConfiguration c = y('/');
        c.set("/a", 1);
        assertEquals(1, c.get("a"));
        assertEquals(Set.of("a"), c.getKeys(true));
        YamlConfiguration d = y('/');
        d.set("a//b", 1);
        assertEquals(1, d.get("a/b"));
        assertTrue(d.isConfigurationSection("a/"));
        assertThrows(IllegalArgumentException.class, () -> d.set("x/", 1));
        assertThrows(IllegalArgumentException.class, () -> d.createSection("y/"));
        assertTrue(d.contains("x"), "Paper 는 던지기 전에 중간 구역 x 를 만든다");
        assertThrows(IllegalArgumentException.class, () -> d.set("", 1));
    }

    /** R5: 구분자가 든 YAML 키·맵 키는 Paper 에서 쪼개진다(fromNodeTree → createSection/set). */
    @Test
    void r5_keysContainingSeparatorAreSplitOnLoad() throws Exception {
        YamlConfiguration c = y('/');
        c.loadFromString("a/b: 1\ne:\n  f/g: 3\n");
        assertEquals(1, c.get("a/b"));
        assertEquals(3, c.get("e/f/g"));
        YamlConfiguration m = y('/');
        m.createSection("s", Map.of("a/b", 1));
        assertEquals(1, m.get("s/a/b"));
    }

    /** R4: 앞 구분자가 붙은 경로로도 주석이 읽히고 써진다(Paper: 빈 단계는 현재 구역). */
    @Test
    void r4_commentsFollowEmptySegmentRule() {
        YamlConfiguration c = y('/');
        c.set("a.b/c", 1);
        c.setComments("a.b/c", java.util.List.of("note"));
        assertEquals(java.util.List.of("note"), c.getComments("/a.b/c"));
        c.setInlineComments("/a.b/c", java.util.List.of("tail"));
        assertEquals(java.util.List.of("tail"), c.getInlineComments("a.b/c"));
    }

    /** R4/R5: 끝 구분자 조회는 구역일 때만 구역이고, 값이면 null. */
    @Test
    void r4_trailingSeparatorOnAValueIsNull() {
        YamlConfiguration c = y('/');
        c.set("a/b", 1);
        assertEquals(1, c.getConfigurationSection("a/").getInt("b"));
        assertNull(c.get("a/b/"));
        assertFalse(c.contains("a/b/"));
    }

    /** 끝 구분자가 붙은 경로의 주석: Paper 는 빈 목록을 돌려주고 쓰지 않는다. */
    @Test
    void trailingSeparatorCommentsAreNoOps() {
        YamlConfiguration c = y('/');
        c.set("a.b/c", 1);
        c.setComments("a.b", java.util.List.of("sec"));
        assertEquals(java.util.List.of(), c.getComments("a.b/"));
        c.setComments("a.b/", java.util.List.of("x"));
        c.setInlineComments("a.b/", java.util.List.of("y"));
        assertEquals(java.util.List.of("sec"), c.getComments("a.b"));
    }
}
