// 정해진 보기 중 하나(대소문자 구분)를 대답으로 받는 단계.
package org.bukkit.conversations;

import java.util.Arrays;
import java.util.List;

public abstract class FixedSetPrompt extends ValidatingPrompt {

    protected List<String> fixedSet;

    public FixedSetPrompt(String... fixedSet) { this.fixedSet = Arrays.asList(fixedSet); }

    @Override
    protected boolean isInputValid(ConversationContext context, String input) { return fixedSet.contains(input); }

    /** 보기를 "[a, b, c]" 꼴로. */
    protected String formatFixedSet() { return "[" + String.join(", ", fixedSet) + "]"; }
}
