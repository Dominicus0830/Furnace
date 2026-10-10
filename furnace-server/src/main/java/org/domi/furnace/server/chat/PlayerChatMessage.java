// 바닐라 PlayerChatMessage — 사슬 고리·서명·본문·서명되지 않은 꾸민 내용·필터 마스크.
package org.domi.furnace.server.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.Text;

public record PlayerChatMessage(SignedMessageLink link, MessageSignature signature, SignedMessageBody signedBody,
                                Text unsignedContent, boolean fullyFiltered) {

    /** 서버 기준 만료(바닐라 MESSAGE_EXPIRES_AFTER_SERVER = 5분). */
    public static final Duration MESSAGE_EXPIRES_AFTER_SERVER = Duration.ofMinutes(5);

    public static PlayerChatMessage system(String content) { return unsigned(SignedMessageLink.NIL_UUID, content); }

    public static PlayerChatMessage unsigned(UUID sender, String content) {
        return new PlayerChatMessage(SignedMessageLink.unsigned(sender), null, SignedMessageBody.unsigned(content), null, false);
    }

    /** 꾸민 내용이 서명된 원문과 같으면 서명되지 않은 내용을 두지 않는다. */
    public PlayerChatMessage withUnsignedContent(Text text) {
        Text u = text.toJson().equals(Text.literal(signedContent()).toJson()) ? null : text;
        return new PlayerChatMessage(link, signature, signedBody, u, fullyFiltered);
    }

    public static void updateSignature(java.security.Signature out, SignedMessageLink link, SignedMessageBody body)
            throws java.security.SignatureException {
        out.update(Bytes.ofInt(1));
        link.updateSignature(out);
        body.updateSignature(out);
    }

    public boolean verify(SignatureValidator validator) {
        return signature != null && validator.validate(out -> updateSignature(out, link, signedBody), signature.bytes());
    }

    public String signedContent() { return signedBody.content(); }

    public Text decoratedContent() { return unsignedContent != null ? unsignedContent : Text.literal(signedContent()); }

    public Instant timeStamp() { return signedBody.timeStamp(); }

    public boolean hasExpiredServer(Instant now) { return now.isAfter(timeStamp().plus(MESSAGE_EXPIRES_AFTER_SERVER)); }

    public UUID sender() { return link.sender(); }

    public boolean isSystem() { return sender().equals(SignedMessageLink.NIL_UUID); }

    public boolean hasSignature() { return signature != null; }
}
