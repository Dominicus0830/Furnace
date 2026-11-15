// 번역 파일 점검: 언어마다 키·{} 자리가 같고, 코드가 쓰는 모든 키가 있고, 한글 로그 하드코딩이 새로 늘지 않는다.
package org.domi.furnace.server.i18n;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LangBundlesTest {

    private static final Pattern SLOT = Pattern.compile("\\{(\\d*)}");
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern LOG_CALL = Pattern.compile("(?:\\b\\w*(?:log|LOG|logger|LOGGER)\\w*|getLogger\\([^()]*\\))\\.(?:info|warn|error|severe|warning)\\(|\\bwarn(?:ings)?\\.(?:accept|add)\\(");
    private static final Pattern LANG_KEY = Pattern.compile("Lang\\.t\\(\\s*\"([^\"]+)\"");
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src/main/java"), Path.of("../furnace-compat-paper/src/main/java"), Path.of("../furnace-api/src/main/java"));

    @Test
    void everySupportedLanguageHasAFileWithTheSameKeysAndSlots() {
        Set<String> enKeys = Lang.keys("en");
        assertFalse(enKeys.isEmpty());
        for (String lang : Lang.SUPPORTED) {
            Set<String> keys = Lang.keys(lang);
            assertEquals(new TreeSet<>(enKeys), new TreeSet<>(keys), lang + ": the keys differ from en.properties");
            for (String key : enKeys) {
                String en = Lang.pattern("en", key);
                String other = Lang.pattern(lang, key);
                assertFalse(other.isBlank(), lang + ":" + key + " is empty");
                assertEquals(slots(en), slots(other), lang + ":" + key + " has different {} slots than en");
            }
        }
    }

    /** 자리 목록(순서 없이): 번호 없는 {} 는 개수만, 번호 있는 {n} 은 번호별로 센다. */
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
    void everyKeyTheCodeUsesExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    Matcher m = LANG_KEY.matcher(Files.readString(f, StandardCharsets.UTF_8));
                    while (m.find()) {
                        String key = m.group(1);
                        if (Lang.pattern("en", key) == null) missing.add(f.getFileName() + ": " + key);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Lang.t keys missing from en.properties: " + missing);
    }

    @Test
    void everyKeyIsUsedByTheCode() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) all.append(Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        String source = all.toString();
        List<String> unused = new ArrayList<>();
        for (String key : Lang.keys("en")) if (!source.contains("\"" + key + "\"")) unused.add(key);
        assertTrue(unused.isEmpty(), "keys in en.properties that no code uses (typo in a Lang.t call, or leftover): " + unused);
    }

    @Test
    void noNewHangulLiteralsInLogCalls() throws IOException {
        Set<String> pending = new TreeSet<>(Files.readAllLines(Path.of("src/test/resources/lang-migration-pending.txt"), StandardCharsets.UTF_8)
                .stream().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList());
        Set<String> found = new TreeSet<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    if (hasHangulLog(Files.readString(f, StandardCharsets.UTF_8))) found.add(f.getFileName().toString());
                }
            }
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(pending);
        Set<String> stale = new TreeSet<>(pending);
        stale.removeAll(found);
        assertTrue(added.isEmpty(), "Korean text hard-coded in a log.info/warn/error call - put it in furnace-lang/{en,ko}.properties and use Lang.t(): " + added);
        assertTrue(stale.isEmpty(), "Already migrated - remove from src/test/resources/lang-migration-pending.txt: " + stale);
    }

    /**
     * 시작 설정 검증 예외(운영자가 기동 때 그대로 보는 문구)는 번역한다 - 설정 읽기 파일에는 한글 예외 문구를 새로 두지 않는다.
     * 그 밖 파일의 한글 예외 문구(약 200개 파일)는 별도 작업이라 여기서 보지 않는다.
     */
    @Test
    void settingsValidationExceptionsAreNotHardCodedKorean() throws IOException {
        Pattern thrown = Pattern.compile("new (?:IllegalArgumentException|IllegalStateException)\\(");
        List<String> bad = new ArrayList<>();
        for (String rel : List.of("config", "plugin/PluginSettings.java", "net/TransportOptions.java", "net/ConnectionLimits.java",
                "remote/RconLimits.java", "maintenance/MaintenanceOptions.java", "entity/spawn/SpawnConfig.java", "metrics/MetricsOptions.java")) {
            Path root = Path.of("src/main/java/org/domi/furnace/server").resolve(rel);
            if (!Files.exists(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    if (hasHangulCall(thrown, Files.readString(f, StandardCharsets.UTF_8))) bad.add(f.getFileName().toString());
                }
            }
        }
        assertTrue(bad.isEmpty(), "Korean text hard-coded in a settings validation exception - use Lang.t(): " + bad);
    }

    /** log.info/warn/error( ... ) 호출 안(괄호가 닫힐 때까지)에 한글이 든 문자열이 있는가. */
    static boolean hasHangulLog(String source) {
        return hasHangulCall(LOG_CALL, source);
    }

    static boolean hasHangulCall(Pattern call, String source) {
        Matcher m = call.matcher(source);
        while (m.find()) {
            int depth = 1;
            boolean inString = false;
            int i = m.end();
            int start = i;
            for (; i < source.length() && depth > 0; i++) {
                char c = source.charAt(i);
                if (inString) {
                    if (c == '\\') i++;
                    else if (c == '"') inString = false;
                } else if (c == '"') inString = true;
                else if (c == '(') depth++;
                else if (c == ')') depth--;
            }
            if (HANGUL.matcher(source.substring(start, Math.min(i, source.length()))).find()) return true;
        }
        return false;
    }
}
