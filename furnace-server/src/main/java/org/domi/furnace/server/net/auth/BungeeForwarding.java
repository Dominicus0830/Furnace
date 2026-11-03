// BungeeCord 레거시 IP 포워딩(Spigot settings.bungeecord 와 같은 규약) — 프록시가 핸드셰이크 주소 칸에
// "원래 호스트\0실제 IP\0UUID(대시 없음)[\0속성 JSON 배열]" 을 실어 보낸다. 서명이 없으므로 서버 포트를 방화벽으로 막아야 한다.
package org.domi.furnace.server.net.auth;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.domi.furnace.server.players.GameProfile;

public final class BungeeForwarding {

    /** Spigot ServerHandshakePacketListenerImpl.HOST_PATTERN — 실제 IP 칸에 허용하는 문자. */
    static final Pattern HOST_PATTERN = Pattern.compile("[0-9a-f\\.:]{0,45}");
    /** Spigot ServerLoginPacketListenerImpl.PROP_PATTERN — 이름이 이 모양인 속성만 받는다. */
    static final Pattern PROP_PATTERN = Pattern.compile("\\w{0,16}");
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Gson GSON = new Gson();

    /** 켜져 있을 때 포워딩 형식이 아니면 보내는 문구(Spigot 과 같다). */
    public static final String NOT_FORWARDED = "If you wish to use IP forwarding, please enable it in your BungeeCord config as well!";
    /** 꺼져 있을 때 포워딩 형식이 들어오면 보내는 문구(Spigot 과 같다). */
    public static final String UNEXPECTED_FORWARD = "Unknown data in login hostname, did you forget to enable BungeeCord in spigot.yml?";

    /** 꺼낸 정보. properties 는 이름 검사를 거친 것만. */
    public record Forwarded(String hostName, InetAddress address, UUID uuid, List<GameProfile.Property> properties) {}

    private record Prop(String name, String value, String signature) {}

    private BungeeForwarding() {}

    /**
     * 핸드셰이크 주소 칸을 푼다. 형식(칸 3·4개, IP 칸 모양)이 맞지 않거나 IP·UUID·JSON 을 읽을 수 없으면 null.
     * Spigot 은 IP 칸에 InetSocketAddress(호스트 이름) 를 써서 DNS 를 탈 수 있지만, 여기서는 IP 리터럴만 받는다
     * (프록시는 늘 IP 리터럴을 보낸다 — 이벤트루프에서 DNS 조회를 하지 않기 위함).
     */
    public static Forwarded parse(String hostField) {
        String[] split = hostField.split("\0");
        if ((split.length != 3 && split.length != 4) || !HOST_PATTERN.matcher(split[1]).matches()) return null;
        InetAddress address = ipLiteral(split[1]);
        UUID uuid = uuidLenient(split[2]);
        if (address == null || uuid == null) return null;
        List<GameProfile.Property> props = List.of();
        if (split.length == 4) {
            Prop[] raw;
            try {
                raw = GSON.fromJson(split[3], Prop[].class);
            } catch (JsonParseException e) {
                return null;
            }
            List<GameProfile.Property> out = new ArrayList<>();
            if (raw != null) {
                for (Prop p : raw) {
                    if (p == null || p.name() == null || p.value() == null || !PROP_PATTERN.matcher(p.name()).matches()) continue;
                    out.add(new GameProfile.Property(p.name(), p.value(), p.signature()));
                }
            }
            props = List.copyOf(out);
        }
        return new Forwarded(split[0], address, uuid, props);
    }

    /** 포워딩을 껐는데 포워딩 형식이 들어왔는지(Spigot: 칸 3·4개 + IP 칸 모양). */
    public static boolean looksForwarded(String hostField) {
        String[] split = hostField.split("\0");
        return (split.length == 3 || split.length == 4) && HOST_PATTERN.matcher(split[1]).matches();
    }

    /** IPv4 점 네 칸이나 IPv6(':' 포함) 리터럴만 받는다 — DNS 조회는 하지 않는다. */
    static InetAddress ipLiteral(String s) {
        try {
            if (IPV4.matcher(s).matches()) {
                String[] oct = s.split("\\.");
                byte[] b = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int v = Integer.parseInt(oct[i]);
                    if (v > 255) return null;
                    b[i] = (byte) v;
                }
                return InetAddress.getByAddress(b);
            }
            // ':' 가 있으면 JDK 는 IPv6 리터럴로만 읽고 실패해도 DNS 로 넘어가지 않는다
            return s.indexOf(':') >= 0 ? InetAddress.getByName(s) : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** UndashedUuid.fromStringLenient: 대시는 무시하고 16진 32자. */
    static UUID uuidLenient(String s) {
        String hex = s.replace("-", "");
        if (hex.length() != 32) return null;
        try {
            return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
