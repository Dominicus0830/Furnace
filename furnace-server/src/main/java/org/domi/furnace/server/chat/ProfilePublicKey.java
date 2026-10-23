// 바닐라 ProfilePublicKey — 클라이언트 채팅 키(만료 시각·RSA 공개키·Mojang 서명). Mojang 서비스 키로 서명을 검증한다.
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ProtocolException;
import org.domi.furnace.api.text.Text;

public record ProfilePublicKey(Data data) {

    public static final Text EXPIRED_PROFILE_PUBLIC_KEY = Text.translatable("multiplayer.disconnect.expired_public_key");
    static final Text INVALID_SIGNATURE = Text.translatable("multiplayer.disconnect.invalid_public_key_signature.new");

    public static final class ValidationException extends Exception {
        private final Text component;

        public ValidationException(Text component) {
            super(component.getString(), null, false, false);
            this.component = component;
        }

        public Text component() { return component; }
    }

    public static ProfilePublicKey createValidated(SignatureValidator servicesValidator, UUID profileId, Data data)
            throws ValidationException {
        if (!data.validateSignature(servicesValidator, profileId)) throw new ValidationException(INVALID_SIGNATURE);
        return new ProfilePublicKey(data);
    }

    public SignatureValidator createSignatureValidator() { return SignatureValidator.from(data.key(), "SHA256withRSA"); }

    public record Data(Instant expiresAt, PublicKey key, byte[] keySignature) {
        private static final int MAX_KEY_SIGNATURE_SIZE = 4096;

        public static Data read(ByteBuf buf) {
            Instant expires = Instant.ofEpochMilli(buf.readLong());
            byte[] keyBytes = readBytes(buf, 512);
            byte[] sig = readBytes(buf, MAX_KEY_SIGNATURE_SIZE);
            try {
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
                return new Data(expires, key, sig);
            } catch (Exception e) {
                throw new ProtocolException("잘못된 공개키");
            }
        }

        public void write(ByteBuf buf) {
            buf.writeLong(expiresAt.toEpochMilli());
            byte[] k = key.getEncoded();
            Types.writeVarInt(buf, k.length);
            buf.writeBytes(k);
            Types.writeVarInt(buf, keySignature.length);
            buf.writeBytes(keySignature);
        }

        private static byte[] readBytes(ByteBuf buf, int max) {
            int n = Types.readVarInt(buf);
            if (n < 0 || n > max || n > buf.readableBytes()) throw new ProtocolException("바이트 배열 길이 오류: " + n);
            byte[] b = new byte[n];
            buf.readBytes(b);
            return b;
        }

        boolean validateSignature(SignatureValidator validator, UUID profileId) {
            return validator.validate(signedPayload(profileId), keySignature);
        }

        /** 서명 대상: 프로필 UUID(상·하위) + 만료 시각(ms) + 공개키 DER. */
        byte[] signedPayload(UUID profileId) {
            byte[] k = key.getEncoded();
            ByteBuffer b = ByteBuffer.allocate(24 + k.length);
            b.putLong(profileId.getMostSignificantBits()).putLong(profileId.getLeastSignificantBits()).putLong(expiresAt.toEpochMilli()).put(k);
            return b.array();
        }

        public boolean hasExpired() { return expiresAt.isBefore(Instant.now()); }

        @Override
        public boolean equals(Object o) {
            return o instanceof Data d && expiresAt.equals(d.expiresAt) && key.equals(d.key) && Arrays.equals(keySignature, d.keySignature);
        }

        @Override
        public int hashCode() { return expiresAt.hashCode() * 31 + key.hashCode(); }
    }
}
