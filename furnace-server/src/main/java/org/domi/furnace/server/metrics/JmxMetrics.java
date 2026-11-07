// JMX 내보내기: 플랫폼 MBeanServer 에 org.domi.furnace:type=Server 하나를 등록한다. 읽을 때마다 MetricsSource 에서 새로 모은다.
package org.domi.furnace.server.metrics;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JmxMetrics implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(JmxMetrics.class);
    public static final String OBJECT_NAME = "org.domi.furnace:type=Server";

    private final MBeanServer server;
    private final ObjectName name;

    private JmxMetrics(MBeanServer server, ObjectName name) {
        this.server = server;
        this.name = name;
    }

    public static JmxMetrics register(MetricsSource source) {
        return register(ManagementFactory.getPlatformMBeanServer(), source);
    }

    public static JmxMetrics register(MBeanServer server, MetricsSource source) {
        try {
            ObjectName name = new ObjectName(OBJECT_NAME);
            if (server.isRegistered(name)) server.unregisterMBean(name);
            server.registerMBean(new Bean(source), name);
            log.info("JMX 메트릭: {}", OBJECT_NAME);
            return new JmxMetrics(server, name);
        } catch (Exception e) {
            log.warn("[METRICS] JMX MBean 등록 실패: {}", e.toString());
            return null;
        }
    }

    @Override
    public void close() {
        try {
            if (server.isRegistered(name)) server.unregisterMBean(name);
        } catch (Exception e) {
            log.warn("[METRICS] JMX MBean 해제 실패: {}", e.toString());
        }
    }

    static String key(Sample s) {
        if (s.labels().isEmpty()) return s.name();
        StringBuilder sb = new StringBuilder(s.name()).append('{');
        boolean first = true;
        for (var l : s.labels().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(l.getKey()).append('=').append(l.getValue());
        }
        return sb.append('}').toString();
    }

    private record Bean(MetricsSource source) implements FurnaceMXBean {
        @Override public Map<String, Double> getMetrics() {
            Map<String, Double> m = new LinkedHashMap<>();
            for (Sample s : source.collect()) m.put(key(s), s.value());
            return m;
        }

        private double one(String name, Map<String, String> labels) {
            for (Sample s : source.collect()) if (s.name().equals(name) && s.labels().equals(labels)) return s.value();
            return Double.NaN;
        }

        @Override public double getPlayersOnline() { return one("furnace_players_online", Map.of()); }

        @Override public double getGlobalTps() { return one("furnace_tps_global", Map.of("window", "5s")); }

        @Override public double getLoadedChunks() {
            double sum = 0;
            boolean any = false;
            for (Sample s : source.collect()) if (s.name().equals("furnace_loaded_chunks")) { sum += s.value(); any = true; }
            return any ? sum : Double.NaN;
        }
    }
}
