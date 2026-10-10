// 바닐라 서버 언어(en_us) — 서버 로그·콘솔 출력에서 번역 키를 영문으로 펼칠 때 쓴다(Component.getString).
// ServiceLoader 로 API Translators 에 자동 등록된다(META-INF/services) — 바닐라처럼 서버 언어는 en_us 고정.
package org.domi.furnace.server.net.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Language implements org.domi.furnace.api.text.Translator {

    private static volatile Map<String, String> table;

    /** ServiceLoader 용. */
    public Language() {}

    @Override
    public String translate(String key, java.util.Locale locale) { return table().get(key); }

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
}
