// 전역 번역기 목록과 바닐라 번역 패턴 해석(TranslatableContents.decomposeTemplate 와 같은 규칙).
package org.domi.furnace.api.text;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Translators {

    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final List<Translator> TRANSLATORS = new CopyOnWriteArrayList<>();
    /** 서버 언어(바닐라 서버는 en_us 고정). */
    public static final Locale SERVER_LOCALE = Locale.US;

    static {
        // 서버가 META-INF/services 로 제공하는 번역기(바닐라 en_us)를 먼저 올린다.
        for (Translator t : java.util.ServiceLoader.load(Translator.class, Translators.class.getClassLoader())) TRANSLATORS.add(t);
    }

    private Translators() {}

    /** 번역기 등록 — 먼저 등록한 것이 우선한다. */
    public static void register(Translator t) { TRANSLATORS.add(t); }

    public static void unregister(Translator t) { TRANSLATORS.remove(t); }

    /** 등록된 번역기에서 패턴을 찾는다(없으면 null). */
    public static String translate(String key, Locale locale) {
        for (Translator t : TRANSLATORS) {
            String s = t.translate(key, locale);
            if (s != null) return s;
        }
        return null;
    }

    /**
     * 바닐라 TranslatableContents.decompose + getString: 패턴(없으면 fallback, 그것도 없으면 키)을 인자로 채운다.
     * 지원하지 않는 형식·범위 밖 인자·홀로 남은 % 가 있으면 바닐라처럼 패턴 문자열 그대로 돌려준다.
     */
    public static String format(String key, String fallback, List<String> args, Locale locale) {
        String pattern = translate(key, locale);
        if (pattern == null) pattern = fallback != null ? fallback : key;
        String out = fill(pattern, args);
        return out != null ? out : pattern;
    }

    /** 패턴을 채운 결과, 형식 오류면 null. */
    static String fill(String format, List<String> args) {
        StringBuilder sb = new StringBuilder(format.length() + 16);
        Matcher m = FORMAT.matcher(format);
        int next = 0, from = 0;
        while (m.find(from)) {
            int start = m.start(), end = m.end();
            if (start > from) {
                String lit = format.substring(from, start);
                if (lit.indexOf('%') != -1) return null;
                sb.append(lit);
            }
            String type = m.group(2);
            String whole = format.substring(start, end);
            if ("%".equals(type) && "%%".equals(whole)) {
                sb.append('%');
            } else {
                if (!"s".equals(type)) return null;
                String idx = m.group(1);
                int i;
                try {
                    i = idx != null ? Integer.parseInt(idx) - 1 : next++;
                } catch (NumberFormatException e) {
                    return null;
                }
                if (i < 0 || i >= args.size()) return null;
                sb.append(args.get(i));
            }
            from = end;
        }
        if (from < format.length()) {
            String rest = format.substring(from);
            if (rest.indexOf('%') != -1) return null;
            sb.append(rest);
        }
        return sb.toString();
    }
}
