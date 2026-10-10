// 바닐라 서버 언어(en_us) — 서버 로그·콘솔 출력에서 번역 키를 영문으로 펼칠 때 쓴다(Component.getString).
package org.domi.furnace.server.net.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Language {

    private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static volatile Map<String, String> table;

    private Language() {}

    private static Map<String, String> table() {
        Map<String, String> t = table;
        if (t == null) {
            synchronized (Language.class) {
                if (table == null) {
                    Map<String, String> m = new HashMap<>();
                    var in = Language.class.getClassLoader().getResourceAsStream("lang/en_us.json");
                    if (in != null) {
                        try (var r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                            for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
                        } catch (Exception ignored) {
                        }
                    }
                    table = m;
                }
                t = table;
            }
        }
        return t;
    }

    public static String getOrDefault(String key) {
        return table().getOrDefault(key, key);
    }

    /** TranslatableContents 의 %s / %n$s / %% 해석(잘못된 형식이면 키 그대로). */
    public static String format(String key, String[] args) {
        String pattern = getOrDefault(key);
        StringBuilder sb = new StringBuilder();
        Matcher m = FORMAT.matcher(pattern);
        int last = 0, next = 0;
        try {
            while (m.find()) {
                sb.append(pattern, last, m.start());
                String type = m.group(2);
                if ("%".equals(type) && m.group(1) == null) {
                    sb.append('%');
                } else if ("s".equals(type)) {
                    int idx = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                    if (idx >= 0 && idx < args.length) sb.append(args[idx]);
                } else {
                    return pattern;
                }
                last = m.end();
            }
        } catch (RuntimeException e) {
            return pattern;
        }
        sb.append(pattern.substring(last));
        return sb.toString();
    }
}
