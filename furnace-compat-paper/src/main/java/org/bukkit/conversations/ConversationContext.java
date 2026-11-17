// 대화 하나의 상태: 시작한 플러그인, 상대, 단계끼리 주고받는 세션 값(만들 때 받은 맵을 그대로 쓴다).
package org.bukkit.conversations;

import java.util.Map;
import org.bukkit.plugin.Plugin;

public class ConversationContext {

    private final Plugin plugin;
    private final Conversable forWhom;
    private final Map<Object, Object> sessionData;

    public ConversationContext(Plugin plugin, Conversable forWhom, Map<Object, Object> initialSessionData) {
        this.plugin = plugin;
        this.forWhom = forWhom;
        this.sessionData = initialSessionData;
    }

    public Plugin getPlugin() { return plugin; }

    public Conversable getForWhom() { return forWhom; }

    public Map<Object, Object> getAllSessionData() { return sessionData; }

    public Object getSessionData(Object key) { return sessionData.get(key); }

    public void setSessionData(Object key, Object value) { sessionData.put(key, value); }
}
