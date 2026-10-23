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
