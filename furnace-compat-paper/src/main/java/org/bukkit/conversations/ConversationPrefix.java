// 대화가 보내는 모든 줄 앞에 붙는 머리말.
package org.bukkit.conversations;

public interface ConversationPrefix {

    String getPrefix(ConversationContext context);
}
