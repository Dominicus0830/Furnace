// 채팅 — 그 플레이어의 연결 스레드에서(서버 틱과 무관하게) 불린다. 취소하면 아무에게도 가지 않는다.
// 내용을 바꾸면 바닐라처럼 "서명 없는 내용(unsigned_content)"으로 보낸다(서명은 원문 기준으로 유지된다).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.text.Text;

public final class AsyncChatEvent extends PlayerEvent implements Cancellable {

    private final String originalMessage;
    private Text message;
    private boolean cancelled;
    private final byte[] signature;

    public AsyncChatEvent(Player player, String originalMessage, Text message) {
        this(player, originalMessage, message, null);
    }

    /** @param signature 메시지 서명(256바이트), 서명 없는 채팅이면 null */
    public AsyncChatEvent(Player player, String originalMessage, Text message, byte[] signature) {
        super(player);
        this.originalMessage = originalMessage;
        this.message = message;
        this.signature = signature == null ? null : signature.clone();
    }

    /** 메시지 서명 사본(256바이트), 서명 없는 채팅이면 null. 나중에 Player.deleteChatMessage 로 지울 때 쓴다. */
    public byte[] signature() { return signature == null ? null : signature.clone(); }

    /** 플레이어가 보낸 원문(서명 대상). */
    public String originalMessage() { return originalMessage; }

    public Text message() { return message; }

    public void message(Text message) { this.message = message; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
