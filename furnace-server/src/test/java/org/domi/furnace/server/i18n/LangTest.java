// 서버 메시지 번역 — 자리 채우기, 언어 고르기(속성·설정·감지), 빠진 키 처리.
package org.domi.furnace.server.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LangTest {

    private Locale savedLocale;

    @AfterEach
    void restore() {
        System.clearProperty(Lang.PROPERTY);
        if (savedLocale != null) Locale.setDefault(savedLocale);
        Lang.reset();
    }

    @Test
    void formatFillsSequentialAndNumberedSlots() {
        assertEquals("a-b-c", Lang.format("{}-{}-{}", "a", "b", "c"));
        assertEquals("b a", Lang.format("{1} {0}", "a", "b"));
        assertEquals("x 3 y 3", Lang.format("x {} y {0}", 3));
    }

    @Test
    void formatLeavesNonSlotBracesAndMissingArguments() {
        assertEquals("{\"k\": 1}", Lang.format("{\"k\": 1}"));
        assertEquals("a {} b {1}", Lang.format("a {} b {1}"));
        assertEquals("1 {}", Lang.format("{} {}", 1));
        assertEquals("no slots", Lang.format("no slots", 1, 2));
    }

    @Test
    void codeNormalizesLocaleSpellings() {
        assertEquals("ko", Lang.code("ko_KR"));
        assertEquals("ko", Lang.code("ko-KR.UTF-8"));
        assertEquals("en", Lang.code("  EN "));
        assertNull(Lang.code("auto"));
        assertNull(Lang.code(""));
        assertNull(Lang.code(null));
    }

    @Test
    void settingPicksLanguageAndUnknownFallsBackToEnglish() {
        assertTrue(Lang.select("ko"));
        assertEquals("ko", Lang.language());
        assertTrue(Lang.select("en"));
        assertEquals("en", Lang.language());
        assertFalse(Lang.select("xx"));
        assertEquals("en", Lang.language());
    }

    @Test
    void autoFollowsTheJvmLocale() {
        savedLocale = Locale.getDefault();
        Locale.setDefault(Locale.KOREA);
        assertTrue(Lang.select("auto"));
        assertEquals("ko", Lang.language());
        Locale.setDefault(Locale.FRANCE);   // 번역 파일이 없는 언어 → 영어
        assertTrue(Lang.select(null));
        assertEquals("en", Lang.language());
    }

    @Test
    void jvmPropertyBeatsTheFileSetting() {
        System.setProperty(Lang.PROPERTY, "ko");
        assertTrue(Lang.select("en"));
        assertEquals("ko", Lang.language());
    }

    @Test
    void aStringArgumentIsNotMistakenForALanguageCode() {
        Lang.forceForTest("en");
        assertEquals("worlds.autoload: could not load world 'nether2' - skipping it", Lang.t("world.autoload-failed", "nether2"));
        Lang.forceForTest("ko");
        assertTrue(Lang.t("world.autoload-failed", "nether2").contains("nether2"));
    }

    @Test
    void translatesPerLanguageAndFallsBackToTheKey() {
        assertEquals("Created furnace.properties (every option with its description)", Lang.in("en", "config.created"));
        assertTrue(Lang.in("ko", "config.created").contains("furnace.properties"));
        assertNotEquals(Lang.in("en", "config.created"), Lang.in("ko", "config.created"));
        // 빠진 키는 키 이름을 그대로(예외로 서버를 멈추지 않는다)
        assertEquals("no.such.key", Lang.in("ko", "no.such.key", 1));
        // 선택한 언어에 없고 영어에는 있는 키는 영어로
        assertEquals(Lang.in("en", "config.created"), Lang.in("fr", "config.created"));
    }
}
