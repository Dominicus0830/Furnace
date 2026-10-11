// 컴포넌트 값 표현 — JSON 과 같은 모양의 자바 값(Map·List·String·Boolean·Integer·Long·Float·Double).
// 바닐라 DataComponentPatch 처럼 기본값과 같으면 패치에서 빠지므로 값 비교는 정규 문자열로 한다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class Components {
    private Components() {}

    public static Object fromJson(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonObject()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var x : ((JsonObject) e).entrySet()) m.put(x.getKey(), fromJson(x.getValue()));
            return m;
        }
        if (e.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (JsonElement x : (JsonArray) e) l.add(fromJson(x));
            return l;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        BigDecimal d = p.getAsBigDecimal();
        if (d.scale() <= 0 || d.stripTrailingZeros().scale() <= 0) {
            try { return d.intValueExact(); } catch (ArithmeticException ex) { return d.longValue(); }
        }
        return d.doubleValue();
    }

    /** 키 정렬·수 정규화한 문자열 — 기본값 비교와 테스트 대조용. */
    public static String canon(Object v) {
        StringBuilder b = new StringBuilder();
        canon(v, b);
        return b.toString();
    }

    private static void canon(Object v, StringBuilder b) {
        if (v == null) { b.append("null"); return; }
        if (v instanceof Map<?, ?> m) {
            b.append('{');
            boolean first = true;
            for (var e : new TreeMap<>(m).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(e.getKey()).append("\":");
                canon(e.getValue(), b);
            }
            b.append('}');
        } else if (v instanceof List<?> l) {
            b.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) b.append(',');
                canon(l.get(i), b);
            }
            b.append(']');
        } else if (v instanceof Boolean bo) {
            b.append(bo ? "1" : "0");   // NBT 에는 불리언이 바이트로 남는다
        } else if (v instanceof Number n) {
            BigDecimal d = n instanceof Float f ? new BigDecimal(Float.toString(f)) : new BigDecimal(n.toString());
            b.append(d.stripTrailingZeros().toPlainString());
        } else {
            b.append('"').append(v).append('"');
        }
    }

    public static boolean same(Object a, Object b) { return canon(a).equals(canon(b)); }
}
