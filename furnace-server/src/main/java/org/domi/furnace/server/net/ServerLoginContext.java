// 연결 처리에 필요한 서버 전역 설정·상태(온라인 모드 키·세션 서비스·사용자 목록·상태 응답 정보). 모든 연결이 공유한다.
package org.domi.furnace.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.domi.furnace.server.config.ServerProperties;
import org.domi.furnace.server.net.auth.Crypt;
import org.domi.furnace.server.net.auth.SessionService;
import org.domi.furnace.server.players.GameProfile;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerLoginContext {

    private static final Logger log = LoggerFactory.getLogger(ServerLoginContext.class);
    /** 상태 응답 sample 에서 목록 공개를 거부한 플레이어 대신 쓰는 익명 프로필(바닐라 MinecraftServer.ANONYMOUS_PLAYER_PROFILE). */
    static final GameProfile ANONYMOUS = new GameProfile(new UUID(0L, 0L), "Anonymous Player");
    private static final int MAX_SAMPLE = 12;

    final boolean onlineMode;
    final boolean preventProxyConnections;
    final boolean acceptsTransfers;
    final boolean enableStatus;
    final boolean hideOnlinePlayers;
    final boolean enforceSecureChat;
    final boolean logIps;
    final int maxPlayers;
    final String motd;
    final KeyPair keyPair;
    final SessionService sessions;
    final PlayerLists lists;
    final GameProfileCache profileCache;
    final String favicon;
    /** 명령·서버 상태 핸들(테스트에서는 null 일 수 있다). */
    volatile org.domi.furnace.server.ServerCore core;

    public ServerLoginContext core(org.domi.furnace.server.ServerCore core) {
        this.core = core;
        return this;
    }

    /** server.properties 의 리소스 팩(없으면 null). 바닐라처럼 기동 때 한 번 읽는다. */
    volatile ServerProperties.ResourcePackInfo resourcePack;

    public ServerLoginContext resourcePack(ServerProperties.ResourcePackInfo info) {
        this.resourcePack = info;
        return this;
    }

    /** 접속 방어 설정(단계별 패킷 속도·크기·시간 초과, 길이 상한, PROXY 프로토콜). 기본은 지금까지의 동작. */
    volatile ConnectionLimits limits = ConnectionLimits.DEFAULT;

    public ServerLoginContext limits(ConnectionLimits limits) {
        this.limits = limits == null ? ConnectionLimits.DEFAULT : limits;
        return this;
    }

    public ConnectionLimits limits() { return limits; }

    /** null 이면 Velocity 포워딩 꺼짐. */
    volatile org.domi.furnace.server.net.auth.VelocityForwarding velocity;
    private final Random random = new Random();

    public ServerLoginContext(boolean onlineMode, boolean preventProxyConnections, boolean acceptsTransfers, boolean enableStatus,
                              boolean hideOnlinePlayers, boolean enforceSecureChat, boolean logIps, int maxPlayers, String motd,
                              KeyPair keyPair, SessionService sessions, PlayerLists lists, GameProfileCache profileCache,
                              String favicon) {
        this.onlineMode = onlineMode;
        this.preventProxyConnections = preventProxyConnections;
        this.acceptsTransfers = acceptsTransfers;
        this.enableStatus = enableStatus;
        this.hideOnlinePlayers = hideOnlinePlayers;
        this.enforceSecureChat = enforceSecureChat;
        this.logIps = logIps;
        this.maxPlayers = maxPlayers;
        this.motd = motd;
        this.keyPair = keyPair;
        this.sessions = sessions;
        this.lists = lists;
        this.profileCache = profileCache;
        this.favicon = favicon;
    }

    /** Velocity 모던 포워딩 비밀(빈 값이면 끔). */
    public ServerLoginContext velocitySecret(String secret) {
        if (bungeecord && secret != null && !secret.isEmpty()) throw new IllegalStateException("BungeeCord 포워딩과 Velocity 포워딩은 함께 켤 수 없다");
        this.velocity = secret == null || secret.isEmpty() ? null : new org.domi.furnace.server.net.auth.VelocityForwarding(secret);
        return this;
    }

    /** BungeeCord 레거시 IP 포워딩(Spigot settings.bungeecord). 켜면 핸드셰이크 주소 칸에서 실제 IP·UUID·속성을 읽는다. */
    volatile boolean bungeecord;

    public ServerLoginContext bungeecord(boolean enabled) {
        if (enabled && velocity != null) throw new IllegalStateException("BungeeCord 포워딩과 Velocity 포워딩은 함께 켤 수 없다");
        this.bungeecord = enabled;
        return this;
    }

    /** 테스트·내장용: 오프라인, 목록 검사 없음. */
    public static ServerLoginContext offline(String motd) {
        return new ServerLoginContext(false, false, false, true, false, false, true, 20, motd,
                null, null, null, null, null);
    }

    /** server.properties 로 만든다. keys 는 기동 초반에 만든 서버 키 쌍(바닐라 "Generating keypair"). */
    public static ServerLoginContext fromProperties(ServerProperties p, KeyPair keys, PlayerLists lists, GameProfileCache cache,
                                                    Path iconFile) {
        // 서명 채팅 강제는 Mojang 프로필 공개키를 검증할 수 있어야 한다(2-5 에서 키 조회와 함께 켠다).
        return new ServerLoginContext(p.onlineMode, p.preventProxyConnections, p.acceptsTransfers, p.enableStatus,
                p.hideOnlinePlayers, false, p.logIps, p.maxPlayers, p.motd, keys, new SessionService(), lists, cache,
                loadFavicon(iconFile)).resourcePack(p.serverResourcePackInfo.orElse(null));
    }

    /** server-icon.png(64x64)를 PNG 로 다시 인코딩해 data URI 로. 없거나 크기가 다르면 null. */
    static String loadFavicon(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img.getWidth() != 64) throw new IllegalStateException("Must be 64 pixels wide");
            if (img.getHeight() != 64) throw new IllegalStateException("Must be 64 pixels high");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("Couldn't load server icon", e);
            return null;
        }
    }

    /** server_data 패킷용 아이콘 PNG 바이트(없으면 null). */
    byte[] faviconBytes() {
        if (favicon == null) return null;
        int comma = favicon.indexOf(',');
        return java.util.Base64.getDecoder().decode(favicon.substring(comma + 1));
    }

    /**
     * 바닐라 ServerStatus JSON — description, players{max, online, sample?}, version, favicon?, enforcesSecureChat.
     * sample: 접속자 중 임의 시작점부터 최대 12명, 목록 공개를 끈 사람은 익명, 섞어서. hide-online-players 면 생략.
     */
    String statusJson(List<GameProfile> online, List<Boolean> allowsListing) {
        return statusJson(online, allowsListing, org.domi.furnace.server.plugin.GameEvents.NONE, null);
    }

    /** 플러그인 ServerListPingEvent 가 MOTD·인원·목록 숨김을 바꿀 수 있다(바꾸지 않으면 위와 같은 JSON). */
    String statusJson(List<GameProfile> online, List<Boolean> allowsListing, org.domi.furnace.server.plugin.GameEvents events, java.net.InetAddress address) {
        var ov = events.serverListPing(address, motd, online.size(), maxPlayers, hideOnlinePlayers);
        JsonObject o = new JsonObject();
        if (ov != null && ov.motdJson() != null) o.add("description", com.google.gson.JsonParser.parseString(ov.motdJson()));
        else o.add("description", new JsonPrimitive(motd));
        JsonObject players = new JsonObject();
        players.addProperty("max", ov != null ? ov.max() : maxPlayers);
        players.addProperty("online", ov != null ? ov.online() : online.size());
        boolean hide = ov != null ? ov.hidePlayers() : hideOnlinePlayers;
        if (!hide && !online.isEmpty()) {
            int n = Math.min(online.size(), MAX_SAMPLE);
            int start = online.size() - n == 0 ? 0 : random.nextInt(online.size() - n + 1);
            List<GameProfile> sample = new ArrayList<>(n);
            for (int i = 0; i < n; i++) sample.add(allowsListing.get(start + i) ? online.get(start + i) : ANONYMOUS);
            Collections.shuffle(sample, random);
            JsonArray arr = new JsonArray();
            for (GameProfile g : sample) {
                JsonObject e = new JsonObject();
                e.addProperty("id", g.id().toString());
                e.addProperty("name", g.name());
                arr.add(e);
            }
            players.add("sample", arr);
        }
        o.add("players", players);
        JsonObject version = new JsonObject();
        version.addProperty("name", "1.21.1");
        version.addProperty("protocol", 767);
        o.add("version", version);
        if (favicon != null) o.addProperty("favicon", favicon);
        o.addProperty("enforcesSecureChat", core != null ? core.enforceSecureProfile() : enforceSecureChat);
        return o.toString();
    }
}
