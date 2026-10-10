// 바닐라 SignedMessageChain — 세션별 고리 index 를 이어 가며 서명을 검증한다. 실패 사유는 chat.disabled.* 번역.
package org.domi.furnace.server.chat;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.net.protocol.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SignedMessageChain {

    private static final Logger log = LoggerFactory.getLogger(SignedMessageChain.class);

    public static final class DecodeException extends Exception {
        public static final Text MISSING_PROFILE_KEY = Text.translatable("chat.disabled.missingProfileKey");
        public static final Text CHAIN_BROKEN = Text.translatable("chat.disabled.chain_broken");
        public static final Text EXPIRED_PROFILE_KEY = Text.translatable("chat.disabled.expiredProfileKey");
        public static final Text INVALID_SIGNATURE = Text.translatable("chat.disabled.invalid_signature");
        public static final Text OUT_OF_ORDER_CHAT = Text.translatable("chat.disabled.out_of_order_chat");

        private final Text component;

        public DecodeException(Text component) {
            super(component.getString(), null, false, false);
            this.component = component;
        }

        public Text component() { return component; }
    }

    public interface Decoder {
        PlayerChatMessage unpack(MessageSignature signature, SignedMessageBody body) throws DecodeException;

        default void setChainBroken() {}

        /** 세션이 없을 때: secure chat 강제면 거부, 아니면 서명 없는 메시지. */
        static Decoder unsigned(UUID sender, BooleanSupplier enforcesSecureChat) {
            return (signature, body) -> {
                if (enforcesSecureChat.getAsBoolean()) throw new DecodeException(DecodeException.MISSING_PROFILE_KEY);
                return PlayerChatMessage.unsigned(sender, body.content());
            };
        }
    }

    private SignedMessageLink nextLink;
    private Instant lastTimeStamp = Instant.EPOCH;

    public SignedMessageChain(UUID profileId, UUID sessionId) {
        this.nextLink = SignedMessageLink.root(profileId, sessionId);
    }

    public Decoder decoder(ProfilePublicKey key) {
        SignatureValidator validator = key.createSignatureValidator();
        return new Decoder() {
            @Override
            public PlayerChatMessage unpack(MessageSignature signature, SignedMessageBody body) throws DecodeException {
                if (signature == null) throw new DecodeException(DecodeException.MISSING_PROFILE_KEY);
                if (key.data().hasExpired()) throw new DecodeException(DecodeException.EXPIRED_PROFILE_KEY);
                SignedMessageLink link = nextLink;
                if (link == null) throw new DecodeException(DecodeException.CHAIN_BROKEN);
                if (body.timeStamp().isBefore(lastTimeStamp)) {
                    setChainBroken();
                    throw new DecodeException(DecodeException.OUT_OF_ORDER_CHAT);
                }
                lastTimeStamp = body.timeStamp();
                PlayerChatMessage msg = new PlayerChatMessage(link, signature, body, null, false);
                if (!msg.verify(validator)) {
                    setChainBroken();
                    throw new DecodeException(DecodeException.INVALID_SIGNATURE);
                }
                if (msg.hasExpiredServer(Instant.now())) {
                    log.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", body.content());
                }
                nextLink = link.advance();
                return msg;
            }

            @Override
            public void setChainBroken() { nextLink = null; }
        };
    }
}
