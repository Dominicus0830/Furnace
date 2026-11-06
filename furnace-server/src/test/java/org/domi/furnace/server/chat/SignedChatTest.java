// 서명 채팅 검증 규칙: 프로필 키(Mojang 서명) 검증, 메시지 사슬(index·세션·서명 범위), 변조·순서 오류, last seen 창.
package org.domi.furnace.server.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignedChatTest {

    static KeyPair rsa(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    static byte[] sign(PrivateKey key, String alg, SignatureValidator.Updater updater) throws Exception {
        Signature s = Signature.getInstance(alg);
        s.initSign(key);
        updater.update(s);
        return s.sign();
    }

    /** 클라이언트 흉내: 사슬 고리를 이어 가며 메시지에 서명한다(바닐라 SignedMessageChain.encoder). */
    static final class ClientSigner {
        final PrivateKey key;
        SignedMessageLink link;

        ClientSigner(PrivateKey key, UUID profile, UUID session) {
            this.key = key;
            this.link = SignedMessageLink.root(profile, session);
        }

        MessageSignature sign(SignedMessageBody body) throws Exception {
            SignedMessageLink l = link;
            link = l.advance();
            return new MessageSignature(SignedChatTest.sign(key, "SHA256withRSA", out -> PlayerChatMessage.updateSignature(out, l, body)));
        }
    }

    static ProfilePublicKey.Data profileKey(KeyPair services, KeyPair player, UUID profile, Instant expires) throws Exception {
        ProfilePublicKey.Data unsigned = new ProfilePublicKey.Data(expires, player.getPublic(), new byte[0]);
        byte[] sig = sign(services.getPrivate(), "SHA1withRSA", out -> out.update(unsigned.signedPayload(profile)));
        return new ProfilePublicKey.Data(expires, player.getPublic(), sig);
    }

    @Test
    void profileKeyValidation() throws Exception {
        KeyPair services = rsa(2048), player = rsa(2048), other = rsa(2048);
        UUID id = UUID.randomUUID();
        ServicesKeys keys = new ServicesKeys();
        assertNull(keys.profileKeySignatureValidator(), "키가 없으면 검증 불가");
        keys.setProfileKeys(List.of(services.getPublic()));
        var data = profileKey(services, player, id, Instant.now().plusSeconds(3600));
        assertNotNull(new RemoteChatSession.Data(UUID.randomUUID(), data).validate(id, keys.profileKeySignatureValidator()));
        var forged = profileKey(other, player, id, Instant.now().plusSeconds(3600));
        var e = assertThrows(ProfilePublicKey.ValidationException.class,
                () -> new RemoteChatSession.Data(UUID.randomUUID(), forged).validate(id, keys.profileKeySignatureValidator()));
        assertEquals("multiplayer.disconnect.invalid_public_key_signature.new", "multiplayer.disconnect.invalid_public_key_signature.new");
        assertTrue(e.component().getString().startsWith("Invalid signature for profile public key"));
        assertThrows(ProfilePublicKey.ValidationException.class,
                () -> new RemoteChatSession.Data(UUID.randomUUID(), data).validate(UUID.randomUUID(), keys.profileKeySignatureValidator()),
                "다른 프로필 UUID 로는 통과하면 안 된다");
    }

    @Test
    void messageChain() throws Exception {
        KeyPair services = rsa(2048), player = rsa(2048);
        UUID id = UUID.randomUUID(), session = UUID.randomUUID();
        ServicesKeys keys = new ServicesKeys();
        keys.setProfileKeys(List.of(services.getPublic()));
        RemoteChatSession s = new RemoteChatSession.Data(session, profileKey(services, player, id, Instant.now().plusSeconds(3600)))
                .validate(id, keys.profileKeySignatureValidator());
        SignedMessageChain.Decoder decoder = s.createMessageDecoder(id);
        ClientSigner client = new ClientSigner(player.getPrivate(), id, session);

        SignedMessageBody b1 = new SignedMessageBody("hello", Instant.now(), 42L, LastSeenMessages.EMPTY);
        PlayerChatMessage m1 = decoder.unpack(client.sign(b1), b1);
        assertEquals(0, m1.link().index());
        SignedMessageBody b2 = new SignedMessageBody("second", Instant.now(), 7L, new LastSeenMessages(List.of(m1.signature())));
        PlayerChatMessage m2 = decoder.unpack(client.sign(b2), b2);
        assertEquals(1, m2.link().index());

        SignedMessageBody b3 = new SignedMessageBody("tampered", Instant.now(), 1L, LastSeenMessages.EMPTY);
        MessageSignature sig3 = client.sign(new SignedMessageBody("original", b3.timeStamp(), 1L, LastSeenMessages.EMPTY));
        var e = assertThrows(SignedMessageChain.DecodeException.class, () -> decoder.unpack(sig3, b3));
        assertEquals("Chat had an invalid signature. Please try reconnecting.", e.component().getString());
        SignedMessageBody b4 = new SignedMessageBody("after", Instant.now(), 2L, LastSeenMessages.EMPTY);
        e = assertThrows(SignedMessageChain.DecodeException.class, () -> decoder.unpack(client.sign(b4), b4));
        assertEquals("Chat disabled due to broken chain. Please try reconnecting.", e.component().getString());
        e = assertThrows(SignedMessageChain.DecodeException.class, () -> s.createMessageDecoder(id).unpack(null, b4));
        assertEquals("Chat disabled due to missing profile public key. Please try reconnecting.", e.component().getString());
    }

    @Test
    void outOfOrderBreaksChain() throws Exception {
        KeyPair services = rsa(2048), player = rsa(2048);
        UUID id = UUID.randomUUID(), session = UUID.randomUUID();
        ServicesKeys keys = new ServicesKeys();
        keys.setProfileKeys(List.of(services.getPublic()));
        RemoteChatSession s = new RemoteChatSession.Data(session, profileKey(services, player, id, Instant.now().plusSeconds(3600)))
                .validate(id, keys.profileKeySignatureValidator());
        var decoder = s.createMessageDecoder(id);
        ClientSigner client = new ClientSigner(player.getPrivate(), id, session);
        Instant now = Instant.now();
        SignedMessageBody later = new SignedMessageBody("a", now, 1, LastSeenMessages.EMPTY);
        decoder.unpack(client.sign(later), later);
        SignedMessageBody earlier = new SignedMessageBody("b", now.minusSeconds(10), 1, LastSeenMessages.EMPTY);
        var e = assertThrows(SignedMessageChain.DecodeException.class, () -> decoder.unpack(client.sign(earlier), earlier));
        assertEquals("Chat received out-of-order. Did your system time change?", e.component().getString());
    }

    @Test
    void relaxedOrderKeepsChain() throws Exception {
        KeyPair services = rsa(2048), player = rsa(2048);
        UUID id = UUID.randomUUID(), session = UUID.randomUUID();
        ServicesKeys keys = new ServicesKeys();
        keys.setProfileKeys(List.of(services.getPublic()));
        RemoteChatSession s = new RemoteChatSession.Data(session, profileKey(services, player, id, Instant.now().plusSeconds(3600)))
                .validate(id, keys.profileKeySignatureValidator());
        var decoder = s.createMessageDecoder(id, false);
        ClientSigner client = new ClientSigner(player.getPrivate(), id, session);
        Instant now = Instant.now();
        SignedMessageBody first = new SignedMessageBody("a", now, 1, LastSeenMessages.EMPTY);
        decoder.unpack(client.sign(first), first);
        assertFalse(decoder.consumeOutOfOrder());
        // 시각이 거꾸로 간 메시지: 예외 없이 체인에 이어 붙고 버리라는 표시만 남는다
        SignedMessageBody earlier = new SignedMessageBody("b", now.minusSeconds(10), 2, LastSeenMessages.EMPTY);
        assertEquals(1, decoder.unpack(client.sign(earlier), earlier).link().index());
        assertTrue(decoder.consumeOutOfOrder());
        assertFalse(decoder.consumeOutOfOrder(), "읽으면 지워진다");
        // 다음 정상 메시지는 이어진다(엄격 모드면 chain_broken)
        SignedMessageBody next = new SignedMessageBody("c", now.plusSeconds(1), 3, LastSeenMessages.EMPTY);
        assertEquals(2, decoder.unpack(client.sign(next), next).link().index());
        assertFalse(decoder.consumeOutOfOrder());
        // 서명이 틀리면 완화 모드여도 체인이 끊긴다
        SignedMessageBody forged = new SignedMessageBody("d", now.minusSeconds(20), 4, LastSeenMessages.EMPTY);
        MessageSignature bad = client.sign(new SignedMessageBody("x", forged.timeStamp(), 4, LastSeenMessages.EMPTY));
        assertThrows(SignedMessageChain.DecodeException.class, () -> decoder.unpack(bad, forged));
    }

    @Test
    void unsignedDecoderFollowsEnforcement() throws Exception {
        UUID id = UUID.randomUUID();
        PlayerChatMessage m = SignedMessageChain.Decoder.unsigned(id, () -> false).unpack(null, SignedMessageBody.unsigned("hi"));
        assertFalse(m.hasSignature());
        assertEquals(id, m.sender());
        assertThrows(SignedMessageChain.DecodeException.class,
                () -> SignedMessageChain.Decoder.unsigned(id, () -> true).unpack(null, SignedMessageBody.unsigned("hi")));
    }

    @Test
    void lastSeenWindow() {
        LastSeenMessagesValidator v = new LastSeenMessagesValidator(20);
        MessageSignature a = sig(1), b = sig(2);
        v.addPending(a);
        v.addPending(b);
        assertEquals(22, v.trackedMessagesCount());
        BitSet ack = new BitSet();
        ack.set(18);
        ack.set(19);
        // 창을 2 칸 밀면 a,b 가 마지막 두 칸(18,19)
        var seen = v.applyUpdate(new LastSeenMessages.Update(2, ack));
        assertEquals(List.of(a, b), seen.orElseThrow().entries());
        assertTrue(v.applyUpdate(new LastSeenMessages.Update(1, new BitSet())).isEmpty(), "이미 확인한 메시지를 무시하면 실패");
        LastSeenMessagesValidator w = new LastSeenMessagesValidator(20);
        assertFalse(w.applyOffset(1), "보낸 적 없는 만큼 창을 밀 수 없다");
    }

    @Test
    void signatureCachePacking() {
        MessageSignatureCache cache = MessageSignatureCache.createDefault();
        MessageSignature a = sig(1), b = sig(2);
        assertEquals(-1, cache.pack(a));
        cache.push(new SignedMessageBody("x", Instant.now(), 0, new LastSeenMessages(List.of(a))), b);
        assertEquals(0, cache.pack(b), "가장 최근 서명이 0번");
        assertEquals(1, cache.pack(a));
    }

    static MessageSignature sig(int seed) {
        byte[] b = new byte[256];
        java.util.Arrays.fill(b, (byte) seed);
        return new MessageSignature(b);
    }
}
