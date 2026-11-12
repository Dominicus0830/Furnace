package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.domi.furnace.api.config.ConfigSection;

/** 설정 값 ↔ 트리 값 변환(MemorySection 과 YamlConfiguration 이 같이 쓴다). */
public final class ConfigValues {

    private ConfigValues() {}

    /** 값 → 트리에 넣을 꼴: ConfigurationSerializable 은 참조를 보관하는 잎({@link ConfigObject}), 집합은 리스트로. */
    public static Object lower(Object v) { return lower(v, true); }

    private static Object lower(Object v, boolean keepObjects) {
        if (v instanceof MemorySection m) return m.furnaceSection();
        if (v instanceof ConfigurationSection s) return s.getValues(false);
        if (v instanceof ConfigurationSerializable c) return keepObjects ? new ConfigObject(c) : lowerSerializable(c);
        if (v instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) out.put(e.getKey(), lower(e.getValue(), keepObjects));
            return out;
        }
        if (v instanceof Iterable<?> it && !(v instanceof ConfigSection)) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) out.add(lower(o, keepObjects));
            return out;
        }
        return v;
    }

    /** 직렬화 객체의 저장 꼴(맨 앞 {@code ==} 별칭 + serialize() 의 항목, 안쪽 객체도 같은 꼴의 맵으로). */
    public static Map<String, Object> lowerSerializable(ConfigurationSerializable c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(c.getClass()));
        for (var e : c.serialize().entrySet()) m.put(e.getKey(), lower(e.getValue(), false));
        return m;
    }

    /** 트리 값 → Bukkit 이 돌려주는 꼴: 객체 잎은 그 객체, 구역은 맵(목록 안 원소용), 목록은 안쪽까지. */
    public static Object plainOut(Object v) {
        if (v instanceof ConfigObject o) return o.value();
        if (v instanceof ConfigSection s) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : s.keys(false)) out.put(k, plainOut(s.getAt(List.of(k))));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(plainOut(o));
            return out;
        }
        return v;
    }

    /** plainOut 이 바꿀 것이 목록 안에 있는가(없으면 목록 그대로 돌려준다 — 참조 유지). */
    public static boolean needsOut(Object v) {
        if (v instanceof ConfigObject || v instanceof ConfigSection) return true;
        if (v instanceof List<?> l) for (Object o : l) if (needsOut(o)) return true;
        return false;
    }

    /**
     * 읽어 온 트리의 {@code ==} 구역(별칭이 등록된 것)을 직렬화 객체로 되살려 잎으로 바꾼다 — Paper YamlConstructor 처럼 읽을 때 한 번(안쪽부터).
     * 되살리지 못하면(별칭을 모르거나 되읽기가 null·예외) 구역 그대로 둔다.
     */
    public static void reviveTree(ConfigSection s) {
        for (String k : new ArrayList<>(s.keys(false))) {
            Object v = s.getAt(List.of(k));
            Object r = reviveNode(v);
            if (r != v) s.setLiteral(k, r);
        }
    }

    private static Object reviveNode(Object v) {
        if (v instanceof ConfigSection c) {
            reviveTree(c);
            if (c.getAt(List.of(ConfigurationSerialization.SERIALIZED_TYPE_KEY)) instanceof String alias && ConfigurationSerialization.getClassByAlias(alias) != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> plain = (Map<String, Object>) plainOut(c);
                    ConfigurationSerializable obj = ConfigurationSerialization.deserializeObject(plain);
                    if (obj != null) return new ConfigObject(obj);
                } catch (RuntimeException ignored) {
                    // 되살리지 못하면 구역 그대로
                }
            }
            return c;
        }
        if (v instanceof List<?> l) {
            List<Object> out = null;
            for (int i = 0; i < l.size(); i++) {
                Object o = reviveNode(l.get(i));
                if (o != l.get(i) && out == null) out = new ArrayList<>(l.subList(0, i));
                if (out != null) out.add(o);
            }
            return out == null ? v : out;
        }
        return v;
    }
}
