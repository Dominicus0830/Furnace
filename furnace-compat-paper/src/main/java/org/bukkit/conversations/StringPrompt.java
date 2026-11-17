// 아무 문자열이나 대답으로 받는 단계.
package org.bukkit.conversations;

public abstract class StringPrompt implements Prompt {

    @Override
    public boolean blocksForInput(ConversationContext context) { return true; }
}
