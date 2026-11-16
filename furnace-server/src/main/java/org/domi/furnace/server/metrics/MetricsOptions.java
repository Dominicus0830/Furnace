// 메트릭 내보내기 옵션(furnace.properties) — 모두 기본 꺼짐.
//   metrics.prometheus.enabled=false   Prometheus 텍스트 형식을 HTTP GET /metrics 로 내보낸다
//   metrics.prometheus.bind=127.0.0.1  바인딩 주소(기본 루프백 — 밖으로 열려면 직접 바꾼다)
//   metrics.prometheus.port=9225       포트(0 = 비어 있는 포트)
//   metrics.prometheus.token=          비어 있지 않으면 `Authorization: Bearer <값>` 이 맞아야 응답한다
//   metrics.jmx.enabled=false          같은 값을 JMX MBean(org.domi.furnace:type=Server)으로 등록한다
package org.domi.furnace.server.metrics;

import org.domi.furnace.server.i18n.Lang;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

public record MetricsOptions(boolean prometheus, String bind, int port, String token, boolean jmx) {

    public static final MetricsOptions DEFAULT = new MetricsOptions(false, "127.0.0.1", 9225, "", false);

    public static MetricsOptions load(Path path, Consumer<String> warn) {
        if (!Files.exists(path)) return DEFAULT;
        return from(org.domi.furnace.server.config.FurnaceProps.read(path), warn);
    }

    static MetricsOptions from(Properties props, Consumer<String> warn) {
        boolean prom = bool(props, "metrics.prometheus.enabled", DEFAULT.prometheus);
        boolean jmx = bool(props, "metrics.jmx.enabled", DEFAULT.jmx);
        String bind = props.getProperty("metrics.prometheus.bind", DEFAULT.bind).strip();
        if (bind.isEmpty()) bind = DEFAULT.bind;
        int port = DEFAULT.port;
        String raw = props.getProperty("metrics.prometheus.port");
        if (raw != null) {
            try {
                port = Integer.parseInt(raw.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(Lang.t("cfg.not-number", "metrics.prometheus.port", raw), e);
            }
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException(Lang.t("cfg.out-of-range", "metrics.prometheus.port", port, 0, 65535));
            }
        }
        String token = props.getProperty("metrics.prometheus.token", "").strip();
        if (prom && token.isEmpty() && !isLoopback(bind)) {
            warn.accept(Lang.t("cfg.metrics-open", bind));
        }
        return new MetricsOptions(prom, bind, port, token, jmx);
    }

    static boolean isLoopback(String bind) {
        return bind.equals("localhost") || bind.startsWith("127.") || bind.equals("::1") || bind.equals("[::1]");
    }

    private static boolean bool(Properties props, String key, boolean def) {
        String v = props.getProperty(key);
        if (v == null) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(Lang.t("cfg.invalid-bool", key, v));
        }
        return Boolean.parseBoolean(v);
    }
}
