// 세션 서버 hasJoined 조회(authlib YggdrasilMinecraftSessionService.hasJoinedServer 대응). 네트워크 스레드를 막지 않게 비동기.
// 호스트는 authlib 과 같은 시스템 속성 minecraft.api.session.host 로 바꿀 수 있다(테스트·사설 인증 서버).
package org.domi.furnace.server.net.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.players.GameProfile;

public class SessionService {

    /** 인증 서버에 닿지 못함(바닐라 AuthenticationUnavailableException) — "Authentication servers are down". */
    public static final class AuthenticationUnavailableException extends Exception {
        public AuthenticationUnavailableException(String message, Throwable cause) { super(message, cause); }
    }

    private final String host;
    private final HttpClient client;

    public SessionService() {
        this(System.getProperty("minecraft.api.session.host", "https://sessionserver.mojang.com"));
    }

    public SessionService(String host) {
        this.host = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .proxy(java.net.ProxySelector.getDefault()).build();
    }

    /**
     * 완료값: 확인된 프로필, 또는 세션이 없으면(204 등) empty. 인증 서버 장애면 AuthenticationUnavailableException 으로 실패.
     * ip 는 prevent-proxy-connections 일 때만 넘긴다.
     */
    public CompletableFuture<Optional<GameProfile>> hasJoinedServer(String name, String serverId, InetAddress ip) {
        StringBuilder q = new StringBuilder(host).append("/session/minecraft/hasJoined?username=")
                .append(enc(name)).append("&serverId=").append(enc(serverId));
        if (ip != null) q.append("&ip=").append(enc(ip.getHostAddress()));
        HttpRequest req = HttpRequest.newBuilder(URI.create(q.toString())).timeout(Duration.ofSeconds(10)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, err) -> {
                    if (err != null) {
                        throw new java.util.concurrent.CompletionException(
                                new AuthenticationUnavailableException("Cannot contact authentication server", err));
                    }
                    int code = resp.statusCode();
                    if (code >= 500 || code == 429) {
                        throw new java.util.concurrent.CompletionException(
                                new AuthenticationUnavailableException("Authentication server error " + code, null));
                    }
                    if (code != 200 || resp.body() == null || resp.body().isBlank()) return Optional.<GameProfile>empty();
                    return parseProfile(resp.body());
                });
    }

    /**
     * 세션 서버의 profile/&lt;uuid&gt;?unsigned=false — 서명된 속성(스킨 textures)까지 든 프로필(authlib fetchProfile(id, true)).
     * 못 찾거나(204·404) 요청이 막히거나(429)·실패하면 empty(authlib 처럼 null 로 끝나는 경우). 네트워크 스레드를 막지 않게 비동기.
     */
    public CompletableFuture<Optional<GameProfile>> fetchProfile(UUID id) {
        String hex = id.toString().replace("-", "");
        HttpRequest req = HttpRequest.newBuilder(URI.create(host + "/session/minecraft/profile/" + hex + "?unsigned=false"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, err) -> {
                    if (err != null || resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) return Optional.<GameProfile>empty();
                    return parseProfile(resp.body());
                });
    }

    /**
     * 서비스 서버({@code minecraft.api.services.host}, 기본 api.minecraftservices.com)의 이름 → 프로필(UUID·이름만) 조회.
     * 없는 이름(404)·요청 막힘(429)·실패는 empty.
     */
    public CompletableFuture<Optional<GameProfile>> lookupByName(String name) {
        String servicesHost = System.getProperty("minecraft.api.services.host", "https://api.minecraftservices.com");
        if (servicesHost.endsWith("/")) servicesHost = servicesHost.substring(0, servicesHost.length() - 1);
        HttpRequest req = HttpRequest.newBuilder(URI.create(servicesHost + "/minecraft/profile/lookup/name/" + enc(name)))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, err) -> {
                    if (err != null || resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) return Optional.<GameProfile>empty();
                    return parseProfile(resp.body());
                });
    }

    /**
     * {@link #fetchProfile} 의 확인용 판: 429·5xx·연결 실패는 {@code attempts} 번까지 {@code delay} 간격으로 다시 시도하고, 끝내 안 되면 **실패한 Future**
     * (없는 프로필 = empty 와 구별한다 — 일시 장애가 "프로필 없음"으로 굳지 않게). 204·404 등 "없음"은 바로 empty.
     */
    public CompletableFuture<Optional<GameProfile>> fetchProfileRetrying(UUID id, int attempts, Duration delay) {
        String hex = id.toString().replace("-", "");
        return getProfileRetrying(host + "/session/minecraft/profile/" + hex + "?unsigned=false", attempts, delay);
    }

    /** {@link #lookupByName} 의 확인용 판(재시도는 {@link #fetchProfileRetrying} 과 같다). */
    public CompletableFuture<Optional<GameProfile>> lookupByNameRetrying(String name, int attempts, Duration delay) {
        String servicesHost = System.getProperty("minecraft.api.services.host", "https://api.minecraftservices.com");
        if (servicesHost.endsWith("/")) servicesHost = servicesHost.substring(0, servicesHost.length() - 1);
        return getProfileRetrying(servicesHost + "/minecraft/profile/lookup/name/" + enc(name), attempts, delay);
    }

    private CompletableFuture<Optional<GameProfile>> getProfileRetrying(String url, int attemptsLeft, Duration delay) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((resp, err) -> {
            if (err == null) {
                int code = resp.statusCode();
                if (code == 200) return CompletableFuture.completedFuture(resp.body() == null || resp.body().isBlank() ? Optional.<GameProfile>empty() : parseProfile(resp.body()));
                if (code != 429 && code < 500) return CompletableFuture.completedFuture(Optional.<GameProfile>empty());   // 없음
            }
            if (attemptsLeft > 1) {
                return CompletableFuture.supplyAsync(() -> (Void) null, CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS))
                        .thenCompose(v -> getProfileRetrying(url, attemptsLeft - 1, delay));
            }
            return CompletableFuture.<Optional<GameProfile>>failedFuture(err != null ? err
                    : new java.io.IOException("profile server answered " + resp.statusCode()));
        }).thenCompose(f -> f);
    }

    static Optional<GameProfile> parseProfile(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            UUID id = parseUuid(o.get("id").getAsString());
            String name = o.get("name").getAsString();
            List<GameProfile.Property> props = new ArrayList<>();
            if (o.get("properties") instanceof JsonArray arr) {
                for (JsonElement e : arr) {
                    JsonObject p = e.getAsJsonObject();
                    String sig = p.has("signature") && !p.get("signature").isJsonNull() ? p.get("signature").getAsString() : null;
                    props.add(new GameProfile.Property(p.get("name").getAsString(), p.get("value").getAsString(), sig));
                }
            }
            return Optional.of(new GameProfile(id, name, List.copyOf(props)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 대시 없는 32자리(세션 서버 형식) 또는 표준 UUID 문자열. */
    static UUID parseUuid(String s) {
        if (s.length() == 32) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        return UUID.fromString(s);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
