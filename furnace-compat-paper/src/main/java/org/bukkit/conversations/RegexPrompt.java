// 정규식에 통째로 맞는(matches) 대답을 받는 단계.
package org.bukkit.conversations;

import java.util.regex.Pattern;

public abstract class RegexPrompt extends ValidatingPrompt {

    private final Pattern pattern;

    public RegexPrompt(String regex) { this(Pattern.compile(regex)); }

    public RegexPrompt(Pattern pattern) { this.pattern = pattern; }

    @Override
    protected boolean isInputValid(ConversationContext context, String input) { return pattern.matcher(input).matches(); }
}
