package org.domi.furnace.server.metrics;

import java.util.Map;

/** 메트릭 값 하나. type 은 Prometheus 의 "gauge" 또는 "counter". NaN 값은 내보내지 않는다(잴 수 없음). */
public record Sample(String name, String help, String type, Map<String, String> labels, double value) {
    public static Sample gauge(String name, String help, double value, String... labelKv) {
        return new Sample(name, help, "gauge", labels(labelKv), value);
    }

    public static Sample counter(String name, String help, double value, String... labelKv) {
        return new Sample(name, help, "counter", labels(labelKv), value);
    }

    private static Map<String, String> labels(String[] kv) {
        var m = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
