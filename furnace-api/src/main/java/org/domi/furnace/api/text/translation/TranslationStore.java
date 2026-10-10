// 번역 저장소 — 플러그인이 정의한 번역 키를 언어별로 보관한다(locale → key → 패턴). 바닐라 형식(%s, %1$s)과 MessageFormat 형식({0})을 받는다.
// Translators 에 등록하면 Text.plainText 등이 쓰고, GlobalRenderer 로 플레이어 언어에 맞게 미리 렌더링할 수 있다.
package org.domi.furnace.api.text.translation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.text.Translator;
import org.domi.furnace.api.text.Translators;

public final class TranslationStore implements Translator {

    /** 패턴 형식. */
    public enum Format {
        /** 바닐라: %s, %1$s, %%. */
        VANILLA,
        /** java.text.MessageFormat 의 자리표시자 부분만: {0}, {1}, '' 와 '...' 따옴표. 형식 지정자({0,number})는 무시한다. */
        MESSAGE_FORMAT
    }

    private final Locale defaultLocale;
    private final Format defaultFormat;
    private final Map<Locale, Map<String, String>> patterns = new ConcurrentHashMap<>();

    private TranslationStore(Locale defaultLocale, Format defaultFormat) {
        this.defaultLocale = Objects.requireNonNull(defaultLocale);
        this.defaultFormat = Objects.requireNonNull(defaultFormat);
    }

    /** 바닐라 형식 저장소. 찾는 언어에 없으면 defaultLocale 로 넘어간다. */
    public static TranslationStore create(Locale defaultLocale) { return new TranslationStore(defaultLocale, Format.VANILLA); }

    public static TranslationStore create(Locale defaultLocale, Format defaultFormat) { return new TranslationStore(defaultLocale, defaultFormat); }

    public Locale defaultLocale() { return defaultLocale; }

    // ------------------------------------------------------------------ 등록

    public TranslationStore register(Locale locale, String key, String pattern) { return register(locale, key, pattern, defaultFormat); }

    public TranslationStore register(Locale locale, String key, String pattern, Format format) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(pattern, "pattern");
        String vanilla = format == Format.MESSAGE_FORMAT ? messageFormatToVanilla(pattern) : pattern;
        patterns.computeIfAbsent(locale, l -> new ConcurrentHashMap<>()).put(key, vanilla);
        return this;
    }

    public TranslationStore registerAll(Locale locale, Map<String, String> entries) {
        for (Map.Entry<String, String> e : entries.entrySet()) register(locale, e.getKey(), e.getValue());
        return this;
    }

    /** ResourceBundle 의 모든 키를 등록한다(.properties 파일 로딩용). */
    public TranslationStore registerAll(Locale locale, ResourceBundle bundle) {
        for (String k : bundle.keySet()) register(locale, k, bundle.getString(k));
        return this;
    }

    public TranslationStore unregister(String key) {
        for (Map<String, String> m : patterns.values()) m.remove(key);
        return this;
    }

    // ------------------------------------------------------------------ 조회

    /** 어느 언어에든 이 키가 있나. */
    public boolean contains(String key) {
        for (Map<String, String> m : patterns.values()) if (m.containsKey(key)) return true;
        return false;
    }

    /**
     * 바닐라 형식 패턴을 찾는다. 순서: 정확한 로케일 → 같은 언어(국가·변형 제거) → 기본 로케일 → 기본 로케일의 언어. 없으면 null.
     */
    public String lookup(String key, Locale locale) {
        String p = find(key, locale);
        if (p == null && !locale.equals(defaultLocale)) p = find(key, defaultLocale);
        return p;
    }

    private String find(String key, Locale l) {
        Map<String, String> m = patterns.get(l);
        String p = m == null ? null : m.get(key);
        if (p != null) return p;
        if (!l.getCountry().isEmpty() || !l.getVariant().isEmpty()) {
            Locale lang = Locale.of(l.getLanguage());
            Map<String, String> lm = patterns.get(lang);
            p = lm == null ? null : lm.get(key);
        }
        return p;
    }

    @Override
    public String translate(String key, Locale locale) { return lookup(key, locale); }

    // ------------------------------------------------------------------ 전역 등록

    /** Translators 와 GlobalRenderer 에 등록한다(이미 등록돼 있으면 무시). */
    public void registerGlobally() {
        if (GlobalRenderer.addGlobal(this)) Translators.register(this);
    }

    public void unregisterGlobally() {
        if (GlobalRenderer.removeGlobal(this)) Translators.unregister(this);
    }

    // ------------------------------------------------------------------ 형식 변환

    /** MessageFormat 패턴의 {n} 자리표시자와 따옴표 규칙을 바닐라 %n$s 로 바꾼다. */
    static String messageFormatToVanilla(String p) {
        StringBuilder sb = new StringBuilder(p.length() + 8);
        boolean quoted = false;
        int n = p.length();
        for (int i = 0; i < n; i++) {
            char c = p.charAt(i);
            if (c == '\'') {
                if (i + 1 < n && p.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && c == '{') {
                int close = p.indexOf('}', i);
                if (close > 0) {
                    String inner = p.substring(i + 1, close);
                    int comma = inner.indexOf(',');
                    String num = (comma < 0 ? inner : inner.substring(0, comma)).trim();
                    try {
                        int idx = Integer.parseInt(num);
                        if (idx >= 0) {
                            sb.append('%').append(idx + 1).append("$s");
                            i = close;
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // 자리표시자가 아니면 글자로
                    }
                }
            }
            if (c == '%') sb.append("%%");
            else sb.append(c);
        }
        return sb.toString();
    }
}
