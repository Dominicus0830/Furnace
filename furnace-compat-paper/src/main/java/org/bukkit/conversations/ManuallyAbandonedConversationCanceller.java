// Conversation.abandon() 으로 끝났다는 표지로만 쓰인다(ConversationAbandonedEvent.getCanceller). 대화에 달아 쓰는 것이 아니다.
package org.bukkit.conversations;

public class ManuallyAbandonedConversationCanceller implements ConversationCanceller {

    @Override
    public void setConversation(Conversation conversation) { throw new UnsupportedOperationException(); }

    @Override
    public boolean cancelBasedOnInput(ConversationContext context, String input) { throw new UnsupportedOperationException(); }

    @Override
    public ConversationCanceller clone() { throw new UnsupportedOperationException(); }
}
