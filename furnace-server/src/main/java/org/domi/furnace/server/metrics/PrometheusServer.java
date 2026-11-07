// Prometheus 내보내기 HTTP 서버: GET /metrics 만. JDK 내장 HttpServer(전용 데몬 스레드 1개) — 게임·region 스레드를 건드리지 않는다.
// 값 수집은 요청 스레드에서 읽기 전용 계측만 읽는다.
package org.domi.furnace.server.metrics;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrometheusServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(PrometheusServer.class);
    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final HttpServer http;
    private final java.util.concurrent.ExecutorService executor;

    private PrometheusServer(HttpServer http, java.util.concurrent.ExecutorService executor) {
        this.http = http;
        this.executor = executor;
    }

    /** 바인딩 실패(포트 점유 등)면 경고만 하고 null — 메트릭 때문에 서버가 못 뜨면 안 된다. */
    public static PrometheusServer create(String bind, int port, String token, MetricsSource source) {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(bind, port), 0);
            var executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Furnace Prometheus");
                t.setDaemon(true);
                return t;
            });
            byte[] expected = token.isEmpty() ? null : ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
            http.createContext("/metrics", ex -> {
                try (ex) {
                    if (!"GET".equals(ex.getRequestMethod()) && !"HEAD".equals(ex.getRequestMethod())) {
                        ex.getResponseHeaders().set("Allow", "GET, HEAD");
                        ex.sendResponseHeaders(405, -1);
                        return;
                    }
                    if (expected != null) {
                        String h = ex.getRequestHeaders().getFirst("Authorization");
                        if (h == null || !MessageDigest.isEqual(h.getBytes(StandardCharsets.UTF_8), expected)) {
                            ex.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                            ex.sendResponseHeaders(401, -1);
                            return;
                        }
                    }
                    byte[] body = PrometheusFormat.render(source.collect()).getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
                    if ("HEAD".equals(ex.getRequestMethod())) {
                        ex.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
                        ex.sendResponseHeaders(200, -1);
                        return;
                    }
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                } catch (RuntimeException e) {
                    log.warn("[METRICS] 응답 실패", e);
                    try { ex.sendResponseHeaders(500, -1); } catch (IOException | RuntimeException ignored) { }
                }
            });
            http.setExecutor(executor);
            http.start();
            log.info("Prometheus 메트릭: http://{}:{}/metrics", bind, http.getAddress().getPort());
            return new PrometheusServer(http, executor);
        } catch (IOException | RuntimeException e) {
            log.warn("[METRICS] Prometheus 서버를 열지 못했습니다({}:{}): {}", bind, port, e.toString());
            return null;
        }
    }

    public int port() { return http.getAddress().getPort(); }

    @Override
    public void close() {
        http.stop(0);
        executor.shutdownNow();
    }
}
