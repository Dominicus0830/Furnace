// 호환 계층 번역 점검: 언어마다 키·{} 자리가 같고, 코드가 쓰는 키가 모두 있고 안 쓰는 키가 없으며, 서버 Lang 과 같은 언어를 고른다.
package org.domi.furnace.compat.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.domi.furnace.server.i18n.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompatLangTest {

    private static final Pattern SLOT = Pattern.compile("\\{(\\d*)}");
    private static final Pattern KEY = Pattern.compile("CompatLang\\.t\\(\\s*\"([^\"]+)\"");
    private static final Path SOURCES = Path.of("src/main/java");

    @AfterEach
    void restore() {
        Lang.reset();
        System.setProperty(CompatLang.ACTIVE_PROPERTY, Lang.language());
    }

    private static Properties bundle(String lang) throws IOException {
        Properties p = new Properties();
        try (InputStream in = CompatLang.class.getResourceAsStream("/furnace-compat-lang/" + lang + ".properties")) {
            assertNotNull(in, lang + ".properties is missing");
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static String slots(String pattern) {
        int plain = 0;
        TreeSet<String> numbered = new TreeSet<>();
        Matcher m = SLOT.matcher(pattern);
        while (m.find()) {
            if (m.group(1).isEmpty()) plain++;
            else numbered.add(m.group(1));
        }
        return plain + "/" + numbered;
    }

    @Test
    void everyLanguageHasTheSameKeysAndSlots() throws IOException {
        Properties en = bundle("en");
        assertFalse(en.isEmpty());
        for (String lang : CompatLang.SUPPORTED) {
            Properties other = bundle(lang);
            assertEquals(new TreeSet<>(en.stringPropertyNames()), new TreeSet<>(other.stringPropertyNames()), lang + ": the keys differ from en");
            for (String key : en.stringPropertyNames()) {
                assertFalse(other.getProperty(key).isBlank(), lang + ":" + key + " is empty");
                assertEquals(slots(en.getProperty(key)), slots(other.getProperty(key)), lang + ":" + key + " has different {} slots than en");
            }
        }
    }

    @Test
    void usedKeysExistAndEveryKeyIsUsed() throws IOException {
        Properties en = bundle("en");
        StringBuilder all = new StringBuilder();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                all.append(src);
                Matcher m = KEY.matcher(src);
                while (m.find()) if (en.getProperty(m.group(1)) == null) missing.add(f.getFileName() + ": " + m.group(1));
            }
        }
        assertTrue(missing.isEmpty(), "CompatLang.t keys missing from en.properties: " + missing);
        List<String> unused = new ArrayList<>();
        for (String key : en.stringPropertyNames()) if (!all.toString().contains("\"" + key + "\"")) unused.add(key);
        assertTrue(unused.isEmpty(), "compat keys no code uses: " + unused);
    }

    @Test
    void followsTheServerLanguage() {
        assertEquals(Lang.ACTIVE_PROPERTY, CompatLang.ACTIVE_PROPERTY);
        assertEquals(Lang.PROPERTY, CompatLang.PROPERTY);
        assertEquals(Lang.ENV, CompatLang.ENV);
        assertEquals(Lang.SUPPORTED, CompatLang.SUPPORTED);
        for (String code : Lang.SUPPORTED) {
            Lang.forceForTest(code);
            assertEquals(code, CompatLang.language());
        }
        Lang.forceForTest("ko");
        assertTrue(CompatLang.t("compat.mirror.request-failed", "x").contains("주인에게"));
        Lang.forceForTest("en");
        assertEquals(CompatLang.in("en", "compat.mirror.request-failed", "x"), CompatLang.t("compat.mirror.request-failed", "x"));
    }

    @Test
    void withoutTheServerPropertyItUsesTheSameRulesAsTheServer() {
        String saved = System.getProperty(CompatLang.PROPERTY);
        System.clearProperty(CompatLang.ACTIVE_PROPERTY);
        try {
            System.setProperty(CompatLang.PROPERTY, "ko_KR.UTF-8");
            assertEquals("ko", CompatLang.language());
            System.setProperty(CompatLang.PROPERTY, "fr");
            assertEquals("en", CompatLang.language());
            System.setProperty(CompatLang.PROPERTY, "auto");
            assertTrue(CompatLang.SUPPORTED.contains(CompatLang.language()));
        } finally {
            if (saved == null) System.clearProperty(CompatLang.PROPERTY);
            else System.setProperty(CompatLang.PROPERTY, saved);
        }
    }

    @Test
    void unknownKeyComesBackAsTheKey() {
        assertEquals("compat.no-such-key", CompatLang.t("compat.no-such-key"));
    }
}
