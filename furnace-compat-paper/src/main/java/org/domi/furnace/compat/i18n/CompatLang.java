// 호환 계층(furnace-compat-paper)의 운영자 메시지 번역. 서버의 Lang 은 서버 클래스라 호환 로더에서 볼 수 없어서(CompatIsolationStaticTest),
// 같은 규칙을 가진 작은 사본과 호환 전용 번들(furnace-compat-lang/{en,ko}.properties)을 둔다.
//
// 언어: 서버가 정한 코드를 시스템 속성 furnace.language.active 로 알려 준다(서버 Lang.ACTIVE_PROPERTY). 그 속성이 없으면(서버가 아직 고르기 전이거나 단독 시험)
//   -Dfurnace.language → 환경변수 FURNACE_LANGUAGE → JVM 로케일 순으로 서버와 같은 규칙으로 고르고, 지원하지 않으면 영어. 값은 부를 때마다 읽는다(서버가 나중에 바꿔도 따라간다).
// 문장 자리: {} 는 왼쪽부터, {0} 은 번호로. 안 채운 자리·모르는 키는 서버 Lang 과 같이 처리한다.
package org.domi.furnace.compat.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class CompatLang {

    /** 서버 Lang.ACTIVE_PROPERTY 와 같은 이름이어야 한다(호환 시험이 짝을 본다). */
    public static final String ACTIVE_PROPERTY = "furnace.language.active";
    /** 서버 Lang.PROPERTY / ENV 와 같다. */
    public static final String PROPERTY = "furnace.language";
    public static final String ENV = "FURNACE_LANGUAGE";
    public static final String FALLBACK = "en";
    public static final List<String> SUPPORTED = List.of("en", "ko");

    private static final Map<String, Properties> BUNDLES = new ConcurrentHashMap<>();

    private CompatLang() {}

    /** 지금 쓰는 언어 코드. */
    public static String language() {
        String a = System.getProperty(ACTIVE_PROPERTY);
        if (a != null && SUPPORTED.contains(a)) return a;
        return detect();
    }

    /** 번역한 문장. 키가 선택한 언어에 없으면 영어, 영어에도 없으면 키 이름을 그대로 돌려준다. */
    public static String t(String key, Object... args) {
        return in(language(), key, args);
    }

    public static String in(String language, String key, Object... args) {
        String pattern = bundle(language).getProperty(key);
        if (pattern == null && !FALLBACK.equals(language)) pattern = bundle(FALLBACK).getProperty(key);
        return pattern == null ? key : format(pattern, args);
    }

    /** 서버 Lang.detect 와 같은 규칙: 속성·환경변수(모르는 언어면 영어) → JVM 로케일(번역이 없으면 영어). */
    static String detect() {
        String o = System.getProperty(PROPERTY);
        if (o == null || o.isBlank()) o = System.getenv(ENV);
        String c = code(o);
        if (c != null) return SUPPORTED.contains(c) ? c : FALLBACK;
        c = code(Locale.getDefault().getLanguage());
        return c != null && SUPPORTED.contains(c) ? c : FALLBACK;
    }

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

    private static Properties bundle(String language) {
        return BUNDLES.computeIfAbsent(language, CompatLang::load);
    }

    private static Properties load(String language) {
        Properties p = new Properties();
        try (InputStream in = CompatLang.class.getResourceAsStream("/furnace-compat-lang/" + language + ".properties")) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 번들을 못 읽으면 키 이름이 그대로 나온다(로그 한 줄이 못생길 뿐 기능은 그대로)
        }
        return p;
    }

    static String format(String pattern, Object... args) {
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
