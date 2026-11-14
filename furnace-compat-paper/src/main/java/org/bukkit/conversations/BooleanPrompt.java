// 예/아니오 대답 단계(대소문자 무시): true on yes y 1 right correct valid / false off no n 0 wrong incorrect invalid.
package org.bukkit.conversations;

import java.util.Locale;
import java.util.Set;

public abstract class BooleanPrompt extends ValidatingPrompt {

    private static final Set<String> YES = Set.of("true", "on", "yes", "y", "1", "right", "correct", "valid");
    private static final Set<String> NO = Set.of("false", "off", "no", "n", "0", "wrong", "incorrect", "invalid");

    public BooleanPrompt() {}

    @Override
    protected boolean isInputValid(ConversationContext context, String input) {
        String s = input.toLowerCase(Locale.ROOT);
        return YES.contains(s) || NO.contains(s);
    }

    @Override
    protected Prompt acceptValidatedInput(ConversationContext context, String input) {
        return acceptValidatedInput(context, YES.contains(input.toLowerCase(Locale.ROOT)));
    }

    protected abstract Prompt acceptValidatedInput(ConversationContext context, boolean input);
}
