package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;

/** 설정 값 ↔ 저장 꼴 변환(MemorySection 과 YamlConfiguration 이 같이 쓴다). */
public final class ConfigValues {

    private ConfigValues() {}

    /** 값 → 저장할 꼴: ConfigurationSerializable 은 {@code ==} 별칭이 맨 앞인 맵으로(안쪽도 같게), 집합은 리스트로. */
    public static Object lower(Object v) {
        if (v instanceof MemorySection m) return m.furnaceSection();
        if (v instanceof ConfigurationSection s) return s.getValues(false);
        if (v instanceof ConfigurationSerializable c) return lowerSerializable(c);
        if (v instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) out.put(e.getKey(), lower(e.getValue()));
            return out;
        }
        if (v instanceof Iterable<?> it && !(v instanceof org.domi.furnace.api.config.ConfigSection)) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) out.add(lower(o));
            return out;
        }
        return v;
    }

    /** 직렬화 객체의 저장 꼴(맨 앞 {@code ==} 별칭 + serialize() 의 항목을 같은 방식으로). */
    public static Map<String, Object> lowerSerializable(ConfigurationSerializable c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(c.getClass()));
        for (var e : c.serialize().entrySet()) m.put(e.getKey(), lower(e.getValue()));
        return m;
    }
}
