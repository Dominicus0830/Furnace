// 설정 프리셋(config.preset): 건드리지 않은 키(파일에 없거나 기본값 그대로인 키)를 한꺼번에 채우는 값 묶음. 기본값과 다르게 적은 키가 항상 이긴다.
//   none(기본)   — 아무것도 채우지 않는다. 키가 없으면 각 로더의 기본값(= 바닐라)이다.
//   vanilla      — 바닐라 동작에 영향을 주는 키를 바닐라 값으로 못 박는다(옛 설정·다른 프리셋에서 돌아올 때).
//   performance  — 서버 부하를 줄이는 쪽으로 보수적으로 조정한다(엔티티 가시 거리를 줄이고 쓰지 않는 청크를 일찍 내린다).
//                  멀리 있는 몹이 클라이언트에 안 보이는 등 바닐라와 관측 결과가 조금 달라지므로 켠 사람만 받는다.
package org.domi.furnace.server.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ConfigPresets {

    private ConfigPresets() {}

    public static final String KEY = "config.preset";
    public static final List<String> NAMES = List.of("none", "vanilla", "performance");

    private static final Map<String, Map<String, String>> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("none", Map.of());
        Map<String, String> vanilla = new LinkedHashMap<>();
        vanilla.put("tick-nanos", "50000000");
        vanilla.put("chat.strict-order", "true");
        vanilla.put("chat.unsigned-as-system", "false");
        vanilla.put("gravity-block-portal-dupe", "true");
        vanilla.put("limits.chat-length", "256");
        vanilla.put("limits.command-length", "256");
        for (String k : new String[]{"display-name", "item-name", "lore-line", "book-page", "book-author"}) vanilla.put("limits.item." + k, "0");
        vanilla.put("plugins.dev-mode", "false");
        vanilla.put("idle-pause.empty-seconds", "0");
        PRESETS.put("vanilla", vanilla);
        Map<String, String> perf = new LinkedHashMap<>();
        perf.put("entity-view-distance", "6");
        perf.put("chunk-eviction-grace-ticks", "40");
        PRESETS.put("performance", perf);
    }

    /** 프리셋의 키와 값(복사). 모르는 이름이면 null. */
    public static Map<String, String> values(String name) {
        Map<String, String> m = PRESETS.get(name);
        return m == null ? null : new LinkedHashMap<>(m);
    }

    /** props 의 config.preset 을 읽어, props 에 없는 키만 프리셋 값으로 채운다. 모르는 이름이면 IllegalArgumentException. */
    public static void apply(Properties props) {
        String name = name(props);
        Map<String, String> m = PRESETS.get(name);
        if (m == null) {
            throw new IllegalArgumentException("설정 '" + KEY + "' 값 '" + name + "' 이(가) 잘못됐습니다: " + String.join(", ", NAMES) + " 중 하나");
        }
        for (var e : m.entrySet()) {
            String cur = props.getProperty(e.getKey());
            // 자동 생성된 furnace.properties 는 모든 키를 기본값으로 적어 두므로, '기본값 그대로인 키' 도 안 건드린 것으로 본다
            if (cur == null || isDefault(e.getKey(), cur)) props.setProperty(e.getKey(), e.getValue());
        }
    }

    private static boolean isDefault(String key, String value) {
        for (OptionKeys.Option o : OptionKeys.all()) {
            if (o.key().equals(key)) return !ConfigDump.differs(o, value);
        }
        return false;
    }

    public static String name(Properties props) {
        return props.getProperty(KEY, "none").strip().toLowerCase(java.util.Locale.ROOT);
    }
}
