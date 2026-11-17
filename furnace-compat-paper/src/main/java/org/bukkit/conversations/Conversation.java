// 대화: 상대에게 단계(Prompt)를 차례로 보여 주고 대답을 받는다. 기본은 모달(대화 중 다른 메시지를 막음)·입력 되울림 켜짐·머리말 없음.
// 대화를 시작하면 상대(Conversable)에게 맡기고, 상대가 입력을 acceptInput 으로 넘긴다. 단계가 끝(null)을 돌려주면 정상 종료다.
package org.bukkit.conversations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

public class Conversation {

    private final Prompt firstPrompt;
    private boolean abandoned;
    protected Prompt currentPrompt;
    protected ConversationContext context;
    protected boolean modal;
    protected boolean localEchoEnabled;
    protected ConversationPrefix prefix;
    protected List<ConversationCanceller> cancellers;
    protected List<ConversationAbandonedListener> abandonedListeners;

    public Conversation(Plugin plugin, Conversable forWhom, Prompt firstPrompt) {
        this(plugin, forWhom, firstPrompt, new HashMap<>());
    }

    public Conversation(Plugin plugin, Conversable forWhom, Prompt firstPrompt, Map<Object, Object> initialSessionData) {
        this.firstPrompt = firstPrompt;
        this.context = new ConversationContext(plugin, forWhom, initialSessionData);
        this.modal = true;
        this.localEchoEnabled = true;
        this.prefix = new NullConversationPrefix();
        this.cancellers = new ArrayList<>();
        this.abandonedListeners = new ArrayList<>();
    }

    public Conversable getForWhom() { return context.getForWhom(); }

    /** 모달이면 대화 중에 상대가 다른 메시지(플러그인 sendMessage)를 받지 않는다. */
    public boolean isModal() { return modal; }

    void setModal(boolean modal) { this.modal = modal; }

    /** 상대가 친 입력을 머리말과 함께 되돌려 보여 주는가. */
    public boolean isLocalEchoEnabled() { return localEchoEnabled; }

    public void setLocalEchoEnabled(boolean localEchoEnabled) { this.localEchoEnabled = localEchoEnabled; }

    public ConversationPrefix getPrefix() { return prefix; }

    void setPrefix(ConversationPrefix prefix) { this.prefix = prefix; }

    void addConversationCanceller(ConversationCanceller canceller) {
        canceller.setConversation(this);
        cancellers.add(canceller);
    }

    public List<ConversationCanceller> getCancellers() { return cancellers; }

    public ConversationContext getContext() { return context; }

    /** 시작한다(이미 시작했으면 아무 일 없다). 상대가 다른 대화 중이면 그 뒤로 줄 선다. */
    public void begin() {
        if (currentPrompt != null) return;
        abandoned = false;
        currentPrompt = firstPrompt;
        context.getForWhom().beginConversation(this);
    }

    public ConversationState getState() {
        if (currentPrompt != null) return ConversationState.STARTED;
        return abandoned ? ConversationState.ABANDONED : ConversationState.UNSTARTED;
    }

    /** 상대의 입력 한 줄: 되울림 → 끝내기 검사(첫 번째로 참인 canceller 가 끝낸다) → 지금 단계에 넘기고 다음 단계를 보여 준다. */
    public void acceptInput(String input) {
        if (currentPrompt == null) return;
        if (localEchoEnabled) context.getForWhom().sendRawMessage(prefix.getPrefix(context) + input);
        for (ConversationCanceller c : cancellers) {
            if (c.cancelBasedOnInput(context, input)) {
                abandon(new ConversationAbandonedEvent(this, c));
                return;
            }
        }
        currentPrompt = currentPrompt.acceptInput(context, input);
        outputNextPrompt();
    }

    public synchronized void addConversationAbandonedListener(ConversationAbandonedListener listener) { abandonedListeners.add(listener); }

    public synchronized void removeConversationAbandonedListener(ConversationAbandonedListener listener) { abandonedListeners.remove(listener); }

    /** 손으로 끝낸다(이유: {@link ManuallyAbandonedConversationCanceller}). */
    public void abandon() { abandon(new ConversationAbandonedEvent(this, new ManuallyAbandonedConversationCanceller())); }

    /** 끝낸다 — 한 번만: 상대에게서 떼고 끝 듣기들을 부른다. */
    public synchronized void abandon(ConversationAbandonedEvent details) {
        if (abandoned) return;
        abandoned = true;
        currentPrompt = null;
        context.getForWhom().abandonConversation(this);
        for (ConversationAbandonedListener l : abandonedListeners) l.conversationAbandoned(details);
    }

    /** 지금 단계의 문구를 보여 준다. 입력을 기다리지 않는 단계는 곧바로 넘어간다. 단계가 없으면 정상 종료. */
    public void outputNextPrompt() {
        while (true) {
            if (currentPrompt == null) {
                abandon(new ConversationAbandonedEvent(this));
                return;
            }
            context.getForWhom().sendRawMessage(prefix.getPrefix(context) + currentPrompt.getPromptText(context));
            if (currentPrompt.blocksForInput(context)) return;
            currentPrompt = currentPrompt.acceptInput(context, null);
        }
    }

    public enum ConversationState {
        UNSTARTED,
        STARTED,
        ABANDONED
    }
}
