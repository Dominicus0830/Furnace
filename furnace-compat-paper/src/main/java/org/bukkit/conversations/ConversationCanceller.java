// 입력을 보고 대화를 끝낼지 정한다. 대화마다 복제본을 쓴다(ConversationFactory 가 clone 한다).
package org.bukkit.conversations;

public interface ConversationCanceller extends Cloneable {

    void setConversation(Conversation conversation);

    /** 참이면 이 입력으로 대화를 끝낸다(입력은 단계로 넘어가지 않는다). */
    boolean cancelBasedOnInput(ConversationContext context, String input);

    ConversationCanceller clone();
}
