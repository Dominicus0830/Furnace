// 대화가 끝났다. canceller 가 null 이면 마지막 단계가 끝을 돌려준 정상 종료다.
package org.bukkit.conversations;

import java.util.EventObject;

public class ConversationAbandonedEvent extends EventObject {

    private final ConversationContext context;
    private final ConversationCanceller canceller;

    public ConversationAbandonedEvent(Conversation conversation) { this(conversation, null); }

    public ConversationAbandonedEvent(Conversation conversation, ConversationCanceller canceller) {
        super(conversation);
        this.context = conversation.getContext();
        this.canceller = canceller;
    }

    public ConversationCanceller getCanceller() { return canceller; }

    public ConversationContext getContext() { return context; }

    /** 정상 종료(끝낸 canceller 가 없다)인가. */
    public boolean gracefulExit() { return canceller == null; }
}
