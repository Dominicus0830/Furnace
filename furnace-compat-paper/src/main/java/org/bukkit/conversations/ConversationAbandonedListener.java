// 대화가 끝날 때 불린다.
package org.bukkit.conversations;

import java.util.EventListener;

public interface ConversationAbandonedListener extends EventListener {

    void conversationAbandoned(ConversationAbandonedEvent abandonedEvent);
}
