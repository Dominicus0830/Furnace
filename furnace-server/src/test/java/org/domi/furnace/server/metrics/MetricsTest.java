package org.domi.furnace.server.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

class MetricsTest {

    private static final List<Sample> SAMPLES = List.of(
            Sample.gauge("furnace_players_online", "접속자", 3),
            Sample.gauge("furnace_tps_global", "전역", 19.5, "window", "5s"),
            Sample.gauge("furnace_tps_global", "전역", Double.NaN, "window", "1m"),
            Sample.gauge("furnace_loaded_chunks", "청크", 100, "dimension", "minecraft:overworld"),
            Sample.gauge("furnace_loaded_chunks", "청크", 7, "dimension", "a\"b\\c"));

    @Test
    void optionsDefaultOff() {
        var o = MetricsOptions.from(new Properties(), s -> fail(s));
        assertFalse(o.prometheus());
        assertFalse(o.jmx());
        assertEquals("127.0.0.1", o.bind());
    }

    @Test
    void optionsParseAndWarnOnOpenBind() {
        Properties p = new Properties();
        p.setProperty("metrics.prometheus.enabled", "true");
        p.setProperty("metrics.prometheus.bind", "0.0.0.0");
        p.setProperty("metrics.prometheus.port", "9100");
        p.setProperty("metrics.jmx.enabled", "true");
        List<String> warns = new ArrayList<>();
        var o = MetricsOptions.from(p, warns::add);
        assertTrue(o.prometheus() && o.jmx());
        assertEquals(9100, o.port());
        assertEquals(1, warns.size());
        p.setProperty("metrics.prometheus.token", "t");
        warns.clear();
        MetricsOptions.from(p, warns::add);
        assertTrue(warns.isEmpty());
        p.setProperty("metrics.prometheus.port", "70000");
        assertThrows(IllegalArgumentException.class, () -> MetricsOptions.from(p, s -> {}));
        Properties bad = new Properties();
        bad.setProperty("metrics.jmx.enabled", "yes");
        assertThrows(IllegalArgumentException.class, () -> MetricsOptions.from(bad, s -> {}));
    }

    @Test
    void prometheusFormat() {
        String t = PrometheusFormat.render(SAMPLES);
        assertTrue(t.contains("# HELP furnace_players_online 접속자\n# TYPE furnace_players_online gauge\nfurnace_players_online 3\n"), t);
        assertTrue(t.contains("furnace_tps_global{window=\"5s\"} 19.5\n"), t);
        assertFalse(t.contains("window=\"1m\""), "NaN 은 내보내지 않는다");
        assertEquals(1, t.split("# TYPE furnace_loaded_chunks", -1).length - 1, "이름마다 TYPE 한 번");
        assertTrue(t.contains("dimension=\"a\\\"b\\\\c\""), t);
    }

    @Test
    void prometheusServerServesMetricsAndChecksToken() throws Exception {
        try (var open = PrometheusServer.create("127.0.0.1", 0, "", () -> SAMPLES);
             var locked = PrometheusServer.create("127.0.0.1", 0, "secret", () -> SAMPLES)) {
            assertNotNull(open);
            var c = HttpClient.newHttpClient();
            var r = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + open.port() + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode());
            assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
            assertTrue(r.body().contains("furnace_players_online 3"));
            var post = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + open.port() + "/metrics"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, post.statusCode());
            var other = c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + open.port() + "/other")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, other.statusCode());

            String url = "http://127.0.0.1:" + locked.port() + "/metrics";
            assertEquals(401, c.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, c.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer wrong").build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, c.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer secret").build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void prometheusBindFailureReturnsNull() {
        try (var a = PrometheusServer.create("127.0.0.1", 0, "", () -> SAMPLES)) {
            assertNull(PrometheusServer.create("127.0.0.1", a.port(), "", () -> SAMPLES));
        }
    }

    @Test
    void jmxRegistersAndUnregisters() throws Exception {
        var server = ManagementFactory.getPlatformMBeanServer();
        var name = new ObjectName(JmxMetrics.OBJECT_NAME);
        try (var j = JmxMetrics.register(server, () -> SAMPLES)) {
            assertNotNull(j);
            assertEquals(3.0, (Double) server.getAttribute(name, "PlayersOnline"));
            assertEquals(19.5, (Double) server.getAttribute(name, "GlobalTps"));
            assertEquals(107.0, (Double) server.getAttribute(name, "LoadedChunks"));
            // MXBean 의 Map 속성은 TabularData 로 보인다(키·값 열)
            var m = (javax.management.openmbean.TabularData) server.getAttribute(name, "Metrics");
            var row = m.get(new Object[] {"furnace_tps_global{window=5s}"});
            assertEquals(19.5, (Double) row.get("value"));
        }
        assertFalse(server.isRegistered(name));
    }
}
