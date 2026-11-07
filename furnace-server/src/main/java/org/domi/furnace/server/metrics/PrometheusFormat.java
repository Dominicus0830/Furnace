// Prometheus 텍스트 노출 형식 0.0.4: 이름마다 # HELP / # TYPE 한 번, 이어서 값 줄들.
package org.domi.furnace.server.metrics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PrometheusFormat {
    private PrometheusFormat() {}

    public static String render(List<Sample> samples) {
        Map<String, List<Sample>> byName = new LinkedHashMap<>();
        for (Sample s : samples) {
            if (Double.isNaN(s.value())) continue;
            byName.computeIfAbsent(s.name(), k -> new java.util.ArrayList<>()).add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (var e : byName.entrySet()) {
            Sample first = e.getValue().get(0);
            sb.append("# HELP ").append(e.getKey()).append(' ').append(escapeHelp(first.help())).append('\n');
            sb.append("# TYPE ").append(e.getKey()).append(' ').append(first.type()).append('\n');
            for (Sample s : e.getValue()) {
                sb.append(s.name());
                if (!s.labels().isEmpty()) {
                    sb.append('{');
                    boolean firstLabel = true;
                    for (var l : s.labels().entrySet()) {
                        if (!firstLabel) sb.append(',');
                        firstLabel = false;
                        sb.append(l.getKey()).append("=\"").append(escapeLabel(l.getValue())).append('"');
                    }
                    sb.append('}');
                }
                sb.append(' ').append(number(s.value())).append('\n');
            }
        }
        return sb.toString();
    }

    static String number(double v) {
        if (Double.isInfinite(v)) return v > 0 ? "+Inf" : "-Inf";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%s", Double.toString(v));
    }

    static String escapeHelp(String s) { return s.replace("\\", "\\\\").replace("\n", "\\n"); }

    static String escapeLabel(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
