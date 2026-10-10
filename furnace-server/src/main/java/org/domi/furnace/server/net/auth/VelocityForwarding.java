// Velocity 모던 포워딩(Paper VelocityProxy 와 같은 규약) — 프록시가 velocity:player_info 로그인 질의 응답에 HMAC-SHA256 으로 서명한
// 실제 클라이언트 주소·프로필을 실어 보낸다. 켜져 있으면 서버는 암호화·세션 인증을 하지 않고 이 정보를 믿는다.
package org.domi.furnace.server.net.auth;

import io.netty.buffer.ByteBuf;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.players.GameProfile;

public final class VelocityForwarding {

    public static final String PLAYER_INFO_CHANNEL = "velocity:player_info";
    public static final int MODERN_FORWARDING_WITH_KEY = 2;
    public static final int MODERN_LAZY_SESSION = 4;
    public static final byte MAX_SUPPORTED_FORWARDING_VERSION = MODERN_LAZY_SESSION;

    /** 검증된 포워딩 정보. */
    public record Forwarded(int version, InetAddress address, GameProfile profile) {}

    private final byte[] secret;

    public VelocityForwarding(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 서명이 맞지 않으면 null. 지원하지 않는 버전이면 IllegalStateException. */
    public Forwarded read(ByteBuf buf) throws Exception {
        if (buf.readableBytes() < 32) return null;
        byte[] signature = new byte[32];
        buf.readBytes(signature);
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        if (!MessageDigest.isEqual(signature, mac.doFinal(data))) return null;
        int version = Types.readVarInt(buf);
        if (version > MAX_SUPPORTED_FORWARDING_VERSION) {
            throw new IllegalStateException("Unsupported forwarding version " + version + ", wanted upto " + MAX_SUPPORTED_FORWARDING_VERSION);
        }
        InetAddress address = InetAddress.getByName(Types.readString(buf, Short.MAX_VALUE)); // 프록시는 IP 리터럴을 보낸다
        java.util.UUID id = Types.readUuid(buf);
        String name = Types.readString(buf, 16);
        int n = Types.readVarInt(buf);
        List<GameProfile.Property> props = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String pn = Types.readString(buf, Short.MAX_VALUE);
            String pv = Types.readString(buf, Short.MAX_VALUE);
            String sig = buf.readBoolean() ? Types.readString(buf, Short.MAX_VALUE) : null;
            props.add(new GameProfile.Property(pn, pv, sig));
        }
        // 서명 채팅 키(버전 2·3)는 2-5 에서 다룬다 — 지금은 남은 바이트를 무시한다.
        return new Forwarded(version, address, new GameProfile(id, name, List.copyOf(props)));
    }

    /** 테스트·프록시 흉내용: 포워딩 페이로드에 서명을 붙인다. */
    public byte[] sign(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] sig = mac.doFinal(payload);
        byte[] out = new byte[32 + payload.length];
        System.arraycopy(sig, 0, out, 0, 32);
        System.arraycopy(payload, 0, out, 32, payload.length);
        return out;
    }
}
