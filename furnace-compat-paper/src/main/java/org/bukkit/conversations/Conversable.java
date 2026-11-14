// 대화(Conversation)를 받을 수 있는 쪽 — 플레이어와 콘솔. 대화 중에는 채팅(콘솔은 입력 줄)이 대화의 대답으로 간다.
package org.bukkit.conversations;

import java.util.UUID;

public interface Conversable {

    /** 지금 대화 중인가(대기 중인 대화만 있으면 거짓 — 맨 앞 대화가 시작돼야 참). */
    boolean isConversing();

    /** 대화의 대답으로 입력 한 줄을 넘긴다. 대화 중이 아니면 무시. */
    void acceptConversationInput(String input);

    /** 대화를 시작한다. 이미 대화 중이면 줄을 세워 앞 대화가 끝난 뒤 시작한다. 줄에 넣었으면 false. */
    boolean beginConversation(Conversation conversation);

    /** 대화를 끝낸다(줄에 있던 것이면 빼기만 한다). */
    void abandonConversation(Conversation conversation);

    /** 위와 같고 끝난 이유를 함께 넘긴다. */
    void abandonConversation(Conversation conversation, ConversationAbandonedEvent details);

    /** 대화 중에도(모달 대화가 다른 메시지를 막는 동안에도) 보이는 메시지. */
    void sendRawMessage(String message);

    /** 위와 같고 보낸 사람을 함께(클라이언트 채팅 숨기기용). */
    void sendRawMessage(UUID sender, String message);
}
