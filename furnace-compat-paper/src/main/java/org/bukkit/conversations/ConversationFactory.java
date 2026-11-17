// 같은 모양의 대화를 여러 번 만드는 틀. 기본값: 모달, 되울림, 머리말 없음, 첫 단계 없음(곧바로 끝), 세션 값 없음.
// buildConversation 은 세션 값을 복사하고 canceller 는 clone 해서 대화마다 따로 준다(끝 듣기는 같은 객체를 나눠 쓴다).
package org.bukkit.conversations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ConversationFactory {

    protected Plugin plugin;
    protected boolean isModal;
    protected boolean localEchoEnabled;
    protected ConversationPrefix prefix;
    protected Prompt firstPrompt;
    protected Map<Object, Object> initialSessionData;
    protected String playerOnlyMessage;
    protected List<ConversationCanceller> cancellers;
    protected List<ConversationAbandonedListener> abandonedListeners;

    public ConversationFactory(Plugin plugin) {
        this.plugin = plugin;
        this.isModal = true;
        this.localEchoEnabled = true;
        this.prefix = new NullConversationPrefix();
        this.firstPrompt = Prompt.END_OF_CONVERSATION;
        this.initialSessionData = new HashMap<>();
        this.playerOnlyMessage = null;
        this.cancellers = new ArrayList<>();
        this.abandonedListeners = new ArrayList<>();
    }

    public ConversationFactory withModality(boolean modal) {
        this.isModal = modal;
        return this;
    }

    public ConversationFactory withLocalEcho(boolean localEchoEnabled) {
        this.localEchoEnabled = localEchoEnabled;
        return this;
    }

    public ConversationFactory withPrefix(ConversationPrefix prefix) {
        this.prefix = prefix;
        return this;
    }

    /** 입력 없이 이 초가 지나면 끝낸다. */
    public ConversationFactory withTimeout(int timeoutSeconds) {
        return withConversationCanceller(new InactivityConversationCanceller(plugin, timeoutSeconds));
    }

    public ConversationFactory withFirstPrompt(Prompt firstPrompt) {
        this.firstPrompt = firstPrompt;
        return this;
    }

    public ConversationFactory withInitialSessionData(Map<Object, Object> initialSessionData) {
        this.initialSessionData = initialSessionData;
        return this;
    }

    /** 이 문자열을 치면 끝낸다. */
    public ConversationFactory withEscapeSequence(String escapeSequence) {
        return withConversationCanceller(new ExactMatchConversationCanceller(escapeSequence));
    }

    public ConversationFactory withConversationCanceller(ConversationCanceller canceller) {
        cancellers.add(canceller);
        return this;
    }

    /** 플레이어가 아닌 상대(콘솔)에게는 대화 대신 이 메시지 하나만 보이고 끝낸다. null 이면 누구와도 대화한다. */
    public ConversationFactory thatExcludesNonPlayersWithMessage(String playerOnlyMessage) {
        this.playerOnlyMessage = playerOnlyMessage;
        return this;
    }

    public ConversationFactory addConversationAbandonedListener(ConversationAbandonedListener listener) {
        abandonedListeners.add(listener);
        return this;
    }

    public Conversation buildConversation(Conversable forWhom) {
        if (playerOnlyMessage != null && !(forWhom instanceof Player)) {
            return new Conversation(plugin, forWhom, new NotPlayerMessagePrompt());   // 머리말·canceller 없이 메시지만
        }
        Conversation c = new Conversation(plugin, forWhom, firstPrompt, new HashMap<>(initialSessionData));
        c.setModal(isModal);
        c.setLocalEchoEnabled(localEchoEnabled);
        c.setPrefix(prefix);
        for (ConversationCanceller cc : cancellers) c.addConversationCanceller(cc.clone());
        for (ConversationAbandonedListener l : abandonedListeners) c.addConversationAbandonedListener(l);
        return c;
    }

    private final class NotPlayerMessagePrompt extends MessagePrompt {

        @Override
        public String getPromptText(ConversationContext context) { return playerOnlyMessage; }

        @Override
        protected Prompt getNextPrompt(ConversationContext context) { return Prompt.END_OF_CONVERSATION; }
    }
}
