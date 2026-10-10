// 바닐라 SharedSuggestionProvider.suggest — 입력이 후보의 앞이나 '_'·'.' 다음 부분과 맞으면 제안.
package org.domi.furnace.server.command;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;

public final class SharedSuggestions {

    private SharedSuggestions() {}

    public static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String s : values) if (matchesSubStr(remaining, s.toLowerCase(Locale.ROOT))) builder.suggest(s);
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggest(String[] values, SuggestionsBuilder builder) {
        return suggest(java.util.Arrays.asList(values), builder);
    }

    static boolean matchesSubStr(String input, String substring) {
        for (int i = 0; !substring.startsWith(input, i); i++) {
            int dot = substring.indexOf('.', i);
            int under = substring.indexOf('_', i);
            if (Math.max(dot, under) < 0) return false;
            if (dot >= 0 && under >= 0) i = Math.min(under, dot);
            else i = dot >= 0 ? dot : under;
        }
        return true;
    }
}
