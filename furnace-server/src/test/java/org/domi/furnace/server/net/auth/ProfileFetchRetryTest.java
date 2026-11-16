// E11d: 프로필 확인용 조회의 재시도 — 429·5xx 는 다시 시도하고 끝내 안 되면 실패한 Future, "없음"(204·404)은 바로 empty.
package org.domi.furnace.server.net.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProfileFetchRetryTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String BODY = "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}";
    private static final Duration FAST = Duration.ofMillis(10);
    private HttpServer http;

    @AfterEach
    void stop() {
        System.clearProperty("minecraft.api.services.host");
        if (http != null) http.stop(0);
    }

    /** 처음 {@code failures} 번은 status 로 답하고 그 뒤는 200 + 본문. */
    private String serve(String path, int failures, int status, AtomicInteger calls) throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext(path, ex -> {
            if (calls.incrementAndGet() <= failures) {
                ex.sendResponseHeaders(status, -1);
            } else {
                byte[] b = BODY.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        http.start();
        return "http://127.0.0.1:" + http.getAddress().getPort();
    }

    @Test
    void aRateLimitedRequestIsRetriedUntilItSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String host = serve("/session/minecraft/profile", 2, 429, calls);
        var found = new SessionService(host).fetchProfileRetrying(NOTCH, 3, FAST).get(10, TimeUnit.SECONDS);
        assertEquals("Notch", found.orElseThrow().name());
        assertEquals(3, calls.get());
    }

    @Test
    void nameLookupRetriesServerErrorsToo() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String host = serve("/minecraft/profile/lookup/name", 1, 503, calls);
        System.setProperty("minecraft.api.services.host", host);
        var found = new SessionService("http://127.0.0.1:1").lookupByNameRetrying("Notch", 3, FAST).get(10, TimeUnit.SECONDS);
        assertEquals(NOTCH, found.orElseThrow().id());
        assertEquals(2, calls.get());
    }

    @Test
    void stillFailingAfterTheLastAttemptIsAFailureNotAMissingProfile() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String host = serve("/session/minecraft/profile", 99, 429, calls);
        var f = new SessionService(host).fetchProfileRetrying(NOTCH, 3, FAST);
        assertThrows(ExecutionException.class, () -> f.get(10, TimeUnit.SECONDS));
        assertEquals(3, calls.get());
        // 닿지 않는 서버도 같다
        var g = new SessionService("http://127.0.0.1:1").fetchProfileRetrying(NOTCH, 2, FAST);
        assertThrows(ExecutionException.class, () -> g.get(10, TimeUnit.SECONDS));
    }

    @Test
    void noSuchProfileIsEmptyWithoutRetrying() throws Exception {
        for (int status : new int[] {204, 404}) {
            AtomicInteger calls = new AtomicInteger();
            String host = serve("/session/minecraft/profile", 99, status, calls);
            assertTrue(new SessionService(host).fetchProfileRetrying(NOTCH, 3, FAST).get(10, TimeUnit.SECONDS).isEmpty(), "상태 " + status);
            assertEquals(1, calls.get(), "없음은 다시 묻지 않는다");
            http.stop(0);
        }
    }
}
