// 바닐라 RemoteChatSession — 세션 id + 검증된 프로필 공개키.
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;

public record RemoteChatSession(UUID sessionId, ProfilePublicKey profilePublicKey) {

    public SignedMessageChain.Decoder createMessageDecoder(UUID profileId) {
        return new SignedMessageChain(profileId, sessionId).decoder(profilePublicKey);
    }

    public SignedMessageChain.Decoder createMessageDecoder(UUID profileId, boolean strictOrder) {
        return new SignedMessageChain(profileId, sessionId, strictOrder).decoder(profilePublicKey);
    }

    public Data asData() { return new Data(sessionId, profilePublicKey.data()); }

    public record Data(UUID sessionId, ProfilePublicKey.Data profilePublicKey) {
        public static Data read(ByteBuf buf) { return new Data(Types.readUuid(buf), ProfilePublicKey.Data.read(buf)); }

        public void write(ByteBuf buf) {
            Types.writeUuid(buf, sessionId);
            profilePublicKey.write(buf);
        }

        public RemoteChatSession validate(UUID profileId, SignatureValidator services) throws ProfilePublicKey.ValidationException {
            return new RemoteChatSession(sessionId, ProfilePublicKey.createValidated(services, profileId, profilePublicKey));
        }
    }
}
