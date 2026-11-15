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
    private static final Pattern LANG_KEY = Pattern.compile("(?<![A-Za-z])Lang\\.t\\(\\s*\"([^\"]+)\"");
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src/main/java"), Path.of("../furnace-compat-paper/src/main/java"), Path.of("../furnace-api/src/main/java"),
            Path.of("../furnace-test/src/main/java"), Path.of("../furnace-plugin-test/src/main/java"));

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

    /**
     * 운영자·플러그인 개발자에게 보일 수 있는 문구(로그·명령 응답·콘솔 출력·보고서·메트릭 설명 등)에 한글을 코드에 직접 적지 않는다 —
     * 예외 메시지·assert·DEBUG 수준 호출·requireNonNull 밖에서 한글 문자열이 나오는 파일을 {@link HangulTextScanner} 로 찾는다(server·compat·api 전체).
     * 아직 안 옮긴 파일은 lang-migration-pending.txt 에 있고, 옮기면 지운다(남아 있으면 그것도 실패).
     * 한계: 문자열을 변수·상수에 담았다 나중에 예외로 보내는 흐름은 따라가지 않는다(스캐너 머리말). 예외 메시지 자체의 번역 여부는 이 가드 밖이다.
     */
    @Test
    void noHangulUserTextOutsideExceptions() throws IOException {
        Set<String> pending = new TreeSet<>(Files.readAllLines(Path.of("src/test/resources/lang-migration-pending.txt"), StandardCharsets.UTF_8)
                .stream().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList());
        Set<String> found = new TreeSet<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            String module = root.toAbsolutePath().normalize().getParent().getParent().getParent().getFileName().toString();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    if (!HangulTextScanner.userTextLines(Files.readString(f, StandardCharsets.UTF_8)).isEmpty()) {
                        found.add(module + "/" + root.relativize(f).toString().replace('\\', '/'));
                    }
                }
            }
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(pending);
        Set<String> stale = new TreeSet<>(pending);
        stale.removeAll(found);
        assertTrue(added.isEmpty(), "Korean text hard-coded outside an exception/assert/debug call - put it in furnace-lang/{en,ko}.properties "
                + "(server: Lang.t, compat: CompatLang.t) or, if it is an exception message, pass it straight to the exception constructor:\n" + String.join("\n", added));
        assertTrue(stale.isEmpty(), "Already migrated - remove from src/test/resources/lang-migration-pending.txt:\n" + String.join("\n", stale));
    }

    /**
     * 예외·requireNonNull 안까지 보는 두 번째 가드: 개발자가 스택 트레이스로 보는 진단 문구는 영어를 코드에 직접 적는다.
     * 아직 한글이 남은 파일은 `파일 줄수` 로 lang-exception-english-pending.txt 에 있다(작업 목록 겸용). 줄 수가 늘면 실패하고(새 한글 진단 금지),
     * 줄어들었는데 목록을 안 고쳐도 실패한다(옮긴 만큼 낮춘다, 0 이 되면 줄을 지운다).
     */
    @Test
    void noNewHangulDeveloperDiagnostics() throws IOException {
        java.util.Map<String, Integer> cap = new java.util.TreeMap<>();
        for (String l : Files.readAllLines(Path.of("src/test/resources/lang-exception-english-pending.txt"), StandardCharsets.UTF_8)) {
            l = l.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            int sp = l.lastIndexOf(' ');
            cap.put(l.substring(0, sp).strip(), Integer.parseInt(l.substring(sp + 1)));
        }
        java.util.Map<String, Integer> found = new java.util.TreeMap<>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            String module = root.toAbsolutePath().normalize().getParent().getParent().getParent().getFileName().toString();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    int n = HangulTextScanner.developerTextLines(Files.readString(f, StandardCharsets.UTF_8)).size();
                    if (n > 0) found.put(module + "/" + root.relativize(f).toString().replace('\\', '/'), n);
                }
            }
        }
        List<String> more = new java.util.ArrayList<>(), fewer = new java.util.ArrayList<>();
        for (var e : found.entrySet()) {
            int allowed = cap.getOrDefault(e.getKey(), 0);
            if (e.getValue() > allowed) more.add(e.getKey() + " " + e.getValue() + " (allowed " + allowed + ")");
            else if (e.getValue() < allowed) fewer.add(e.getKey() + " " + e.getValue() + " (list says " + allowed + ")");
        }
        for (var e : cap.entrySet()) if (!found.containsKey(e.getKey())) fewer.add(e.getKey() + " 0 (list says " + e.getValue() + ")");
        assertTrue(more.isEmpty(), "Korean text in an exception/diagnostic message - write developer-facing diagnostics in English "
                + "(or, for operator-facing console text, use Lang.t / CompatLang.t). Do not raise the numbers in lang-exception-english-pending.txt:\n" + String.join("\n", more));
        assertTrue(fewer.isEmpty(), "Already (partly) English - lower the line count, or delete the line at 0, in src/test/resources/lang-exception-english-pending.txt:\n" + String.join("\n", fewer));
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
