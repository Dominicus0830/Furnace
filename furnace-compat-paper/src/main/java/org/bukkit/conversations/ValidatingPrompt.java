// 대답을 검사하는 단계. 틀리면 실패 문구(있으면 빨간색으로)를 보이고 같은 단계를 다시 보여 준다.
package org.bukkit.conversations;

import org.bukkit.ChatColor;

public abstract class ValidatingPrompt implements Prompt {

    public ValidatingPrompt() {}

    @Override
    public Prompt acceptInput(ConversationContext context, String input) {
        if (isInputValid(context, input)) return acceptValidatedInput(context, input);
        String fail = getFailedValidationText(context, input);
        if (fail != null) context.getForWhom().sendRawMessage(ChatColor.RED + fail);
        return this;
    }

    @Override
    public boolean blocksForInput(ConversationContext context) { return true; }

    protected abstract boolean isInputValid(ConversationContext context, String input);

    protected abstract Prompt acceptValidatedInput(ConversationContext context, String input);

    /** 틀린 대답에 보일 문구. null 이면 아무것도 안 보이고 다시 묻는다. */
    protected String getFailedValidationText(ConversationContext context, String invalidInput) { return null; }
}
