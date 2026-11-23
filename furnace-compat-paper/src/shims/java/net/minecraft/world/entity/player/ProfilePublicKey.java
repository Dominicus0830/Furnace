package net.minecraft.world.entity.player;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;

/**
 * 플레이어 채팅 공개 키 shim(Mojang 이름, ProtocolLib 다리 2b-3). 바닐라 1.21.1 과 같은 모양 — 만료 시각, RSA 공개 키, Mojang 이 붙인 키 서명.
 * 채팅 세션(RemoteChatSession)에 들어 있다. 키 검증(createValidated)은 Furnace 가 하지 않는다.
 */
record ProfilePublicKey {

    /** 바닐라: 만료 뒤에도 받아 주는 시간. */
    public static final Duration EXPIRY_GRACE_PERIOD = Duration.ofHours(8L);

    /** 바닐라: 키 값(만료 시각, X.509 RSA 공개 키, 키 서명 4096바이트까지). */
    public record Data(Instant expiresAt, PublicKey key, byte[] keySignature) {

        public boolean hasExpired() { return expiresAt.isBefore(Instant.now()); }

        public boolean hasExpired(Duration gracePeriod) { return expiresAt.plus(gracePeriod).isBefore(Instant.now()); }
    }
}
