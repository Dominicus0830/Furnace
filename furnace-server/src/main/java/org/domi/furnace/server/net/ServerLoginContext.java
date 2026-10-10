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
        this.velocity = secret == null || secret.isEmpty() ? null : new org.domi.furnace.server.net.auth.VelocityForwarding(secret);
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
                loadFavicon(iconFile));
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

    /**
     * 바닐라 ServerStatus JSON — description, players{max, online, sample?}, version, favicon?, enforcesSecureChat.
     * sample: 접속자 중 임의 시작점부터 최대 12명, 목록 공개를 끈 사람은 익명, 섞어서. hide-online-players 면 생략.
     */
    String statusJson(List<GameProfile> online, List<Boolean> allowsListing) {
        JsonObject o = new JsonObject();
        o.add("description", new JsonPrimitive(motd));
        JsonObject players = new JsonObject();
        players.addProperty("max", maxPlayers);
        players.addProperty("online", online.size());
        if (!hideOnlinePlayers && !online.isEmpty()) {
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
        o.addProperty("enforcesSecureChat", enforceSecureChat);
        return o.toString();
    }
}
