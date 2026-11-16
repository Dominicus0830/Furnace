package org.domi.furnace.compat.nms;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.domi.furnace.api.packet.PacketStruct;

/**
 * 채팅 서명 값 shim ↔ Furnace 범용 값(서버 GenericHand767 의 모양). 명령 인자 서명(argument_signatures), 채팅 세션(chat_session),
 * 본 메시지 확인(last_seen_update). 바닐라 모양이 아닌 값(null 필드, 21칸 이상 비트)은 IllegalArgumentException.
 */
final class ChatValues {

    /** 바닐라 채팅 확인 비트 수(FixedBitSet 20칸). */
    static final int LAST_SEEN_BITS = 20;

    private ChatValues() {}

    static List<Object> argumentSignatures(Object v, String at) {
        List<Object> out = new ArrayList<>();
        int i = 0;
        for (ArgumentSignatures.Entry e : need((ArgumentSignatures) v, at).entries()) {
            String eat = at + "[" + i++ + "]";
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("name", need(e.name(), eat + ".name"));
            m.put("signature", need(e.signature(), eat + ".signature").bytes());
            out.add(PacketStruct.of(m));
        }
        return out;
    }

    static ArgumentSignatures argumentSignaturesFrom(Object v, String at) {
        List<ArgumentSignatures.Entry> out = new ArrayList<>();
        for (Object o : need((List<?>) v, at)) {
            PacketStruct s = (PacketStruct) o;
            out.add(new ArgumentSignatures.Entry((String) s.get("name"), new MessageSignature((byte[]) s.get("signature"))));
        }
        return new ArgumentSignatures(List.copyOf(out));
    }

    static PacketStruct chatSession(Object v, String at) {
        RemoteChatSession.Data d = need((RemoteChatSession.Data) v, at);
        ProfilePublicKey.Data k = need(d.profilePublicKey(), at + ".profilePublicKey");
        LinkedHashMap<String, Object> key = new LinkedHashMap<>();
        key.put("expires_at", need(k.expiresAt(), at + ".profilePublicKey.expiresAt").toEpochMilli());
        key.put("key", need(k.key(), at + ".profilePublicKey.key").getEncoded());
        key.put("key_signature", need(k.keySignature(), at + ".profilePublicKey.keySignature"));
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", need(d.sessionId(), at + ".sessionId"));
        m.put("profile_public_key", PacketStruct.of(key));
        return PacketStruct.of(m);
    }

    static RemoteChatSession.Data chatSessionFrom(Object v, String at) {
        PacketStruct s = need((PacketStruct) v, at);
        PacketStruct k = (PacketStruct) s.get("profile_public_key");
        return new RemoteChatSession.Data((UUID) s.get("session_id"), new ProfilePublicKey.Data(Instant.ofEpochMilli(((Number) k.get("expires_at")).longValue()),
                rsaKey((byte[]) k.get("key"), at + ".profile_public_key.key"), (byte[]) k.get("key_signature")));
    }

    static PacketStruct lastSeen(Object v, String at) {
        LastSeenMessages.Update u = need((LastSeenMessages.Update) v, at);
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("offset", u.offset());
        m.put("acknowledged", ackMask(u.acknowledged(), at + ".acknowledged"));
        return PacketStruct.of(m);
    }

    static LastSeenMessages.Update lastSeenFrom(Object v, String at) {
        PacketStruct s = need((PacketStruct) v, at);
        return new LastSeenMessages.Update(((Number) s.get("offset")).intValue(), ackBits(((Number) s.get("acknowledged")).intValue()));
    }

    /** 20칸 비트 → 정수 마스크(비트 i = 최근 i 번째). */
    static int ackMask(BitSet bits, String at) {
        need(bits, at);
        if (bits.length() > LAST_SEEN_BITS) {
            throw new IllegalArgumentException(at + " must have " + LAST_SEEN_BITS + " bits (vanilla also fails to write more bits): " + bits);
        }
        long[] words = bits.toLongArray();
        return words.length == 0 ? 0 : (int) words[0];
    }

    static BitSet ackBits(int mask) {
        return BitSet.valueOf(new long[] {Integer.toUnsignedLong(mask)});
    }

    private static PublicKey rsaKey(byte[] a, String at) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(need(a, at)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(at + " is not an RSA public key (X.509): " + e.getMessage(), e);
        }
    }

    private static <T> T need(T v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " is null (vanilla packets always carry this value)");
        return v;
    }
}
