// 서버 메시지(콘솔 로그·콘솔 명령 결과) 번역. 영어(en)가 기준이고 한국어(ko)를 함께 싣는다.
//
// 어떤 언어로 찍을지는 이 순서로 정한다:
//   1) JVM 속성 -Dfurnace.language=ko  2) 환경변수 FURNACE_LANGUAGE  3) furnace.properties 의 language 키
//   4) 위가 없거나 auto 면 JVM 기본 로케일(user.language — 리눅스는 LANG, 윈도는 지역 설정)  5) 그 언어 파일이 없으면 영어.
// 번역하지 않는 것: 바닐라·Paper 문구 그대로인 줄(호스팅 패널·플러그인이 읽는다), 스택 트레이스·예외 메시지, DEBUG 로그, 설정 키·명령 이름.
// 문장 속 자리는 {} (왼쪽부터 차례로) 또는 {0} {1} (번호 — 언어마다 어순이 달라 필요할 때). 예: Lang.t("pause.close-failed", dir)
package org.domi.furnace.server.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lang {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 기준 언어 — 번역이 없거나 지원하지 않는 언어면 이걸로 돌아간다. */
    public static final String FALLBACK = "en";
    /** 싣고 있는 언어(resources/furnace-lang/&lt;코드&gt;.properties). 새 언어를 넣으면 여기에도 적는다(시험이 파일이 있는지 본다). */
    public static final List<String> SUPPORTED = List.of("en", "ko");
    public static final String PROPERTY = "furnace.language";
    public static final String ENV = "FURNACE_LANGUAGE";
    /** furnace.properties 의 키. */
    public static final String KEY = "language";

    private static final Map<String, Properties> BUNDLES = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static volatile String active = detect();

    private Lang() {}

    /** 지금 쓰는 언어 코드(예: "ko"). */
    public static String language() { return active; }

    /**
     * furnace.properties 의 language 값을 반영한다(없으면 null·"auto"). JVM 속성·환경변수가 있으면 그쪽이 이긴다.
     * @return 알 수 없는 언어라 영어로 돌아갔으면 그 값(속성·환경변수·파일 중 실제로 본 값 — 호출한 쪽이 한 줄 알린다), 아니면 null
     */
    public static String select(String fileSetting) {
        String requested = override() != null ? override() : fileSetting;
        String c = code(requested);
        if (c == null) {
            active = detect();
            return null;
        }
        if (SUPPORTED.contains(c)) {
            active = c;
            return null;
        }
        active = FALLBACK;
        return requested.strip();
    }

    /** 시험·재설정용: 로케일 감지로 되돌린다. */
    public static void reset() { active = detect(); }

    /** 시험용: 언어를 직접 정한다(null 이면 감지로 되돌림). */
    public static void forceForTest(String code) { active = code == null ? detect() : code; }

    private static String override() {
        String p = System.getProperty(PROPERTY);
        if (p != null && !p.isBlank()) return p;
        String e = System.getenv(ENV);
        return e != null && !e.isBlank() ? e : null;
    }

    /** "ko", "ko_KR", "ko-KR.UTF-8", "KO" → "ko". 빈 값·auto → null(자동). */
    static String code(String setting) {
        if (setting == null) return null;
        String s = setting.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("auto")) return null;
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == '@') { cut = i; break; }
        }
        return s.substring(0, cut);
    }

    /** 감지: JVM 속성·환경변수(알 수 없는 언어면 영어) → JVM 로케일(번역이 없는 언어면 영어). */
    private static String detect() {
        String c = code(override());
        if (c != null) return SUPPORTED.contains(c) ? c : FALLBACK;
        c = code(Locale.getDefault().getLanguage());
        return c != null && SUPPORTED.contains(c) ? c : FALLBACK;
    }

    /** 번역한 문장. 키가 선택한 언어에 없으면 영어, 영어에도 없으면 키 이름을 그대로 돌려준다. */
    public static String t(String key, Object... args) {
        return in(active, key, args);
    }

    /** 언어를 지정해 번역(시험·언어별 비교용). t(key, args...) 와 이름을 다르게 둔 것은 인자 문자열이 언어 코드로 읽히지 않게 하려는 것이다. */
    public static String in(String language, String key, Object... args) {
        String pattern = bundle(language).getProperty(key);
        if (pattern == null && !FALLBACK.equals(language)) pattern = bundle(FALLBACK).getProperty(key);
        if (pattern == null) {
            if (REPORTED.add(key)) log.debug("Missing language key: {}", key);
            return key;
        }
        return format(pattern, args);
    }

    /** 번역 파일의 모든 키(시험용). */
    public static Set<String> keys(String language) { return bundle(language).stringPropertyNames(); }

    /** 문장 틀 그대로(시험용). */
    public static String pattern(String language, String key) { return bundle(language).getProperty(key); }

    private static Properties bundle(String language) {
        return BUNDLES.computeIfAbsent(language, Lang::load);
    }

    private static Properties load(String language) {
        Properties p = new Properties();
        try (InputStream in = Lang.class.getResourceAsStream("/furnace-lang/" + language + ".properties")) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Could not read the language file {}: {}", language, e.toString());
        }
        return p;
    }

    /** {} 는 왼쪽부터 차례로, {0} 은 번호로 채운다. 자리 모양이 아닌 중괄호는 그대로 둔다. 인자가 모자라면 자리표시를 남긴다. */
    public static String format(String pattern, Object... args) {
        if (pattern.indexOf('{') < 0) return pattern;
        StringBuilder sb = new StringBuilder(pattern.length() + 16);
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int end = pattern.indexOf('}', i);
                if (end > 0 && isSlot(pattern, i + 1, end)) {
                    int idx = end == i + 1 ? next++ : Integer.parseInt(pattern.substring(i + 1, end));
                    if (args != null && idx < args.length) sb.append(args[idx]);
                    else sb.append(pattern, i, end + 1);
                    i = end;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isSlot(String s, int from, int to) {
        for (int i = from; i < to; i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return to - from <= 3;
    }
}
