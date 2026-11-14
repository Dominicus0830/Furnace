// 머리말 없음(대화 기본값).
package org.bukkit.conversations;

public class NullConversationPrefix implements ConversationPrefix {

    @Override
    public String getPrefix(ConversationContext context) { return ""; }
}
