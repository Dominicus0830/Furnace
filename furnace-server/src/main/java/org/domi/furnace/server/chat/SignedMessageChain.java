// 바닐라 SignedMessageChain — 세션별 고리 index 를 이어 가며 서명을 검증한다. 실패 사유는 chat.disabled.* 번역.
package org.domi.furnace.server.chat;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.domi.furnace.api.text.Text;
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

        /** 직전 unpack 이 순서가 어긋난 메시지를 체인에는 이어 붙이되 버려야 한다고 표시했는가(chat.strict-order=false). 읽으면 지운다. */
        default boolean consumeOutOfOrder() { return false; }

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

    private final boolean strictOrder;
    private boolean outOfOrder;

    public SignedMessageChain(UUID profileId, UUID sessionId) {
        this(profileId, sessionId, org.domi.furnace.server.config.OperatorOptions.current().strictChatOrder());
    }

    /** strictOrder=false: 시각이 거꾸로 간 메시지는 서명을 확인해 체인에 이어 붙이되 시각 기준은 그대로 두고, 호출자가 버리게 표시한다. */
    public SignedMessageChain(UUID profileId, UUID sessionId, boolean strictOrder) {
        this.nextLink = SignedMessageLink.root(profileId, sessionId);
        this.strictOrder = strictOrder;
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
                boolean late = body.timeStamp().isBefore(lastTimeStamp);
                if (late && strictOrder) {
                    setChainBroken();
                    throw new DecodeException(DecodeException.OUT_OF_ORDER_CHAT);
                }
                if (!late) lastTimeStamp = body.timeStamp();
                PlayerChatMessage msg = new PlayerChatMessage(link, signature, body, null, false);
                if (!msg.verify(validator)) {
                    setChainBroken();
                    throw new DecodeException(DecodeException.INVALID_SIGNATURE);
                }
                if (msg.hasExpiredServer(Instant.now())) {
                    log.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", body.content());
                }
                nextLink = link.advance();
                if (late) outOfOrder = true;
                return msg;
            }

            @Override
            public boolean consumeOutOfOrder() {
                boolean r = outOfOrder;
                outOfOrder = false;
                return r;
            }

            @Override
            public void setChainBroken() { nextLink = null; }
        };
    }
}
