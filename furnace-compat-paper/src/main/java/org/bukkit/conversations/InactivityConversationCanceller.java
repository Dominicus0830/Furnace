// 정한 초 동안 입력이 없으면 대화를 끝낸다. 타이머는 Bukkit 동기 지연 작업(초 × 20틱)이고 입력이 올 때마다 다시 잰다.
// 대화가 아직 시작 전(다른 대화 뒤에 줄 서 있음)이면 타이머만 다시 건다.
package org.bukkit.conversations;

import org.bukkit.plugin.Plugin;

public class InactivityConversationCanceller implements ConversationCanceller {

    protected Plugin plugin;
    protected int timeoutSeconds;
    protected Conversation conversation;
    private int taskId = -1;

    public InactivityConversationCanceller(Plugin plugin, int timeoutSeconds) {
        this.plugin = plugin;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
        startTimer();
    }

    @Override
    public boolean cancelBasedOnInput(ConversationContext context, String input) {
        stopTimer();
        startTimer();
        return false;
    }

    @Override
    public ConversationCanceller clone() { return new InactivityConversationCanceller(plugin, timeoutSeconds); }

    private void startTimer() {
        taskId = plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            switch (conversation.getState()) {
                case UNSTARTED -> startTimer();
                case STARTED -> {
                    cancelling(conversation);
                    conversation.abandon(new ConversationAbandonedEvent(conversation, this));
                }
                case ABANDONED -> {}
            }
        }, timeoutSeconds * 20L);
    }

    private void stopTimer() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /** 시간이 다 돼 대화를 끝내기 직전 — 하위 클래스가 알림을 보낼 자리. */
    protected void cancelling(Conversation conversation) {}
}
