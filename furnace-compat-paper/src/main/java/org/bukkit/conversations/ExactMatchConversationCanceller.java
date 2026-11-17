// 입력이 정해진 문자열과 똑같으면(대소문자 구분) 대화를 끝낸다.
package org.bukkit.conversations;

public class ExactMatchConversationCanceller implements ConversationCanceller {

    private final String escapeSequence;

    public ExactMatchConversationCanceller(String escapeSequence) { this.escapeSequence = escapeSequence; }

    @Override
    public void setConversation(Conversation conversation) {}

    @Override
    public boolean cancelBasedOnInput(ConversationContext context, String input) { return input.equals(escapeSequence); }

    @Override
    public ConversationCanceller clone() { return new ExactMatchConversationCanceller(escapeSequence); }
}
