// 입력을 기다리지 않고 문구만 보여 주는 단계 — 보여 준 뒤 getNextPrompt 로 넘어간다.
package org.bukkit.conversations;

public abstract class MessagePrompt implements Prompt {

    public MessagePrompt() {}

    @Override
    public boolean blocksForInput(ConversationContext context) { return false; }

    @Override
    public Prompt acceptInput(ConversationContext context, String input) { return getNextPrompt(context); }

    protected abstract Prompt getNextPrompt(ConversationContext context);
}
